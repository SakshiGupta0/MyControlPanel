package app.mycontrolpanel.core

import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Core logic tests. Plain Kotlin (no JUnit dependency) so they run both
 * locally with kotlinc and in CI through CoreSuiteTest (JUnit wrapper).
 */
object CoreSuite {
    private val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    private var idSeq = 0
    private val env = Env(zone) { "id${++idSeq}" }

    // 2026-10-05 is a Monday.
    private fun t(day: Int, h: Int, m: Int = 0, month: Int = 10) = Scheduler.toEpoch(LocalDateTime.of(2026, month, day, h, m), zone)
    private fun lt(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, day, h, m)

    private fun eq(expected: Any?, actual: Any?, what: String) {
        if (expected != actual) throw AssertionError("$what: expected <$expected> but was <$actual>")
    }
    private fun ok(cond: Boolean, what: String) { if (!cond) throw AssertionError(what) }

    private fun modeAt(modes: List<Mode>, day: Int, h: Int, m: Int = 0): String =
        when (val r = Scheduler.resolve(modes, lt(day, h, m))) {
            is ScheduleResult.Active -> r.mode.id
            is ScheduleResult.Conflict -> "conflict:" + r.modes.joinToString(",") { it.id }
            ScheduleResult.Free -> "free"
        }

    private fun st0(day: Int = 5, h: Int = 12) = Reducer.initial(Defaults.data().copy(onboarded = true), t(day, h), env)
    private fun AppState.go(a: Action, now: Long = this.now) = Reducer.reduce(this, a, now, env).state
    private fun AppState.eff() = Scheduler.effective(data, now, zone)

