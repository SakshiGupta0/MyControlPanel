package app.mycontrolpanel.core

import java.time.ZoneId

// ---------------------------------------------------------------------------
// Single source of truth (PRD §34): AppState = persisted AppData + transient Ui.
// Every user action and every clock tick goes through reduce(). The UI only renders
// state and dispatches actions; rendering never mutates state.
// ---------------------------------------------------------------------------

enum class SettingsSection(val label: String) {
    WIDGETS("Dashboard"), MODES("Modes"), ROUTINES("Routines"), TASKS("Tasks & focus"), CALENDAR("Calendar"),
    MUSIC("Music"), APPEARANCE("Appearance"), DEVICE("Device"), ACCOUNT("Account & sharing"), RESET("Reset"), ABOUT("About"),
}

/** Task list filters: "today", "inbox", "all", "done", or a mode id. */
object TaskFilter { const val TODAY = "today"; const val INBOX = "inbox"; const val ALL = "all"; const val DONE = "done" }

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
    data class Tasks(val filter: String = TaskFilter.TODAY) : Screen
    object Focus : Screen
    /** First-run setup. [returning] = the account already had a setup, so only device questions are asked. */
    data class Onboarding(val step: Int = 0, val answers: OnboardingAnswers = OnboardingAnswers(), val returning: Boolean = false) : Screen {
        val steps get() = app.mycontrolpanel.core.Onboarding.steps(returning)
        val current get() = steps[step.coerceIn(0, steps.size - 1)]
    }
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
    /** Brain dump: one text box, saves to the Inbox. */
    object Capture : Dialog
    data class TaskEditor(val task: Task, val isNew: Boolean) : Dialog
}

data class Ui(
    val screen: Screen = Screen.Dashboard,
    val dialog: Dialog? = null,
    val message: String? = null,
    val saveError: String? = null,
    /** "Pick one for me" result — shown big on the Now card until done or cleared. */
    val spotlightTaskId: String? = null,
)

data class AppState(val data: AppData, val ui: Ui = Ui(), val now: Long = 0, val live: Live = Live())

sealed interface Effect {
    object PlayAlert : Effect
    /** Softer sound for the mode-change heads-up. */
    object Chime : Effect
    /** A small happy moment on screen (petals) when something gets finished. */
    object Celebrate : Effect
    object RetrySave : Effect
}

data class Reduced(val state: AppState, val effects: List<Effect> = emptyList())

class Env(val zone: ZoneId, val rand: (Int) -> Int = { kotlin.random.Random.nextInt(it) }, val newId: () -> String)

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
    // tasks
    data class OpenTasks(val filter: String) : Action
    data class Capture(val text: String, val today: Boolean = false, val spaceId: String? = null) : Action
    data class EditTask(val id: String?) : Action
    data class UpdateTaskDraft(val task: Task) : Action
    object SaveTask : Action
    data class DeleteTask(val id: String) : Action
    data class ToggleTaskDone(val id: String) : Action
    data class ToggleToday(val id: String) : Action
    object PickOne : Action
    object ClearSpotlight : Action
    object FreshStart : Action
    object ClearDone : Action
    // focus
    data class StartFocus(val taskId: String?, val minutes: Int? = null) : Action
    object FocusPause : Action
    object FocusResume : Action
    data class FocusExtend(val minutes: Int) : Action
    data class EndFocus(val markTaskDone: Boolean) : Action
    object ShowFocus : Action
    // live device data
    data class CalendarLoaded(val events: List<CalEvent>, val status: CalendarStatus) : Action
    // accounts & sync (dispatched by the sync engine)
    data class AccountChanged(val account: Account?, val firebaseReady: Boolean) : Action
    data class SyncStatusChanged(val status: SyncStatus, val error: String? = null) : Action
    data class ApplyRemote(val docs: List<Sync.Doc>, val accountWasEmpty: Boolean = false) : Action
    data class MarkSent(val docs: List<Sync.Doc>) : Action
    data class SpacesChanged(val spaces: List<Space>) : Action
    object SignedOut : Action
    // Google Tasks
    data class SetGoogleTasks(val on: Boolean) : Action
    data class GTasksStatus(val text: String?) : Action
    data class ApplyGTasks(val result: GTasksPlan.Result, val at: Long, val created: Map<String, String> = emptyMap()) : Action
    data class GListsLoaded(val lists: List<Pair<String, String>>) : Action
    data class ChooseGList(val id: String, val title: String, val copyExisting: Boolean) : Action
    // onboarding & dashboard features
    object StartOnboarding : Action
    data class OnbUpdate(val answers: OnboardingAnswers) : Action
    object OnbNext : Action
    object OnbBack : Action
    object OnbFinish : Action
    object OnbSkip : Action
    data class SetFeatures(val features: Set<Feature>) : Action
    data class SetMusic(val music: MusicPrefs) : Action
}

