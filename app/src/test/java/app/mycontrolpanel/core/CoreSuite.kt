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

    private fun st0(day: Int = 5, h: Int = 12) = Reducer.initial(Defaults.data(), t(day, h), env)
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
            val late = Reducer.initial(Defaults.data(), t(6, 9, 30), env); eq(0, Derive.prompts(late, env).size, "outside window")
            val weekend = Reducer.initial(Defaults.data(), t(10, 7, 35), env); eq(0, Derive.prompts(weekend, env).size, "weekdays only")
            val started = Reducer.initial(Defaults.data(), t(7, 7, 35), env).go(Action.StartRoutine("r_morning")).go(Action.ExitRoutine)
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
            var s = Reducer.initial(Defaults.data().copy(modes = Defaults.modes() + gym), t(5, 12), env)
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
