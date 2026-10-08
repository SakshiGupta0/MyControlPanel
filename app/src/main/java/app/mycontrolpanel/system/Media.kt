package app.mycontrolpanel.system

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.KeyEvent
import app.mycontrolpanel.core.NowPlaying

/**
 * Exists only so the user can grant "notification access", which Android requires before an app may
 * see which media session is playing. We never read or store notifications.
 */
class MediaListenerService : NotificationListenerService()

/** Media keys go to whatever app is playing — no Spotify login, no SDK. */
object MediaKeys {
    fun send(ctx: Context, keyCode: Int) {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }
}

/** Watches the active media session (if notification access is granted) and reports what's playing. */
class MediaWatcher(private val ctx: Context, private val onChange: (NowPlaying?, Boolean) -> Unit) {
    private val main = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null
    private val component = ComponentName(ctx, MediaListenerService::class.java)
    private val msm = ctx.getSystemService(MediaSessionManager::class.java)

    fun hasAccess(): Boolean {
        val enabled = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners") ?: return false
        return enabled.contains(component.flattenToString()) || enabled.contains(ctx.packageName)
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list ?: emptyList()) }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = report()
        override fun onPlaybackStateChanged(state: PlaybackState?) = report()
        override fun onSessionDestroyed() { controller = null; report() }
    }

    fun start() {
        if (!hasAccess() || msm == null) { onChange(null, false); return }
        try {
            msm.addOnActiveSessionsChangedListener(sessionsListener, component, main)
            pick(msm.getActiveSessions(component))
        } catch (e: SecurityException) { onChange(null, false) }
    }

    fun stop() {
        runCatching { msm?.removeOnActiveSessionsChangedListener(sessionsListener) }
        controller?.unregisterCallback(callback); controller = null
    }

    private fun pick(list: List<MediaController>) {
        val best = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = best
            best?.registerCallback(callback, main)
        }
        report()
    }

    private fun report() {
        val c = controller
        if (c == null) { onChange(null, true); return }
        val md = c.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST) ?: ""
        val app = runCatching { ctx.packageManager.getApplicationLabel(ctx.packageManager.getApplicationInfo(c.packageName, 0)).toString() }.getOrDefault("")
        onChange(NowPlaying(title, artist, app, c.playbackState?.state == PlaybackState.STATE_PLAYING), true)
    }

    /** Prefer the session's own transport controls; fall back to media keys. */
    fun control(ctxKey: Int) {
        val t = controller?.transportControls
        if (t == null) { MediaKeys.send(ctx, ctxKey); return }
        when (ctxKey) {
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> if (controller?.playbackState?.state == PlaybackState.STATE_PLAYING) t.pause() else t.play()
            KeyEvent.KEYCODE_MEDIA_NEXT -> t.skipToNext()
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> t.skipToPrevious()
        }
    }
}
