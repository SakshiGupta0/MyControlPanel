package app.mycontrolpanel.system

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent

/**
 * Play / pause / skip for whatever app is playing (Spotify, YouTube Music…), via standard media keys.
 * Needs no permission. (Showing the song title would need "notification access", which Play Protect
 * blocks for apps installed outside the Play Store — so we deliberately don't ask for it.)
 */
object MediaKeys {
    fun send(ctx: Context, keyCode: Int) {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }
}