    val tests: List<Pair<String, () -> Unit>> = listOf(

        "default schedule resolves every phase of the day" to {
            val m = Defaults.modes()
            eq("morning", modeAt(m, 5, 8), "Mon 08:00")
            eq("morning", modeAt(m, 5, 7, 0), "Mon 07:00 boundary")
            eq("bedtime", modeAt(m, 5, 6, 59), "Mon 06:59 (Sunday night's bedtime)")
            eq("work", modeAt(m, 5, 10, 0), "Mon 10:00 boundary")
            eq("work", modeAt(m, 9, 18, 29), "Fri 18:29")
            eq("evening", modeAt(m, 9, 18, 30), "Fri 18:30")
            eq("dayoff", modeAt(m, 10, 12), "Sat 12:00")
            eq("dayoff", modeAt(m, 11, 12), "Sun 12:00")
            eq("bedtime", modeAt(m, 5, 23, 30), "Mon 23:30")
            eq("bedtime", modeAt(m, 6, 2), "Tue 02:00")
            eq("bedtime", modeAt(m, 12, 0, 30), "Mon 00:30 (from Sunday)")
        },

        "default schedule has no overlaps and no gaps" to {
            val m = Defaults.modes()
            eq(0, Scheduler.conflicts(m).size, "conflicts")
            var tm = lt(5, 0)
            while (tm.isBefore(lt(12, 0))) { ok(Scheduler.resolve(m, tm) is ScheduleResult.Active, "gap at $tm"); tm = tm.plusMinutes(15) }
        },

        "midnight-crossing mode belongs to the day it starts" to {
            val fri = Mode("x", "Late", "x", 23 * 60, 3 * 60, setOf(5))
            eq("x", modeAt(listOf(fri), 9, 23, 30), "Fri 23:30")
            eq("x", modeAt(listOf(fri), 10, 2, 59), "Sat 02:59")
            eq("free", modeAt(listOf(fri), 10, 3, 0), "Sat 03:00 end is exclusive")
            eq("free", modeAt(listOf(fri), 9, 2, 0), "Fri 02:00 (Thursday not active)")
            val sun = Mode("s", "SunLate", "s", 22 * 60, 2 * 60, setOf(7))
            eq("s", modeAt(listOf(sun), 12, 1, 0), "Mon 01:00 from Sunday (week wrap)")
        },

        "overlaps are detected per day, including across the week wrap" to {
            val gym = Mode("gym", "Gym", "g", 18 * 60, 19 * 60, setOf(1, 3))
            val c = Scheduler.conflictsFor(gym, Defaults.modes())
            eq(4, c.size, "Gym vs Work+Evening on Mon+Wed")
            ok(c.any { it.dow == 1 && it.start == 18 * 60 && it.end == 18 * 60 + 30 && (it.a.id == "work" || it.b.id == "work") }, "Mon 18:00-18:30 vs work")
            val x = Mode("x", "X", "x", 22 * 60, 2 * 60, setOf(7))
            val y = Mode("y", "Y", "y", 60, 3 * 60, setOf(1))
            val cy = Scheduler.conflicts(listOf(x, y))
            eq(1, cy.size, "wrap conflict count"); eq(1, cy[0].dow, "wrap conflict day"); eq(60, cy[0].start, "start"); eq(120, cy[0].end, "end")
            eq(0, Scheduler.conflicts(listOf(x, y.copy(scheduled = false))).size, "manual-only modes never conflict")
        },

        "overlapping modes never silently compete; explicit priority resolves" to {
            val gym = Mode("gym", "Gym", "g", 18 * 60, 19 * 60, setOf(1))
            val modes = Defaults.modes() + gym
            eq("conflict:work,gym", modeAt(modes, 5, 18, 10).let { r -> r.split(":")[0] + ":" + r.split(":")[1].split(",").sorted().reversed().joinToString(",") }, "unresolved")
            val pri = Defaults.modes() + gym.copy(winsOver = setOf("work", "evening"))
            eq("gym", modeAt(pri, 5, 18, 10), "gym wins over work")
            eq("gym", modeAt(pri, 5, 18, 45), "gym wins over evening")
            eq("evening", modeAt(pri, 5, 19, 0), "evening resumes")
            eq("work", modeAt(pri, 6, 18, 10), "Tuesday untouched")
            eq(0, Scheduler.conflicts(pri).size, "no remaining conflicts")
            val cycle = listOf(gym.copy(winsOver = setOf("e")), Mode("e", "E", "e", 18 * 60, 20 * 60, setOf(1), winsOver = setOf("gym")))
            ok(modeAt(cycle, 5, 18, 30).startsWith("conflict"), "mutual priority stays a conflict")
        },

        "next scheduled change" to {
            val m = Defaults.modes()
            eq(lt(5, 10), Scheduler.nextChange(m, lt(5, 8))?.first, "Mon 08:00 → 10:00")
            eq(lt(10, 7), Scheduler.nextChange(m, lt(9, 23, 30))?.first, "Fri 23:30 → Sat 07:00")
            eq(null, Scheduler.nextChange(emptyList(), lt(5, 8)), "no modes")
        },

        "manual override: timed, until next change, until return" to {
            var s = st0(5, 12)
            eq("work", s.eff().key, "scheduled")
            s = s.go(Action.SwitchMode("dayoff", OverrideLength.H1))
            ok(s.eff() is Effective.Overridden, "overridden"); eq("dayoff", s.eff().key, "override mode")
            eq(null, s.ui.dialog, "switcher closed")
            s = s.go(Action.Tick, t(5, 12, 59)); eq("dayoff", s.eff().key, "still overridden at 59m")
            s = s.go(Action.Tick, t(5, 13, 1)); eq("work", s.eff().key, "expired"); eq(null, s.data.override, "override cleared")
            s = s.go(Action.SwitchMode("evening", OverrideLength.NEXT_CHANGE))
            eq(t(5, 18, 30), s.data.override?.until, "until 18:30")
            s = s.go(Action.SwitchMode("bedtime", OverrideLength.UNTIL_RETURN))
            s = s.go(Action.Tick, t(7, 12)); eq("bedtime", s.eff().key, "indefinite survives days")
            s = s.go(Action.ReturnToSchedule); eq("work", s.eff().key, "back to schedule")
        },

        "routine runner: timers, pause, alert, auto-advance, completion" to {
            val r = Routine("r", "R", steps = listOf(
                Step("a", "A", durationSec = 60, timerEnabled = true),
                Step("b", "B", durationSec = 10, timerEnabled = true, autoAdvance = true),
                Step("c", "C"),
            ))
            var run = Runner.start(r, 0)
            eq(60_000L, Runner.remainingMs(run, r, 0), "full"); eq(30_000L, Runner.remainingMs(run, r, 30_000), "half")
            run = Runner.pause(run, 30_000); eq(30_000L, Runner.remainingMs(run, r, 999_000), "paused holds")
            run = Runner.resume(run, r, 100_000); eq(20_000L, Runner.remainingMs(run, r, 110_000), "resumed")
            var tk = Runner.tick(run, r, 130_000); ok(tk.alert, "alert at zero"); eq(0, tk.run.index, "no auto-advance on A")
            tk = Runner.tick(tk.run, r, 131_000); ok(!tk.alert, "alert only once")
            run = Runner.back(tk.run, r, 0); eq(0, run.index, "back at first step is a no-op")
            run = Runner.done(tk.run, r, 140_000); eq(1, run.index, "done → B")
            tk = Runner.tick(run, r, 150_000); ok(tk.alert, "B alert"); eq(2, tk.run.index, "auto-advanced to C")
            eq(null, Runner.remainingMs(tk.run, r, 150_000), "C has no timer")
            run = Runner.back(tk.run, r, 151_000); eq(1, run.index, "back to B"); eq(10_000L, Runner.remainingMs(run, r, 151_000), "B timer restarts")
            run = Runner.done(Runner.done(run, r, 152_000), r, 152_000); ok(run.completed, "completed after last")
            ok(Runner.done(run, r, 153_000) == run, "done after completion is a no-op")
            val finished = Runner.tick(Runner.start(r, 0), r, 60_000).run
            val restarted = Runner.resume(finished, r, 70_000)
            eq(60_000L, Runner.remainingMs(restarted, r, 70_000), "resume after finish restarts timer")
        },

        "quick routine: popup gone, routine runs, completion returns to the same dashboard" to {
            var s = st0(5, 12)
            s = s.go(Action.OpenDialog(Dialog.QuickRoutines)); eq(Dialog.QuickRoutines, s.ui.dialog, "popup open")
            s = s.go(Action.StartRoutine("r_hydrate"))
            eq(null, s.ui.dialog, "popup dismissed"); eq(Screen.RunningRoutine, s.ui.screen, "routine screen")
            val steps = s.data.routines.first { it.id == "r_hydrate" }.steps.size
            repeat(steps) { s = s.go(Action.RoutineDone) }
            ok(s.data.run!!.completed, "completed")
            s = s.go(Action.FinishRoutine)
            eq(Screen.Dashboard, s.ui.screen, "dashboard"); eq(null, s.data.run, "run cleared"); eq(null, s.ui.dialog, "no overlay")
            eq("work", s.eff().key, "same mode")
        },

        "exit needs a deliberate step; back on a running routine asks first" to {
            var s = st0().go(Action.StartRoutine("r_bedtime"))
            s = s.go(Action.Back); eq(Dialog.ConfirmExitRoutine, s.ui.dialog, "confirm dialog"); eq(Screen.RunningRoutine, s.ui.screen, "still running")
            s = s.go(Action.Back); eq(null, s.ui.dialog, "back closes dialog"); ok(s.data.run != null, "still running")
            s = s.go(Action.ExitRoutine); eq(Screen.Dashboard, s.ui.screen, "exited"); eq(null, s.data.run, "cleared")
            val busy = st0().go(Action.StartRoutine("r_bedtime")).go(Action.StartRoutine("r_lunch"))
            eq("r_bedtime", busy.data.run?.routineId, "second start ignored"); ok(busy.ui.message != null, "explains why")
            val empty = st0().copy(data = st0().data.copy(routines = st0().data.routines + Routine("e", "Empty"))).go(Action.StartRoutine("e"))
            eq(Screen.Dashboard, empty.ui.screen, "empty routine does not start")
        },

        "routine in progress survives a restart" to {
            val s = st0().go(Action.StartRoutine("r_morning")).go(Action.RoutineDone).go(Action.RoutineDone)
            val reloaded = Reducer.initial(Codec.decode(Codec.encode(s.data)), s.now + 5_000, env)
            eq(Screen.RunningRoutine, reloaded.ui.screen, "routine screen restored"); eq(2, reloaded.data.run?.index, "same step")
        },

        "mode editor: create, conflict blocks save, resolve, save, cancel" to {
            var s = st0().go(Action.OpenSettings(SettingsSection.MODES)).go(Action.NewMode)
            val ed = s.ui.screen as Screen.ModeEditor
            s = s.go(Action.SaveMode); ok(s.ui.screen is Screen.ModeEditor, "blank name blocked"); ok(s.ui.message!!.contains("name"), "says name")
            s = s.go(Action.UpdateModeDraft(ed.draft.copy(name = " Gym ", start = 18 * 60, end = 19 * 60, days = setOf(1, 3))))
            s = s.go(Action.SaveMode); ok(s.ui.screen is Screen.ModeEditor, "conflict blocked"); ok(s.ui.message!!.contains("overlap"), "says overlap")
            val d2 = (s.ui.screen as Screen.ModeEditor).draft
            s = s.go(Action.UpdateModeDraft(d2.copy(winsOver = setOf("work", "evening"))))
            s = s.go(Action.SaveMode)
            eq(Screen.Settings(SettingsSection.MODES), s.ui.screen, "saved → list")
            val gym = s.data.modes.first { it.name == "Gym" }; eq(6, s.data.modes.size, "added")
            eq(ModeWidget.values().size, gym.widgets.size, "all widget slots present")
            // edit + cancel with changes → confirm discard
            s = s.go(Action.EditMode(gym.id)).go(Action.UpdateModeDraft(gym.copy(name = "Gym!")))
            s = s.go(Action.CancelEdit); eq(Dialog.ConfirmDiscard, s.ui.dialog, "discard asked")
            s = s.go(Action.ConfirmDiscard); eq("Gym", s.data.modes.first { it.id == gym.id }.name, "unchanged")
            eq(Screen.Settings(SettingsSection.MODES), s.ui.screen, "back at list"); eq(null, s.ui.dialog, "no dialog")
            // clean cancel → no prompt
            s = s.go(Action.EditMode(gym.id)).go(Action.Back); eq(Screen.Settings(SettingsSection.MODES), s.ui.screen, "clean back")
        },

        "mode editor: 'turn off the other schedule' applies only on save" to {
            var s = st0().go(Action.NewMode)
            val d = (s.ui.screen as Screen.ModeEditor).draft.copy(name = "Holiday", start = 10 * 60, end = 18 * 60 + 30, days = setOf(1, 2, 3, 4, 5))
            s = s.go(Action.UpdateModeDraft(d)).go(Action.DisableOnSave("work", true))
            ok(s.data.modes.first { it.id == "work" }.scheduled, "not applied yet")
            s = s.go(Action.SaveMode)
            ok(!s.data.modes.first { it.id == "work" }.scheduled, "work schedule off after save")
            eq("Holiday", s.eff().modeOrNull()?.name, "holiday active now")
        },

        "routine editor: steps add/edit/reorder/delete, save, reopen" to {
            var s = st0().go(Action.OpenSettings(SettingsSection.ROUTINES)).go(Action.NewRoutine)
            s = s.go(Action.SaveRoutine); ok(s.ui.screen is Screen.RoutineEditor, "blank blocked")
            val r0 = (s.ui.screen as Screen.RoutineEditor).draft
            s = s.go(Action.UpdateRoutineDraft(r0.copy(name = "Plants", modeIds = setOf("dayoff", "ghost"), quick = true)))
            s = s.go(Action.EditStep(null)).go(Action.SaveStep); ok(s.ui.dialog is Dialog.StepEditor, "blank step blocked")
            val sd = (s.ui.dialog as Dialog.StepEditor).step
            s = s.go(Action.UpdateStepDraft(sd.copy(title = "Water", durationSec = 0, timerEnabled = true))).go(Action.SaveStep)
            ok(s.ui.dialog is Dialog.StepEditor, "timer without duration blocked")
            s = s.go(Action.UpdateStepDraft(sd.copy(title = "Water", durationSec = 120, timerEnabled = true))).go(Action.SaveStep)
            s = s.go(Action.EditStep(null)); s = s.go(Action.UpdateStepDraft((s.ui.dialog as Dialog.StepEditor).step.copy(title = "Mist"))).go(Action.SaveStep)
            s = s.go(Action.EditStep(null)); s = s.go(Action.UpdateStepDraft((s.ui.dialog as Dialog.StepEditor).step.copy(title = "Rotate"))).go(Action.SaveStep)
            s = s.go(Action.MoveStep(2, -2))
            eq(listOf("Rotate", "Water", "Mist"), (s.ui.screen as Screen.RoutineEditor).draft.steps.map { it.title }, "reordered")
            s = s.go(Action.MoveStep(0, -1)); eq("Rotate", (s.ui.screen as Screen.RoutineEditor).draft.steps[0].title, "out of range ignored")
            s = s.go(Action.EditStep(1)); s = s.go(Action.UpdateStepDraft((s.ui.dialog as Dialog.StepEditor).step.copy(title = "Water well"))).go(Action.SaveStep)
            s = s.go(Action.DeleteStep(2))
            s = s.go(Action.SaveRoutine)
            val saved = s.data.routines.first { it.name == "Plants" }
            eq(listOf("Rotate", "Water well"), saved.steps.map { it.title }, "saved steps")
            eq(setOf("dayoff"), saved.modeIds, "unknown mode ids dropped")
            val reopened = Codec.decode(Codec.encode(s.data)).routines.first { it.id == saved.id }
            eq(saved, reopened, "survives persistence")
            ok(Derive.quickRoutines(s).any { it.id == saved.id }, "appears in quick routines")
        },

        "reset: cancel keeps data, confirm restores defaults once" to {
            var s = st0().go(Action.AddWater(3)).go(Action.SwitchMode("bedtime", OverrideLength.UNTIL_RETURN))
            s = s.go(Action.OpenSettings(SettingsSection.RESET)).go(Action.OpenDialog(Dialog.ConfirmReset)).go(Action.CloseDialog)
            eq(3, s.data.daily.water, "cancel keeps data")
            s = s.go(Action.OpenDialog(Dialog.ConfirmReset)).go(Action.ConfirmReset)
            eq(Defaults.modes(), s.data.modes, "modes"); eq(Defaults.routines(), s.data.routines, "routines")
            eq(null, s.data.override, "override"); eq(0, s.data.daily.water, "water")
            eq(Screen.Dashboard, s.ui.screen, "dashboard"); eq(null, s.ui.dialog, "no dialog"); eq("work", s.eff().key, "mode recalculated")
        },

        "persistence: round trip, defensive decode, garbage rejected" to {
            var s = st0().go(Action.AddWater(2)).go(Action.ToggleMeal(1)).go(Action.SwitchMode("evening", OverrideLength.H2))
                .go(Action.UpdatePrefs(Prefs(use24h = true, textScale = 1.3f, sleepAudioUri = "spotify:playlist:abc\"q")))
            val json = Codec.encode(s.data)
            eq(s.data, Codec.decode(json), "round trip")
            eq(json, Codec.encode(Codec.decode(json)), "stable encoding")
            val partial = Codec.decode("""{"modes":[{"id":"x","name":"X","widgets":[{"w":"NOPE"}],"days":[1,9]}],"prefs":{"waterTarget":500}}""")
            eq(1, partial.modes.size, "partial modes"); eq(setOf(1), partial.modes[0].days, "invalid day dropped")
            eq(ModeWidget.values().size, partial.modes[0].widgets.size, "slots normalized")
            eq(30, partial.prefs.waterTarget, "clamped"); eq(Defaults.routines().size, partial.routines.size, "missing routines → defaults")
            var threw = false; try { Codec.decode("{not json") } catch (e: Json.ParseException) { threw = true }
            ok(threw, "garbage throws")
        },

        "json escapes and unicode" to {
            val v = mapOf("a" to "quote \" back \\ nl \n tab \t ☀️🌙", "n" to listOf(1L, -2L, 3.5, true, null))
            eq(v, Json.parse(Json.write(v)), "round trip")
        },

        "daily counters roll over at midnight" to {
            var s = st0(5, 22).go(Action.AddWater(5)).go(Action.ToggleMeal(0)).go(Action.TookBreak)
            s = s.go(Action.Tick, t(5, 23, 59)); eq(5, s.data.daily.water, "same day")
            s = s.go(Action.Tick, t(6, 0, 1)); eq(0, s.data.daily.water, "reset"); eq(emptySet<Int>(), s.data.daily.mealsDone, "meals reset")
            eq(0, st0().go(Action.AddWater(-3)).data.daily.water, "never negative")
        },

        "scheduled routine prompts: once, within window, dismissable" to {
            var s = st0(5, 7).go(Action.Tick, t(5, 7, 29)); eq(0, Derive.prompts(s, env).size, "not yet")
            s = s.go(Action.Tick, t(5, 7, 31)); eq(listOf("r_morning"), Derive.prompts(s, env).map { it.id }, "prompted")
            s = s.go(Action.DismissPrompt("r_morning")); eq(0, Derive.prompts(s, env).size, "dismissed")
            s = s.go(Action.Tick, t(5, 7, 40)); eq(0, Derive.prompts(s, env).size, "not re-prompted")
            val late = Reducer.initial(Defaults.data().copy(onboarded = true), t(6, 9, 30), env); eq(0, Derive.prompts(late, env).size, "outside window")
            val weekend = Reducer.initial(Defaults.data().copy(onboarded = true), t(10, 7, 35), env); eq(0, Derive.prompts(weekend, env).size, "weekdays only")
            val started = Reducer.initial(Defaults.data().copy(onboarded = true), t(7, 7, 35), env).go(Action.StartRoutine("r_morning")).go(Action.ExitRoutine)
            eq(0, Derive.prompts(started, env).size, "starting it clears the prompt")
        },

        "deleting a mode/routine cleans every reference" to {
            var s = st0().go(Action.SwitchMode("evening", OverrideLength.UNTIL_RETURN))
            s = s.go(Action.DeleteMode("evening")); eq(Dialog.ConfirmDeleteMode("evening"), s.ui.dialog, "asks first")
            s = s.go(Action.ConfirmDelete)
            ok(s.data.modes.none { it.id == "evening" }, "removed"); eq(null, s.data.override, "override cleared")
            ok(s.data.routines.none { "evening" in it.modeIds }, "routine assignments cleaned")
            s = s.go(Action.DeleteRoutine("r_bedtime")).go(Action.ConfirmDelete)
            eq(null, s.data.modes.first { it.id == "bedtime" }.featuredRoutineId, "featured cleared")
        },

        "conflict on the dashboard is resolved by an explicit pick" to {
            val gym = Mode("gym", "Gym", "g", 11 * 60, 13 * 60, setOf(1))
            var s = Reducer.initial(Defaults.data().copy(onboarded = true).copy(modes = Defaults.modes() + gym), t(5, 12), env)
            ok(s.eff() is Effective.Conflict, "conflict shown, not guessed")
            s = s.go(Action.PickConflictMode("gym")); eq("gym", s.eff().key, "picked")
            eq(t(5, 13), s.data.override?.until, "only until the schedule changes")
        },

        "mode entry time drives the break reminder" to {
            var s = st0(5, 9).go(Action.Tick, t(5, 10, 0))
            eq(t(5, 10, 0), s.data.modeEnteredAt, "entered work at 10:00")
            s = s.go(Action.Tick, t(5, 10, 55)); eq(55L, Derive.minutesSinceBreak(s), "55 min")
            s = s.go(Action.TookBreak); eq(0L, Derive.minutesSinceBreak(s), "reset")
        },

        "next routine fire" to {
            val r = Defaults.routines().first { it.id == "r_morning" }
            eq(lt(5, 7, 30), Scheduler.nextRoutineFire(r, lt(5, 7)), "same day")
            eq(lt(12, 7, 30), Scheduler.nextRoutineFire(r, lt(9, 8)), "Fri after → next Mon")
            eq(null, Scheduler.nextRoutineFire(r.copy(enabled = false), lt(5, 7)), "disabled")
        },

        "night dim follows the bedtime mode" to {
            ok(Derive.isDimmed(st0(5, 23), env), "dim at 23:00")
            ok(!Derive.isDimmed(st0(5, 12), env), "not at noon")
            ok(!Derive.isDimmed(st0(5, 23).go(Action.UpdatePrefs(Prefs(nightDim = false))), env), "toggle off")
        },

        "brain dump: capture goes to the inbox, empty text does nothing" to {
            var s = st0().go(Action.OpenDialog(Dialog.Capture)).go(Action.Capture("  call the bank  "))
            eq(null, s.ui.dialog, "dialog closed"); eq("call the bank", s.data.tasks.single().title, "trimmed")
            eq(listOf("call the bank"), Derive.tasksFor(s, TaskFilter.INBOX, env).map { it.title }, "in inbox")
            s = s.go(Action.OpenDialog(Dialog.Capture)).go(Action.Capture("   ")); eq(1, s.data.tasks.size, "blank ignored"); eq(null, s.ui.dialog, "closed")
        },

        "today's three: soft limit, toggle, fresh start" to {
            var s = st0()
            listOf("a", "b", "c", "d").forEach { s = s.go(Action.Capture(it, today = true)) }
            eq(3, Derive.todayTasks(s).size, "limit 3"); ok(s.ui.message!!.contains("full"), "explains overflow")
            val d = s.data.tasks.first { it.title == "d" }
            s = s.go(Action.ToggleToday(d.id)); ok(!s.data.tasks.first { it.id == d.id }.today, "4th refused"); ok(s.ui.message!!.contains("Today holds"), "kind msg")
            s = s.go(Action.ToggleTaskDone(s.data.tasks.first { it.title == "a" }.id))
            eq(2, Derive.todayTasks(s).size, "done leaves open list"); eq(3, Derive.tasksFor(s, TaskFilter.TODAY, env).size, "done still visible today")
            s = s.go(Action.ToggleToday(d.id)); ok(s.data.tasks.first { it.id == d.id }.today, "room again")
            s = s.go(Action.FreshStart); eq(0, Derive.todayTasks(s).size, "fresh start"); eq(4, s.data.tasks.size, "nothing deleted")
            s = s.go(Action.ClearDone); eq(3, s.data.tasks.size, "done cleared")
        },

        "pick one for me spotlights a task; finishing it clears the spotlight" to {
            var s = st0().go(Action.PickOne); ok(s.ui.message!!.contains("Nothing"), "empty pool message")
            s = s.go(Action.Capture("x", today = true)).go(Action.Capture("y", today = true))
            s = s.go(Action.PickOne); val id = s.ui.spotlightTaskId!!
            ok(Derive.now(s, env) is NowItem.Spotlight, "now = spotlight")
            s = s.go(Action.PickOne); ok(s.ui.spotlightTaskId != id, "re-pick gives a different one")
            s = s.go(Action.ToggleTaskDone(s.ui.spotlightTaskId!!)); eq(null, s.ui.spotlightTaskId, "cleared on done")
        },

        "task editor: validation, modes, delete" to {
            var s = st0().go(Action.EditTask(null)).go(Action.SaveTask); ok(s.ui.dialog is Dialog.TaskEditor, "blank blocked")
            val t = (s.ui.dialog as Dialog.TaskEditor).task
            s = s.go(Action.UpdateTaskDraft(t.copy(title = "Report", modeIds = setOf("work", "ghost")))).go(Action.SaveTask)
            eq(setOf("work"), s.data.tasks.single().modeIds, "unknown mode dropped")
            eq(listOf("Report"), Derive.modeTasks(s, "work").map { it.title }, "mode filter")
            s = s.go(Action.DeleteTask(s.data.tasks.single().id)); eq(0, s.data.tasks.size, "deleted")
        },

        "focus timer: start, pause, extend, bell, end marks task done" to {
            var s = st0().go(Action.Capture("write intro", today = true))
            val tid = s.data.tasks.single().id
            s = s.go(Action.StartFocus(tid, 25)); eq(Screen.Focus, s.ui.screen, "focus screen")
            val t0 = s.now
            s = s.go(Action.FocusPause, t0 + 60_000); s = s.go(Action.Tick, t0 + 3_600_000)
            eq(24 * 60_000L, Focus.remaining(s.data.focus!!, s.now), "paused holds")
            s = s.go(Action.FocusResume, t0 + 3_600_000).go(Action.FocusExtend(5), t0 + 3_600_000)
            eq(29 * 60_000L, Focus.remaining(s.data.focus!!, t0 + 3_600_000), "extended")
            val r = Reducer.reduce(s, Action.Tick, t0 + 3_600_000 + 29 * 60_000L, env)
            ok(r.effects.contains(Effect.PlayAlert), "bell"); ok(r.state.data.focus!!.finished, "finished")
            ok(Reducer.reduce(r.state, Action.Tick, r.state.now + 1000, env).effects.isEmpty(), "bell once")
            s = r.state.go(Action.EndFocus(markTaskDone = true))
            eq(Screen.Dashboard, s.ui.screen, "back"); eq(null, s.data.focus, "cleared"); ok(s.data.tasks.single().done, "task done")
            eq(1, s.data.daily.focusDone, "counted")
        },

        "focus survives restart, back leaves it running, routines wait for it" to {
            var s = st0().go(Action.StartFocus(null, 10))
            val re = Reducer.initial(Codec.decode(Codec.encode(s.data)), s.now + 60_000, env)
            eq(Screen.Focus, re.ui.screen, "restored to focus")
            s = s.go(Action.Back); eq(Screen.Dashboard, s.ui.screen, "back to dashboard"); ok(s.data.focus != null, "still running")
            ok(Derive.now(s, env) is NowItem.FocusActive, "now card shows it")
            s = s.go(Action.StartRoutine("r_lunch")); eq(Screen.Dashboard, s.ui.screen, "routine blocked"); ok(s.ui.message != null, "says why")
            s = s.go(Action.ShowFocus); eq(Screen.Focus, s.ui.screen, "return to focus")
        },

        "heads-up chime once before a mode change" to {
            var s = st0(5, 18)  // Work until 18:30
            var r = Reducer.reduce(s, Action.Tick, t(5, 18, 19), env); ok(r.effects.isEmpty(), "too early")
            r = Reducer.reduce(r.state, Action.Tick, t(5, 18, 21), env); ok(r.effects.contains(Effect.Chime), "chime at 9 min")
            eq(9L, Derive.transitionSoon(r.state, env)?.first, "9 minutes left")
            r = Reducer.reduce(r.state, Action.Tick, t(5, 18, 25), env); ok(!r.effects.contains(Effect.Chime), "only once")
            s = r.state.go(Action.UpdatePrefs(r.state.data.prefs.copy(chimeOnTransition = false)))
            r = Reducer.reduce(s, Action.Tick, t(5, 22, 52), env); ok(!r.effects.contains(Effect.Chime), "chime can be off")
            ok(Derive.transitionSoon(r.state, env) != null, "banner still shows")
            val ov = st0(5, 18).go(Action.SwitchMode("dayoff", OverrideLength.UNTIL_RETURN))
            eq(null, Derive.transitionSoon(ov.go(Action.Tick, t(5, 18, 25)), env), "no heads-up during a manual override")
        },

        "mode time-left window" to {
            val s = st0(5, 12)
            eq(t(5, 10) to t(5, 18, 30), Derive.modeWindow(s, env), "work block 10:00-18:30")
            val night = st0(6, 2)
            eq(t(5, 23) to t(6, 7), Derive.modeWindow(night, env), "bedtime across midnight")
            val ov = s.go(Action.SwitchMode("evening", OverrideLength.H1))
            eq(s.now to s.now + 3_600_000, Derive.modeWindow(ov, env), "override window")
            eq(null, Derive.modeWindow(s.go(Action.SwitchMode("evening", OverrideLength.UNTIL_RETURN)), env), "no end → no bar")
        },

        "the Now card picks one thing in priority order" to {
            var s = st0(5, 7).go(Action.Tick, t(5, 7, 31))
            ok(Derive.now(s, env) is NowItem.Prompt, "routine prompt first")
            s = s.go(Action.DismissPrompt("r_morning"))
            ok((Derive.now(s, env) as NowItem.Featured).routine.id == "r_morning", "then featured routine")
            s = s.go(Action.StartRoutine("r_morning"))
            repeat(6) { s = s.go(Action.RoutineDone) }
            s = s.go(Action.FinishRoutine); ok("r_morning" in s.data.daily.routinesDone, "finished counted")
            eq(NowItem.Rest, Derive.now(s, env), "nothing left → rest")
            s = s.go(Action.Capture("stretch", today = true))
            ok(Derive.now(s, env) is NowItem.TodayTask, "then today's task")
            eq(1, Derive.doneTodayCount(s, env), "one routine finished today")
        },

        "calendar: current, next, tomorrow" to {
            val base = st0(5, 12)
            val ev = listOf(
                CalEvent("1", "Standup", t(5, 11, 30), t(5, 12, 15), false),
                CalEvent("2", "Design review", t(5, 15), t(5, 16), false),
                CalEvent("3", "Gym", t(6, 8), t(6, 9), false),
                CalEvent("4", "Holiday", t(5, 0), t(6, 0), true),
            )
            val s = base.go(Action.CalendarLoaded(ev, CalendarStatus.OK))
            eq("Standup", Derive.currentEvent(s)?.title, "current"); eq("Design review", Derive.nextEvent(s, env)?.title, "next today")
            eq("Gym", Derive.tomorrowFirst(s, env)?.title, "tomorrow"); eq(listOf("Holiday", "Standup", "Design review"), Derive.todayEvents(s, env).map { it.title }, "today list")
            ok(!Codec.encode(s.data).contains("Standup"), "calendar is live-only, never saved")
        },

        "v0.1 save files load into v0.2" to {
            val old = """{"schema":1,"modes":[{"id":"bedtime","name":"Bedtime","icon":"🌙","start":1380,"end":420,"days":[1,2,3,4,5,6,7],
                "widgets":[{"w":"SLEEP_AUDIO","on":true},{"w":"ROUTINES","on":true}]}],"routines":[],"prefs":{"use24h":true}}"""
            val d = Codec.decode(old)
            eq(ModeWidget.MUSIC, d.modes[0].widgets[0].widget, "SLEEP_AUDIO → MUSIC"); ok(d.modes[0].widgets[0].enabled, "kept on")
            eq(4, d.modes[0].tint, "bedtime tint"); eq(emptyList<Task>(), d.tasks, "no tasks"); ok(d.prefs.use24h, "prefs kept")
            eq(3, d.prefs.todayLimit, "new prefs default")
        },

        "sync: two devices converge through the server (newest change wins)" to {
            val server = LinkedHashMap<String, Sync.Doc>()
            fun push(st: AppState): AppState {
                val out = Sync.outgoing(st.data)
                out.forEach { d -> if ((server[d.key]?.mt ?: -1) <= d.mt) server[d.key] = d }  // rules: never go back in time
                return st.go(Action.MarkSent(out))
            }
            fun pull(st: AppState) = st.go(Action.ApplyRemote(server.values.toList()))
            var a = st0(5, 12)
            a = a.go(Action.ApplyRemote(emptyList(), accountWasEmpty = true)); a = push(a)
            ok(server.containsKey("mode:work") && server.containsKey("prefs"), "first device uploaded everything")
            var b = pull(Reducer.initial(Defaults.data().copy(onboarded = true), t(5, 12), env))
            eq(a.data.modes, b.data.modes, "second device took the account's modes")
            a = a.go(Action.Capture("buy milk"), t(5, 12, 1)); a = push(a)
            b = b.go(Action.AddWater(2), t(5, 12, 2)); b = push(b)
            a = pull(a); b = pull(b)
            eq(listOf("buy milk"), b.data.tasks.map { it.title }, "task reached B"); eq(2, a.data.daily.water, "water reached A")
            ok(Sync.outgoing(a.data).isEmpty() && Sync.outgoing(b.data).isEmpty(), "nothing left to send")
            // concurrent edits of the same task: later one wins everywhere
            val id = a.data.tasks.single().id
            a = a.go(Action.EditTask(id), t(5, 13)).let { it.go(Action.UpdateTaskDraft((it.ui.dialog as Dialog.TaskEditor).task.copy(title = "buy oat milk")), t(5, 13)) }.go(Action.SaveTask, t(5, 13))
            b = b.go(Action.EditTask(id), t(5, 14)).let { it.go(Action.UpdateTaskDraft((it.ui.dialog as Dialog.TaskEditor).task.copy(title = "buy milk + eggs")), t(5, 14)) }.go(Action.SaveTask, t(5, 14))
            b = push(b); a = push(a)   // A's older write arrives last but must not win
            a = pull(a); b = pull(b)
            eq("buy milk + eggs", a.data.tasks.single().title, "A converged"); eq("buy milk + eggs", b.data.tasks.single().title, "B converged")
        },

        "sync: deletes travel; a newer edit resurrects" to {
            val base = Reducer.initial(Defaults.data().copy(onboarded = true), t(5, 12), env).go(Action.Capture("x"))
            val id = base.data.tasks.single().id
            val del = base.go(Action.DeleteTask(id), t(5, 13))
            val delDocs = Sync.outgoing(del.data)
            ok(delDocs.any { it.key == "task:$id" && it.deleted }, "tombstone goes out")
            val other = base.go(Action.ApplyRemote(delDocs))
            ok(other.data.tasks.none { it.id == id }, "deleted on the other device")
            val olderEdit = Sync.Doc("task:$id", t(5, 12, 30), false, Json.write(Codec.taskOut(base.data.tasks.single().copy(title = "old"))))
            ok(other.go(Action.ApplyRemote(listOf(olderEdit))).data.tasks.none { it.id == id }, "older edit can't undo the delete")
            val newerEdit = olderEdit.copy(mt = t(5, 14))
            eq("old", other.go(Action.ApplyRemote(listOf(newerEdit))).data.tasks.single().title, "newer edit brings it back")
            ok(other.go(Action.ApplyRemote(listOf(Sync.Doc("task:zz", t(6, 1), false, "{broken")))).data.tasks.isEmpty(), "corrupt doc ignored")
        },

        "sync: device-only settings stay on the device" to {
            var s = Reducer.initial(Defaults.data().copy(onboarded = true), t(5, 12), env)
            s = s.go(Action.MarkSent(Sync.outgoing(s.data)))
            s = s.go(Action.UpdatePrefs(s.data.prefs.copy(textScale = 1.3f, keepScreenOn = false, fullscreen = false)))
            ok(Sync.outgoing(s.data).none { it.key == "prefs" }, "text size / screen not synced")
            s = s.go(Action.UpdatePrefs(s.data.prefs.copy(use24h = true)))
            ok(Sync.outgoing(s.data).any { it.key == "prefs" }, "24h clock synced")
            val other = Reducer.initial(Defaults.data().copy(onboarded = true), t(5, 12), env).go(Action.UpdatePrefs(Prefs(textScale = 0.9f)))
                .go(Action.ApplyRemote(Sync.outgoing(s.data)))
            ok(other.data.prefs.use24h, "remote pref applied"); eq(0.9f, other.data.prefs.textScale, "local text size kept")
            ok(Sync.outgoing(Reducer.initial(Defaults.data().copy(onboarded = true), t(5, 12), env).go(Action.StartRoutine("r_lunch")).data).none { it.key.startsWith("run") }, "routine in progress is local")
        },

        "sync: shared space tasks, tombstones and leaving a space" to {
            var s = Reducer.initial(Defaults.data().copy(onboarded = true), t(5, 12), env)
            s = s.go(Action.EditTask(null)).let { it.go(Action.UpdateTaskDraft((it.ui.dialog as Dialog.TaskEditor).task.copy(title = "bins out", spaceId = "flat"))) }.go(Action.SaveTask)
            val out = Sync.outgoing(s.data).single { it.key.startsWith("task:") }
            eq("flat", out.spaceId, "goes to the space")
            s = s.go(Action.MarkSent(Sync.outgoing(s.data)))
            val tid = s.data.tasks.single().id
            s = s.go(Action.AccountChanged(Account("u1", "a@b.c", "Sakshi Gupta"), true))
            s = s.go(Action.ToggleTaskDone(tid), s.now + 500); eq("Sakshi", s.data.tasks.single().doneBy, "who did it")
            val s2 = s.go(Action.DeleteTask(tid), s.now + 1000)
            eq("flat", Sync.outgoing(s2.data).single { it.key == "task:$tid" }.spaceId, "tombstone to the space")
            val left = s.go(Action.SpacesChanged(emptyList()))
            ok(left.data.tasks.isEmpty(), "space tasks leave with the space"); ok(Sync.outgoing(left.data).none { it.deleted }, "…without deleting them for others")
        },

        "google tasks: plan both directions, conflicts, deletes" to {
            var ids = 0
            val gen = { "g${++ids}" }
            var d = Defaults.data().copy(gLastSync = 0, tasks = listOf(
                Task("l1", "local only"), Task("l2", "linked", googleId = "G2"), Task("l3", "done local", done = true),
                Task("s1", "shared", spaceId = "flat"),
            ))
            val remote = listOf(GTasksPlan.GTask("G1", "from google", "", false, 1000), GTasksPlan.GTask("G2", "linked", "", false, 1000))
            var r = GTasksPlan.plan(d, remote, gen)
            eq(listOf("from google"), r.localUpserts.map { it.title }, "new from Google")
            eq(listOf("l1"), r.remoteCreates.map { it.id }, "local → Google (not done, not shared)")
            ok(r.remoteUpdates.isEmpty() && r.localDeletes.isEmpty(), "linked + same → nothing")
            d = GTasksPlan.applyLocal(d, r, 2000, mapOf("l1" to "G9"))
            eq("G9", d.tasks.first { it.id == "l1" }.googleId, "link stored"); eq(2000L, d.gLastSync, "sync time")
            // both sides edit l2; Google's edit is newer
            d = d.copy(meta = d.meta + ("task:l2" to 2500L), tasks = d.tasks.map { if (it.id == "l2") it.copy(title = "local edit") else it })
            val r2 = GTasksPlan.plan(d, listOf(GTasksPlan.GTask("G2", "google edit", "", false, 3000), GTasksPlan.GTask("G9", "local only", "", false, 2000)), gen)
            eq("google edit", r2.localUpserts.single().title, "newer Google edit wins")
            val r3 = GTasksPlan.plan(d, listOf(GTasksPlan.GTask("G2", "linked", "", true, 1500), GTasksPlan.GTask("G9", "local only", "", false, 2000)), gen)
            eq("G2", r3.remoteUpdates.single().first, "newer local edit goes to Google")
            // deleted at Google since last sync → delete here
            val r4 = GTasksPlan.plan(d, listOf(GTasksPlan.GTask("G2", "x", "", false, 1000)), gen)
            ok("l1" in r4.localDeletes, "removed at Google → removed here")
            // deleted here → deleted at Google, once
            var st = Reducer.initial(d, t(5, 12), env).go(Action.DeleteTask("l2"))
            ok("G2" in st.data.gDeletes, "queued for Google")
            val r5 = GTasksPlan.plan(st.data, listOf(GTasksPlan.GTask("G2", "linked", "", false, 1000), GTasksPlan.GTask("G9", "local only", "", false, 2000)), gen)
            eq(setOf("G2"), r5.remoteDeletes, "delete at Google"); ok(r5.localUpserts.none { it.title == "linked" }, "not re-imported")
            st = st.go(Action.ApplyGTasks(r5, 4000))
            ok(st.data.gDeletes.isEmpty(), "queue cleared: ${st.data.gDeletes} handled=${r5.handledGDeletes}")
        },

        "onboarding: fresh install starts in setup; skip lands on the dashboard" to {
            var s = Reducer.initial(Defaults.data(), t(5, 12), env)
            ok(s.ui.screen is Screen.Onboarding, "setup first")
            s = s.go(Action.OnbSkip); eq(Screen.Dashboard, s.ui.screen, "skipped"); ok(s.data.onboarded, "remembered")
            ok(Reducer.initial(Codec.decode(Codec.encode(s.data)), t(5, 13), env).ui.screen == Screen.Dashboard, "not asked again")
            val v2 = Codec.decode("""{"schema":2,"modes":[],"routines":[]}""")
            ok(v2.onboarded, "upgrades from v0.2/v0.3 are not sent through setup")
        },

        "onboarding: answers shape the dashboard and the day" to {
            var s = Reducer.initial(Defaults.data(), t(5, 12), env)
            val a = OnboardingAnswers(use = DeviceUse.PHONE, profile = Profile.SIMPLE, needs = setOf(Need.WATER, Need.CALM),
                worksWeekdays = true, wake = 6 * 60 + 30, workStart = 9 * 60, workEnd = 17 * 60 + 30, sleep = 30)  // 00:30
            s = s.go(Action.OnbUpdate(a)); repeat(7) { s = s.go(Action.OnbNext) }
            eq(Screen.Dashboard, s.ui.screen, "finished after last step")
            val f = s.data.prefs.features
            eq(Onboarding.SIMPLE_CORE + setOf(Feature.WATER, Feature.OVERWHELMED), f, "simple + what was asked for")
            ok(!s.data.prefs.keepScreenOn && !s.data.prefs.fullscreen, "phone behaviour")
            eq(6 * 60 + 30 to 9 * 60, s.data.modes.first { it.id == "morning" }.let { it.start to it.end }, "morning")
            eq(17 * 60 + 30 to 30, s.data.modes.first { it.id == "evening" }.let { it.start to it.end }, "evening to 00:30")
            eq(0, Scheduler.conflicts(s.data.modes).size, "no overlaps")
            var tm = lt(5, 0)
            while (tm.isBefore(lt(12, 0))) { ok(Scheduler.resolve(s.data.modes, tm) is ScheduleResult.Active, "gap at $tm"); tm = tm.plusMinutes(15) }
            eq("bedtime", modeAt(s.data.modes, 6, 0, 45), "after midnight → bedtime")
            eq(NotifyStyle.NONE, s.data.routines.first { it.id == "r_morning" }.schedule?.notify, "no routine nagging unless asked")
        },

        "breather bubble: on in every preset, and switched on for older installs" to {
            Profile.values().forEach { ok(Feature.OVERWHELMED in Onboarding.preset(it), "in ${it.name}") }
            val old = Codec.decode("""{"schema":3,"prefs":{"features":["NOW"]}}""")
            eq(setOf(Feature.NOW, Feature.OVERWHELMED, Feature.MOCHI), old.prefs.features, "migrated")
            eq(setOf(Feature.NOW, Feature.MOCHI), Codec.decode("""{"schema":4,"prefs":{"features":["NOW"]}}""").prefs.features, "v4 gets Mochi")
            Profile.values().forEach { ok(Feature.MOCHI in Onboarding.preset(it), "Mochi in ${it.name}") }
            val cur = Codec.decode(Codec.encode(old.copy(prefs = old.prefs.copy(features = setOf(Feature.NOW)))))
            eq(setOf(Feature.NOW), cur.prefs.features, "turning it off sticks")
        },
        "greeting name: nickname wins, else account first name, and it syncs" to {
            val acct = Account("u1", "a@b.c", "Sakshi Gupta")
            eq("Sakshi", displayName(Prefs(), acct), "account first name")
            eq("Saku", displayName(Prefs(nickname = " Saku "), acct), "nickname wins")
            eq(null, displayName(Prefs(), null), "no name")
            var s = Reducer.initial(Defaults.data(), t(5, 12), env)
            s = s.go(Action.OnbUpdate(OnboardingAnswers(nickname = "Riya"))); repeat(7) { s = s.go(Action.OnbNext) }
            eq("Riya", s.data.prefs.nickname, "setup saves it")
            ok("nickname" in Sync.SYNCED_PREFS, "follows the account")
            eq("Riya", Codec.decode(Codec.encode(s.data)).prefs.nickname, "survives save/load")
        },
        "onboarding: no fixed work week, invalid times are refused" to {
            var s = Reducer.initial(Defaults.data(), t(5, 12), env)
            s = s.go(Action.OnbUpdate(OnboardingAnswers(worksWeekdays = false)))
            repeat(3) { s = s.go(Action.OnbNext) }
            eq(OnbStep.DAY, (s.ui.screen as Screen.Onboarding).current, "on the day step")
            val bad = (s.ui.screen as Screen.Onboarding).answers.copy(workStart = 6 * 60, wake = 8 * 60)
            s = s.go(Action.OnbUpdate(bad)).go(Action.OnbNext)
            eq(OnbStep.DAY, (s.ui.screen as Screen.Onboarding).current, "blocked"); ok(s.ui.message!!.contains("order"), "explains")
            s = s.go(Action.OnbUpdate(OnboardingAnswers(worksWeekdays = false))).go(Action.OnbNext).go(Action.OnbNext)
            ok(!s.data.modes.first { it.id == "work" }.scheduled, "work becomes manual")
            eq((1..7).toSet(), s.data.modes.first { it.id == "dayoff" }.days, "every day is a day-off shape")
            eq(0, Scheduler.conflicts(s.data.modes).size, "still no overlaps")
            s = s.go(Action.Back); eq(Screen.Dashboard, s.ui.screen, "back on dashboard does nothing")
        },

        "onboarding: signing in to an existing account only asks device questions" to {
            var s = Reducer.initial(Defaults.data(), t(5, 12), env)
            s = s.go(Action.AccountChanged(Account("u", "e", "Sakshi"), true))
            val remoteMode = Sync.Doc("mode:work", t(5, 11), false, Json.write(Codec.modeOut(Defaults.modes()[1].copy(name = "Office"))))
            s = s.go(Action.ApplyRemote(listOf(remoteMode)))
            val sc = s.ui.screen as Screen.Onboarding
            ok(sc.returning, "returning"); eq(OnbStep.DEVICE, sc.current, "moved past welcome")
            eq(listOf(OnbStep.WELCOME, OnbStep.DEVICE, OnbStep.NEEDS, OnbStep.READY), sc.steps, "short path")
            s = s.go(Action.OnbUpdate(sc.answers.copy(profile = Profile.EVERYTHING))).go(Action.OnbNext).go(Action.OnbNext).go(Action.OnbNext).go(Action.OnbNext)
            eq(Screen.Dashboard, s.ui.screen, "done")
            eq("Office", s.data.modes.first { it.id == "work" }.name, "synced setup untouched")
            eq(Feature.values().toSet(), s.data.prefs.features, "device shows everything")
            ok(Sync.outgoing(s.data).none { it.key == "prefs" && it.json.contains("features") }, "features stay on this device")
        },

        "onboarding: account data arriving late still protects the synced setup" to {
            var s = Reducer.initial(Defaults.data(), t(5, 12), env)
            s = s.go(Action.AccountChanged(Account("u", "e", "Sakshi"), true))
            repeat(3) { s = s.go(Action.OnbNext) }   // impatient: already on the day step
            eq(OnbStep.DAY, (s.ui.screen as Screen.Onboarding).current, "on day step")
            val remoteMode = Sync.Doc("mode:work", t(5, 11), false, Json.write(Codec.modeOut(Defaults.modes()[1].copy(start = 8 * 60))))
            s = s.go(Action.ApplyRemote(listOf(remoteMode)))
            val sc = s.ui.screen as Screen.Onboarding
            ok(sc.returning, "switched to returning"); eq(OnbStep.READY, sc.current, "skips the day questions")
            s = s.go(Action.OnbNext)
            eq(8 * 60, s.data.modes.first { it.id == "work" }.start, "synced work time untouched")
        },

        "music: provider choice is per device, set in setup, and survives restarts" to {
            var s = Reducer.initial(Defaults.data(), t(5, 12), env)
            val a = OnboardingAnswers(music = MusicProvider.APP, musicAppPackage = "com.google.android.apps.youtube.music", musicAppName = "YT Music")
            s = s.go(Action.OnbUpdate(a)); repeat(7) { s = s.go(Action.OnbNext) }
            val m = s.data.prefs.music
            eq(MusicProvider.APP, m.provider, "provider"); eq("YT Music", m.appName, "app")
            s = s.go(Action.SetMusic(m.copy(provider = MusicProvider.BUILT_IN, ambient = Ambient.OCEAN, sleepTimerMin = 45)), s.now + 1000)
            eq(s.data.prefs.music, Codec.decode(Codec.encode(s.data)).prefs.music, "round trip")
            ok(Sync.outgoing(s.data).none { it.key == "prefs" && it.json.contains("OCEAN") }, "music choice stays on this device")
            eq(MusicPrefs(), Codec.decode("""{"schema":3,"prefs":{}}""").prefs.music, "default: calm sounds")
            eq(OnbStep.NEEDS, Onboarding.steps(true)[Onboarding.returningStepFor(OnbStep.NEEDS)], "late merge on needs stays there (music)")
            eq(5, Onboarding.steps(false).size, "five screens")
        },

        "finishing things celebrates (tasks, routines, focus) — unticking doesn't" to {
            var s = st0().go(Action.Capture("x"))
            val id = s.data.tasks.single().id
            ok(Effect.Celebrate in Reducer.reduce(s, Action.ToggleTaskDone(id), s.now, env).effects, "task")
            s = s.go(Action.ToggleTaskDone(id))
            ok(Effect.Celebrate !in Reducer.reduce(s, Action.ToggleTaskDone(id), s.now, env).effects, "untick")
            var r = st0().go(Action.StartRoutine("r_hydrate")); repeat(3) { r = r.go(Action.RoutineDone) }
            ok(Effect.Celebrate in Reducer.reduce(r, Action.FinishRoutine, r.now, env).effects, "routine")
            ok(Effect.Celebrate !in Reducer.reduce(st0().go(Action.StartRoutine("r_hydrate")), Action.ExitRoutine, s.now, env).effects, "exit early")
            val f = st0().go(Action.StartFocus(null, 10))
            ok(Effect.Celebrate in Reducer.reduce(f, Action.EndFocus(false), f.now + 11 * 60_000, env).effects, "focus finished")
        },

        "features gate heads-up and routine prompts" to {
            var s = st0(5, 18).go(Action.SetFeatures(Onboarding.SIMPLE_CORE))
            val r = Reducer.reduce(s, Action.Tick, t(5, 18, 25), env)
            ok(!r.effects.contains(Effect.Chime), "no chime when heads-up is off"); eq(null, Derive.transitionSoon(r.state, env), "no card")
            s = Reducer.initial(Defaults.data().copy(onboarded = true, prefs = Prefs(features = Onboarding.SIMPLE_CORE)), t(5, 7, 31), env)
            eq(0, Derive.prompts(s, env).size, "no routine prompt when off")
            eq(Profile.SIMPLE, Onboarding.profileOf(Onboarding.SIMPLE_CORE), "preset detection")
            eq(null, Onboarding.profileOf(Onboarding.SIMPLE_CORE + Feature.WATER), "custom mix")
        },

        "google tasks: choosing one list unlinks and only copies new tasks" to {
            var s = st0().go(Action.Capture("old task"))
            s = s.copy(data = s.data.copy(tasks = s.data.tasks.map { it.copy(googleId = "X1") }))
            s = s.go(Action.ChooseGList("L2", "Control Panel", copyExisting = false), s.now + 1000)
            eq("L2", s.data.prefs.gListId, "list chosen"); ok(s.data.tasks.all { it.googleId == null }, "unlinked")
            ok(s.data.gTasksOn && s.data.gLastSync == 0L, "fresh sync")
            s = s.go(Action.Capture("new task"), s.now + 2000)
            val plan = GTasksPlan.plan(s.data, emptyList()) { "n" }
            eq(listOf("new task"), plan.remoteCreates.map { it.title }, "only tasks made after choosing")
            val all = s.go(Action.ChooseGList("L2", "Control Panel", copyExisting = true), s.now + 3000)
            eq(2, GTasksPlan.plan(all.data, emptyList()) { "n" }.remoteCreates.size, "copy existing too")
            ok(Sync.outgoing(s.data).any { it.key == "prefs" && it.json.contains("L2") }, "list choice syncs to other devices")
        },
    )

    /** Runs everything; returns failure messages (empty = all passed). */
    fun runAll(log: (String) -> Unit = ::println): List<String> {
        val failures = ArrayList<String>()
        for ((name, body) in tests) {
            try { body(); log("  ✓ $name") } catch (e: Throwable) { failures += "$name — ${e.message}"; log("  ✗ $name\n      ${e.message}") }
        }
        log("${tests.size - failures.size}/${tests.size} passed")
        return failures
    }
}
