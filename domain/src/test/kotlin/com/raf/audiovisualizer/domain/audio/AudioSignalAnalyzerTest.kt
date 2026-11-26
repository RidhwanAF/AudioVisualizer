package com.raf.audiovisualizer.domain.audio

import com.raf.audiovisualizer.domain.model.AudioTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioSignalAnalyzerTest {

    @Test
    fun `given silent fft frame, when analyzed, then emits zero energy and pulse`() {
        val analyzer = AudioSignalAnalyzer()
        val silent = ByteArray(1024)

        val telemetry = analyzer.analyzeFft(
            fft = silent,
            samplingRateMilliHz = 48_000_000,
            loudnessGain = 0f,
        )

        assertTrue(telemetry.isLive)
        assertEquals(0f, telemetry.bass.normalized, 0.001f)
        assertEquals(0f, telemetry.energy, 0.001f)
        assertEquals(0f, telemetry.beatPulse, 0.001f)
        assertEquals(AudioTelemetry.FFT_BAR_COUNT, telemetry.fftBars.size)
        assertEquals(AudioTelemetry.WAVEFORM_SAMPLE_COUNT, telemetry.waveformSamples.size)
    }

    @Test
    fun `given quiet relaxing track, when analyzed, then stays calm and proportional`() {
        val analyzer = AudioSignalAnalyzer()
        val softFrame = ByteArray(1024).apply {
            this[4] = 45
            this[5] = 20
            this[20] = 40
            this[21] = 18
        }

        var latest = AudioTelemetry.Silent
        repeat(6) { idx ->
            latest = analyzer.analyzeFft(
                fft = softFrame,
                samplingRateMilliHz = 48_000_000,
                loudnessGain = 0.24f,
                timestampMs = (idx + 1) * 50L,
            )
        }

        assertTrue("Quiet bass should stay low (< 0.25) but was ${latest.bass.normalized}", latest.bass.normalized < 0.25f)
        assertTrue("Quiet pulse should stay calm (< 0.25) but was ${latest.beatPulse}", latest.beatPulse < 0.25f)
        assertTrue("Quiet energy should stay calm (< 0.25) but was ${latest.energy}", latest.energy < 0.25f)
    }

    @Test
    fun `given periodic 120 bpm kicks, when analyzed, then tracks bpm near 120 and high pulse`() {
        val analyzer = AudioSignalAnalyzer()
        val quiet = ByteArray(1024)
        val kick = ByteArray(1024).apply {
            this[2] = 100
            this[3] = 55
            this[4] = 115
            this[5] = 60
            this[6] = 95
            this[7] = 50
        }

        var latest = AudioTelemetry.Silent
        // 120 BPM = one kick every 500ms
        (0 until 6).forEach { beat ->
            val baseTime = beat * 500L + 100L
            repeat(4) { step ->
                analyzer.analyzeFft(quiet, 48_000_000, loudnessGain = 0.10f, timestampMs = baseTime + step * 50L)
            }
            latest = analyzer.analyzeFft(
                fft = kick,
                samplingRateMilliHz = 48_000_000,
                loudnessGain = 0.92f,
                crestFactor = 0.85f,
                timestampMs = baseTime + 250L,
            )
        }

        assertTrue("Expected beatPulse > 0.65 but was ${latest.beatPulse}", latest.beatPulse > 0.65f)
        assertTrue("Expected estimatedBpm near 120 (105..135) but was ${latest.estimatedBpm}", latest.estimatedBpm in 105..135)
    }

    @Test
    fun `given alternating waveform buffer, when updated, then computes non-zero rms peak and zcr`() {
        val analyzer = AudioSignalAnalyzer()
        val wave = ByteArray(1024) { i ->
            if ((i / 8) % 2 == 0) (128 + 90).toByte() else (128 - 90).toByte()
        }

        analyzer.updateWaveform(wave, loudnessGain = 0.9f)
        val snapshot = analyzer.analyzeFft(ByteArray(1024), 48_000_000, loudnessGain = 0.9f)

        assertTrue("Expected waveformRms > 0.25", snapshot.waveformRms > 0.25f)
        assertTrue("Expected waveformPeak > 0.35", snapshot.waveformPeak > 0.35f)
        assertTrue("Expected zeroCrossingRate > 0.15", snapshot.zeroCrossingRate > 0.15f)
    }

    @Test
    fun `given softRecover after HAL reattach, when analyzed, then preserves bands without spike`() {
        val analyzer = AudioSignalAnalyzer()
        val frame = ByteArray(1024).apply {
            this[4] = 65
            this[5] = 30
            this[22] = 55
            this[23] = 20
        }
        var steady = AudioTelemetry.Silent
        repeat(6) {
            steady = analyzer.analyzeFft(frame, 48_000_000, loudnessGain = 0.50f)
        }

        analyzer.softRecover()
        val recovered = analyzer.analyzeFft(frame, 48_000_000, loudnessGain = 0.50f)

        assertEquals(steady.bass.normalized, recovered.bass.normalized, 0.05f)
        assertTrue(recovered.beatPulse <= steady.beatPulse + 0.05f)
    }
}
