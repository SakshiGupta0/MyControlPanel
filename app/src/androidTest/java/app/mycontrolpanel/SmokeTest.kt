package app.mycontrolpanel

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.SemanticsNodeInteraction
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
/** Scroll the node into view when it lives in a scrolling container (phone layouts), then click. */
fun SemanticsNodeInteraction.scrollIfPossible(): SemanticsNodeInteraction {
    try { performScrollTo() } catch (_: AssertionError) { } catch (_: IllegalStateException) { }
    return this
}
fun SemanticsNodeInteraction.tap(): SemanticsNodeInteraction = scrollIfPossible().performClick()
fun SemanticsNodeInteraction.typeIn(text: String): SemanticsNodeInteraction { scrollIfPossible().performTextInput(text); return this }

@RunWith(AndroidJUnit4::class)
class SmokeTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val store get() = App.instance.store

    @Before
    fun resetToDefaults() {
        rule.runOnUiThread {
            store.dispatch(Action.ConfirmReset)
            // The breather bubble floats over content; it has its own test, so keep it out of the way here.
            store.dispatch(Action.SetFeatures(app.mycontrolpanel.core.Onboarding.preset(app.mycontrolpanel.core.Profile.BALANCED) - app.mycontrolpanel.core.Feature.OVERWHELMED))
            store.dispatch(Action.DismissMessage)
        }
        rule.waitForIdle()
    }

    @Test
    fun dashboardShowsClockModeAndWidgets() {
        rule.onNodeWithTag("dashboard").assertIsDisplayed()
        rule.onNodeWithTag("clock").assertIsDisplayed()
        rule.onNodeWithTag("modeChip").assertIsDisplayed()
        rule.onNodeWithTag("waterCard").assertExists()
    }

    @Test
    fun waterIncrementPersists() {
        rule.onNodeWithTag("addWater").tap()
        rule.onNodeWithTag("addWater").tap()
        rule.waitForIdle()
        assertEquals(2, store.state.value.data.daily.water)
        waitForDisk { it.daily.water == 2 }
    }

    @Test
    fun quickRoutineLaunchesLeavesNoOverlayAndReturns() {
        val modeBefore = store.state.value.data.lastModeKey
        rule.onNodeWithTag("quickRoutinesButton").tap()
        rule.onNodeWithTag("quickRoutinesDialog").assertExists()
        rule.onNodeWithTag("quickRoutine_r_hydrate").tap()
        rule.waitForIdle()
        rule.onNodeWithTag("quickRoutinesDialog").assertDoesNotExist()
        rule.onNodeWithTag("routineScreen").assertIsDisplayed()
        val steps = Defaults.routines().first { it.id == "r_hydrate" }.steps.size
        repeat(steps) { rule.onNodeWithTag("doneButton").tap(); rule.waitForIdle() }
        rule.onNodeWithTag("finishButton").tap()
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
        rule.onNodeWithTag("confirmButton").tap()
        rule.waitForIdle()
        rule.onNodeWithTag("dashboard").assertIsDisplayed()
        assertNull(store.state.value.data.run)
    }

    @Test
    fun createManualModeSavesAndSurvivesRestart() {
        rule.onNodeWithTag("settingsButton").tap()
        rule.onNodeWithTag("settingsSection_MODES").tap()
        rule.onNodeWithTag("newModeButton").tap()
        rule.onNodeWithTag("modeNameField").typeIn("Sick Day")
        rule.onNodeWithTag("scheduleSwitch").tap()
        rule.waitForIdle()
        rule.onNodeWithTag("saveButton").tap()
        rule.waitForIdle()
        assertTrue(store.state.value.data.modes.any { it.name == "Sick Day" && !it.scheduled })
        assertEquals(Screen.Settings(app.mycontrolpanel.core.SettingsSection.MODES), store.state.value.ui.screen)
        waitForDisk { d -> d.modes.any { it.name == "Sick Day" } } // a fresh load from disk = app restart
    }

    @Test
    fun routineEditorAddsStepAndSaves() {
        rule.onNodeWithTag("settingsButton").tap()
        rule.onNodeWithTag("settingsSection_ROUTINES").tap()
        rule.onNodeWithTag("newRoutineButton").tap()
        rule.onNodeWithTag("routineNameField").typeIn("Plants")
        rule.onNodeWithTag("addStep").tap()
        rule.onNodeWithTag("stepTitleField").typeIn("Water the plants")
        rule.onNodeWithTag("saveStep").tap()
        rule.waitForIdle()
        rule.onNodeWithTag("saveButton").tap()
        rule.waitForIdle()
        val r = store.state.value.data.routines.first { it.name == "Plants" }
        assertEquals(listOf("Water the plants"), r.steps.map { it.title })
        waitForDisk { d -> d.routines.any { it.name == "Plants" } }
    }

    @Test
    fun cancelWithChangesAsksToDiscard() {
        rule.onNodeWithTag("settingsButton").tap()
        rule.onNodeWithTag("settingsSection_MODES").tap()
        rule.onNodeWithTag("newModeButton").tap()
        rule.onNodeWithTag("modeNameField").typeIn("Draft")
        rule.onNodeWithTag("cancelEdit").tap()
        rule.onNodeWithTag("confirmDialog").assertExists()
        rule.onNodeWithTag("confirmButton").tap()
        rule.waitForIdle()
        assertTrue(store.state.value.data.modes.none { it.name == "Draft" })
        rule.onNodeWithTag("settingsScreen").assertIsDisplayed()
    }

    @Test
    fun resetCancelThenConfirm() {
        rule.onNodeWithTag("addWater").tap()
        rule.onNodeWithTag("settingsButton").tap()
        rule.onNodeWithTag("settingsSection_RESET").tap()
        rule.onNodeWithTag("resetButton").tap()
        rule.onNodeWithTag("cancelButton").tap()
        rule.waitForIdle()
        assertEquals(1, store.state.value.data.daily.water)
        rule.onNodeWithTag("resetButton").tap()
        rule.onNodeWithTag("confirmButton").tap()
        rule.waitForIdle()
        rule.onNodeWithTag("dashboard").assertIsDisplayed()
        rule.onNodeWithTag("confirmDialog").assertDoesNotExist()
        assertEquals(Defaults.modes(), store.state.value.data.modes)
        assertEquals(0, store.state.value.data.daily.water)
    }

    @Test
    fun manualOverrideAndReturnToSchedule() {
        rule.onNodeWithTag("modeChip").tap()
        rule.onNodeWithTag("len_UNTIL_RETURN").tap()
        val target = Defaults.modes().first { it.id != store.state.value.data.lastModeKey }
        rule.onNodeWithTag("switchTo_${target.id}").tap()
        rule.waitForIdle()
        rule.onNodeWithTag("modeSwitcherDialog").assertDoesNotExist()
        assertEquals(target.id, store.state.value.data.override?.modeId)
        rule.onNodeWithTag("modeName", useUnmergedTree = true).assertTextContains(target.name)
        rule.onNodeWithTag("returnToSchedule").tap()
        rule.waitForIdle()
        assertNull(store.state.value.data.override)
    }

    @Test
    fun brainDumpGoesToInboxAndStarsIntoToday() {
        rule.onNodeWithTag("captureButton").tap()
        rule.onNodeWithTag("captureDialog").assertExists()
        rule.onNodeWithTag("captureField").typeIn("Call the bank")
        rule.onNodeWithTag("captureSave").tap()
        rule.waitForIdle()
        rule.onNodeWithTag("captureDialog").assertDoesNotExist()
        val t = store.state.value.data.tasks.single()
        assertEquals("Call the bank", t.title)
        waitForDisk { d -> d.tasks.any { it.title == "Call the bank" } }
        rule.onNodeWithTag("openTasks").tap()
        rule.onNodeWithTag("filter_inbox").tap()
        rule.onNodeWithTag("star_Call the bank").tap()
        rule.waitForIdle()
        assertTrue(store.state.value.data.tasks.single().today)
        rule.onNodeWithTag("closeTasks").tap()
        rule.onNodeWithTag("check_Call the bank").tap()
        rule.waitForIdle()
        assertTrue(store.state.value.data.tasks.single().done)
    }

    @Test
    fun focusTimerStartsAndEndsWithTaskDone() {
        rule.runOnUiThread { store.dispatch(Action.Capture("Write intro", today = true)) }
        rule.waitForIdle()
        rule.runOnUiThread { store.dispatch(Action.StartFocus(store.state.value.data.tasks.single().id, 25)) }
        rule.waitForIdle()
        rule.onNodeWithTag("focusScreen").assertIsDisplayed()
        rule.onNodeWithTag("focusPause").tap()
        rule.waitForIdle()
        assertNull(store.state.value.data.focus!!.runningSince)
        rule.onNodeWithTag("focusResume").tap()
        rule.onNodeWithTag("focusDoneEarly").tap()
        rule.waitForIdle()
        rule.onNodeWithTag("dashboard").assertIsDisplayed()
        assertNull(store.state.value.data.focus)
        assertTrue(store.state.value.data.tasks.single().done)
    }

    @Test
    fun nowCardAndHeadsUpRender() {
        rule.onNodeWithTag("nowCard").assertExists()
        rule.onNodeWithTag("todayCard").assertExists()
        rule.onNodeWithTag("doneToday").assertDoesNotExist()   // "Done today" is only in the Everything preset
    }

    @Test
    fun accountScreenOffersSignInWithoutBreakingLocalUse() {
        rule.onNodeWithTag("settingsButton").tap()
        rule.onNodeWithTag("settingsSection_ACCOUNT").tap()
        rule.waitForIdle()
        assertTrue("Firebase should be configured in this build", store.state.value.live.firebaseReady)
        if (store.state.value.live.account == null) rule.onNodeWithTag("signIn").scrollIfPossible().assertExists()
        rule.onNodeWithTag("closeSettings").tap()
        rule.onNodeWithTag("dashboard").assertIsDisplayed()
    }

    @Test
    fun onboardingBuildsASimpleDashboard() {
        rule.runOnUiThread { store.dispatch(Action.StartOnboarding) }
        rule.waitForIdle()
        rule.onNodeWithTag("onboarding").assertExists()
        rule.onNodeWithTag("onbNickname").typeIn("Riya")
        rule.onNodeWithTag("onbNext").tap()   // hello → this device
        rule.onNodeWithTag("use_PHONE").tap()
        rule.onNodeWithTag("profile_SIMPLE").tap()
        rule.onNodeWithTag("onbNext").tap()   // → what helps + music
        rule.onNodeWithTag("need_WATER").tap()
        rule.onNodeWithTag("music_BUILT_IN").tap()
        rule.onNodeWithTag("onbNext").tap()   // → your day
        rule.onNodeWithTag("onbNext").tap()   // → all set
        rule.onNodeWithTag("onbNext").tap()   // finish
        rule.waitForIdle()
        rule.onNodeWithTag("dashboard").assertExists()
        val f = store.state.value.data.prefs.features
        assertTrue(app.mycontrolpanel.core.Feature.WATER in f)
        assertTrue(app.mycontrolpanel.core.Feature.MEALS !in f)
        rule.onNodeWithTag("waterCard").assertExists()
        rule.onNodeWithTag("mealsCard").assertDoesNotExist()
        rule.onNodeWithTag("nowCard").assertExists()
        rule.onNodeWithTag("mochiHome", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("breatherBubble").assertExists()   // on in every preset
        assertTrue(store.state.value.data.prefs.nickname == "Riya")
        rule.onNodeWithTag("greeting", useUnmergedTree = true).assertTextContains("Riya", substring = true)
        assertTrue(store.state.value.data.prefs.music.provider == app.mycontrolpanel.core.MusicProvider.BUILT_IN)
    }

    @Test
    fun breatherBubbleOpensACalmBreathingScreen() {
        rule.runOnUiThread { store.dispatch(Action.SetFeatures(store.state.value.data.prefs.features + app.mycontrolpanel.core.Feature.OVERWHELMED)) }
        rule.waitForIdle()
        rule.onNodeWithTag("breatherBubble").assertIsDisplayed().performClick()
        rule.onNodeWithTag("breather").assertIsDisplayed()
        rule.onNodeWithTag("breathCue", useUnmergedTree = true).assertExists()
        rule.onNodeWithTag("breatherClose").tap()
        rule.onNodeWithTag("breather").assertDoesNotExist()
        rule.onNodeWithTag("dashboard").assertIsDisplayed()
        rule.onNodeWithTag("backdrop").assertExists()
    }

    @Test
    fun musicSettingsOfferEveryProviderAndSpotifyKeyIsInApp() {
        rule.runOnUiThread { store.dispatch(Action.OpenSettings(app.mycontrolpanel.core.SettingsSection.MUSIC)) }
        rule.waitForIdle()
        rule.onNodeWithTag("provider_BUILT_IN").scrollIfPossible().tap()
        rule.onNodeWithTag("sleepTimer_plus").scrollIfPossible().assertExists()
        rule.onNodeWithTag("provider_SPOTIFY").scrollIfPossible().tap()
        rule.onNodeWithTag("spotifyClientId").scrollIfPossible().assertExists()
        assertTrue(store.state.value.data.prefs.music.provider == app.mycontrolpanel.core.MusicProvider.SPOTIFY)
        rule.onNodeWithTag("provider_BUILT_IN").scrollIfPossible().tap()
        rule.onNodeWithTag("closeSettings").tap()
        rule.onNodeWithTag("dashboard").assertIsDisplayed()
    }

    @Test
    fun dashboardPresetsSwitchWhatIsShown() {
        rule.onNodeWithTag("settingsButton").tap()
        rule.onNodeWithTag("preset_SIMPLE").tap()
        rule.onNodeWithTag("closeSettings").tap()
        rule.waitForIdle()
        rule.onNodeWithTag("waterCard").assertDoesNotExist()
        rule.onNodeWithTag("quickRoutinesButton").assertDoesNotExist()
        rule.onNodeWithTag("captureButton").assertExists()
        rule.onNodeWithTag("settingsButton").tap()
        rule.onNodeWithTag("preset_EVERYTHING").tap()
        rule.onNodeWithTag("closeSettings").tap()
        rule.waitForIdle()
        rule.onNodeWithTag("doneToday").assertExists()
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
