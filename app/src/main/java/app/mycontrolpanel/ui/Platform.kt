package app.mycontrolpanel.ui

import androidx.compose.runtime.staticCompositionLocalOf

enum class MediaKey { PLAY_PAUSE, NEXT, PREVIOUS }

/** Things the UI needs from Android that are not app state (opening apps, permissions, media keys, voice). */
interface Platform {
    val versionName: String
    fun openUri(uri: String): Boolean
    fun openSpotify(): Boolean
    fun notificationsAllowed(): Boolean
    fun requestNotifications()
    fun calendarAllowed(): Boolean
    fun requestCalendar()
    fun media(key: MediaKey)
    val voiceAvailable: Boolean
    /** Starts speech-to-text; the result arrives in [onText] (never called if cancelled). */
    fun listen(onText: (String) -> Unit)
    // accounts & sharing
    fun signIn()
    fun signOut()
    fun createSpace(name: String)
    fun joinSpace(code: String)
    fun leaveSpace(id: String)
    fun share(text: String)
    fun connectGoogleTasks()
    fun syncGoogleTasksNow()
    fun loadGLists()
    fun createGList(title: String, copyExisting: Boolean)
    // music & sounds
    /** Installed music/audio apps as (package, name). */
    fun musicApps(): List<Pair<String, String>>
    fun openApp(pkg: String): Boolean
    /** Opens a playlist/station link in a specific app (falls back to any app). */
    fun openLink(uri: String, pkg: String): Boolean
    fun pickAudioFile()
    fun playAmbient(a: app.mycontrolpanel.core.Ambient, timerMin: Int)
    fun playFile(uri: String, name: String, timerMin: Int)
    fun stopSound()
}

val LocalPlatform = staticCompositionLocalOf<Platform> { error("Platform not provided") }

/** Spotify connection (null in previews/tests that don't provide it). */
val LocalSpotify = staticCompositionLocalOf<app.mycontrolpanel.data.SpotifyEngine?> { null }