object Reducer {

    fun initial(data: AppData, now: Long, env: Env, message: String? = null): AppState {
        val screen = when {
            data.run != null && data.routines.any { it.id == data.run.routineId } -> Screen.RunningRoutine
            data.focus != null -> Screen.Focus
            !data.onboarded -> Screen.Onboarding()
            else -> Screen.Dashboard
        }
        val cleaned = if (screen != Screen.RunningRoutine) data.copy(run = null) else data
        return reduce(AppState(cleaned, Ui(screen = screen, message = message), now), Action.Tick, now, env).state
    }

    /** Reduce, then record which synced items changed (for upload). Remote merges are not re-stamped. */
    fun reduce(prev: AppState, a: Action, now: Long, env: Env): Reduced {
        val r = reduceInner(prev, a, now, env)
        if (a is Action.ApplyRemote || a is Action.MarkSent || a is Action.SpacesChanged || a is Action.SignedOut) return r
        var stamped = Sync.stamp(prev.data, r.state.data, now)
        // tasks removed *because* Google deleted them must not be queued for deletion at Google again
        if (a is Action.ApplyGTasks) stamped = stamped.copy(gDeletes = r.state.data.gDeletes)
        return if (stamped == r.state.data) r else r.copy(state = r.state.copy(data = stamped))
    }

