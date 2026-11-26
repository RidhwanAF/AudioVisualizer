package com.raf.audiovisualizer.data.media

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.raf.audiovisualizer.domain.model.NowPlayingTrack
import com.raf.audiovisualizer.domain.repository.NowPlayingRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observes active system [MediaController] sessions via [MediaSessionManager], decoding album art
 * and extracting ambient palette colors on a sequential background dispatcher.
 */
@Singleton
class NowPlayingRepositoryImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : NowPlayingRepository {

    private val mediaSessionManager =
        context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
    private val listenerComponent = ComponentName(context, VisualizerNotificationListenerService::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    private val _currentTrack = MutableStateFlow(NowPlayingTrack.Empty)
    override val currentTrack: StateFlow<NowPlayingTrack> = _currentTrack.asStateFlow()

    private val controllersLock = Any()
    private val activeCallbacks = mutableMapOf<MediaController, MediaController.Callback>()
    private var sessionListenerRegistered = false

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        syncControllers(controllers.orEmpty())
    }

    init {
        workerScope.launch {
            VisualizerNotificationListenerService.isConnected.collect { connected ->
                if (connected) {
                    registerAndRefresh()
                }
            }
        }
        refreshNow()
    }

    override fun hasNotificationAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    override fun refreshNow() {
        workerScope.launch {
            registerAndRefresh()
        }
    }

    private fun registerAndRefresh() {
        if (!hasNotificationAccess()) {
            _currentTrack.value = NowPlayingTrack.Empty
            return
        }
        runCatching {
            if (!sessionListenerRegistered) {
                mediaSessionManager.addOnActiveSessionsChangedListener(
                    sessionsChangedListener,
                    listenerComponent,
                    mainHandler,
                )
                sessionListenerRegistered = true
            }
            val controllers = mediaSessionManager.getActiveSessions(listenerComponent)
            syncControllers(controllers)
        }.onFailure { err ->
            Log.w(TAG, "Failed to query active media sessions: ${err.message}")
        }
    }

    private fun syncControllers(controllers: List<MediaController>) {
        synchronized(controllersLock) {
            activeCallbacks.forEach { (controller, cb) ->
                runCatching { controller.unregisterCallback(cb) }
            }
            activeCallbacks.clear()

            controllers.forEach { controller ->
                val callback = object : MediaController.Callback() {
                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        workerScope.launch { publishBestTrack(controllers) }
                    }

                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        workerScope.launch { publishBestTrack(controllers) }
                    }
                }
                runCatching {
                    controller.registerCallback(callback, mainHandler)
                    activeCallbacks[controller] = callback
                }
            }
        }
        workerScope.launch { publishBestTrack(controllers) }
    }

    private fun publishBestTrack(controllers: List<MediaController>) {
        val preferred = controllers.firstOrNull { isControllerPlaying(it) }
            ?: controllers.firstOrNull { it.metadata != null }
        if (preferred == null) {
            _currentTrack.value = NowPlayingTrack.Empty
            return
        }
        val meta = preferred.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: ""
        val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: ""
        val album = meta?.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
        val bitmap = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        val palette = MediaArtworkColorExtractor.extract(bitmap)
        val artworkBytes = bitmap?.let(::compressArtwork)

        _currentTrack.value = NowPlayingTrack(
            title = title,
            artist = artist,
            album = album,
            packageName = preferred.packageName,
            isPlaying = isControllerPlaying(preferred),
            artworkBytes = artworkBytes,
            primaryColorArgb = palette.primaryArgb,
            secondaryColorArgb = palette.secondaryArgb,
            ambientSurfaceArgb = palette.ambientSurfaceArgb,
        )
    }

    private fun isControllerPlaying(controller: MediaController): Boolean {
        val state = controller.playbackState?.state ?: return false
        return state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING
    }

    private fun compressArtwork(bitmap: Bitmap): ByteArray? = runCatching {
        val maxDim = 512
        val target = if (bitmap.width > maxDim || bitmap.height > maxDim) {
            val scale = maxDim.toFloat() / maxOf(bitmap.width, bitmap.height).toFloat()
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        target.compress(Bitmap.CompressFormat.JPEG, 88, out)
        if (target !== bitmap) target.recycle()
        out.toByteArray()
    }.getOrNull()

    private companion object {
        const val TAG = "NowPlayingRepo"
    }
}
