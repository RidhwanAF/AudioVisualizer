package com.raf.audiovisualizer.presentation.visualizer

import androidx.compose.runtime.Immutable
import com.raf.audiovisualizer.domain.model.AudioAnimationParams
import com.raf.audiovisualizer.domain.model.AudioTelemetry
import com.raf.audiovisualizer.domain.model.CardVisualEffect
import com.raf.audiovisualizer.domain.model.NowPlayingTrack

/** Bottom navigation tabs for switching visualization & raw telemetry views. */
enum class VisualizerTab {
    FFT,
    WAVEFORM,
    METRICS,
}

/** Immutable UI state bound to [VisualizerViewModel] via `collectAsStateWithLifecycle()`. */
@Immutable
data class VisualizerUiState(
    val telemetry: AudioTelemetry = AudioTelemetry.Silent,
    val animationParams: AudioAnimationParams = AudioAnimationParams.Idle,
    val track: NowPlayingTrack = NowPlayingTrack.Empty,
    val selectedTab: VisualizerTab = VisualizerTab.FFT,
    val selectedCardEffect: CardVisualEffect = CardVisualEffect.WATER_DROP,
    val dustTriggerCount: Int = 0,
    val hasAudioPermission: Boolean = false,
    val hasNotificationAccess: Boolean = false,
)
