package app.mycontrolpanel.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.mycontrolpanel.core.Action
import app.mycontrolpanel.core.AppState
import app.mycontrolpanel.core.Derive
import app.mycontrolpanel.core.Dialog
import app.mycontrolpanel.core.Effective
import app.mycontrolpanel.core.Env
import app.mycontrolpanel.core.OverrideLength
import app.mycontrolpanel.core.Screen
import kotlinx.coroutines.delay

/** Renders the current screen and (at most one) dialog straight from state. */
@Composable
fun AppRoot(s: AppState, env: Env, dispatch: Dispatch) {
    val p = P()
    val reduceMotion = LocalReduceMotion.current
    Box(Modifier.fillMaxSize().background(p.bg)) {
        // Key the transition by screen *kind* so typing in an editor doesn't re-animate.
        val kind = when (s.ui.screen) {
            Screen.Dashboard -> "dashboard"; Screen.RunningRoutine -> "routine"
            is Screen.Settings -> "settings"; is Screen.ModeEditor -> "modeEditor"; is Screen.RoutineEditor -> "routineEditor"
        }
        AnimatedContent(targetState = kind, label = "screen",
            transitionSpec = { if (reduceMotion) fadeIn(tween(0)) togetherWith fadeOut(tween(0)) else fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }
        ) { k ->
            // Always render from the *current* state, never a captured copy.
            when (val sc = s.ui.screen) {
                Screen.Dashboard -> if (k == "dashboard") Dashboard(s, env, dispatch)
                Screen.RunningRoutine -> if (k == "routine") RoutinePlayer(s, dispatch)
                is Screen.Settings -> if (k == "settings") SettingsScreen(s, sc.section, dispatch)
                is Screen.ModeEditor -> if (k == "modeEditor") ModeEditor(s, sc, dispatch)
                is Screen.RoutineEditor -> if (k == "routineEditor") RoutineEditor(s, sc, dispatch)
            }
        }

        when (val dl = s.ui.dialog) {
            null -> {}
            Dialog.QuickRoutines -> QuickRoutinesDialog(s, dispatch)
            Dialog.ModeSwitcher -> ModeSwitcherDialog(s, env, dispatch)
            Dialog.ConfirmExitRoutine -> ConfirmDialog("Exit this routine?", "You can start it again any time.", "Exit routine",
                { dispatch(Action.ExitRoutine) }, { dispatch(Action.CloseDialog) }, dismiss = "Keep going")
            Dialog.ConfirmReset -> ConfirmDialog("Reset everything?", "All modes, routines, widgets and preferences go back to the defaults. This can't be undone.",
                "Reset", { dispatch(Action.ConfirmReset) }, { dispatch(Action.CloseDialog) }, danger = true)
            Dialog.ConfirmDiscard -> ConfirmDialog("Discard changes?", "Your unsaved changes will be lost.", "Discard",
                { dispatch(Action.ConfirmDiscard) }, { dispatch(Action.CloseDialog) }, danger = true, dismiss = "Keep editing")
            is Dialog.ConfirmDeleteMode -> ConfirmDialog("Delete “${s.data.modes.find { it.id == dl.id }?.name ?: "mode"}”?",
                "Routines assigned to it stay, just without this mode.", "Delete", { dispatch(Action.ConfirmDelete) }, { dispatch(Action.CloseDialog) }, danger = true)
            is Dialog.ConfirmDeleteRoutine -> ConfirmDialog("Delete “${s.data.routines.find { it.id == dl.id }?.name ?: "routine"}”?",
                "Its steps will be gone for good.", "Delete", { dispatch(Action.ConfirmDelete) }, { dispatch(Action.CloseDialog) }, danger = true)
            is Dialog.StepEditor -> StepEditorDialog(dl.index, dl.step, dispatch)
        }

        s.ui.message?.let { msg ->
            LaunchedEffect(msg) { delay(4500); dispatch(Action.DismissMessage) }
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp)
                .clip(RoundedCornerShape(18.dp)).background(p.text).clickable { dispatch(Action.DismissMessage) }
                .padding(horizontal = 28.dp, vertical = 16.dp).widthIn(max = 760.dp).testTag("message")) {
                T(msg, Type.label, p.bg)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickRoutinesDialog(s: AppState, dispatch: Dispatch) {
    val p = P()
    val list = Derive.quickRoutines(s)
    AlertDialog(
        onDismissRequest = { dispatch(Action.CloseDialog) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 1000.dp).padding(32.dp).testTag("quickRoutinesDialog"),
        containerColor = p.card,
        title = { T("Quick routines", Type.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (list.isEmpty()) T("No Quick Routines yet. Turn on “Quick Routine” for any routine in Settings → Routines.", Type.body, p.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    list.forEach { r ->
                        // One tap = one action: the reducer closes this popup and opens the routine in a single state change.
                        Column(
                            Modifier.width(210.dp).heightIn(min = 130.dp).clip(CardShape).background(p.cardStrong)
                                .clickable { dispatch(Action.StartRoutine(r.id)) }.padding(18.dp).testTag("quickRoutine_${r.id}"),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            T(r.icon, Type.display)
                            T(r.name, Type.label)
                            val mins = routineMinutes(r)
                            T("${r.steps.size} steps" + if (mins > 0) " · ~$mins min" else "", Type.small, p.muted)
                        }
                    }
                }
            }
        },
        confirmButton = { SecondaryButton("Close", { dispatch(Action.CloseDialog) }, tag = "closeQuick") },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ModeSwitcherDialog(s: AppState, env: Env, dispatch: Dispatch) {
    val p = P()
    var length by remember { mutableStateOf(OverrideLength.NEXT_CHANGE) }
    val eff = Derive.effective(s, env)
    AlertDialog(
        onDismissRequest = { dispatch(Action.CloseDialog) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 1000.dp).padding(32.dp).testTag("modeSwitcherDialog"),
        containerColor = p.card,
        title = { T("Switch mode", Type.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                T("For how long?", Type.label, p.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OverrideLength.values().forEach { l -> Chip(l.label, l == length, { length = l }, tag = "len_${l.name}") }
                }
                T("Then pick a mode:", Type.label, p.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    s.data.modes.sortedBy { it.order }.forEach { m ->
                        val current = (eff as? Effective.Overridden)?.mode?.id == m.id || (eff as? Effective.Scheduled)?.mode?.id == m.id
                        Row(
                            Modifier.widthIn(min = 200.dp).heightIn(min = 72.dp).clip(CardShape)
                                .background(if (current) p.accentSoft else p.cardStrong)
                                .clickable { dispatch(Action.SwitchMode(m.id, length)) }
                                .padding(horizontal = 20.dp).testTag("switchTo_${m.id}"),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            T(m.icon, Type.title); T("  ${m.name}", Type.heading)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (eff is Effective.Overridden) PrimaryButton("Back to schedule", { dispatch(Action.ReturnToSchedule) }, tag = "dialogReturnToSchedule")
                SecondaryButton("Close", { dispatch(Action.CloseDialog) })
            }
        },
    )
}
