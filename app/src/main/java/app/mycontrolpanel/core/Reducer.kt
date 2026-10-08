package app.mycontrolpanel.core

import java.time.ZoneId

// ---------------------------------------------------------------------------
// Single source of truth (PRD §34): AppState = persisted AppData + transient Ui.
// Every user action and every clock tick goes through reduce(). The UI only renders
// state and dispatches actions; rendering never mutates state.
// ---------------------------------------------------------------------------

enum class SettingsSection(val label: String) {
    MODES("Modes"), ROUTINES("Routines"), WIDGETS("Widgets"), TASKS_CALENDAR("Tasks & Calendar"),
    MUSIC("Music"), APPEARANCE("Appearance"), DEVICE("Device"), RESET("Reset"), ABOUT("About"),
}

enum class OverrideLength(val label: String, val minutes: Int) {
    M30("30 min", 30), H1("1 hour", 60), H2("2 hours", 120), H4("4 hours", 240),
    NEXT_CHANGE("Until next scheduled change", -1), UNTIL_RETURN("Until I switch back", -2),
}

sealed interface Screen {
    object Dashboard : Screen
    object RunningRoutine : Screen
    data class Settings(val section: SettingsSection) : Screen
    /** [initial] is what the editor opened with — used to detect unsaved changes. */
    data class ModeEditor(val draft: Mode, val initial: Mode, val isNew: Boolean, val disableOnSave: Set<String> = emptySet()) : Screen
    data class RoutineEditor(val draft: Routine, val initial: Routine, val isNew: Boolean) : Screen
}

sealed interface Dialog {
    object QuickRoutines : Dialog
    object ModeSwitcher : Dialog
    object ConfirmExitRoutine : Dialog
    object ConfirmReset : Dialog
    object ConfirmDiscard : Dialog
    data class ConfirmDeleteMode(val id: String) : Dialog
    data class ConfirmDeleteRoutine(val id: String) : Dialog
    /** index == null → adding a new step. */
    data class StepEditor(val index: Int?, val step: Step) : Dialog
}

data class Ui(
    val screen: Screen = Screen.Dashboard,
    val dialog: Dialog? = null,
    val message: String? = null,
    val saveError: String? = null,
)

data class AppState(val data: AppData, val ui: Ui = Ui(), val now: Long = 0)

sealed interface Effect {
    object PlayAlert : Effect
    object RetrySave : Effect
}

data class Reduced(val state: AppState, val effects: List<Effect> = emptyList())

class Env(val zone: ZoneId, val newId: () -> String)

sealed interface Action {
    object Tick : Action
    object Back : Action
    // dashboard
    data class SwitchMode(val modeId: String, val length: OverrideLength) : Action
    data class PickConflictMode(val modeId: String) : Action
    object ReturnToSchedule : Action
    data class OpenDialog(val dialog: Dialog) : Action
    object CloseDialog : Action
    data class AddWater(val delta: Int) : Action
    data class ToggleMeal(val index: Int) : Action
    object TookBreak : Action
    data class DismissPrompt(val routineId: String) : Action
    object DismissMessage : Action
    // routines (running)
    data class StartRoutine(val routineId: String) : Action
    object RoutineDone : Action
    object RoutineBack : Action
    object RoutinePause : Action
    object RoutineResume : Action
    object ExitRoutine : Action        // deliberate exit (hold-to-exit or confirmed dialog)
    object FinishRoutine : Action      // from the completion screen
    // settings navigation
    data class OpenSettings(val section: SettingsSection) : Action
    object GoDashboard : Action
    // mode editor
    object NewMode : Action
    data class EditMode(val id: String) : Action
    data class UpdateModeDraft(val draft: Mode) : Action
    data class DisableOnSave(val modeId: String, val disable: Boolean) : Action
    object SaveMode : Action
    data class DeleteMode(val id: String) : Action
    // routine editor
    object NewRoutine : Action
    data class EditRoutine(val id: String) : Action
    data class UpdateRoutineDraft(val draft: Routine) : Action
    object SaveRoutine : Action
    data class DeleteRoutine(val id: String) : Action
    data class EditStep(val index: Int?) : Action
    data class UpdateStepDraft(val step: Step) : Action
    object SaveStep : Action
    data class DeleteStep(val index: Int) : Action
    data class MoveStep(val index: Int, val delta: Int) : Action
    object CancelEdit : Action
    object ConfirmDiscard : Action
    object ConfirmDelete : Action
    // prefs / reset
    data class UpdatePrefs(val prefs: Prefs) : Action
    object ConfirmReset : Action
    // persistence feedback
    data class SaveFailed(val message: String) : Action
    object SaveSucceeded : Action
    object RetrySave : Action
    data class ShowMessage(val message: String) : Action
}

