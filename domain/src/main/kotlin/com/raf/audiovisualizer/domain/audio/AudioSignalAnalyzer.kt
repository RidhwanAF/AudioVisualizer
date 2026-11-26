package com.raf.audiovisualizer.domain.audio

import com.raf.audiovisualizer.domain.model.AudioTelemetry
import com.raf.audiovisualizer.domain.model.FrequencyBandLevel
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pure-Kotlin real-time DSP analyzer combining FFT frequency spectrum, 48-band logarithmic
 * equalizer mapping, rhythm/BPM onset tracking, and time-domain waveform metrics.
 *
 * Uses only standard `kotlin.math` primitives (`hypot`, `ln`, `pow`, `sqrt`, `abs`) with zero
 * Android framework dependencies so it is 100% unit-testable on the JVM.
 */
class AudioSignalAnalyzer {

    private var smoothSubBass = 0f
    private var smoothBass = 0f
    private var smoothLowMid = 0f
    private var smoothMid = 0f
    private var smoothHighMid = 0f
    private var smoothTreble = 0f
    private var smoothBrilliance = 0f
    private var smoothEnergy = 0f
    private var smoothCentroidHz = 0f
    private var smoothFluxNorm = 0f
    private var smoothWaveRms = 0f
    private var smoothWavePeak = 0f
    private var smoothZcr = 0f

    private var bassBaseline = 0f
    private var hasBaseline = false
    private var currentBeatPulse = 0f

    private val smoothBars = FloatArray(AudioTelemetry.FFT_BAR_COUNT)
    private val peakHoldBars = FloatArray(AudioTelemetry.FFT_BAR_COUNT)
    private var latestWaveform = FloatArray(AudioTelemetry.WAVEFORM_SAMPLE_COUNT)

    private val onsetTimestampsMs = ArrayDeque<Long>()
    private var lastOnsetMs = 0L
    private var estimatedBpm = 0

    private class BandAccumulator {
        var sum = 0.0
        var max = 0.0
        var count = 0

        fun add(mag: Double) {
            sum += mag
            if (mag > max) max = mag
            count++
        }

        fun blended(): Double {
            if (count <= 0) return 0.0
            return (sum / count) * 0.40 + max * 0.60
        }
    }

    /** Resets all smoothing envelopes, peak-hold caps, waveform samples, and tempo history. */
    fun reset() {
        smoothSubBass = 0f
        smoothBass = 0f
        smoothLowMid = 0f
        smoothMid = 0f
        smoothHighMid = 0f
        smoothTreble = 0f
        smoothBrilliance = 0f
        smoothEnergy = 0f
        smoothCentroidHz = 0f
        smoothFluxNorm = 0f
        smoothWaveRms = 0f
        smoothWavePeak = 0f
        smoothZcr = 0f
        bassBaseline = 0f
        hasBaseline = false
        currentBeatPulse = 0f
        smoothBars.fill(0f)
        peakHoldBars.fill(0f)
        latestWaveform.fill(0f)
        onsetTimestampsMs.clear()
        lastOnsetMs = 0L
        estimatedBpm = 0
    }

    /**
     * Preserves live smoothed bands and tempo lock across seamless Audio HAL re-attachments while
     * clearing only the flux baseline so recovery produces no synthetic spike or dip.
     */
    fun softRecover() {
        hasBaseline = false
    }

