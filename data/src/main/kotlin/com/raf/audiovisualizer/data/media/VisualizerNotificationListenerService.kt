package com.raf.audiovisualizer.data.media

import android.service.notification.NotificationListenerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight [NotificationListenerService] enabling runtime access to
 * [android.media.session.MediaSessionManager.getActiveSessions] so the visualizer can display
 * live album artwork, track metadata, and ambient colors without requiring ADB.
 */
class VisualizerNotificationListenerService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isConnected.value = true
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _isConnected.value = false
    }

    companion object {
        private val _isConnected = MutableStateFlow(false)
        val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    }
}