object Reducer {

    fun initial(data: AppData, now: Long, env: Env, message: String? = null): AppState {
        val screen = if (data.run != null && data.routines.any { it.id == data.run.routineId }) Screen.RunningRoutine else Screen.Dashboard
        val cleaned = if (screen == Screen.Dashboard) data.copy(run = null) else data
        return reduce(AppState(cleaned, Ui(screen = screen, message = message), now), Action.Tick, now, env).state
    }

    fun reduce(prev: AppState, a: Action, now: Long, env: Env): Reduced {
        val s = prev.copy(now = now)
        val d = s.data
        val ui = s.ui
        fun data(nd: AppData) = Reduced(s.copy(data = nd))
        fun ui(nu: Ui) = Reduced(s.copy(ui = nu))
        fun msg(m: String) = ui(ui.copy(message = m))

        return when (a) {
            Action.Tick -> tick(s, env)

            Action.Back -> back(s, env)

            // ---------------- dashboard ----------------
            is Action.SwitchMode -> {
                if (d.modes.none { it.id == a.modeId }) return msg("That mode no longer exists.")
                val until: Long? = when (a.length) {
                    OverrideLength.UNTIL_RETURN -> null
                    OverrideLength.NEXT_CHANGE -> Scheduler.nextChange(d.modes, Scheduler.toLocal(now, env.zone))
                        ?.let { Scheduler.toEpoch(it.first, env.zone) }
                    else -> now + a.length.minutes * 60_000L
                }
                val ns = s.copy(data = d.copy(override = Override(a.modeId, until)), ui = ui.copy(dialog = null))
                tick(ns, env)
            }
            is Action.PickConflictMode -> {
                // resolves "right now" only: override until the schedule next changes
                val until = Scheduler.nextChange(d.modes, Scheduler.toLocal(now, env.zone))?.let { Scheduler.toEpoch(it.first, env.zone) }
                tick(s.copy(data = d.copy(override = Override(a.modeId, until))), env)
            }
            Action.ReturnToSchedule -> tick(s.copy(data = d.copy(override = null), ui = ui.copy(dialog = null)), env)
            is Action.OpenDialog -> ui(ui.copy(dialog = a.dialog))
            Action.CloseDialog -> ui(ui.copy(dialog = null))
            is Action.AddWater -> data(d.copy(daily = d.daily.copy(water = (d.daily.water + a.delta).coerceIn(0, 99))))
            is Action.ToggleMeal -> {
                val m = d.daily.mealsDone
                data(d.copy(daily = d.daily.copy(mealsDone = if (a.index in m) m - a.index else m + a.index)))
            }
            Action.TookBreak -> data(d.copy(daily = d.daily.copy(lastBreakAt = now)))
            is Action.DismissPrompt -> data(d.copy(daily = d.daily.copy(dismissed = d.daily.dismissed + a.routineId)))
            Action.DismissMessage -> ui(ui.copy(message = null))
            is Action.ShowMessage -> msg(a.message)

            // ---------------- running a routine ----------------
            is Action.StartRoutine -> {
                val r = d.routines.find { it.id == a.routineId } ?: return msg("That routine no longer exists.")
                if (d.run != null && !d.run.completed) return msg("Finish or exit the current routine first.")
                if (r.steps.isEmpty()) return msg("“${r.name}” has no steps yet. Add some in Settings → Routines.")
                // One state change: popup gone, routine on screen, prompt cleared. No overlay can be left behind.
                Reduced(s.copy(
                    data = d.copy(run = Runner.start(r, now), daily = d.daily.copy(dismissed = d.daily.dismissed + r.id)),
                    ui = ui.copy(screen = Screen.RunningRoutine, dialog = null),
                ))
            }
            Action.RoutineDone -> withRun(s) { run, r -> Runner.done(run, r, now) }
            Action.RoutineBack -> withRun(s) { run, r -> Runner.back(run, r, now) }
            Action.RoutinePause -> withRun(s) { run, _ -> Runner.pause(run, now) }
            Action.RoutineResume -> withRun(s) { run, r -> Runner.resume(run, r, now) }
            Action.ExitRoutine, Action.FinishRoutine ->
                // Return to the exact dashboard; the mode is derived from data, so nothing else to restore.
                tick(s.copy(data = d.copy(run = null), ui = ui.copy(screen = Screen.Dashboard, dialog = null)), env)

            // ---------------- settings navigation ----------------
            is Action.OpenSettings -> {
                if (ui.screen == Screen.RunningRoutine) return Reduced(s)
                ui(ui.copy(screen = Screen.Settings(a.section), dialog = null))
            }
            Action.GoDashboard -> ui(ui.copy(screen = Screen.Dashboard, dialog = null))

            // ---------------- mode editor ----------------
            Action.NewMode -> {
                val m = Mode(env.newId(), "", "⭐", 12 * 60, 13 * 60, setOf(1, 2, 3, 4, 5, 6, 7),
                    scheduled = true, widgets = defaultWidgetSlots(listOf(ModeWidget.ROUTINES, ModeWidget.NEXT_MODE)),
                    order = (d.modes.maxOfOrNull { it.order } ?: 0) + 1)
                ui(ui.copy(screen = Screen.ModeEditor(m, m, isNew = true), dialog = null))
            }
            is Action.EditMode -> {
                val m = d.modes.find { it.id == a.id } ?: return msg("That mode no longer exists.")
                ui(ui.copy(screen = Screen.ModeEditor(m, m, isNew = false), dialog = null))
            }
            is Action.UpdateModeDraft -> {
                val ed = ui.screen as? Screen.ModeEditor ?: return Reduced(s)
                ui(ui.copy(screen = ed.copy(draft = a.draft.copy(widgets = normalizeSlots(a.draft.widgets)))))
            }
            is Action.DisableOnSave -> {
                val ed = ui.screen as? Screen.ModeEditor ?: return Reduced(s)
                ui(ui.copy(screen = ed.copy(disableOnSave = if (a.disable) ed.disableOnSave + a.modeId else ed.disableOnSave - a.modeId)))
            }
            Action.SaveMode -> {
                val ed = ui.screen as? Screen.ModeEditor ?: return Reduced(s)
                val problems = Validation.mode(ed.draft, othersAfterSave(d.modes, ed))
                if (problems.isNotEmpty()) return msg(problems.first())
                val draft = ed.draft.copy(name = ed.draft.name.trim())
                val others = othersAfterSave(d.modes, ed)
                val modes = if (ed.isNew) others + draft else others.map { if (it.id == draft.id) draft else it }.let {
                    if (it.any { m -> m.id == draft.id }) it else it + draft
                }
                val ns = s.copy(data = d.copy(modes = modes.sortedBy { it.order }),
                    ui = ui.copy(screen = Screen.Settings(SettingsSection.MODES), dialog = null, message = "Saved “${draft.name}”."))
                tick(ns, env)
            }
            is Action.DeleteMode -> ui(ui.copy(dialog = Dialog.ConfirmDeleteMode(a.id)))

            // ---------------- routine editor ----------------
            Action.NewRoutine -> {
                val r = Routine(env.newId(), "", icon = "✨", steps = emptyList(), order = (d.routines.maxOfOrNull { it.order } ?: 0) + 1)
                ui(ui.copy(screen = Screen.RoutineEditor(r, r, isNew = true), dialog = null))
            }
            is Action.EditRoutine -> {
                val r = d.routines.find { it.id == a.id } ?: return msg("That routine no longer exists.")
                ui(ui.copy(screen = Screen.RoutineEditor(r, r, isNew = false), dialog = null))
            }
            is Action.UpdateRoutineDraft -> {
                val ed = ui.screen as? Screen.RoutineEditor ?: return Reduced(s)
                ui(ui.copy(screen = ed.copy(draft = a.draft)))
            }
            is Action.EditStep -> {
                val ed = ui.screen as? Screen.RoutineEditor ?: return Reduced(s)
                val step = a.index?.let { ed.draft.steps.getOrNull(it) } ?: Step(env.newId(), "")
                ui(ui.copy(dialog = Dialog.StepEditor(a.index, step)))
            }
            is Action.UpdateStepDraft -> {
                val dl = ui.dialog as? Dialog.StepEditor ?: return Reduced(s)
                ui(ui.copy(dialog = dl.copy(step = a.step)))
            }
            Action.SaveStep -> {
                val ed = ui.screen as? Screen.RoutineEditor ?: return Reduced(s)
                val dl = ui.dialog as? Dialog.StepEditor ?: return Reduced(s)
                val problems = Validation.step(dl.step)
                if (problems.isNotEmpty()) return msg(problems.first())
                val step = dl.step.copy(title = dl.step.title.trim(), instruction = dl.step.instruction.trim())
                val steps = ed.draft.steps.toMutableList()
                if (dl.index == null || dl.index !in steps.indices) steps += step else steps[dl.index] = step
                ui(ui.copy(screen = ed.copy(draft = ed.draft.copy(steps = steps)), dialog = null))
            }
            is Action.DeleteStep -> {
                val ed = ui.screen as? Screen.RoutineEditor ?: return Reduced(s)
                if (a.index !in ed.draft.steps.indices) return Reduced(s)
                ui(ui.copy(screen = ed.copy(draft = ed.draft.copy(steps = ed.draft.steps.filterIndexed { i, _ -> i != a.index }))))
            }
            is Action.MoveStep -> {
                val ed = ui.screen as? Screen.RoutineEditor ?: return Reduced(s)
                val to = a.index + a.delta
                val steps = ed.draft.steps.toMutableList()
                if (a.index !in steps.indices || to !in steps.indices) return Reduced(s)
                steps.add(to, steps.removeAt(a.index))
                ui(ui.copy(screen = ed.copy(draft = ed.draft.copy(steps = steps))))
            }
            Action.SaveRoutine -> {
                val ed = ui.screen as? Screen.RoutineEditor ?: return Reduced(s)
                val problems = Validation.routine(ed.draft)
                if (problems.isNotEmpty()) return msg(problems.first())
                val r = ed.draft.copy(name = ed.draft.name.trim(), description = ed.draft.description.trim(),
                    modeIds = ed.draft.modeIds.filter { id -> d.modes.any { it.id == id } }.toSet())
                val routines = if (d.routines.any { it.id == r.id }) d.routines.map { if (it.id == r.id) r else it } else d.routines + r
                Reduced(s.copy(data = d.copy(routines = routines),
                    ui = ui.copy(screen = Screen.Settings(SettingsSection.ROUTINES), dialog = null, message = "Saved “${r.name}”.")))
            }
            is Action.DeleteRoutine -> ui(ui.copy(dialog = Dialog.ConfirmDeleteRoutine(a.id)))

            Action.CancelEdit -> {
                val dirty = when (val sc = ui.screen) {
                    is Screen.ModeEditor -> sc.draft != sc.initial || sc.disableOnSave.isNotEmpty()
                    is Screen.RoutineEditor -> sc.draft != sc.initial
                    else -> false
                }
                if (dirty) ui(ui.copy(dialog = Dialog.ConfirmDiscard)) else ui(ui.copy(screen = editorParent(ui.screen), dialog = null))
            }
            Action.ConfirmDiscard -> ui(ui.copy(screen = editorParent(ui.screen), dialog = null))

            Action.ConfirmDelete -> when (val dl = ui.dialog) {
                is Dialog.ConfirmDeleteMode -> {
                    val name = d.modes.find { it.id == dl.id }?.name ?: "Mode"
                    val nd = d.copy(
                        modes = d.modes.filter { it.id != dl.id }.map { it.copy(winsOver = it.winsOver - dl.id) },
                        routines = d.routines.map { it.copy(modeIds = it.modeIds - dl.id) },
                        override = d.override?.takeIf { it.modeId != dl.id },
                        prefs = d.prefs.copy(dimModeIds = d.prefs.dimModeIds - dl.id),
                    )
                    tick(s.copy(data = nd, ui = ui.copy(screen = Screen.Settings(SettingsSection.MODES), dialog = null, message = "Deleted “$name”.")), env)
                }
                is Dialog.ConfirmDeleteRoutine -> {
                    val name = d.routines.find { it.id == dl.id }?.name ?: "Routine"
                    val nd = d.copy(
                        routines = d.routines.filter { it.id != dl.id },
                        modes = d.modes.map { if (it.featuredRoutineId == dl.id) it.copy(featuredRoutineId = null) else it },
                    )
                    Reduced(s.copy(data = nd, ui = ui.copy(screen = Screen.Settings(SettingsSection.ROUTINES), dialog = null, message = "Deleted “$name”.")))
                }
                else -> ui(ui.copy(dialog = null))
            }

            // ---------------- prefs / reset ----------------
            is Action.UpdatePrefs -> data(d.copy(prefs = a.prefs))
            Action.ConfirmReset -> {
                // Destructive: replace everything, recalc mode, single state emission → dashboard rebuilt exactly once.
                val fresh = Defaults.data()
                tick(AppState(fresh, Ui(screen = Screen.Dashboard, message = "Everything is back to the defaults."), now), env)
            }

            // ---------------- persistence feedback ----------------
            is Action.SaveFailed -> ui(ui.copy(saveError = a.message))
            Action.SaveSucceeded -> if (ui.saveError == null) Reduced(s) else ui(ui.copy(saveError = null))
            Action.RetrySave -> Reduced(s, listOf(Effect.RetrySave))
        }
    }

