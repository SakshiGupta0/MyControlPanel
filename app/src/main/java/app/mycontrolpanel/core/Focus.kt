package app.mycontrolpanel.core

/** Focus timer maths. Pure, wall-clock based (survives sleep and restarts) like the routine Runner. */
object Focus {
    fun start(taskId: String?, label: String, minutes: Int, now: Long) =
        FocusSession(taskId, label, minutes.coerceIn(1, 240) * 60_000L, runningSince = now, startedAt = now)

    fun elapsed(f: FocusSession, now: Long) = f.elapsedMs + (f.runningSince?.let { (now - it).coerceAtLeast(0) } ?: 0)
    fun remaining(f: FocusSession, now: Long) = (f.durationMs - elapsed(f, now)).coerceAtLeast(0)
    fun fraction(f: FocusSession, now: Long) = (remaining(f, now).toFloat() / f.durationMs).coerceIn(0f, 1f)

    fun pause(f: FocusSession, now: Long) = if (f.runningSince == null) f else f.copy(elapsedMs = elapsed(f, now), runningSince = null)
    fun resume(f: FocusSession, now: Long) = if (f.runningSince != null || f.finished) f else f.copy(runningSince = now)

    /** Adds time; also revives a finished session so "+5 min" after the bell keeps you going. */
    fun extend(f: FocusSession, minutes: Int, now: Long): FocusSession {
        val base = if (f.finished) f.copy(finished = false, runningSince = now, elapsedMs = elapsed(f, now).coerceAtMost(f.durationMs)) else f
        return base.copy(durationMs = base.durationMs + minutes * 60_000L)
    }

    /** Returns the new session and whether the bell should ring now. */
    fun tick(f: FocusSession, now: Long): Pair<FocusSession, Boolean> {
        if (f.finished || f.runningSince == null || remaining(f, now) > 0) return f to false
        return f.copy(finished = true, elapsedMs = f.durationMs, runningSince = null) to true
    }
}