    private fun reduceInner(prev: AppState, a: Action, now: Long, env: Env): Reduced {
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
                val ns = s.copy(data = d.copy(override = Override(a.modeId, until, now)), ui = ui.copy(dialog = null))
                tick(ns, env)
            }
            is Action.PickConflictMode -> {
                // resolves "right now" only: override until the schedule next changes
                val until = Scheduler.nextChange(d.modes, Scheduler.toLocal(now, env.zone))?.let { Scheduler.toEpoch(it.first, env.zone) }
                tick(s.copy(data = d.copy(override = Override(a.modeId, until, now))), env)
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
                if (d.focus != null) return msg("Finish your focus session first — or end it, then start this.")
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
            Action.ExitRoutine, Action.FinishRoutine -> {
                // Return to the exact dashboard; the mode is derived from data, so nothing else to restore.
                val finished = d.run?.takeIf { it.completed }?.routineId
                val daily = if (a == Action.FinishRoutine && finished != null) d.daily.copy(routinesDone = d.daily.routinesDone + finished) else d.daily
                val r = tick(s.copy(data = d.copy(run = null, daily = daily), ui = ui.copy(screen = Screen.Dashboard, dialog = null)), env)
                if (a == Action.FinishRoutine && finished != null) r.copy(effects = r.effects + Effect.Celebrate) else r
            }

            // ---------------- settings navigation ----------------
            is Action.OpenSettings -> {
                if (ui.screen == Screen.RunningRoutine || ui.screen == Screen.Focus) return Reduced(s)
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
                        tasks = d.tasks.map { if (dl.id in it.modeIds) it.copy(modeIds = it.modeIds - dl.id) else it },
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
                val fresh = Defaults.data().copy(onboarded = true, prefs = Defaults.data().prefs.copy(features = d.prefs.features))
                tick(AppState(fresh, Ui(screen = Screen.Dashboard, message = "Everything is back to the defaults."), now, s.live), env)
            }

            // ---------------- tasks ----------------
            is Action.OpenTasks -> {
                if (ui.screen == Screen.RunningRoutine) return Reduced(s)
                ui(ui.copy(screen = Screen.Tasks(a.filter), dialog = null))
            }
            is Action.Capture -> {
                val text = a.text.trim()
                if (text.isEmpty()) return ui(ui.copy(dialog = null))
                val addToday = a.today && Derive.todayTasks(s).size < d.prefs.todayLimit
                val t = Task(env.newId(), text.take(300), today = addToday, createdAt = now, order = now, spaceId = a.spaceId)
                Reduced(s.copy(data = d.copy(tasks = d.tasks + t), ui = ui.copy(dialog = null,
                    message = if (a.spaceId != null) "Added to the shared list." else if (addToday) "Added to today." else if (a.today) "Saved to your inbox — today's list is full." else "Saved to your inbox. Out of your head ✓")))
            }
            is Action.EditTask -> {
                val t = a.id?.let { id -> d.tasks.find { it.id == id } } ?: Task(env.newId(), "", createdAt = now, order = now)
                ui(ui.copy(dialog = Dialog.TaskEditor(t, isNew = a.id == null || d.tasks.none { it.id == a.id })))
            }
            is Action.UpdateTaskDraft -> {
                val dl = ui.dialog as? Dialog.TaskEditor ?: return Reduced(s)
                ui(ui.copy(dialog = dl.copy(task = a.task)))
            }
            Action.SaveTask -> {
                val dl = ui.dialog as? Dialog.TaskEditor ?: return Reduced(s)
                val problems = Validation.task(dl.task)
                if (problems.isNotEmpty()) return msg(problems.first())
                var t = dl.task.copy(title = dl.task.title.trim(), notes = dl.task.notes.trim(),
                    modeIds = dl.task.modeIds.filter { id -> d.modes.any { it.id == id } }.toSet())
                val wasToday = d.tasks.find { it.id == t.id }?.today == true
                var note: String? = null
                if (t.today && !wasToday && !t.done && Derive.todayTasks(s).size >= d.prefs.todayLimit) {
                    t = t.copy(today = false); note = "Saved — today's list is full, so it went to the list for later."
                }
                val tasks = if (d.tasks.any { it.id == t.id }) d.tasks.map { if (it.id == t.id) t else it } else d.tasks + t
                Reduced(s.copy(data = d.copy(tasks = tasks), ui = ui.copy(dialog = null, message = note)))
            }
            is Action.DeleteTask -> Reduced(s.copy(
                data = d.copy(tasks = d.tasks.filter { it.id != a.id }),
                ui = ui.copy(dialog = null, spotlightTaskId = ui.spotlightTaskId?.takeIf { it != a.id }),
            ))
            is Action.ToggleTaskDone -> {
                val t = d.tasks.find { it.id == a.id } ?: return Reduced(s)
                val nt = if (t.done) t.copy(done = false, doneAt = null, doneBy = null)
                         else t.copy(done = true, doneAt = now, doneBy = if (t.spaceId != null) s.live.account?.name?.substringBefore(' ') else null)
                Reduced(effects = if (nt.done) listOf(Effect.Celebrate) else emptyList(), state = s.copy(
                    data = d.copy(tasks = d.tasks.map { if (it.id == t.id) nt else it }),
                    ui = ui.copy(spotlightTaskId = ui.spotlightTaskId?.takeIf { it != t.id || !nt.done },
                        message = if (nt.done) Encourage.forTask(Derive.doneTodayCount(s) + 1, env.rand) else ui.message),
                ))
            }
            is Action.ToggleToday -> {
                val t = d.tasks.find { it.id == a.id } ?: return Reduced(s)
                if (!t.today && Derive.todayTasks(s).size >= d.prefs.todayLimit)
                    return msg("Today holds ${d.prefs.todayLimit} things. Finish or un-star one first — short lists actually get done.")
                data(d.copy(tasks = d.tasks.map { if (it.id == t.id) it.copy(today = !it.today) else it }))
            }
            Action.PickOne -> {
                val pool = Derive.pickPool(s, env).filter { it.id != ui.spotlightTaskId }.ifEmpty { Derive.pickPool(s, env) }
                if (pool.isEmpty()) return msg("Nothing to pick from yet. Brain-dump a few things first.")
                ui(ui.copy(spotlightTaskId = pool[env.rand(pool.size)].id, screen = Screen.Dashboard, dialog = null))
            }
            Action.ClearSpotlight -> ui(ui.copy(spotlightTaskId = null))
            Action.FreshStart -> Reduced(s.copy(
                data = d.copy(tasks = d.tasks.map { if (it.today && !it.done) it.copy(today = false) else it }),
                ui = ui.copy(spotlightTaskId = null, message = "Clean slate. Pick up to ${d.prefs.todayLimit} things when you're ready."),
            ))
            Action.ClearDone -> data(d.copy(tasks = d.tasks.filter { !it.done }))

            // ---------------- focus ----------------
            is Action.StartFocus -> {
                if (d.run != null && !d.run.completed) return msg("Finish or exit the current routine first.")
                val task = a.taskId?.let { id -> d.tasks.find { it.id == id } }
                val f = Focus.start(task?.id, task?.title ?: "Focus", a.minutes ?: d.prefs.focusMin, now)
                Reduced(s.copy(data = d.copy(focus = f), ui = ui.copy(screen = Screen.Focus, dialog = null)))
            }
            Action.FocusPause -> d.focus?.let { data(d.copy(focus = Focus.pause(it, now))) } ?: Reduced(s)
            Action.FocusResume -> d.focus?.let { data(d.copy(focus = Focus.resume(it, now))) } ?: Reduced(s)
            is Action.FocusExtend -> d.focus?.let { data(d.copy(focus = Focus.extend(it, a.minutes, now))) } ?: Reduced(s)
            Action.ShowFocus -> if (d.focus != null) ui(ui.copy(screen = Screen.Focus, dialog = null)) else Reduced(s)
            is Action.EndFocus -> {
                val f = d.focus ?: return ui(ui.copy(screen = Screen.Dashboard))
                val completedSession = f.finished || Focus.remaining(f, now) == 0L
                var tasks = d.tasks
                if (a.markTaskDone && f.taskId != null) tasks = tasks.map { if (it.id == f.taskId) it.copy(done = true, doneAt = now) else it }
                val daily = if (completedSession) d.daily.copy(focusDone = d.daily.focusDone + 1) else d.daily
                val r0 = tick(s.copy(
                    data = d.copy(focus = null, tasks = tasks, daily = daily),
                    ui = ui.copy(screen = Screen.Dashboard, dialog = null,
                        spotlightTaskId = ui.spotlightTaskId?.takeIf { !(a.markTaskDone && it == f.taskId) },
                        message = if (a.markTaskDone) "Done. That's real progress." else if (completedSession) "Focus session complete. Take a breath." else null),
                ), env)
                if (a.markTaskDone || completedSession) r0.copy(effects = r0.effects + Effect.Celebrate) else r0
            }

            // ---------------- live device data ----------------
            is Action.CalendarLoaded -> Reduced(s.copy(live = s.live.copy(calendar = a.events, calendarStatus = a.status)))

            // ---------------- accounts & sync ----------------
            is Action.AccountChanged -> Reduced(s.copy(live = s.live.copy(account = a.account, firebaseReady = a.firebaseReady,
                syncStatus = if (a.account == null) SyncStatus.LOCAL_ONLY else SyncStatus.CONNECTING, syncError = null,
                spaces = if (a.account == null) emptyList() else s.live.spaces)))
            is Action.SyncStatusChanged -> Reduced(s.copy(live = s.live.copy(syncStatus = a.status, syncError = a.error)))
            is Action.ApplyRemote -> {
                // An empty account (first sign-in anywhere) adopts this device's data; otherwise merge, newest wins.
                val merged = if (a.accountWasEmpty) Sync.stampAll(d, now) else Sync.applyRemote(d, a.docs)
                // Signing in on the welcome screen moves setup along: an existing account only needs device questions.
                val sc = ui.screen
                val existing = !a.accountWasEmpty && a.docs.isNotEmpty()
                val nui = when {
                    sc !is Screen.Onboarding || s.live.account == null -> ui
                    // An account that already has a setup: never let setup questions overwrite it, whatever step we're on.
                    existing && !sc.returning -> ui.copy(screen = Screen.Onboarding(Onboarding.returningStepFor(sc.current), sc.answers, returning = true))
                    sc.current == OnbStep.WELCOME -> ui.copy(screen = Screen.Onboarding(1, sc.answers, returning = existing))
                    else -> ui
                }
                tick(s.copy(data = merged, ui = nui), env)
            }
            is Action.MarkSent -> data(Sync.markSent(d, a.docs))
            is Action.SpacesChanged -> Reduced(s.copy(
                data = Sync.dropSpaces(d, a.spaces.map { it.id }.toSet()),
                live = s.live.copy(spaces = a.spaces),
            ))
            Action.SignedOut -> Reduced(s.copy(
                // Personal data stays on this tablet; shared-space tasks leave with the account.
                data = Sync.dropSpaces(d, emptySet()).copy(synced = emptyMap(), gTasksOn = false),
                live = s.live.copy(account = null, spaces = emptyList(), syncStatus = SyncStatus.LOCAL_ONLY),
                ui = ui.copy(message = "Signed out. Your own modes, routines and tasks stay on this tablet."),
            ))

            // ---------------- Google Tasks ----------------
            is Action.SetGoogleTasks -> data(d.copy(gTasksOn = a.on, gLastSync = if (a.on) d.gLastSync else 0))
            is Action.GTasksStatus -> Reduced(s.copy(live = s.live.copy(gTasksStatus = a.text)))
            is Action.ApplyGTasks -> data(GTasksPlan.applyLocal(d, a.result, a.at, a.created))
            is Action.GListsLoaded -> Reduced(s.copy(live = s.live.copy(gLists = a.lists)))
            is Action.ChooseGList -> data(d.copy(
                // Google ids belong to a list, so switching lists unlinks everything and starts a fresh match.
                prefs = d.prefs.copy(gListId = a.id, gListTitle = a.title),
                tasks = d.tasks.map { if (it.googleId != null) it.copy(googleId = null) else it },
                gTasksOn = true, gLastSync = 0, gDeletes = emptySet(),
                gPushAfter = if (a.copyExisting) 0 else now,
            ))

            // ---------------- onboarding & features ----------------
            Action.StartOnboarding -> {
                val p = d.prefs
                val answers = OnboardingAnswers(
                    use = if (p.fullscreen || p.keepScreenOn) DeviceUse.PANEL else DeviceUse.PHONE,
                    nickname = p.nickname,
                    profile = Onboarding.profileOf(p.features) ?: Profile.BALANCED,
                    worksWeekdays = d.modes.find { it.id == "work" }?.scheduled ?: true,
                    wake = d.modes.find { it.id == "morning" }?.start ?: 7 * 60,
                    workStart = d.modes.find { it.id == "work" }?.start ?: 10 * 60,
                    workEnd = d.modes.find { it.id == "work" }?.end ?: (18 * 60 + 30),
                    sleep = d.modes.find { it.id == "bedtime" }?.start ?: 23 * 60,
                    music = p.music.provider, musicAppPackage = p.music.appPackage, musicAppName = p.music.appName,
                )
                ui(ui.copy(screen = Screen.Onboarding(0, answers, returning = false), dialog = null))
            }
            is Action.OnbUpdate -> {
                val sc = ui.screen as? Screen.Onboarding ?: return Reduced(s)
                ui(ui.copy(screen = sc.copy(answers = a.answers)))
            }
            Action.OnbNext -> {
                val sc = ui.screen as? Screen.Onboarding ?: return Reduced(s)
                if (sc.current == OnbStep.DAY && !Onboarding.validDay(sc.answers))
                    return msg("Those times overlap — wake-up, start, finish and bedtime need to be in that order.")
                if (sc.step >= sc.steps.size - 1) return reduceInner(s, Action.OnbFinish, now, env)
                ui(ui.copy(screen = sc.copy(step = sc.step + 1)))
            }
            Action.OnbBack -> {
                val sc = ui.screen as? Screen.Onboarding ?: return Reduced(s)
                ui(ui.copy(screen = sc.copy(step = (sc.step - 1).coerceAtLeast(0))))
            }
            Action.OnbFinish -> {
                val sc = ui.screen as? Screen.Onboarding ?: return Reduced(s)
                val nd = Onboarding.apply(d, sc.answers, sc.returning)
                tick(s.copy(data = nd, ui = ui.copy(screen = Screen.Dashboard, dialog = null,
                    message = "All set! Tap ⚙ any time to change things.")), env)
            }
            Action.OnbSkip -> tick(s.copy(data = d.copy(onboarded = true), ui = ui.copy(screen = Screen.Dashboard, dialog = null)), env)
            is Action.SetFeatures -> data(d.copy(prefs = d.prefs.copy(features = a.features)))
            is Action.SetMusic -> data(d.copy(prefs = d.prefs.copy(music = a.music)))

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
            is Screen.Settings, is Screen.Tasks -> Reduced(s.copy(ui = ui.copy(screen = Screen.Dashboard)))
            Screen.Focus -> Reduced(s.copy(ui = ui.copy(screen = Screen.Dashboard))) // timer keeps running; Now card brings you back
            is Screen.Onboarding -> reduceInner(s, Action.OnbBack, s.now, env)
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
            Feature.ROUTINE_PROMPTS in d.prefs.features && r.enabled && sc != null && sc.notify != NotifyStyle.NONE && dow in sc.days &&
                minute >= sc.time && minute < sc.time + PROMPT_WINDOW_MIN && r.id !in d.daily.prompted
        }.map { it.id }
        if (newlyPrompted.isNotEmpty()) d = d.copy(daily = d.daily.copy(prompted = d.daily.prompted + newlyPrompted))

