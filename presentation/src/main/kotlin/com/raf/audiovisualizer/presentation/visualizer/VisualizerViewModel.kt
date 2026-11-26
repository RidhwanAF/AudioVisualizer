package com.raf.audiovisualizer.presentation.visualizer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raf.audiovisualizer.domain.audio.AudioAnimationParamsComputer
import com.raf.audiovisualizer.domain.model.CardVisualEffect
import com.raf.audiovisualizer.domain.repository.AudioVisualizerRepository
import com.raf.audiovisualizer.domain.repository.NowPlayingRepository
import com.raf.audiovisualizer.domain.usecase.ObserveAudioTelemetryUseCase
import com.raf.audiovisualizer.domain.usecase.ObserveNowPlayingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

private data class CardEffectSelection(
    val effect: CardVisualEffect = CardVisualEffect.WATER_DROP,
    val dustTriggerCount: Int = 0,
)

/**
 * Single screen-level [ViewModel] coordinating real-time audio telemetry, active album metadata,
 * runtime permissions, selectable AGSL album-card effects, and active visualization tab selection.
 */
@HiltViewModel
class VisualizerViewModel @Inject constructor(
    observeAudioTelemetry: ObserveAudioTelemetryUseCase,
    observeNowPlaying: ObserveNowPlayingUseCase,
    private val audioVisualizerRepository: AudioVisualizerRepository,
    private val nowPlayingRepository: NowPlayingRepository,
) : ViewModel() {

    private val selectedTab = MutableStateFlow(VisualizerTab.FFT)
    private val effectSelection = MutableStateFlow(CardEffectSelection())
    private val hasAudioPermission = MutableStateFlow(audioVisualizerRepository.hasAudioPermission())
    private val hasNotificationAccess = MutableStateFlow(nowPlayingRepository.hasNotificationAccess())

    val uiState: StateFlow<VisualizerUiState> = combine(
        observeAudioTelemetry(),
        observeNowPlaying(),
        selectedTab,
        effectSelection,
        combine(hasAudioPermission, hasNotificationAccess, ::Pair),
    ) { telemetry, track, tab, effectState, (audioPerm, notifPerm) ->
        VisualizerUiState(
            telemetry = telemetry,
            animationParams = AudioAnimationParamsComputer.compute(telemetry),
            track = track,
            selectedTab = tab,
            selectedCardEffect = effectState.effect,
            dustTriggerCount = effectState.dustTriggerCount,
            hasAudioPermission = audioPerm,
            hasNotificationAccess = notifPerm,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = VisualizerUiState(
            hasAudioPermission = audioVisualizerRepository.hasAudioPermission(),
            hasNotificationAccess = nowPlayingRepository.hasNotificationAccess(),
        ),
    )

    fun selectTab(tab: VisualizerTab) {
        selectedTab.value = tab
    }

    fun selectCardEffect(effect: CardVisualEffect) {
        val current = effectSelection.value
        val nextDustCount = if (effect == CardVisualEffect.REVERSE_DUST) {
            current.dustTriggerCount + 1
        } else {
            current.dustTriggerCount
        }
        effectSelection.value = CardEffectSelection(
            effect = effect,
            dustTriggerCount = nextDustCount,
        )
    }

    fun onForegroundChanged(isForeground: Boolean) {
        refreshPermissions()
        audioVisualizerRepository.setCaptureActive(isForeground && hasAudioPermission.value)
    }

    fun refreshPermissions() {
        val audioGranted = audioVisualizerRepository.hasAudioPermission()
        val notifGranted = nowPlayingRepository.hasNotificationAccess()
        hasAudioPermission.value = audioGranted
        hasNotificationAccess.value = notifGranted
        if (notifGranted) {
            nowPlayingRepository.refreshNow()
        }
        if (audioGranted) {
            audioVisualizerRepository.setCaptureActive(true)
        }
    }

    override fun onCleared() {
        audioVisualizerRepository.setCaptureActive(false)
    }
}