    /**
     * Ingests a raw unsigned 8-bit PCM waveform buffer (`0..255`, centered at `128`) and updates
     * the downsampled oscilloscope samples, RMS, peak amplitude, and zero-crossing rate.
     */
    fun updateWaveform(waveform: ByteArray, loudnessGain: Float) {
        if (waveform.size < 4) return
        val gain = loudnessGain.coerceIn(0f, 1f)
        val n = waveform.size
        var sumSq = 0.0
        var maxAmp = 0.0
        var zeroCrossings = 0
        var prevSign = 0

        (0 until n).forEach { i ->
            val centered = ((waveform[i].toInt() and 0xFF) - 128) / 128.0
            val scaled = centered * (0.20 + 0.80 * gain)
            sumSq += scaled * scaled
            val absVal = abs(scaled)
            if (absVal > maxAmp) maxAmp = absVal
            val sign = when {
                centered > 0.02 -> 1
                centered < -0.02 -> -1
                else -> prevSign
            }
            if (prevSign != 0 && sign != 0 && sign != prevSign) {
                zeroCrossings++
            }
            if (sign != 0) prevSign = sign
        }

        val rawRms = sqrt(sumSq / n).toFloat().coerceIn(0f, 1f)
        val rawPeak = maxAmp.toFloat().coerceIn(0f, 1f)
        val rawZcr = (zeroCrossings.toFloat() / (n * 0.25f)).coerceIn(0f, 1f)

        smoothWaveRms = smoothAsymmetric(smoothWaveRms, rawRms, attack = 0.65f, release = 0.28f)
        smoothWavePeak = smoothAsymmetric(smoothWavePeak, rawPeak, attack = 0.78f, release = 0.24f)
        smoothZcr = smoothAsymmetric(smoothZcr, rawZcr, attack = 0.45f, release = 0.25f)

        val targetCount = AudioTelemetry.WAVEFORM_SAMPLE_COUNT
        val downsampled = FloatArray(targetCount)
        val step = n.toFloat() / targetCount.toFloat()
        (0 until targetCount).forEach { idx ->
            val srcIdx = (idx * step).toInt().coerceIn(0, n - 1)
            val centered = ((waveform[srcIdx].toInt() and 0xFF) - 128) / 128f
            val targetSample = (centered * (0.18f + 0.82f * gain)).coerceIn(-1f, 1f)
            downsampled[idx] = latestWaveform[idx] * 0.35f + targetSample * 0.65f
        }
        latestWaveform = downsampled
    }