        val focus = d.focus
        if (focus != null) {
            val (nf, ring) = Focus.tick(focus, now)
            if (nf != focus) d = d.copy(focus = nf)
            if (ring) effects += Effect.PlayAlert
        }

        // gentle heads-up before the mode changes (time blindness)
        val warn = d.prefs.transitionWarnMin
        if (warn > 0 && d.override == null && Feature.HEADS_UP in d.prefs.features) {
            val nc = Scheduler.nextChange(d.modes, local)
            if (nc != null) {
                val at = Scheduler.toEpoch(nc.first, env.zone)
                if (at - now <= warn * 60_000L && d.daily.warnedFor != at) {
                    d = d.copy(daily = d.daily.copy(warnedFor = at))
                    if (d.prefs.chimeOnTransition) effects += Effect.Chime
                }
            }
        }

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

    // ---------- tasks ----------
    fun todayTasks(s: AppState) = s.data.tasks.filter { it.today && !it.done }.sortedBy { it.order }

    fun isToday(epoch: Long?, s: AppState, env: Env): Boolean =
        epoch != null && Scheduler.toLocal(epoch, env.zone).toLocalDate() == Scheduler.toLocal(s.now, env.zone).toLocalDate()

    /** Tasks finished today (stay visible, struck through) + routines finished today + focus sessions. */
    fun doneTodayCount(s: AppState, env: Env? = null): Int {
        val tasks = if (env == null) s.data.tasks.count { it.done && it.doneAt != null && s.now - it.doneAt < 24 * 3600_000L }
                    else s.data.tasks.count { it.done && isToday(it.doneAt, s, env) }
        return tasks + s.data.daily.routinesDone.size + s.data.daily.focusDone
    }

