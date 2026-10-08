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
    fun openMediaAccessSettings()
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
}

val LocalPlatform = staticCompositionLocalOf<Platform> { error("Platform not provided") }