    /**
     * Processes one packed FFT frame together with hardware Peak/RMS calibration metrics and
     * returns an immutable [AudioTelemetry] snapshot ready for real-time Compose rendering.
     */
    fun analyzeFft(
        fft: ByteArray,
        samplingRateMilliHz: Int,
        loudnessGain: Float = 1f,
        crestFactor: Float = 0f,
        rmsDb: Float = -96f,
        peakDb: Float = -96f,
        volumeAttenuationDb: Float = 0f,
        estimatedSourceDb: Float = -96f,
        timestampMs: Long = 0L,
    ): AudioTelemetry {
        val binCount = fft.size / 2
        if (binCount < 8) return AudioTelemetry.Silent

        val clampedGain = loudnessGain.coerceIn(0f, 1f)
        val clampedCrest = crestFactor.coerceIn(0f, 1f)
        val sampleRateHz = (samplingRateMilliHz / 1000.0).coerceAtLeast(8_000.0)
        val hzPerBin = sampleRateHz / fft.size.toDouble()

        val magnitudes = DoubleArray(binCount)
        val subBassAcc = BandAccumulator()
        val bassAcc = BandAccumulator()
        val lowMidAcc = BandAccumulator()
        val midAcc = BandAccumulator()
        val highMidAcc = BandAccumulator()
        val trebleAcc = BandAccumulator()
        val brillianceAcc = BandAccumulator()

        var weightedFreqSum = 0.0
        var totalMagSum = 0.0

        (1 until binCount).forEach { k ->
            val re = fft[2 * k].toDouble()
            val im = fft[2 * k + 1].toDouble()
            val mag = hypot(re, im)
            magnitudes[k] = mag
            val freqHz = k * hzPerBin

            if (freqHz in 20.0..16_000.0 && mag > NOISE_FLOOR_MAG) {
                weightedFreqSum += freqHz * mag
                totalMagSum += mag
            }

            when {
                freqHz < 20.0 -> Unit
                freqHz <= 60.0 -> subBassAcc.add(mag)
                freqHz <= 220.0 -> bassAcc.add(mag)
                freqHz <= 500.0 -> lowMidAcc.add(mag)
                freqHz <= 2_000.0 -> midAcc.add(mag)
                freqHz <= 4_000.0 -> highMidAcc.add(mag)
                freqHz <= 8_000.0 -> trebleAcc.add(mag)
                freqHz <= 16_000.0 -> brillianceAcc.add(mag)
            }
        }

        val rawSubBass = mapMagnitudeToLevel(subBassAcc.blended(), 1.40) * clampedGain
        val rawBass = mapMagnitudeToLevel(bassAcc.blended(), 1.42) * clampedGain
        val rawLowMid = mapMagnitudeToLevel(lowMidAcc.blended(), 1.45) * clampedGain
        val rawMid = mapMagnitudeToLevel(midAcc.blended(), 1.48) * clampedGain
        val rawHighMid = mapMagnitudeToLevel(highMidAcc.blended(), 1.50) * clampedGain
        val rawTreble = mapMagnitudeToLevel(trebleAcc.blended(), 1.52) * clampedGain
        val rawBrilliance = mapMagnitudeToLevel(brillianceAcc.blended(), 1.55) * clampedGain

        val combinedLow = (rawSubBass * 0.45f + rawBass * 0.55f).coerceIn(0f, 1f)
        val rawEnergy = (
            rawSubBass * 0.22f +
                rawBass * 0.28f +
                rawLowMid * 0.14f +
                rawMid * 0.16f +
                rawHighMid * 0.10f +
                rawTreble * 0.06f +
                rawBrilliance * 0.04f
            ).coerceIn(0f, 1f)

        smoothSubBass = smoothAsymmetric(smoothSubBass, rawSubBass, attack = 0.74f, release = 0.30f)
        smoothBass = smoothAsymmetric(smoothBass, rawBass, attack = 0.74f, release = 0.30f)
        smoothLowMid = smoothAsymmetric(smoothLowMid, rawLowMid, attack = 0.68f, release = 0.28f)
        smoothMid = smoothAsymmetric(smoothMid, rawMid, attack = 0.65f, release = 0.28f)
        smoothHighMid = smoothAsymmetric(smoothHighMid, rawHighMid, attack = 0.65f, release = 0.28f)
        smoothTreble = smoothAsymmetric(smoothTreble, rawTreble, attack = 0.65f, release = 0.30f)
        smoothBrilliance = smoothAsymmetric(smoothBrilliance, rawBrilliance, attack = 0.65f, release = 0.30f)
        smoothEnergy = smoothAsymmetric(smoothEnergy, rawEnergy, attack = 0.68f, release = 0.28f)

        // Spectral flux & beat pulse
        val rawFlux = if (hasBaseline) (combinedLow - bassBaseline).coerceAtLeast(0f) else 0f
        val fluxNorm = (rawFlux * 2.8f).coerceIn(0f, 1f)
        smoothFluxNorm = smoothAsymmetric(smoothFluxNorm, fluxNorm, attack = 0.75f, release = 0.25f)

        val crestBoost = if (rawEnergy > 0.005f) clampedCrest * clampedGain * 0.32f else 0f
        val proportionalPunch = (combinedLow * 0.54f + rawFlux * 1.55f + crestBoost).coerceIn(0f, 1f)
        currentBeatPulse = if (proportionalPunch > currentBeatPulse) {
            proportionalPunch
        } else {
            (currentBeatPulse * 0.70f + proportionalPunch * 0.30f).let { if (it < 0.008f) 0f else it }
        }

        // Tempo / BPM & rhythm density tracking from clean low-band onset peaks
        if (timestampMs > 0L && hasBaseline && rawFlux > ONSET_FLUX_THRESHOLD && combinedLow > ONSET_MIN_BASS) {
            recordBeatOnset(timestampMs)
        }
        val rhythmDensity = computeRhythmDensity(timestampMs)

        bassBaseline = if (!hasBaseline) combinedLow else bassBaseline * 0.85f + combinedLow * 0.15f
        hasBaseline = true

        // Spectral centroid (Hz) & normalized brightness (0..1)
        val rawCentroidHz = if (totalMagSum > 0.0) (weightedFreqSum / totalMagSum).toFloat() else 0f
        smoothCentroidHz = smoothAsymmetric(smoothCentroidHz, rawCentroidHz, attack = 0.35f, release = 0.20f)
        val brightness = ((smoothCentroidHz - 150f) / 4_850f).coerceIn(0f, 1f)

        // 48 logarithmically spaced equalizer bars (20 Hz -> 16 kHz) with falling peak-hold caps
        computeLogSpectrumBars(
            magnitudes = magnitudes,
            hzPerBin = hzPerBin,
            loudnessGain = clampedGain,
        )

        val bpmNorm = if (estimatedBpm > 0) {
            ((estimatedBpm - 60f) / 140f).coerceIn(0f, 1f)
        } else {
            0f
        }

        return AudioTelemetry(
            isLive = true,
            samplingRateHz = sampleRateHz.roundToInt(),
            captureSize = fft.size,
            subBass = FrequencyBandLevel(smoothSubBass, subBassAcc.blended().toFloat(), subBassAcc.max.toFloat()),
            bass = FrequencyBandLevel(smoothBass, bassAcc.blended().toFloat(), bassAcc.max.toFloat()),
            lowMid = FrequencyBandLevel(smoothLowMid, lowMidAcc.blended().toFloat(), lowMidAcc.max.toFloat()),
            mid = FrequencyBandLevel(smoothMid, midAcc.blended().toFloat(), midAcc.max.toFloat()),
            highMid = FrequencyBandLevel(smoothHighMid, highMidAcc.blended().toFloat(), highMidAcc.max.toFloat()),
            treble = FrequencyBandLevel(smoothTreble, trebleAcc.blended().toFloat(), trebleAcc.max.toFloat()),
            brilliance = FrequencyBandLevel(smoothBrilliance, brillianceAcc.blended().toFloat(), brillianceAcc.max.toFloat()),
            beatPulse = currentBeatPulse,
            energy = smoothEnergy,
            spectralFluxRaw = rawFlux,
            spectralFluxNormalized = smoothFluxNorm,
            estimatedBpm = estimatedBpm,
            bpmNormalized = bpmNorm,
            rhythmDensity = rhythmDensity,
            spectralCentroidHz = smoothCentroidHz,
            brightness = brightness,
            waveformRms = smoothWaveRms,
            waveformPeak = smoothWavePeak,
            zeroCrossingRate = smoothZcr,
            rmsDb = rmsDb,
            peakDb = peakDb,
            volumeAttenuationDb = volumeAttenuationDb,
            estimatedSourceDb = estimatedSourceDb,
            loudnessGain = clampedGain,
            crestFactor = clampedCrest,
            fftBars = smoothBars.toList(),
            peakHoldBars = peakHoldBars.toList(),
            waveformSamples = latestWaveform.toList(),
        )
    }

