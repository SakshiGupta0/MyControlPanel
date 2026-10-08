package app.mycontrolpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.mycontrolpanel.core.Action
import app.mycontrolpanel.core.AppState
import app.mycontrolpanel.core.DAY_SHORT
import app.mycontrolpanel.core.Mode
import app.mycontrolpanel.core.NotifyStyle
import app.mycontrolpanel.core.Reducer
import app.mycontrolpanel.core.RoutineSchedule
import app.mycontrolpanel.core.Scheduler
import app.mycontrolpanel.core.Screen
import app.mycontrolpanel.core.Step
import app.mycontrolpanel.core.Validation
import app.mycontrolpanel.core.WidgetSlot
import app.mycontrolpanel.core.fmtMinutes

/** Shared editor frame: Cancel · title · Save, plus the first validation problem. */
@Composable
private fun EditorFrame(title: String, problem: String?, dispatch: Dispatch, onSave: () -> Unit, left: @Composable () -> Unit, right: @Composable () -> Unit) {
    val p = P()
    Column(Modifier.fillMaxSize().background(p.bg).testTag("editorScreen")) {
        Row(Modifier.fillMaxWidth().background(p.cardStrong).padding(horizontal = 28.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton("Cancel", { dispatch(Action.CancelEdit) }, tag = "cancelEdit")
            T(title, Type.title, modifier = Modifier.weight(1f).padding(horizontal = 24.dp))
            if (problem != null) T(problem, Type.small, p.warn, Modifier.widthIn(max = 420.dp).padding(end = 16.dp))
            PrimaryButton("Save", onSave, tag = "saveButton", enabled = problem == null)
        }
        Row(Modifier.fillMaxSize().padding(horizontal = 32.dp), horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) { left(); Spacer(Modifier.height(40.dp)) }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) { right(); Spacer(Modifier.height(40.dp)) }
        }
    }
}

// ============================ Mode editor ============================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ModeEditor(s: AppState, ed: Screen.ModeEditor, dispatch: Dispatch) {
    val p = P()
    val m = ed.draft
    val use24h = s.data.prefs.use24h
    val others = Reducer.othersAfterSave(s.data.modes, ed)
    val problem = Validation.mode(m, others).firstOrNull()
    fun set(nm: Mode) = dispatch(Action.UpdateModeDraft(nm))

    EditorFrame(if (ed.isNew) "New mode" else "Edit ${ed.initial.name}", problem, dispatch, { dispatch(Action.SaveMode) },
        left = {
            PanelTextField(m.name, { set(m.copy(name = it)) }, "Name", tag = "modeNameField")
            T("Icon", Type.label, p.muted); IconPicker(m.icon) { set(m.copy(icon = it)) }
            T("Colour", Type.label, p.muted); TintSwatches(m.tint) { set(m.copy(tint = it)) }
            PanelCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ToggleRow("Runs on schedule", m.scheduled, { set(m.copy(scheduled = it)) },
                        sub = if (m.scheduled) "Switches on automatically at these times" else "Manual only — pick it from the mode button when you need it",
                        tag = "scheduleSwitch")
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        TimeField("Starts", m.start, use24h, { set(m.copy(start = it)) }, tag = "startTime")
                        T("→", Type.title, p.muted)
                        TimeField("Ends", m.end, use24h, { set(m.copy(end = it)) }, tag = "endTime")
                    }
                    if (m.end < m.start) T("Crosses midnight — ends the next morning. Days below are the days it starts.", Type.small, p.muted)
                    T("Days", Type.label, p.muted)
                    DayChips(m.days) { set(m.copy(days = it)) }
                }
            }
            if (m.scheduled) ConflictPanel(s, ed, others, dispatch)
            if (!ed.isNew) SecondaryButton("Delete this mode", { dispatch(Action.DeleteMode(m.id)) }, tag = "deleteMode", color = p.danger)
        },
        right = {
            PanelCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    T("Featured routine", Type.heading)
                    T("The big Start button on the dashboard in this mode.", Type.small, p.muted)
                    val choices = listOf<Pair<String?, String>>(null to "None") + s.data.routines.sortedBy { it.order }.map { it.id to "${it.icon}  ${it.name}" }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        choices.forEach { (id, label) -> Chip(label, m.featuredRoutineId == id, { set(m.copy(featuredRoutineId = id)) }) }
                    }
                }
            }
            PanelCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    T("Dashboard widgets", Type.heading)
                    T("Clock, Right now, today's list, water and meals are always shown. Choose and order the rest:", Type.small, p.muted)
                    m.widgets.forEachIndexed { i, slot ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                ToggleRow(slot.widget.label, slot.enabled, { on ->
                                    set(m.copy(widgets = m.widgets.mapIndexed { j, w -> if (j == i) WidgetSlot(w.widget, on) else w }))
                                })
                            }
                            Spacer(Modifier.width(8.dp))
                            RoundIconButton("↑", { set(m.copy(widgets = m.widgets.move(i, -1))) }, enabled = i > 0, size = 48.dp)
                            Spacer(Modifier.width(6.dp))
                            RoundIconButton("↓", { set(m.copy(widgets = m.widgets.move(i, 1))) }, enabled = i < m.widgets.size - 1, size = 48.dp)
                        }
                    }
                }
            }
        })
}

