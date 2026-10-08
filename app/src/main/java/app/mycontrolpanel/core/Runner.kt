package app.mycontrolpanel.core

/**
 * Routine execution state machine. Pure: (run, routine, now) -> run.
 * The timer is wall-clock based (banked elapsed + time since resume), so it stays correct
 * if the screen sleeps or the process is restored from disk mid-step.
 */
object Runner {

    fun start(r: Routine, now: Long): RoutineRun = enter(RoutineRun(r.id, startedAt = now), r, 0, now)

    private fun enter(run: RoutineRun, r: Routine, index: Int, now: Long): RoutineRun {
        val step = r.steps.getOrNull(index)
        return run.copy(
            index = index,
            elapsedMs = 0,
            runningSince = if (step?.hasTimer == true) now else null, // timers start automatically
            alerted = false,
            completed = false,
        )
    }

    fun elapsedMs(run: RoutineRun, now: Long): Long =
        run.elapsedMs + (run.runningSince?.let { (now - it).coerceAtLeast(0) } ?: 0)

    /** Remaining ms for the current step's timer, or null when the step has no timer. */
    fun remainingMs(run: RoutineRun, r: Routine, now: Long): Long? {
        val step = r.steps.getOrNull(run.index) ?: return null
        if (!step.hasTimer) return null
        return (step.durationSec * 1000L - elapsedMs(run, now)).coerceAtLeast(0)
    }

    fun done(run: RoutineRun, r: Routine, now: Long): RoutineRun =
        if (run.completed) run
        else if (run.index >= r.steps.size - 1) run.copy(completed = true, runningSince = null)
        else enter(run, r, run.index + 1, now)

    fun back(run: RoutineRun, r: Routine, now: Long): RoutineRun =
        if (run.completed || run.index == 0) run else enter(run, r, run.index - 1, now)

    fun pause(run: RoutineRun, now: Long): RoutineRun =
        if (run.runningSince == null) run else run.copy(elapsedMs = elapsedMs(run, now), runningSince = null)

    fun resume(run: RoutineRun, r: Routine, now: Long): RoutineRun {
        if (run.runningSince != null || run.completed) return run
        val step = r.steps.getOrNull(run.index) ?: return run
        if (!step.hasTimer) return run
        // restarting a finished timer starts it over
        return if (remainingMs(run, r, now) == 0L) run.copy(elapsedMs = 0, runningSince = now, alerted = false)
        else run.copy(runningSince = now)
    }

    /** Result of a tick: new run state and whether an alert sound should play now. */
    data class TickResult(val run: RoutineRun, val alert: Boolean)

    fun tick(run: RoutineRun, r: Routine, now: Long): TickResult {
        if (run.completed) return TickResult(run, false)
        val step = r.steps.getOrNull(run.index) ?: return TickResult(run, false)
        val rem = remainingMs(run, r, now) ?: return TickResult(run, false)
        if (rem > 0 || run.alerted || run.runningSince == null) return TickResult(run, false)
        val finished = run.copy(alerted = true, elapsedMs = step.durationSec * 1000L, runningSince = null)
        val next = if (step.autoAdvance) done(finished, r, now) else finished
        return TickResult(next, step.alert)
    }
}
