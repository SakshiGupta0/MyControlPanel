package app.mycontrolpanel

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import android.database.ContentObserver
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.provider.Settings
import android.speech.RecognizerIntent
import android.view.KeyEvent
import app.mycontrolpanel.core.CalendarStatus
import app.mycontrolpanel.core.modeOrNull
import app.mycontrolpanel.system.CalendarSource
import app.mycontrolpanel.system.MediaWatcher
import app.mycontrolpanel.ui.MediaKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.mycontrolpanel.core.Action
import app.mycontrolpanel.core.Derive
import app.mycontrolpanel.core.Effect
import app.mycontrolpanel.system.Alarms
import app.mycontrolpanel.ui.AppRoot
import app.mycontrolpanel.ui.ContrastPalette
import app.mycontrolpanel.ui.DayPalette
import app.mycontrolpanel.ui.LocalPlatform
import app.mycontrolpanel.ui.NightPalette
import app.mycontrolpanel.ui.PanelTheme
import app.mycontrolpanel.ui.Platform
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val app get() = application as App
    private val store get() = app.store

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        store.dispatch(Action.ShowMessage(if (it) "Notifications allowed." else "Notifications stay off. You'll still see dashboard cards."))
    }

    private val platform = object : Platform {
        override val versionName: String = BuildConfig.VERSION_NAME
        override fun openUri(uri: String): Boolean = try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri.trim())).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
        } catch (e: ActivityNotFoundException) { false } catch (e: Exception) { false }
        override fun openSpotify(): Boolean {
            val i = packageManager.getLaunchIntentForPackage("com.spotify.music") ?: return false
            return try { startActivity(i); true } catch (e: Exception) { false }
        }
        override fun notificationsAllowed(): Boolean = Alarms.notificationsAllowed(this@MainActivity)
        override fun requestNotifications() {
            if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        override fun calendarAllowed(): Boolean = CalendarSource.allowed(this@MainActivity)
        override fun requestCalendar() = calendarPermission.launch(Manifest.permission.READ_CALENDAR)
        override fun media(key: MediaKey) {
            val code = when (key) {
                MediaKey.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                MediaKey.NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
                MediaKey.PREVIOUS -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            }
            mediaWatcher.control(code)
        }
        override fun openMediaAccessSettings() {
            runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                .onFailure { store.dispatch(Action.ShowMessage("Open Settings → Notifications → Device & app notifications to allow it.")) }
        }
        override val voiceAvailable: Boolean
            get() = packageManager.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isNotEmpty()
        override fun listen(onText: (String) -> Unit) {
            voiceCallback = onText
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say it — I'll write it down")
            runCatching { voiceLauncher.launch(i) }.onFailure { store.dispatch(Action.ShowMessage("Voice input isn't available on this tablet.")) }
        }

        override fun signIn() { googleSignIn() }
        override fun signOut() {
            app.sync.signOut()
            lifecycleScope.launch { runCatching { CredentialManager.create(this@MainActivity).clearCredentialState(ClearCredentialStateRequest()) } }
        }
        private fun say(m: String) = store.dispatch(Action.ShowMessage(m))
        override fun createSpace(name: String) = app.sync.createSpace(name, ::say)
        override fun joinSpace(code: String) = app.sync.joinSpace(code, ::say)
        override fun leaveSpace(id: String) = app.sync.leaveSpace(id, ::say)
        override fun share(text: String) {
            val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            runCatching { startActivity(Intent.createChooser(i, "Share invite")) }
        }
        override fun connectGoogleTasks() = app.gtasks.connect(gtasksConsent)
        override fun syncGoogleTasksNow() = app.gtasks.syncNow(gtasksConsent)
    }

    private val gtasksConsent = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        app.gtasks.onConsentResult(res.data)
    }

    /** Sign in with Google (Credential Manager) → Firebase Auth. The sync engine notices and starts syncing. */
    private fun googleSignIn() {
        val idRes = resources.getIdentifier("default_web_client_id", "string", packageName)
        if (idRes == 0 || !app.sync.ready) { store.dispatch(Action.ShowMessage("Sign-in isn't set up in this build.")); return }
        lifecycleScope.launch {
            try {
                val option = GetSignInWithGoogleOption.Builder(getString(idRes)).build()
                val req = GetCredentialRequest.Builder().addCredentialOption(option).build()
                val cred = CredentialManager.create(this@MainActivity).getCredential(this@MainActivity, req).credential
                if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val g = GoogleIdTokenCredential.createFrom(cred.data)
                    val user = FirebaseAuth.getInstance().signInWithCredential(GoogleAuthProvider.getCredential(g.idToken, null)).await().user
                    store.dispatch(Action.ShowMessage("Signed in as ${user?.displayName ?: user?.email ?: "you"}. Syncing…"))
                } else store.dispatch(Action.ShowMessage("Google sent an unexpected sign-in response. Please try again."))
            } catch (e: GetCredentialCancellationException) {
                // user closed the sheet — nothing to say
            } catch (e: NoCredentialException) {
                store.dispatch(Action.ShowMessage("No Google account on this tablet. Add one in Android Settings → Accounts, then try again."))
            } catch (e: Exception) {
                store.dispatch(Action.ShowMessage("Sign-in didn't work: ${e.message ?: "unknown error"}"))
            }
        }
    }

    private val calendarPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) store.dispatch(Action.ShowMessage("Calendar stays hidden. You can allow it later in Settings → Calendar."))
        reloadCalendar()
    }

    private var voiceCallback: ((String) -> Unit)? = null
    private val voiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val text = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (res.resultCode == RESULT_OK && !text.isNullOrBlank()) voiceCallback?.invoke(text)
        voiceCallback = null
    }

    private val mediaWatcher by lazy { MediaWatcher(this) { np, access -> store.dispatch(Action.MediaChanged(np, access)) } }

    private val calendarObserver by lazy {
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = reloadCalendar()
        }
    }

    /** Loads yesterday → +2 days in the background, then hands the result to the store. */
    private fun reloadCalendar() {
        if (!store.state.value.data.prefs.calendarOn) {
            store.dispatch(Action.CalendarLoaded(emptyList(), CalendarStatus.OFF)); return
        }
        lifecycleScope.launch {
            val now = System.currentTimeMillis()
            val (status, events) = withContext(Dispatchers.IO) { CalendarSource.load(this@MainActivity, now - 86_400_000L, now + 2 * 86_400_000L) }
            store.dispatch(Action.CalendarLoaded(events, status))
        }
    }

    private var fullscreen = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Back is routed through the store so every screen behaves predictably (PRD §35).
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = store.dispatch(Action.Back)
        })
        handleIntent(intent)

        setContent {
            val s by store.state.collectAsStateWithLifecycle()
            val env = store.env()
            val prefs = s.data.prefs
            val dimmed = Derive.isDimmed(s, env)
            val palette = when {
                dimmed -> NightPalette
                prefs.highContrast -> ContrastPalette
                else -> DayPalette
            }
            LaunchedEffect(prefs.keepScreenOn, prefs.fullscreen, dimmed, palette) {
                applyWindow(prefs.keepScreenOn, prefs.fullscreen, dimmed, palette.bg.toArgb())
            }
            LaunchedEffect(prefs.calendarOn) { reloadCalendar() }
            val tint = Derive.effective(s, env).modeOrNull()?.tint ?: 7
            CompositionLocalProvider(LocalPlatform provides platform) {
                PanelTheme(palette, tint, prefs.textScale, prefs.reduceMotion) {
                    AppRoot(s, env, store::dispatch)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Clock: once per second while a routine timer runs, otherwise once per minute (on the minute).
                // Only while visible, so no polling with the screen off; resumes immediately after sleep.
                launch {
                    store.state.map { it.data.run?.runningSince != null || it.data.focus?.runningSince != null }.distinctUntilChanged().collectLatest { fast ->
                        while (true) {
                            store.dispatch(Action.Tick)
                            val now = System.currentTimeMillis()
                            delay(if (fast) 1000 - now % 1000 + 15 else 60_000 - now % 60_000 + 50)
                        }
                    }
                }
                launch {
                    store.effects.collect { e ->
                        when (e) {
                            Effect.PlayAlert -> playAlert()
                            Effect.Chime -> playChime()
                            else -> {}
                        }
                    }
                }
                // Calendar: refresh on start, when the calendar changes, and every 5 minutes as a fallback.
                launch {
                    if (CalendarSource.allowed(this@MainActivity)) runCatching {
                        contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, calendarObserver)
                    }
                    try {
                        while (true) { reloadCalendar(); delay(5 * 60_000L) }
                    } finally { runCatching { contentResolver.unregisterContentObserver(calendarObserver) } }
                }
                // Google Tasks: every 10 minutes while the panel is visible (and right away on wake).
                launch {
                    while (true) { app.gtasks.syncNow(null); delay(10 * 60_000L) }
                }
                // Music: watch what's playing while visible.
                launch {
                    mediaWatcher.start()
                    try { awaitCancellation() } finally { mediaWatcher.stop() }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val id = intent?.getStringExtra(EXTRA_START_ROUTINE) ?: return
        intent.removeExtra(EXTRA_START_ROUTINE)
        store.dispatch(Action.StartRoutine(id))
    }

    override fun onStart() { super.onStart(); app.inForeground = true; store.dispatch(Action.Tick) }
    override fun onStop() { super.onStop(); app.inForeground = false }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && fullscreen) hideBars() // bars can come back after dialogs or other apps
    }

    private fun applyWindow(keepOn: Boolean, full: Boolean, dimmed: Boolean, bgArgb: Int) {
        if (keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        fullscreen = full
        val ctl = WindowCompat.getInsetsController(window, window.decorView)
        if (full) hideBars() else ctl.show(WindowInsetsCompat.Type.systemBars())
        @Suppress("DEPRECATION")
        run {
            window.statusBarColor = bgArgb
            window.navigationBarColor = bgArgb
        }
        ctl.isAppearanceLightStatusBars = !dimmed
        ctl.isAppearanceLightNavigationBars = !dimmed
        window.attributes = window.attributes.apply {
            screenBrightness = if (dimmed) 0.04f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }

    private fun hideBars() {
        val ctl = WindowCompat.getInsetsController(window, window.decorView)
        ctl.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ctl.hide(WindowInsetsCompat.Type.systemBars())
    }

    /** Soft two-note chime at low volume for the mode-change heads-up. */
    private fun playChime() {
        try {
            val tg = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 35)
            tg.startTone(ToneGenerator.TONE_PROP_ACK, 400)
            Handler(Looper.getMainLooper()).postDelayed({ tg.release() }, 800)
        } catch (_: Exception) { }
    }

    private fun playAlert() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
            RingtoneManager.getRingtone(this, uri)?.play()
        } catch (_: Exception) { }
    }

    companion object {
        const val EXTRA_START_ROUTINE = "startRoutine"
    }
}
