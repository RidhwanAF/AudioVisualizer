package com.raf.audiovisualizer.domain.usecase

import com.raf.audiovisualizer.domain.model.AudioTelemetry
import com.raf.audiovisualizer.domain.repository.AudioVisualizerRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Exposes real-time audio spectrum, waveform, rhythm, and loudness telemetry. */
class ObserveAudioTelemetryUseCase @Inject constructor(
    private val repository: AudioVisualizerRepository,
) {
    operator fun invoke(): StateFlow<AudioTelemetry> = repository.telemetry
}
