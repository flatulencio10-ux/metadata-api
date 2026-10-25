package com.customlauncher.data.system

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.customlauncher.services.MediaNotificationListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MediaState(
    val packageName: String,
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val art: Bitmap?,
)

/**
 * Tracks the most relevant active media session (playing first). Requires notification
 * listener access, which grants MediaSessionManager.getActiveSessions().
 */
class MediaRepository(private val context: Context) {

    private val sessionManager = context.getSystemService(MediaSessionManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var controllers: List<MediaController> = emptyList()
    private var attached = false

    private val _state = MutableStateFlow<MediaState?>(null)
    val state: StateFlow<MediaState?> = _state.asStateFlow()

    private val _hasAccess = MutableStateFlow(false)
    val hasAccess: StateFlow<Boolean> = _hasAccess.asStateFlow()

    private val controllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() = publish()
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        setControllers(list.orEmpty())
    }

    /** Called from widgets and from the listener service; safe to call repeatedly. */
    fun ensureAttached() {
        if (attached) return
        _hasAccess.value = MediaNotificationListener.isEnabled(context)
        if (!_hasAccess.value) return
        try {
            val component = MediaNotificationListener.component(context)
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            setControllers(sessionManager.getActiveSessions(component))
            attached = true
        } catch (e: SecurityException) {
            Log.w("MediaRepository", "No notification access", e)
            _hasAccess.value = false
        }
    }

    fun onListenerConnected() {
        attached = false
        ensureAttached()
    }

    private fun setControllers(list: List<MediaController>) {
        controllers.forEach { it.unregisterCallback(controllerCallback) }
        controllers = list
        list.forEach { it.registerCallback(controllerCallback, handler) }
        publish()
    }

    private fun active(): MediaController? =
        controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: controllers.firstOrNull()

    private fun publish() {
        val c = active()
        val meta = c?.metadata
        _state.value = if (c == null) null else MediaState(
            packageName = c.packageName,
            title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "",
            artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: "",
            isPlaying = c.playbackState?.state == PlaybackState.STATE_PLAYING,
            art = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART),
        )
    }

    fun playPause() {
        val c = active() ?: return
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
    }

    fun next() = active()?.transportControls?.skipToNext()
    fun previous() = active()?.transportControls?.skipToPrevious()
}