fun <T> List<T>.move(i: Int, delta: Int): List<T> {
    val to = i + delta
    if (i !in indices || to !in indices) return this
    return toMutableList().also { it.add(to, it.removeAt(i)) }
}

@Composable
private fun ConflictPanel(s: AppState, ed: Screen.ModeEditor, others: List<Mode>, dispatch: Dispatch) {
    val p = P()
    val m = ed.draft
    val use24h = s.data.prefs.use24h
    val conflicts = Scheduler.conflictsFor(m, others)
    val name = m.name.ifBlank { "This mode" }
    val wins = m.winsOver.mapNotNull { id -> s.data.modes.find { it.id == id } }
    val disabled = ed.disableOnSave.mapNotNull { id -> s.data.modes.find { it.id == id } }
    if (conflicts.isEmpty() && wins.isEmpty() && disabled.isEmpty()) return

    PanelCard(Modifier.fillMaxWidth().testTag("conflictPanel")) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (conflicts.isNotEmpty()) {
                T("⚠️  Overlaps with other modes", Type.heading, p.warn)
                T("Two modes can't both be on at once. Change the times, or decide for each overlap:", Type.small, p.muted)
                conflicts.groupBy { if (it.a.id == m.id) it.b.id else it.a.id }.forEach { (otherId, list) ->
                    val other = s.data.modes.find { it.id == otherId } ?: return@forEach
                    val first = list.first()
                    PanelCard(Modifier.fillMaxWidth(), strong = true, padding = 16.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            T("${other.icon} ${other.name}", Type.label)
                            T("${DAY_SHORT[first.dow - 1]} ${fmtMinutes(first.start, use24h)}–${fmtMinutes(first.end, use24h)}" +
                                if (list.size > 1) "  (+${list.size - 1} more)" else "", Type.small, p.muted)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SecondaryButton("$name wins", { dispatch(Action.UpdateModeDraft(m.copy(winsOver = m.winsOver + otherId))) }, tag = "winsOver_$otherId")
                                QuietButton("Turn off ${other.name}'s schedule", { dispatch(Action.DisableOnSave(otherId, true)) })
                            }
                        }
                    }
                }
            }
            wins.forEach { o ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("✓ $name takes priority over ${o.name} when they overlap", Type.small, p.muted, Modifier.weight(1f))
                    QuietButton("Undo", { dispatch(Action.UpdateModeDraft(m.copy(winsOver = m.winsOver - o.id))) })
                }
            }
            disabled.forEach { o ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("✓ ${o.name} becomes manual-only when you save", Type.small, p.muted, Modifier.weight(1f))
                    QuietButton("Undo", { dispatch(Action.DisableOnSave(o.id, false)) })
                }
            }
        }
    }
}

