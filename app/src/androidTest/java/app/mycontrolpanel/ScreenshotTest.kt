package app.mycontrolpanel

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mycontrolpanel.core.Action
import app.mycontrolpanel.core.Dialog
import app.mycontrolpanel.core.Feature
import app.mycontrolpanel.core.OverrideLength
import app.mycontrolpanel.core.SettingsSection
import app.mycontrolpanel.core.TaskFilter
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Not a pass/fail check: pictures of every screen and dialog (top and scrolled to the bottom), in portrait and
 * landscape, at normal and large text, so the layout can be reviewed from CI.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val store get() = App.instance.store

    @Before
    fun reset() {
        // Android's one-time "Viewing full screen" notice would cover the pictures.
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("settings put secure immersive_mode_confirmations confirmed").close()
        rule.runOnUiThread {
            store.dispatch(Action.ConfirmReset)
            store.dispatch(Action.UpdatePrefs(store.state.value.data.prefs.copy(nickname = "Sakshi")))
            store.dispatch(Action.SetFeatures(Feature.values().toSet()))
            listOf("Call the bank about the card", "Buy groceries for the week", "Reply to Riya", "Book dentist").forEach { store.dispatch(Action.Capture(it)) }
            store.dispatch(Action.DismissMessage)
        }
        rule.waitForIdle()
    }

    private fun ui(a: Action) { rule.runOnUiThread { store.dispatch(a); store.dispatch(Action.DismissMessage) } }

    @OptIn(ExperimentalTestApi::class)
    private fun shot(name: String, dialog: Boolean = false) {
        rule.waitForIdle()
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("splash").fetchSemanticsNodes().isEmpty() }
        rule.waitForIdle()
        Thread.sleep(300)
        // The whole device screen, dialogs included.
        val bmp = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val dir = File(rule.activity.getExternalFilesDir(null), "shots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
    }

    /** Top of the screen, then scrolled all the way down. */
    private fun shotTopAndBottom(name: String) {
        shot("${name}_a")
        val scrollers = rule.onAllNodes(hasScrollAction()).fetchSemanticsNodes()
        if (scrollers.isEmpty()) return
        for (i in scrollers.indices) repeat(4) { runCatching { rule.onAllNodes(hasScrollAction())[i].performTouchInput { swipeUp() } } }
        shot("${name}_b")
    }

    private fun rotate(o: Int) {
        rule.runOnUiThread { rule.activity.requestedOrientation = o }
        Thread.sleep(1500)
        rule.waitForIdle()
    }

    private fun tour(prefix: String) {
        ui(Action.Back); ui(Action.CloseDialog)
        for (m in listOf("morning", "work", "evening", "bedtime", "dayoff")) {
            ui(Action.SwitchMode(m, OverrideLength.UNTIL_RETURN))
            if (m == "work" || m == "bedtime") shotTopAndBottom("${prefix}_dash_$m") else shot("${prefix}_dash_$m")
        }
        ui(Action.SwitchMode("morning", OverrideLength.UNTIL_RETURN))
        rule.onNodeWithTag("breatherBubble").performClick(); shot("${prefix}_breather")
        rule.onNodeWithTag("breatherClose").performScrollTo().performClick()
        // dialogs
        for ((n, d) in listOf("capture" to Dialog.Capture, "quick" to Dialog.QuickRoutines, "modes" to Dialog.ModeSwitcher, "reset" to Dialog.ConfirmReset)) {
            ui(Action.OpenDialog(d)); shot("${prefix}_dlg_$n", dialog = true); ui(Action.CloseDialog)
        }
        ui(Action.EditTask(null)); shot("${prefix}_dlg_task", dialog = true); ui(Action.CloseDialog)
        // screens
        ui(Action.OpenTasks(TaskFilter.ALL)); shotTopAndBottom("${prefix}_tasks"); ui(Action.Back)
        ui(Action.StartFocus(null, 10)); shot("${prefix}_focus"); ui(Action.EndFocus(false)); ui(Action.Back)
        ui(Action.StartRoutine("r_morning")); shot("${prefix}_routine"); ui(Action.ExitRoutine); ui(Action.CloseDialog)
        for (sec in SettingsSection.values()) { ui(Action.OpenSettings(sec)); shotTopAndBottom("${prefix}_set_${sec.name.lowercase()}") }
        ui(Action.Back)
        ui(Action.EditMode("work")); shotTopAndBottom("${prefix}_edit_mode"); ui(Action.Back); ui(Action.CloseDialog)
        ui(Action.EditRoutine("r_morning")); shotTopAndBottom("${prefix}_edit_routine"); ui(Action.Back); ui(Action.CloseDialog)
        ui(Action.StartOnboarding)
        for (i in 1..5) { shotTopAndBottom("${prefix}_setup_$i"); if (i < 5) rule.onNodeWithTag("onbNext").performClick() }
        ui(Action.OnbSkip)
    }

    @Test
    fun captureEverything() {
        for ((label, o) in listOf("p" to ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, "l" to ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)) {
            rotate(o)
            ui(Action.UpdatePrefs(store.state.value.data.prefs.copy(textScale = 1.0f)))
            tour(label)
            ui(Action.UpdatePrefs(store.state.value.data.prefs.copy(textScale = 1.3f)))
            tour("${label}big")
        }
        ui(Action.UpdatePrefs(store.state.value.data.prefs.copy(textScale = 1.0f)))
        rotate(ActivityInfo.SCREEN_ORIENTATION_FULL_USER)
    }
}
