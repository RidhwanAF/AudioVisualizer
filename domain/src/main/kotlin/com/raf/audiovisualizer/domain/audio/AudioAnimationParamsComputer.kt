package com.raf.audiovisualizer.domain.audio

import com.raf.audiovisualizer.domain.model.AudioAnimationParams
import com.raf.audiovisualizer.domain.model.AudioTelemetry
import com.raf.audiovisualizer.domain.model.MotionMood

/**
 * Pure-Kotlin parameter synthesizer that fuses all 7 frequency bands, rhythm/tempo dynamics,
 * time-domain waveform metrics, and loudness calibration into concrete [AudioAnimationParams].
 */
object AudioAnimationParamsComputer {

    fun compute(telemetry: AudioTelemetry): AudioAnimationParams {
        if (!telemetry.isLive || (telemetry.energy < 0.005f && telemetry.waveformRms < 0.005f)) {
            return AudioAnimationParams.Idle
        }

        val bassDrive = (
            telemetry.subBass.normalized * 0.42f +
                telemetry.bass.normalized * 0.43f +
                telemetry.lowMid.normalized * 0.15f
            ).coerceIn(0f, 1f)

        val midDrive = (
            telemetry.mid.normalized * 0.55f +
                telemetry.highMid.normalized * 0.45f
            ).coerceIn(0f, 1f)

        val sparkleDrive = (
            telemetry.treble.normalized * 0.50f +
                telemetry.brilliance.normalized * 0.30f +
                telemetry.brightness * 0.20f
            ).coerceIn(0f, 1f)

        val waveDrive = (
            telemetry.waveformRms * 0.60f +
                telemetry.waveformPeak * 0.40f
            ).coerceIn(0f, 1f)

        val paceDrive = (
            telemetry.energy * 0.32f +
                telemetry.rhythmDensity * 0.22f +
                telemetry.bpmNormalized * 0.18f +
                telemetry.beatPulse * 0.16f +
                telemetry.spectralFluxNormalized * 0.12f
            ).coerceIn(0f, 1f)

        val loudnessWeight = 0.45f + telemetry.loudnessGain.coerceIn(0f, 1f) * 0.55f
        val transientPunch = (
            (
                telemetry.beatPulse * 0.45f +
                    bassDrive * 0.30f +
                    telemetry.spectralFluxNormalized * 0.15f +
                    telemetry.crestFactor * 0.10f
                ) * loudnessWeight
            ).coerceIn(0f, 1f)

        val speedNormalized = (
            paceDrive * 0.80f +
                sparkleDrive * 0.10f +
                waveDrive * 0.10f
            ).coerceIn(0f, 1f)

        val speedMultiplier = 0.25f + speedNormalized * 3.55f

        val scaleNormalized = (
            transientPunch * 0.72f +
                bassDrive * 0.18f +
                midDrive * 0.10f
            ).coerceIn(0f, 1f)

        val scaleFactor = 1.0f + scaleNormalized * 0.11f
        val scaleAttackMs = (110f - transientPunch * 65f).toInt().coerceIn(45, 110)
        val scaleReleaseMs = (350f - speedNormalized * 185f).toInt().coerceIn(155, 350)

        val rippleIntensity = (
            telemetry.energy * 0.35f +
                bassDrive * 0.35f +
                telemetry.beatPulse * 0.30f
            ).coerceIn(0f, 1f)

        val rippleTriggerStrength = (
            telemetry.beatPulse * 0.50f +
                telemetry.spectralFluxNormalized * 0.28f +
                bassDrive * 0.22f
            ).coerceIn(0f, 1f)

        val snakeSpeedDegPerSec = (24f + speedNormalized * 240f + telemetry.beatPulse * 56f)
            .coerceIn(24f, 320f)

        val snakeGlowThickness = (
            0.18f +
                bassDrive * 0.34f +
                telemetry.energy * 0.26f +
                telemetry.beatPulse * 0.22f
            ).coerceIn(0.15f, 1f)

        val snakeTailLengthFraction = (
            0.24f +
                telemetry.energy * 0.18f +
                bassDrive * 0.12f +
                midDrive * 0.08f
            ).coerceIn(0.22f, 0.62f)

        val ambientGlowIntensity = (
            0.18f +
                telemetry.energy * 0.38f +
                telemetry.loudnessGain * 0.24f +
                bassDrive * 0.20f
            ).coerceIn(0.15f, 1f)

        val mood = when {
            telemetry.beatPulse >= 0.68f && (bassDrive >= 0.55f || telemetry.energy >= 0.65f) -> MotionMood.DROP
            speedNormalized >= 0.55f || telemetry.energy >= 0.58f -> MotionMood.ENERGETIC
            speedNormalized >= 0.26f || telemetry.energy >= 0.24f -> MotionMood.GROOVE
            else -> MotionMood.CALM
        }

        return AudioAnimationParams(
            speedMultiplier = speedMultiplier,
            speedNormalized = speedNormalized,
            scaleFactor = scaleFactor,
            scaleNormalized = scaleNormalized,
            scaleAttackMs = scaleAttackMs,
            scaleReleaseMs = scaleReleaseMs,
            rippleIntensity = rippleIntensity,
            rippleTriggerStrength = rippleTriggerStrength,
            snakeSpeedDegPerSec = snakeSpeedDegPerSec,
            snakeGlowThickness = snakeGlowThickness,
            snakeTailLengthFraction = snakeTailLengthFraction,
            ambientGlowIntensity = ambientGlowIntensity,
            mood = mood,
        )
    }
}