// ============================ Routine editor ============================

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoutineEditor(s: AppState, ed: Screen.RoutineEditor, dispatch: Dispatch) {
    val p = P()
    val r = ed.draft
    val use24h = s.data.prefs.use24h
    val problem = Validation.routine(r).firstOrNull()
    fun set(nr: app.mycontrolpanel.core.Routine) = dispatch(Action.UpdateRoutineDraft(nr))

    EditorFrame(if (ed.isNew) "New routine" else "Edit ${ed.initial.name}", problem, dispatch, { dispatch(Action.SaveRoutine) },
        left = {
            PanelTextField(r.name, { set(r.copy(name = it)) }, "Name", tag = "routineNameField")
            PanelTextField(r.description, { set(r.copy(description = it)) }, "Short description (optional)")
            T("Icon", Type.label, p.muted); IconPicker(r.icon) { set(r.copy(icon = it)) }
            PanelCard(Modifier.fillMaxWidth()) {
                Column {
                    ToggleRow("Enabled", r.enabled, { set(r.copy(enabled = it)) })
                    ToggleRow("Quick Routine", r.quick, { set(r.copy(quick = it)) }, sub = "Available from the dashboard in any mode", tag = "quickSwitch")
                }
            }
            PanelCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    T("Show in modes", Type.heading)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        s.data.modes.sortedBy { it.order }.forEach { m ->
                            val on = m.id in r.modeIds
                            Chip("${m.icon} ${m.name}", on, { set(r.copy(modeIds = if (on) r.modeIds - m.id else r.modeIds + m.id)) })
                        }
                    }
                }
            }
            PanelCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val sc = r.schedule
                    ToggleRow("Reminder on a schedule", sc != null, { on ->
                        set(r.copy(schedule = if (on) RoutineSchedule(8 * 60, setOf(1, 2, 3, 4, 5), NotifyStyle.PROMPT) else null))
                    }, tag = "routineScheduleSwitch")
                    if (sc != null) {
                        TimeField("At", sc.time, use24h, { set(r.copy(schedule = sc.copy(time = it))) })
                        DayChips(sc.days) { set(r.copy(schedule = sc.copy(days = it))) }
                        T("How to remind you", Type.label, p.muted)
                        NotifyStyle.values().forEach { ns ->
                            Chip(ns.label, sc.notify == ns, { set(r.copy(schedule = sc.copy(notify = ns))) })
                        }
                    }
                }
            }
            if (!ed.isNew) SecondaryButton("Delete this routine", { dispatch(Action.DeleteRoutine(r.id)) }, tag = "deleteRoutine", color = p.danger)
        },
        right = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("Steps", Type.title, modifier = Modifier.weight(1f))
                PrimaryButton("+  Add step", { dispatch(Action.EditStep(null)) }, tag = "addStep")
            }
            if (r.steps.isEmpty()) T("No steps yet. A routine needs at least one.", Type.body, p.muted)
            r.steps.forEachIndexed { i, st ->
                PanelCard(Modifier.fillMaxWidth().testTag("stepRow_$i"), padding = 16.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T("${i + 1}", Type.title, p.muted, Modifier.width(44.dp))
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { dispatch(Action.EditStep(i)) }) {
                            T(st.title, Type.label)
                            val bits = listOfNotNull(
                                if (st.hasTimer) "⏱ ${fmtTimer(st.durationSec * 1000L)}" else null,
                                if (st.hasTimer && st.autoAdvance) "auto-advance" else null,
                                st.instruction.takeIf { it.isNotBlank() },
                            )
                            if (bits.isNotEmpty()) T(bits.joinToString(" · "), Type.small, p.muted, maxLines = 1)
                        }
                        RoundIconButton("↑", { dispatch(Action.MoveStep(i, -1)) }, enabled = i > 0, size = 48.dp)
                        Spacer(Modifier.width(6.dp))
                        RoundIconButton("↓", { dispatch(Action.MoveStep(i, 1)) }, enabled = i < r.steps.size - 1, size = 48.dp)
                        Spacer(Modifier.width(6.dp))
                        RoundIconButton("✎", { dispatch(Action.EditStep(i)) }, size = 48.dp, tag = "editStep_$i")
                        Spacer(Modifier.width(6.dp))
                        RoundIconButton("✕", { dispatch(Action.DeleteStep(i)) }, size = 48.dp, tag = "deleteStep_$i")
                    }
                }
            }
        })
}

/** Step editor dialog (PRD §23): name, instruction, duration, timer, auto-advance, alert. */
@Composable
fun StepEditorDialog(index: Int?, st: Step, dispatch: Dispatch) {
    val p = P()
    fun set(n: Step) = dispatch(Action.UpdateStepDraft(n))
    val problem = Validation.step(st).firstOrNull()
    AlertDialog(
        onDismissRequest = { dispatch(Action.CloseDialog) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.widthIn(max = 760.dp).padding(24.dp).testTag("stepDialog"),
        containerColor = p.card,
        title = { T(if (index == null) "New step" else "Edit step ${index + 1}", Type.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PanelTextField(st.title, { set(st.copy(title = it)) }, "Step", tag = "stepTitleField")
                PanelTextField(st.instruction, { set(st.copy(instruction = it)) }, "Instruction (optional)", singleLine = false)
                ToggleRow("Timer", st.timerEnabled, { on ->
                    set(st.copy(timerEnabled = on, durationSec = if (on && st.durationSec == 0) 120 else st.durationSec))
                }, tag = "stepTimerSwitch")
                if (st.timerEnabled) {
                    Stepper("Minutes", st.durationSec / 60, { m -> set(st.copy(durationSec = m * 60 + st.durationSec % 60)) }, 0..180)
                    Stepper("Seconds", st.durationSec % 60, { sec -> set(st.copy(durationSec = (st.durationSec / 60) * 60 + sec)) }, 0..55, step = 5)
                    ToggleRow("Move on automatically", st.autoAdvance, { set(st.copy(autoAdvance = it)) }, sub = "Go to the next step when the timer ends")
                    ToggleRow("Sound when time's up", st.alert, { set(st.copy(alert = it)) })
                }
                ToggleRow("Breathing guide", st.breathing, { set(st.copy(breathing = it)) }, sub = "Shows a slow breathing circle on this step")
                if (problem != null) T(problem, Type.small, p.warn)
            }
        },
        confirmButton = { PrimaryButton(if (index == null) "Add step" else "Save step", { dispatch(Action.SaveStep) }, tag = "saveStep", enabled = problem == null) },
        dismissButton = { SecondaryButton("Cancel", { dispatch(Action.CloseDialog) }) },
    )
}