    private fun editorParent(sc: Screen): Screen = when (sc) {
        is Screen.ModeEditor -> Screen.Settings(SettingsSection.MODES)
        is Screen.RoutineEditor -> Screen.Settings(SettingsSection.ROUTINES)
        else -> sc
    }

    /** Other modes as they will be after saving the editor (pending "turn off schedule" applied). */
    fun othersAfterSave(modes: List<Mode>, ed: Screen.ModeEditor): List<Mode> =
        modes.filter { it.id != ed.draft.id }.map { if (it.id in ed.disableOnSave) it.copy(scheduled = false) else it }

    private fun back(s: AppState, env: Env): Reduced {
        val ui = s.ui
        if (ui.dialog != null) {
            return Reduced(s.copy(ui = ui.copy(dialog = null)))
        }
        return when (val sc = ui.screen) {
            Screen.Dashboard -> Reduced(s)
            Screen.RunningRoutine -> {
                val run = s.data.run
                if (run == null || run.completed) Reduced(s.copy(data = s.data.copy(run = null), ui = ui.copy(screen = Screen.Dashboard)))
                else Reduced(s.copy(ui = ui.copy(dialog = Dialog.ConfirmExitRoutine))) // exit must be deliberate
            }
            is Screen.Settings -> Reduced(s.copy(ui = ui.copy(screen = Screen.Dashboard)))
            is Screen.ModeEditor, is Screen.RoutineEditor -> reduce(s, Action.CancelEdit, s.now, env)
        }
    }

