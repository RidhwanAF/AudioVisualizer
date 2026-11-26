package com.raf.audiovisualizer.domain.audio

import com.raf.audiovisualizer.domain.model.AudioAnimationParams
import com.raf.audiovisualizer.domain.model.AudioTelemetry
import com.raf.audiovisualizer.domain.model.FrequencyBandLevel
import com.raf.audiovisualizer.domain.model.MotionMood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioAnimationParamsComputerTest {

    @Test
    fun `silent telemetry returns idle params`() {
        val params = AudioAnimationParamsComputer.compute(AudioTelemetry.Silent)
        assertEquals(AudioAnimationParams.Idle, params)
        assertEquals(MotionMood.CALM, params.mood)
        assertEquals(1.0f, params.scaleFactor, 0.0001f)
    }

    @Test
    fun `calm relaxing track stays slow with gentle scale and soft snake`() {
        val calmTelemetry = AudioTelemetry(
            isLive = true,
            subBass = FrequencyBandLevel(normalized = 0.08f),
            bass = FrequencyBandLevel(normalized = 0.12f),
            lowMid = FrequencyBandLevel(normalized = 0.16f),
            mid = FrequencyBandLevel(normalized = 0.20f),
            highMid = FrequencyBandLevel(normalized = 0.14f),
            treble = FrequencyBandLevel(normalized = 0.10f),
            brilliance = FrequencyBandLevel(normalized = 0.06f),
            beatPulse = 0.08f,
            energy = 0.16f,
            spectralFluxNormalized = 0.09f,
            bpmNormalized = 0.20f,
            rhythmDensity = 0.12f,
            brightness = 0.18f,
            waveformRms = 0.14f,
            waveformPeak = 0.22f,
            loudnessGain = 0.30f,
            crestFactor = 0.25f,
        )

        val params = AudioAnimationParamsComputer.compute(calmTelemetry)
        assertEquals(MotionMood.CALM, params.mood)
        assertTrue("Expected calm speed < 1.2x, got ${params.speedMultiplier}", params.speedMultiplier < 1.2f)
        assertTrue("Expected gentle scale < 1.03, got ${params.scaleFactor}", params.scaleFactor < 1.03f)
        assertTrue("Expected relaxed release > 260ms, got ${params.scaleReleaseMs}", params.scaleReleaseMs > 260)
    }

    @Test
    fun `high energy bass drop surges speed scale and glowing snake`() {
        val dropTelemetry = AudioTelemetry(
            isLive = true,
            subBass = FrequencyBandLevel(normalized = 0.92f),
            bass = FrequencyBandLevel(normalized = 0.95f),
            lowMid = FrequencyBandLevel(normalized = 0.72f),
            mid = FrequencyBandLevel(normalized = 0.65f),
            highMid = FrequencyBandLevel(normalized = 0.58f),
            treble = FrequencyBandLevel(normalized = 0.62f),
            brilliance = FrequencyBandLevel(normalized = 0.50f),
            beatPulse = 0.94f,
            energy = 0.88f,
            spectralFluxNormalized = 0.82f,
            bpmNormalized = 0.78f,
            rhythmDensity = 0.80f,
            brightness = 0.66f,
            waveformRms = 0.78f,
            waveformPeak = 0.96f,
            loudnessGain = 0.92f,
            crestFactor = 0.74f,
        )

        val params = AudioAnimationParamsComputer.compute(dropTelemetry)
        assertEquals(MotionMood.DROP, params.mood)
        assertTrue("Expected high speed > 2.6x, got ${params.speedMultiplier}", params.speedMultiplier > 2.6f)
        assertTrue("Expected strong scale > 1.07, got ${params.scaleFactor}", params.scaleFactor > 1.07f)
        assertTrue("Expected fast attack < 70ms, got ${params.scaleAttackMs}", params.scaleAttackMs < 70)
        assertTrue("Expected thick glow > 0.75, got ${params.snakeGlowThickness}", params.snakeGlowThickness > 0.75f)
        assertTrue("Expected long snake tail > 0.45, got ${params.snakeTailLengthFraction}", params.snakeTailLengthFraction > 0.45f)
        assertTrue("Expected strong ripple trigger > 0.75, got ${params.rippleTriggerStrength}", params.rippleTriggerStrength > 0.75f)
    }
}
