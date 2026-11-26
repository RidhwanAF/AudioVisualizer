package com.raf.audiovisualizer.domain.model

/**
 * Single frequency band telemetry exposing both its raw FFT magnitude and its equalizer-scaled
 * normalized ratio (`0.0f..1.0f`).
 */
data class FrequencyBandLevel(
    val normalized: Float = 0f,
    val rawMagnitude: Float = 0f,
    val peakMagnitude: Float = 0f,
) {
    /** Formatted percentage (`0..100`). */
    val percentage: Int get() = (normalized.coerceIn(0f, 1f) * 100f).toInt()
}

/**
 * Complete real-time audio telemetry snapshot combining FFT frequency spectrum analysis,
 * rhythm/tempo detection, time-domain waveform metrics, and hardware loudness calibration.
 */
data class AudioTelemetry(
    val isLive: Boolean = false,
    val samplingRateHz: Int = 48_000,
    val captureSize: Int = 1024,
    // 7-Band Frequency Breakdown
    val subBass: FrequencyBandLevel = FrequencyBandLevel(),
    val bass: FrequencyBandLevel = FrequencyBandLevel(),
    val lowMid: FrequencyBandLevel = FrequencyBandLevel(),
    val mid: FrequencyBandLevel = FrequencyBandLevel(),
    val highMid: FrequencyBandLevel = FrequencyBandLevel(),
    val treble: FrequencyBandLevel = FrequencyBandLevel(),
    val brilliance: FrequencyBandLevel = FrequencyBandLevel(),
    // Rhythm, Beat & Spectral Dynamics
    val beatPulse: Float = 0f,
    val energy: Float = 0f,
    val spectralFluxRaw: Float = 0f,
    val spectralFluxNormalized: Float = 0f,
    val estimatedBpm: Int = 0,
    val bpmNormalized: Float = 0f,
    val rhythmDensity: Float = 0f,
    val spectralCentroidHz: Float = 0f,
    val brightness: Float = 0f,
    // Time-Domain Waveform Metrics
    val waveformRms: Float = 0f,
    val waveformPeak: Float = 0f,
    val zeroCrossingRate: Float = 0f,
    // Hardware Loudness & Volume Calibration
    val rmsDb: Float = -96f,
    val peakDb: Float = -96f,
    val volumeAttenuationDb: Float = 0f,
    val estimatedSourceDb: Float = -96f,
    val loudnessGain: Float = 0f,
    val crestFactor: Float = 0f,
    // Real-Time Visual Render Buffers
    val fftBars: List<Float> = EMPTY_FFT_BARS,
    val peakHoldBars: List<Float> = EMPTY_FFT_BARS,
    val waveformSamples: List<Float> = EMPTY_WAVEFORM,
) {
    companion object {
        const val FFT_BAR_COUNT = 48
        const val WAVEFORM_SAMPLE_COUNT = 128

        val EMPTY_FFT_BARS: List<Float> = List(FFT_BAR_COUNT) { 0f }
        val EMPTY_WAVEFORM: List<Float> = List(WAVEFORM_SAMPLE_COUNT) { 0f }
        val Silent: AudioTelemetry = AudioTelemetry()
    }
}