    private inline fun withRun(s: AppState, f: (RoutineRun, Routine) -> RoutineRun): Reduced {
        val run = s.data.run ?: return Reduced(s)
        val r = s.data.routines.find { it.id == run.routineId } ?: return Reduced(s)
        return Reduced(s.copy(data = s.data.copy(run = f(run, r))))
    }

    /** Clock tick: daily rollover, override expiry, mode-entry tracking, routine prompts, routine timers. */
    private fun tick(s: AppState, env: Env): Reduced {
        val now = s.now
        var d = s.data
        val local = Scheduler.toLocal(now, env.zone)
        val today = local.toLocalDate().toString()
        val effects = ArrayList<Effect>()

        if (d.daily.date != today) d = d.copy(daily = Daily(date = today))

        val ov = d.override
        if (ov != null && ((ov.until != null && now >= ov.until) || d.modes.none { it.id == ov.modeId })) d = d.copy(override = null)

        val eff = Scheduler.effective(d, now, env.zone)
        if (eff.key != d.lastModeKey) d = d.copy(lastModeKey = eff.key, modeEnteredAt = now)

        // scheduled routine prompts (once per day, only within the prompt window)
        val minute = local.hour * 60 + local.minute
        val dow = local.dayOfWeek.value
        val newlyPrompted = d.routines.filter { r ->
            val sc = r.schedule
            r.enabled && sc != null && sc.notify != NotifyStyle.NONE && dow in sc.days &&
                minute >= sc.time && minute < sc.time + PROMPT_WINDOW_MIN && r.id !in d.daily.prompted
        }.map { it.id }
        if (newlyPrompted.isNotEmpty()) d = d.copy(daily = d.daily.copy(prompted = d.daily.prompted + newlyPrompted))

        val run = d.run
        if (run != null) {
            val r = d.routines.find { it.id == run.routineId }
            if (r != null) {
                val t = Runner.tick(run, r, now)
                if (t.run != run) d = d.copy(run = t.run)
                if (t.alert) effects += Effect.PlayAlert
            }
        }
        return Reduced(s.copy(data = d), effects)
    }