    fun tasksFor(s: AppState, filter: String, env: Env): List<Task> {
        val open = s.data.tasks.filter { !it.done }
        val doneToday = s.data.tasks.filter { it.done && isToday(it.doneAt, s, env) }
        return when (filter) {
            TaskFilter.TODAY -> open.filter { it.today }.sortedBy { it.order } + doneToday.filter { it.today }
            TaskFilter.INBOX -> open.filter { it.modeIds.isEmpty() && !it.today && it.spaceId == null }.sortedByDescending { it.createdAt }
            TaskFilter.ALL -> open.sortedWith(compareByDescending<Task> { it.today }.thenBy { it.order })
            TaskFilter.DONE -> s.data.tasks.filter { it.done }.sortedByDescending { it.doneAt ?: 0 }
            else -> if (filter.startsWith("space:")) {
                val sid = filter.removePrefix("space:")
                open.filter { it.spaceId == sid }.sortedBy { it.order } + doneToday.filter { it.spaceId == sid }
            } else open.filter { filter in it.modeIds }.sortedWith(compareByDescending<Task> { it.today }.thenBy { it.order })
        }
    }

    fun modeTasks(s: AppState, modeId: String) = s.data.tasks.filter { !it.done && modeId in it.modeIds }
        .sortedWith(compareByDescending<Task> { it.today }.thenBy { it.order })

