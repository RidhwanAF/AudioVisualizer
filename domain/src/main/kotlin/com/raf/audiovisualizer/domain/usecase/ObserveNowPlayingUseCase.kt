package com.raf.audiovisualizer.domain.usecase

import com.raf.audiovisualizer.domain.model.NowPlayingTrack
import com.raf.audiovisualizer.domain.repository.NowPlayingRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Exposes active media track metadata and ambient album-art color palette. */
class ObserveNowPlayingUseCase @Inject constructor(
    private val repository: NowPlayingRepository,
) {
    operator fun invoke(): StateFlow<NowPlayingTrack> = repository.currentTrack
}