    private fun computeLogSpectrumBars(
        magnitudes: DoubleArray,
        hzPerBin: Double,
        loudnessGain: Float,
    ) {
        val barCount = AudioTelemetry.FFT_BAR_COUNT
        val minFreq = 45.0
        val maxFreq = 14_000.0
        val logRatio = maxFreq / minFreq
        val maxBin = magnitudes.lastIndex

        (0 until barCount).forEach { i ->
            val fStart = minFreq * logRatio.pow(i.toDouble() / barCount)
            val fEnd = minFreq * logRatio.pow((i + 1).toDouble() / barCount)
            val exactStart = (fStart / hzPerBin).coerceIn(1.0, maxBin.toDouble())
            val exactEnd = (fEnd / hzPerBin).coerceIn(exactStart, maxBin.toDouble())
            val bStart = exactStart.toInt().coerceIn(1, maxBin)
            val bEnd = exactEnd.toInt().coerceIn(bStart, maxBin)

            val blended = if (bEnd - bStart < 1) {
                val centerBin = ((exactStart + exactEnd) * 0.5).coerceIn(1.0, maxBin.toDouble())
                val lo = centerBin.toInt().coerceIn(1, maxBin)
                val hi = (lo + 1).coerceAtMost(maxBin)
                val frac = centerBin - lo
                magnitudes[lo] * (1.0 - frac) + magnitudes[hi] * frac
            } else {
                var sum = 0.0
                var peak = 0.0
                val count = (bEnd - bStart + 1).coerceAtLeast(1)
                (bStart..bEnd).forEach { b ->
                    val m = magnitudes[b]
                    sum += m
                    if (m > peak) peak = m
                }
                (sum / count) * 0.45 + peak * 0.55
            }
            val target = mapMagnitudeToLevel(blended, exponent = 1.38) * loudnessGain
            val next = smoothAsymmetric(smoothBars[i], target, attack = 0.72f, release = 0.30f)
            smoothBars[i] = next
            peakHoldBars[i] = if (next >= peakHoldBars[i]) {
                next
            } else {
                (peakHoldBars[i] - 0.022f).coerceAtLeast(next)
            }
        }
    }

