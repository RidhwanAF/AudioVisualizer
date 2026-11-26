package com.raf.audiovisualizer.data.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioPlaybackConfiguration
import android.media.audiofx.Visualizer
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.raf.audiovisualizer.domain.audio.AudioSignalAnalyzer
import com.raf.audiovisualizer.domain.model.AudioTelemetry
import com.raf.audiovisualizer.domain.repository.AudioVisualizerRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.log10

/**
 * First-party Android [Visualizer] (`audioSessionId = 0`) repository polling synchronized FFT +
 * PCM waveform frames at 40 Hz (`25 ms`) on a dedicated background [HandlerThread], fused with
 * 32-bit [Visualizer.MEASUREMENT_MODE_PEAK_RMS] loudness compensated via
 * [AudioManager.getStreamVolumeDb].
 *
 * Uses non-destructive snapshot polling (`getFft` + `getWaveForm`) so it coexists smoothly even
 * when another overlay visualizer (such as CapsuleOS) is active simultaneously on session `0`,
 * avoiding exclusive-control ping-pong while still recovering automatically if the HAL stalls.
 */
@Singleton
class AudioVisualizerRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : AudioVisualizerRepository {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val workerThread = HandlerThread("audio-vis-worker").apply {
        isDaemon = true
        start()
    }
    private val workerHandler = Handler(workerThread.looper)
    private val workerExecutor = Executor { runnable -> workerHandler.post(runnable) }

    private val analyzer = AudioSignalAnalyzer()
    private val peakRms = Visualizer.MeasurementPeakRms()

    private val _telemetry = MutableStateFlow(AudioTelemetry.Silent)
    override val telemetry: StateFlow<AudioTelemetry> = _telemetry.asStateFlow()

    private var visualizer: Visualizer? = null
    private var fftBuffer = ByteArray(TARGET_CAPTURE_SIZE)
    private var waveBuffer = ByteArray(TARGET_CAPTURE_SIZE)
    private var samplingRateMilliHz = DEFAULT_SAMPLE_RATE_MHZ
    private var captureRequested = false
    private var listenersRegistered = false

    private var lastAttachElapsedMs = 0L
    private var lastSignalElapsedMs = 0L
    private var consecutiveErrorTicks = 0

    private data class LoudnessMetrics(
        val measurementValid: Boolean,
        val gain: Float,
        val crestFactor: Float,
        val rmsDb: Float,
        val peakDb: Float,
        val volDb: Float,
        val srcDb: Float,
    )

    private val audioModeListener = AudioManager.OnModeChangedListener { mode ->
        if (!captureRequested) return@OnModeChangedListener
        if (isCallAudioMode(mode)) {
            releaseVisualizerOnWorker(clearState = true)
        } else if (visualizer == null) {
            attachVisualizerOnWorker(preserveAnalyzer = false)
        }
    }

