package com.raf.audiovisualizer.domain.repository

import com.raf.audiovisualizer.domain.model.AudioTelemetry
import kotlinx.coroutines.flow.StateFlow

/**
 * Streams real-time FFT spectrum, waveform, rhythm, and loudness telemetry from the global audio
 * output mix (`audioSessionId = 0`).
 */
interface AudioVisualizerRepository {
    /** Latest smoothed real-time audio telemetry snapshot. */
    val telemetry: StateFlow<AudioTelemetry>

    /** True when `android.permission.RECORD_AUDIO` is currently granted. */
    fun hasAudioPermission(): Boolean

    /** Starts or stops native audio capture cleanly according to foreground lifecycle & permission. */
    fun setCaptureActive(active: Boolean)
}