    const val PROMPT_WINDOW_MIN = 90
}

/** Read-only derivations used by the UI. */
object Derive {
    fun effective(s: AppState, env: Env) = Scheduler.effective(s.data, s.now, env.zone)

    fun prompts(s: AppState, env: Env): List<Routine> {
        val local = Scheduler.toLocal(s.now, env.zone)
        val minute = local.hour * 60 + local.minute
        return s.data.routines.filter { r ->
            val sc = r.schedule
            sc != null && r.id in s.data.daily.prompted && r.id !in s.data.daily.dismissed &&
                minute >= sc.time && minute < sc.time + Reducer.PROMPT_WINDOW_MIN
        }
    }

    fun nextChange(s: AppState, env: Env) = Scheduler.nextChange(s.data.modes, Scheduler.toLocal(s.now, env.zone))

    fun routinesFor(s: AppState, modeId: String?) =
        s.data.routines.filter { it.enabled && modeId != null && modeId in it.modeIds }.sortedBy { it.order }

    fun quickRoutines(s: AppState) = s.data.routines.filter { it.enabled && it.quick }.sortedBy { it.order }

    fun featured(s: AppState, mode: Mode?) =
        mode?.featuredRoutineId?.let { id -> s.data.routines.find { it.id == id && it.enabled } }