    private val playbackConfigCallback = object : AudioManager.AudioPlaybackCallback() {
        override fun onPlaybackConfigChanged(configs: List<AudioPlaybackConfiguration>) {
            if (!captureRequested) return
            consecutiveErrorTicks = 0
        }
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!captureRequested) return
            pollFrameOnWorker()
            workerHandler.postDelayed(this, POLL_INTERVAL_MS)
        }
    }

    override fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    override fun setCaptureActive(active: Boolean) {
        workerHandler.post {
            if (captureRequested == active) {
                if (active && visualizer == null && hasAudioPermission() && !isCallAudioMode(audioManager.mode)) {
                    attachVisualizerOnWorker(preserveAnalyzer = false)
                }
                return@post
            }
            captureRequested = active
            if (active) {
                registerSystemListenersOnWorker()
                if (!isCallAudioMode(audioManager.mode)) {
                    attachVisualizerOnWorker(preserveAnalyzer = false)
                }
                workerHandler.removeCallbacks(pollRunnable)
                workerHandler.post(pollRunnable)
            } else {
                workerHandler.removeCallbacks(pollRunnable)
                unregisterSystemListenersOnWorker()
                releaseVisualizerOnWorker(clearState = true)
            }
        }
    }

    private fun pollFrameOnWorker() {
        if (isCallAudioMode(audioManager.mode)) {
            if (visualizer != null) {
                releaseVisualizerOnWorker(clearState = true)
            }
            return
        }
        val vis = visualizer
        if (vis == null) {
            if (hasAudioPermission()) {
                attachVisualizerOnWorker(preserveAnalyzer = false)
            }
            return
        }

        val now = SystemClock.elapsedRealtime()
        val fftStatus = runCatching { vis.getFft(fftBuffer) }.getOrDefault(Visualizer.ERROR)
        val waveStatus = runCatching { vis.getWaveForm(waveBuffer) }.getOrDefault(Visualizer.ERROR)

        if (fftStatus != Visualizer.SUCCESS) {
            consecutiveErrorTicks++
            if (consecutiveErrorTicks >= ERROR_RECOVERY_TICKS &&
                now - lastAttachElapsedMs > REATTACH_COOLDOWN_MS
            ) {
                consecutiveErrorTicks = 0
                Log.d(TAG, "Reattaching Visualizer after poll error (status=$fftStatus)")
                reattachVisualizerOnWorker()
            }
            return
        }

        consecutiveErrorTicks = 0
        val hasFftEnergy = hasNonZeroFftEnergy(fftBuffer)
        val metrics = readTrackLoudnessMetrics(vis, hasFftEnergy)

        val musicAudible = audioManager.isMusicActive &&
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) > 0

        if (!metrics.measurementValid && !hasFftEnergy) {
            if (musicAudible &&
                now - lastAttachElapsedMs > REATTACH_COOLDOWN_MS &&
                now - lastSignalElapsedMs > DEAD_SIGNAL_THRESHOLD_MS
            ) {
                Log.d(TAG, "Reattaching Visualizer after dead signal while music active")
                reattachVisualizerOnWorker()
            }
            return
        }

        if (hasFftEnergy || (metrics.measurementValid && metrics.gain > 0.01f)) {
            lastSignalElapsedMs = now
        }

        if (waveStatus == Visualizer.SUCCESS) {
            analyzer.updateWaveform(waveBuffer, metrics.gain)
        }

        _telemetry.value = analyzer.analyzeFft(
            fft = fftBuffer,
            samplingRateMilliHz = samplingRateMilliHz,
            loudnessGain = metrics.gain,
            crestFactor = metrics.crestFactor,
            rmsDb = metrics.rmsDb,
            peakDb = metrics.peakDb,
            volumeAttenuationDb = metrics.volDb,
            estimatedSourceDb = metrics.srcDb,
            timestampMs = now,
        )
    }

    private fun reattachVisualizerOnWorker() {
        releaseVisualizerOnWorker(clearState = false)
        attachVisualizerOnWorker(preserveAnalyzer = true)
    }

    private fun registerSystemListenersOnWorker() {
        if (listenersRegistered) return
        runCatching {
            audioManager.addOnModeChangedListener(workerExecutor, audioModeListener)
            audioManager.registerAudioPlaybackCallback(playbackConfigCallback, workerHandler)
            listenersRegistered = true
        }
    }

    private fun unregisterSystemListenersOnWorker() {
        if (!listenersRegistered) return
        runCatching { audioManager.removeOnModeChangedListener(audioModeListener) }
        runCatching { audioManager.unregisterAudioPlaybackCallback(playbackConfigCallback) }
        listenersRegistered = false
    }

    private fun attachVisualizerOnWorker(preserveAnalyzer: Boolean) {
        if (visualizer != null) return
        if (!hasAudioPermission()) {
            _telemetry.value = AudioTelemetry.Silent
            return
        }
        runCatching {
            val range = Visualizer.getCaptureSizeRange()
            val minSize = range.getOrElse(0) { 256 }
            val maxSize = range.getOrElse(1) { 1024 }
            val preferredSize = TARGET_CAPTURE_SIZE.coerceIn(minSize, maxSize)

            if (preserveAnalyzer) {
                analyzer.softRecover()
            } else {
                analyzer.reset()
            }
            val now = SystemClock.elapsedRealtime()
            lastAttachElapsedMs = now
            lastSignalElapsedMs = now
            consecutiveErrorTicks = 0

            val created = Visualizer(GLOBAL_OUTPUT_MIX_SESSION_ID).apply {
                runCatching { enabled = false }
                runCatching { captureSize = preferredSize }
                runCatching { scalingMode = Visualizer.SCALING_MODE_NORMALIZED }
                runCatching { measurementMode = Visualizer.MEASUREMENT_MODE_PEAK_RMS }
                runCatching { enabled = true }
            }
            val actualSize = runCatching { created.captureSize }.getOrDefault(preferredSize).coerceAtLeast(256)
            if (fftBuffer.size != actualSize) fftBuffer = ByteArray(actualSize)
            if (waveBuffer.size != actualSize) waveBuffer = ByteArray(actualSize)
            samplingRateMilliHz = runCatching { created.samplingRate }
                .getOrDefault(DEFAULT_SAMPLE_RATE_MHZ)
                .coerceAtLeast(8_000_000)

            visualizer = created
            Log.d(TAG, "Visualizer attached (size=$actualSize, rate=${samplingRateMilliHz}mHz, seamless=$preserveAnalyzer)")
        }.onFailure { err ->
            Log.w(TAG, "Visualizer attach failed: ${err.message}")
            releaseVisualizerOnWorker(clearState = !preserveAnalyzer)
        }
    }

    private fun hasNonZeroFftEnergy(fft: ByteArray): Boolean {
        val limit = fft.size.coerceAtMost(256)
        return (2 until limit).any { idx -> fft[idx].toInt() != 0 }
    }

    private fun readTrackLoudnessMetrics(vis: Visualizer, hasFftEnergy: Boolean): LoudnessMetrics {
        val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (curVol <= 0) {
            return LoudnessMetrics(true, 0f, 0f, -96f, -96f, -96f, -96f)
        }

        val status = runCatching { vis.getMeasurementPeakRms(peakRms) }.getOrDefault(Visualizer.ERROR)
        if (status != Visualizer.SUCCESS) {
            val fallbackGain = if (hasFftEnergy) 0.55f else 0f
            return LoudnessMetrics(
                measurementValid = false,
                gain = fallbackGain,
                crestFactor = 0f,
                rmsDb = -96f,
                peakDb = -96f,
                volDb = 0f,
                srcDb = if (hasFftEnergy) -20f else -96f,
            )
        }

        val rmsDb = peakRms.mRms / 100f
        val peakDb = peakRms.mPeak / 100f
        if (peakDb <= -90f && rmsDb <= -90f) {
            return LoudnessMetrics(true, 0f, 0f, rmsDb, peakDb, 0f, -96f)
        }

        val volDb = resolveCurrentMusicVolumeDb(curVol)
        val blendedMeasuredDb = rmsDb * 0.60f + peakDb * 0.40f
        val sourceDb = blendedMeasuredDb - volDb
        val gain = ((sourceDb - MIN_SOURCE_DB) / (MAX_SOURCE_DB - MIN_SOURCE_DB)).coerceIn(0f, 1f)
        val crestFactor = (((peakDb - rmsDb) - CREST_FLOOR_DB) / CREST_SPAN_DB).coerceIn(0f, 1f)
        return LoudnessMetrics(
            measurementValid = true,
            gain = gain,
            crestFactor = crestFactor,
            rmsDb = rmsDb,
            peakDb = peakDb,
            volDb = volDb,
            srcDb = sourceDb,
        )
    }

    private fun resolveCurrentMusicVolumeDb(curVol: Int): Float {
        val activeDeviceType = runCatching {
            audioManager.getAudioDevicesForAttributes(MUSIC_ATTRIBUTES).firstOrNull()?.type
        }.getOrNull() ?: AudioDeviceInfo.TYPE_BUILTIN_SPEAKER

        val hwDb = runCatching {
            audioManager.getStreamVolumeDb(AudioManager.STREAM_MUSIC, curVol, activeDeviceType)
        }.getOrNull()

        if (hwDb != null && hwDb.isFinite() && hwDb <= 0f && hwDb > -90f) {
            return hwDb
        }

        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val ratio = (curVol.toFloat() / maxVol.toFloat()).coerceIn(0.04f, 1f)
        return 20f * log10(ratio)
    }

    private fun releaseVisualizerOnWorker(clearState: Boolean) {
        val current = visualizer
        visualizer = null
        if (current != null) {
            runCatching { current.enabled = false }
            runCatching { current.release() }
        }
        if (clearState) {
            analyzer.reset()
            _telemetry.value = AudioTelemetry.Silent
        }
    }

    private fun isCallAudioMode(mode: Int): Boolean =
        mode == AudioManager.MODE_IN_CALL ||
            mode == AudioManager.MODE_IN_COMMUNICATION ||
            mode == AudioManager.MODE_RINGTONE ||
            mode == AudioManager.MODE_CALL_SCREENING

    private companion object {
        const val TAG = "AudioVisEngine"
        const val GLOBAL_OUTPUT_MIX_SESSION_ID = 0
        const val TARGET_CAPTURE_SIZE = 1024
        const val DEFAULT_SAMPLE_RATE_MHZ = 48_000_000
        const val POLL_INTERVAL_MS = 25L
        const val MIN_SOURCE_DB = -42f
        const val MAX_SOURCE_DB = -6f
        const val CREST_FLOOR_DB = 4.5f
        const val CREST_SPAN_DB = 11.5f
        const val ERROR_RECOVERY_TICKS = 16
        const val DEAD_SIGNAL_THRESHOLD_MS = 1_500L
        const val REATTACH_COOLDOWN_MS = 1_600L
        val MUSIC_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
    }
}