    private fun recordBeatOnset(timestampMs: Long) {
        if (lastOnsetMs > 0L && timestampMs - lastOnsetMs < MIN_ONSET_INTERVAL_MS) return
        lastOnsetMs = timestampMs
        onsetTimestampsMs.addLast(timestampMs)
        while (onsetTimestampsMs.size > MAX_ONSET_HISTORY) {
            onsetTimestampsMs.removeFirst()
        }
        if (onsetTimestampsMs.size < 4) return

        val intervals = mutableListOf<Long>()
        val list = onsetTimestampsMs.toList()
        (1 until list.size).forEach { i ->
            val dt = list[i] - list[i - 1]
            if (dt in MIN_BEAT_PERIOD_MS..MAX_BEAT_PERIOD_MS) {
                intervals.add(dt)
            }
        }
        if (intervals.size < 3) return
        intervals.sort()
        val medianMs = intervals[intervals.size / 2]
        var candidateBpm = (60_000.0 / medianMs.toDouble()).roundToInt()
        while (candidateBpm in 1..64) candidateBpm *= 2
        while (candidateBpm > 195) candidateBpm /= 2
        estimatedBpm = if (estimatedBpm == 0) {
            candidateBpm
        } else {
            (estimatedBpm * 0.75 + candidateBpm * 0.25).roundToInt()
        }
    }

    private fun computeRhythmDensity(nowMs: Long): Float {
        if (nowMs <= 0L) return 0f
        while (onsetTimestampsMs.isNotEmpty() && nowMs - onsetTimestampsMs.first() > RHYTHM_WINDOW_MS) {
            onsetTimestampsMs.removeFirst()
        }
        if (onsetTimestampsMs.isEmpty() && nowMs - lastOnsetMs > RHYTHM_WINDOW_MS) {
            estimatedBpm = 0
        }
        val beatsPerSecond = onsetTimestampsMs.size.toFloat() / (RHYTHM_WINDOW_MS / 1000f)
        return (beatsPerSecond / 3.5f).coerceIn(0f, 1f)
    }

    private fun mapMagnitudeToLevel(magnitude: Double, exponent: Double): Float {
        if (magnitude <= NOISE_FLOOR_MAG) return 0f
        val effectiveMag = (magnitude - NOISE_FLOOR_MAG).coerceAtLeast(0.0)
        val logNorm = (ln(1.0 + effectiveMag * LOG_SCALE) / LOG_DENOM).coerceIn(0.0, 1.0)
        return logNorm.pow(exponent).toFloat().coerceIn(0f, 1f)
    }

    private fun smoothAsymmetric(previous: Float, target: Float, attack: Float, release: Float): Float {
        val alpha = if (target >= previous) attack else release
        return (previous + (target - previous) * alpha).coerceIn(0f, 1f)
    }

    private companion object {
        const val NOISE_FLOOR_MAG = 1.0
        const val LOG_SCALE = 0.55
        val LOG_DENOM = ln(1.0 + 110.0 * LOG_SCALE)

        const val ONSET_FLUX_THRESHOLD = 0.08f
        const val ONSET_MIN_BASS = 0.22f
        const val MIN_ONSET_INTERVAL_MS = 240L
        const val MIN_BEAT_PERIOD_MS = 280L
        const val MAX_BEAT_PERIOD_MS = 1_100L
        const val MAX_ONSET_HISTORY = 14
        const val RHYTHM_WINDOW_MS = 6_000L
    }
}