    fun minutesSinceBreak(s: AppState): Long {
        val since = listOfNotNull(s.data.daily.lastBreakAt, s.data.modeEnteredAt).maxOrNull() ?: s.now
        return ((s.now - since) / 60_000L).coerceAtLeast(0)
    }

    fun isDimmed(s: AppState, env: Env): Boolean {
        if (!s.data.prefs.nightDim) return false
        val m = effective(s, env).modeOrNull() ?: return false
        return m.id in s.data.prefs.dimModeIds
    }
}

object Validation {
    fun mode(m: Mode, others: List<Mode>): List<String> {
        val out = ArrayList<String>()
        if (m.name.isBlank()) out += "Give the mode a name."
        if (m.start == m.end) out += "Start and end time can't be the same."
        if (m.scheduled && m.days.isEmpty()) out += "Pick at least one day, or turn off “Runs on schedule”."
        if (m.scheduled) {
            val c = Scheduler.conflictsFor(m, others)
            if (c.isNotEmpty()) out += "Resolve the schedule overlap first: ${describeConflict(c.first(), false)}."
        }
        return out
    }

    fun routine(r: Routine): List<String> {
        val out = ArrayList<String>()
        if (r.name.isBlank()) out += "Give the routine a name."
        if (r.steps.isEmpty()) out += "Add at least one step."
        val sc = r.schedule
        if (sc != null && sc.days.isEmpty()) out += "Pick at least one day for the schedule, or turn the schedule off."
        return out
    }

    fun step(st: Step): List<String> {
        val out = ArrayList<String>()
        if (st.title.isBlank()) out += "Give the step a title."
        if (st.timerEnabled && st.durationSec <= 0) out += "Set a duration for the timer, or turn the timer off."
        return out
    }
}
