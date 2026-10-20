package app.mycontrolpanel

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mycontrolpanel.core.Action
import app.mycontrolpanel.core.Feature
import app.mycontrolpanel.core.OverrideLength
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Not a pass/fail check: saves pictures of every phase, setup and the breather in portrait and
 * landscape, so the look can be reviewed from CI (pulled off the emulator as an artifact).
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val store get() = App.instance.store

    @Before
    fun reset() {
        rule.runOnUiThread {
            store.dispatch(Action.ConfirmReset)
            store.dispatch(Action.UpdatePrefs(store.state.value.data.prefs.copy(nickname = "Sakshi")))
            store.dispatch(Action.SetFeatures(Feature.values().toSet()))
            store.dispatch(Action.DismissMessage)
        }
        rule.waitForIdle()
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val dir = File(rule.activity.getExternalFilesDir(null), "shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun rotate(o: Int) {
        rule.runOnUiThread { rule.activity.requestedOrientation = o }
        Thread.sleep(1500)
        rule.waitForIdle()
    }

    @Test
    fun capturePhasesSetupAndBreather() {
        for ((label, o) in listOf("portrait" to ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, "landscape" to ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)) {
            rotate(o)
            for (m in listOf("morning", "work", "evening", "bedtime", "dayoff")) {
                rule.runOnUiThread { store.dispatch(Action.SwitchMode(m, OverrideLength.UNTIL_RETURN)) }
                shot("${label}_1_dashboard_$m")
            }
            rule.runOnUiThread { store.dispatch(Action.SwitchMode("morning", OverrideLength.UNTIL_RETURN)) }
            rule.onNodeWithTag("breatherBubble").performClick()
            shot("${label}_2_breather")
            rule.onNodeWithTag("breatherClose").performClick()
            rule.runOnUiThread { store.dispatch(Action.StartOnboarding) }
            shot("${label}_3_setup_1_welcome")
            for (i in 2..5) {
                rule.onNodeWithTag("onbNext").performClick()
                shot("${label}_3_setup_$i")
            }
            rule.onNodeWithTag("onbNext").performClick()
            rule.runOnUiThread { store.dispatch(Action.OpenSettings(app.mycontrolpanel.core.SettingsSection.MUSIC)) }
            shot("${label}_4_settings_music")
            rule.runOnUiThread { store.dispatch(Action.Back) }
        }
        rotate(ActivityInfo.SCREEN_ORIENTATION_FULL_USER)
    }
}
