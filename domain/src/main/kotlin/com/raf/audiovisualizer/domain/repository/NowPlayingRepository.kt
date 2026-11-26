package com.raf.audiovisualizer.domain.repository

import com.raf.audiovisualizer.domain.model.NowPlayingTrack
import kotlinx.coroutines.flow.StateFlow

/**
 * Observes the currently playing media session metadata, album artwork, and extracted ambient colors.
 */
interface NowPlayingRepository {
    /** Latest active media session metadata & ambient color palette. */
    val currentTrack: StateFlow<NowPlayingTrack>

    /** Returns true when Notification Listener access is enabled for reading active MediaSessions. */
    fun hasNotificationAccess(): Boolean

    /** Refreshes active MediaSession controllers immediately (e.g., on returning from Settings). */
    fun refreshNow()
}
