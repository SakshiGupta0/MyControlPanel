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
            CompositionLocalProvider(LocalPlatform provides platform) {
                PanelTheme(palette, prefs.textScale, prefs.reduceMotion) {
                    AppRoot(s, env, store::dispatch)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Clock: once per second while a routine timer runs, otherwise once per minute (on the minute).
                // Only while visible, so no polling with the screen off; resumes immediately after sleep.
                launch {
                    store.state.map { it.data.run?.runningSince != null }.distinctUntilChanged().collectLatest { fast ->
                        while (true) {
                            store.dispatch(Action.Tick)
                            val now = System.currentTimeMillis()
                            delay(if (fast) 1000 - now % 1000 + 15 else 60_000 - now % 60_000 + 50)
                        }
                    }
                }
                launch {
                    store.effects.collect { e ->
                        if (e is Effect.PlayAlert) playAlert()
                    }
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
