package com.raf.audiovisualizer.domain.model

/**
 * High-level motion regime classified from the combined real-time audio telemetry.
 */
enum class MotionMood {
    CALM,
    GROOVE,
    ENERGETIC,
    DROP,
}

/**
 * Unified animation parameters synthesized from all extracted [AudioTelemetry] metrics.
 *
 * Drives animation speed, asymmetric scale-in / scale-out behavior, fading ambient background
 * ripples, and the glowing JBL-speaker-style perimeter snake border.
 */
data class AudioAnimationParams(
    val speedMultiplier: Float = 0.25f,
    val speedNormalized: Float = 0f,
    val scaleFactor: Float = 1.0f,
    val scaleNormalized: Float = 0f,
    val scaleAttackMs: Int = 95,
    val scaleReleaseMs: Int = 280,
    val rippleIntensity: Float = 0f,
    val rippleTriggerStrength: Float = 0f,
    val snakeSpeedDegPerSec: Float = 24f,
    val snakeGlowThickness: Float = 0.15f,
    val snakeTailLengthFraction: Float = 0.22f,
    val ambientGlowIntensity: Float = 0.15f,
    val mood: MotionMood = MotionMood.CALM,
) {
    companion object {
        val Idle: AudioAnimationParams = AudioAnimationParams()
    }
}