    /** "Pick one for me": today's list first, then this mode's tasks, then the inbox. */
    fun pickPool(s: AppState, env: Env): List<Task> {
        todayTasks(s).let { if (it.isNotEmpty()) return it }
        effective(s, env).modeOrNull()?.let { m -> modeTasks(s, m.id).let { if (it.isNotEmpty()) return it } }
        return s.data.tasks.filter { !it.done }
    }

    // ---------- time ----------
    /** Minutes until the next mode change, if it's within the heads-up window. */
    fun transitionSoon(s: AppState, env: Env): Pair<Long, ScheduleResult>? {
        val warn = s.data.prefs.transitionWarnMin
        if (warn <= 0 || s.data.override != null || Feature.HEADS_UP !in s.data.prefs.features) return null
        val (t, r) = nextChange(s, env) ?: return null
        val mins = (Scheduler.toEpoch(t, env.zone) - s.now + 59_999) / 60_000
        return if (mins <= warn) mins to r else null
    }

    /** Start and end of the current mode block, for the "time left" bar. Null when there's no end. */
    fun modeWindow(s: AppState, env: Env): Pair<Long, Long>? = when (val e = effective(s, env)) {
        is Effective.Overridden -> e.until?.let { (s.data.override?.setAt ?: s.now) to it }
        is Effective.Scheduled -> {
            val local = Scheduler.toLocal(s.now, env.zone)
            val start = Scheduler.currentBlockStart(s.data.modes, local)
            val end = Scheduler.nextChange(s.data.modes, local)?.first
            if (start == null || end == null) null else Scheduler.toEpoch(start, env.zone) to Scheduler.toEpoch(end, env.zone)
        }
        else -> null
    }

