package app.mycontrolpanel

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.mycontrolpanel.core.Action
import app.mycontrolpanel.core.Defaults
import app.mycontrolpanel.core.Screen
import app.mycontrolpanel.data.Repository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real-device-style smoke tests (PRD §50/§51) — run on an Android emulator in GitHub Actions.
 * Each test starts from defaults.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val store get() = App.instance.store

    @Before
    fun resetToDefaults() {
        rule.runOnUiThread { store.dispatch(Action.ConfirmReset); store.dispatch(Action.DismissMessage) }
        rule.waitForIdle()
    }

    @Test
    fun dashboardShowsClockModeAndWidgets() {
        rule.onNodeWithTag("dashboard").assertIsDisplayed()
        rule.onNodeWithTag("clock").assertIsDisplayed()
        rule.onNodeWithTag("modeChip").assertIsDisplayed()
        rule.onNodeWithTag("waterCard").assertIsDisplayed()
    }

    @Test
    fun waterIncrementPersists() {
        rule.onNodeWithTag("addWater").performClick()
        rule.onNodeWithTag("addWater").performClick()
        rule.waitForIdle()
        assertEquals(2, store.state.value.data.daily.water)
        waitForDisk { it.daily.water == 2 }
    }

    @Test
    fun quickRoutineLaunchesLeavesNoOverlayAndReturns() {
        val modeBefore = store.state.value.data.lastModeKey
        rule.onNodeWithTag("quickRoutinesButton").performClick()
        rule.onNodeWithTag("quickRoutinesDialog").assertExists()
        rule.onNodeWithTag("quickRoutine_r_hydrate").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("quickRoutinesDialog").assertDoesNotExist()
        rule.onNodeWithTag("routineScreen").assertIsDisplayed()
        val steps = Defaults.routines().first { it.id == "r_hydrate" }.steps.size
        repeat(steps) { rule.onNodeWithTag("doneButton").performClick(); rule.waitForIdle() }
        rule.onNodeWithTag("finishButton").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("dashboard").assertIsDisplayed()
        rule.onNodeWithTag("routineScreen").assertDoesNotExist()
        assertNull(store.state.value.ui.dialog)
        assertEquals(modeBefore, store.state.value.data.lastModeKey)
    }

    @Test
    fun backDuringRoutineAsksBeforeExiting() {
        rule.runOnUiThread { store.dispatch(Action.StartRoutine("r_bedtime")) }
        rule.waitForIdle()
        rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }
        rule.waitForIdle()
        rule.onNodeWithTag("confirmDialog").assertExists()
        rule.onNodeWithTag("routineScreen").assertExists()
        rule.onNodeWithTag("confirmButton").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("dashboard").assertIsDisplayed()
        assertNull(store.state.value.data.run)
    }

    @Test
    fun createManualModeSavesAndSurvivesRestart() {
        rule.onNodeWithTag("settingsButton").performClick()
        rule.onNodeWithTag("settingsSection_MODES").performClick()
        rule.onNodeWithTag("newModeButton").performScrollTo().performClick()
        rule.onNodeWithTag("modeNameField").performTextInput("Sick Day")
        rule.onNodeWithTag("scheduleSwitch").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("saveButton").performClick()
        rule.waitForIdle()
        assertTrue(store.state.value.data.modes.any { it.name == "Sick Day" && !it.scheduled })
        assertEquals(Screen.Settings(app.mycontrolpanel.core.SettingsSection.MODES), store.state.value.ui.screen)
        waitForDisk { d -> d.modes.any { it.name == "Sick Day" } } // a fresh load from disk = app restart
    }

    @Test
    fun routineEditorAddsStepAndSaves() {
        rule.onNodeWithTag("settingsButton").performClick()
        rule.onNodeWithTag("settingsSection_ROUTINES").performClick()
        rule.onNodeWithTag("newRoutineButton").performScrollTo().performClick()
        rule.onNodeWithTag("routineNameField").performTextInput("Plants")
        rule.onNodeWithTag("addStep").performClick()
        rule.onNodeWithTag("stepTitleField").performTextInput("Water the plants")
        rule.onNodeWithTag("saveStep").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("saveButton").performClick()
        rule.waitForIdle()
        val r = store.state.value.data.routines.first { it.name == "Plants" }
        assertEquals(listOf("Water the plants"), r.steps.map { it.title })
        waitForDisk { d -> d.routines.any { it.name == "Plants" } }
    }

    @Test
    fun cancelWithChangesAsksToDiscard() {
        rule.onNodeWithTag("settingsButton").performClick()
        rule.onNodeWithTag("settingsSection_MODES").performClick()
        rule.onNodeWithTag("newModeButton").performScrollTo().performClick()
        rule.onNodeWithTag("modeNameField").performTextInput("Draft")
        rule.onNodeWithTag("cancelEdit").performClick()
        rule.onNodeWithTag("confirmDialog").assertExists()
        rule.onNodeWithTag("confirmButton").performClick()
        rule.waitForIdle()
        assertTrue(store.state.value.data.modes.none { it.name == "Draft" })
        rule.onNodeWithTag("settingsScreen").assertIsDisplayed()
    }

    @Test
    fun resetCancelThenConfirm() {
        rule.onNodeWithTag("addWater").performClick()
        rule.onNodeWithTag("settingsButton").performClick()
        rule.onNodeWithTag("settingsSection_RESET").performClick()
        rule.onNodeWithTag("resetButton").performScrollTo().performClick()
        rule.onNodeWithTag("cancelButton").performClick()
        rule.waitForIdle()
        assertEquals(1, store.state.value.data.daily.water)
        rule.onNodeWithTag("resetButton").performScrollTo().performClick()
        rule.onNodeWithTag("confirmButton").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("dashboard").assertIsDisplayed()
        rule.onNodeWithTag("confirmDialog").assertDoesNotExist()
        assertEquals(Defaults.modes(), store.state.value.data.modes)
        assertEquals(0, store.state.value.data.daily.water)
    }

    @Test
    fun manualOverrideAndReturnToSchedule() {
        rule.onNodeWithTag("modeChip").performClick()
        rule.onNodeWithTag("len_UNTIL_RETURN").performClick()
        val target = Defaults.modes().first { it.id != store.state.value.data.lastModeKey }
        rule.onNodeWithTag("switchTo_${target.id}").performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("modeSwitcherDialog").assertDoesNotExist()
        assertEquals(target.id, store.state.value.data.override?.modeId)
        rule.onNodeWithTag("modeName", useUnmergedTree = true).assertTextContains(target.name)
        rule.onNodeWithTag("returnToSchedule").performClick()
        rule.waitForIdle()
        assertNull(store.state.value.data.override)
    }

    /** Persistence is async (background thread); poll the file like a cold start would read it. */
    private fun waitForDisk(check: (app.mycontrolpanel.core.AppData) -> Boolean) {
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        val deadline = System.currentTimeMillis() + 5000
        while (System.currentTimeMillis() < deadline) {
            if (check(Repository(dir).load().data)) return
            Thread.sleep(100)
        }
        throw AssertionError("Change was not written to disk within 5 s")
    }
}