    // ---------- calendar ----------
    fun currentEvent(s: AppState) = s.live.calendar.filter { !it.allDay && it.begin <= s.now && it.end > s.now }.minByOrNull { it.end }
    fun nextEvent(s: AppState, env: Env) = s.live.calendar.filter { !it.allDay && it.begin > s.now && isToday(it.begin, s, env) }.minByOrNull { it.begin }
    fun todayEvents(s: AppState, env: Env) = s.live.calendar.filter { it.end > s.now && (isToday(it.begin, s, env) || (it.allDay && it.begin <= s.now)) }.sortedBy { it.begin }
    fun tomorrowFirst(s: AppState, env: Env): CalEvent? {
        val tomorrow = Scheduler.toLocal(s.now, env.zone).toLocalDate().plusDays(1)
        return s.live.calendar.filter { !it.allDay && Scheduler.toLocal(it.begin, env.zone).toLocalDate() == tomorrow }.minByOrNull { it.begin }
    }

    // ---------- the one thing ----------
    /** The single most useful thing to show right now (one decision at a time). */
    fun now(s: AppState, env: Env): NowItem {
        s.data.focus?.let { return NowItem.FocusActive(it) }
        prompts(s, env).firstOrNull()?.let { return NowItem.Prompt(it) }
        s.ui.spotlightTaskId?.let { id -> s.data.tasks.find { it.id == id && !it.done }?.let { return NowItem.Spotlight(it) } }
        val mode = effective(s, env).modeOrNull()
        featured(s, mode)?.takeIf { it.id !in s.data.daily.routinesDone }?.let { return NowItem.Featured(it) }
        todayTasks(s).firstOrNull()?.let { return NowItem.TodayTask(it) }
        return NowItem.Rest
    }

    fun isDimmed(s: AppState, env: Env): Boolean {
        if (!s.data.prefs.nightDim) return false
        val m = effective(s, env).modeOrNull() ?: return false
        return m.id in s.data.prefs.dimModeIds
    }
}

sealed interface NowItem {
    data class FocusActive(val focus: FocusSession) : NowItem
    data class Prompt(val routine: Routine) : NowItem
    data class Spotlight(val task: Task) : NowItem
    data class Featured(val routine: Routine) : NowItem
    data class TodayTask(val task: Task) : NowItem
    object Rest : NowItem
}

/** Warm, varied, never-guilt-tripping feedback. No streaks, no scores. */
object Encourage {
    private val lines = listOf("Nice. One less thing.", "Done ✓ Your brain thanks you.", "That counts. Well done.",
        "Ticked off. Breathe out.", "Look at you go.", "Small step, real progress.")
    fun forTask(doneToday: Int, rand: (Int) -> Int): String =
        if (doneToday >= 3 && doneToday % 3 == 0) "That's $doneToday things today. Genuinely good going."
        else lines[rand(lines.size)]
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

    fun task(t: Task): List<String> = if (t.title.isBlank()) listOf("Give the task a few words.") else emptyList()

    fun step(st: Step): List<String> {
        val out = ArrayList<String>()
        if (st.title.isBlank()) out += "Give the step a title."
        if (st.timerEnabled && st.durationSec <= 0) out += "Set a duration for the timer, or turn the timer off."
        return out
    }
}
