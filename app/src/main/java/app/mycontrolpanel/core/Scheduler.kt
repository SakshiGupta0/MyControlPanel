package app.mycontrolpanel.core

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

const val DAY_MIN = 24 * 60
const val WEEK_MIN = 7 * DAY_MIN

sealed interface ScheduleResult {
    data class Active(val mode: Mode) : ScheduleResult
    data class Conflict(val modes: List<Mode>) : ScheduleResult
    object Free : ScheduleResult
}

/** What the dashboard shows: schedule result, possibly replaced by a manual override. */
sealed interface Effective {
    val key: String
    data class Scheduled(val mode: Mode) : Effective { override val key get() = mode.id }
    data class Overridden(val mode: Mode, val until: Long?) : Effective { override val key get() = mode.id }
    data class Conflict(val modes: List<Mode>) : Effective { override val key get() = "conflict" }
    object Free : Effective { override val key get() = "free" }
}

fun Effective.modeOrNull(): Mode? = when (this) {
    is Effective.Scheduled -> mode
    is Effective.Overridden -> mode
    else -> null
}

/** One overlap between two modes, expressed on the day it happens. */
data class Conflict(val a: Mode, val b: Mode, val dow: Int, val start: Int, val end: Int)

object Scheduler {

    fun prevDow(dow: Int) = if (dow == 1) 7 else dow - 1

    /** Core rule, including midnight crossing. start == end is invalid (rejected by validation) and never active. */
    fun isActive(m: Mode, dow: Int, minute: Int): Boolean {
        if (!m.scheduled || m.start == m.end) return false
        return if (m.start < m.end) {
            dow in m.days && minute >= m.start && minute < m.end
        } else {
            (dow in m.days && minute >= m.start) || (prevDow(dow) in m.days && minute < m.end)
        }
    }

    fun resolve(modes: List<Mode>, t: LocalDateTime): ScheduleResult {
        val dow = t.dayOfWeek.value
        val minute = t.hour * 60 + t.minute
        val active = modes.filter { isActive(it, dow, minute) }.sortedBy { it.order }
        if (active.isEmpty()) return ScheduleResult.Free
        // A mode is beaten only by an explicit, one-directional "wins over" the user chose.
        val winners = active.filter { a -> active.none { b -> beats(b, a) } }
        return if (winners.size == 1) ScheduleResult.Active(winners[0])
        else ScheduleResult.Conflict(winners.ifEmpty { active }) // never pick one arbitrarily
    }

    fun beats(a: Mode, b: Mode): Boolean = a.id != b.id && b.id in a.winsOver && a.id !in b.winsOver

    /** True when the user has explicitly decided which of the two wins. */
    fun resolved(a: Mode, b: Mode): Boolean = beats(a, b) || beats(b, a)

    fun effective(data: AppData, now: Long, zone: ZoneId): Effective {
        val ov = data.override
        if (ov != null && (ov.until == null || now < ov.until)) {
            val m = data.modes.find { it.id == ov.modeId }
            if (m != null) return Effective.Overridden(m, ov.until)
        }
        return when (val r = resolve(data.modes, toLocal(now, zone))) {
            is ScheduleResult.Active -> Effective.Scheduled(r.mode)
            is ScheduleResult.Conflict -> Effective.Conflict(r.modes)
            ScheduleResult.Free -> Effective.Free
        }
    }

    /** Weekly intervals in "minutes since Monday 00:00", half-open, split at the week wrap. */
    fun weeklyIntervals(m: Mode): List<Pair<Int, Int>> {
        if (!m.scheduled || m.start == m.end) return emptyList()
        val out = ArrayList<Pair<Int, Int>>()
        for (d in m.days.sorted()) {
            val base = (d - 1) * DAY_MIN
            val s = base + m.start
            val e = if (m.end > m.start) base + m.end else base + DAY_MIN + m.end
            if (e <= WEEK_MIN) out += s to e
            else { out += s to WEEK_MIN; out += 0 to (e - WEEK_MIN) }
        }
        return out
    }

    /** Every overlap between any two scheduled modes, split per day so it reads naturally ("Mon 18:00–18:30"). */
    fun conflicts(modes: List<Mode>): List<Conflict> {
        val out = ArrayList<Conflict>()
        val sched = modes.filter { it.scheduled }
        for (i in sched.indices) for (j in i + 1 until sched.size) {
            val a = sched[i]; val b = sched[j]
            if (resolved(a, b)) continue
            for (ia in weeklyIntervals(a)) for (ib in weeklyIntervals(b)) {
                var s = maxOf(ia.first, ib.first)
                val e = minOf(ia.second, ib.second)
                while (s < e) {
                    val dayIdx = s / DAY_MIN
                    val dayEnd = minOf(e, (dayIdx + 1) * DAY_MIN)
                    out += Conflict(a, b, dayIdx + 1, s - dayIdx * DAY_MIN, dayEnd - dayIdx * DAY_MIN)
                    s = dayEnd
                }
            }
        }
        return out.sortedWith(compareBy({ it.dow }, { it.start }))
    }

    /** Conflicts that involve the given mode (used by the mode editor to judge a draft). */
    fun conflictsFor(draft: Mode, others: List<Mode>): List<Conflict> =
        conflicts(others.filter { it.id != draft.id } + draft).filter { it.a.id == draft.id || it.b.id == draft.id }

    /**
     * Next moment (within 8 days) when the schedule result differs from the one at [from].
     * Only start/end boundaries can change the result, so we evaluate those candidates instead of scanning minutes.
     */
    fun nextChange(modes: List<Mode>, from: LocalDateTime): Pair<LocalDateTime, ScheduleResult>? {
        val current = resolve(modes, from)
        val boundaryMinutes = modes.filter { it.scheduled }.flatMap { listOf(it.start, it.end) }.toSortedSet()
        if (boundaryMinutes.isEmpty()) return null
        val startDate = from.toLocalDate()
        val candidates = ArrayList<LocalDateTime>()
        for (d in 0..8) {
            val date = startDate.plusDays(d.toLong())
            for (m in boundaryMinutes) {
                val t = date.atStartOfDay().plusMinutes(m.toLong())
                if (t.isAfter(from)) candidates += t
            }
        }
        candidates.sort()
        for (t in candidates) {
            val r = resolve(modes, t)
            if (!sameResult(r, current)) return t to r
        }
        return null
    }

    fun sameResult(a: ScheduleResult, b: ScheduleResult): Boolean = when {
        a is ScheduleResult.Active && b is ScheduleResult.Active -> a.mode.id == b.mode.id
        a is ScheduleResult.Conflict && b is ScheduleResult.Conflict -> a.modes.map { it.id }.toSet() == b.modes.map { it.id }.toSet()
        else -> a::class == b::class
    }

    /** Next time a routine's schedule fires, strictly after [from]. */
    fun nextRoutineFire(r: Routine, from: LocalDateTime): LocalDateTime? {
        val s = r.schedule ?: return null
        if (!r.enabled || s.days.isEmpty()) return null
        for (d in 0..7) {
            val date: LocalDate = from.toLocalDate().plusDays(d.toLong())
            val t = date.atStartOfDay().plusMinutes(s.time.toLong())
            if (t.isAfter(from) && date.dayOfWeek.value in s.days) return t
        }
        return null
    }

    fun toLocal(epochMs: Long, zone: ZoneId): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), zone)
    fun toEpoch(t: LocalDateTime, zone: ZoneId): Long = t.atZone(zone).toInstant().toEpochMilli()
}

// ---- formatting helpers shared by UI and tests ----
val DAY_SHORT = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

fun fmtMinutes(min: Int, use24h: Boolean): String {
    val m = ((min % DAY_MIN) + DAY_MIN) % DAY_MIN
    val h = m / 60; val mm = m % 60
    if (use24h) return "%02d:%02d".format(h, mm)
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "%d:%02d %s".format(h12, mm, if (h < 12) "AM" else "PM")
}

fun fmtDays(days: Set<Int>): String = when {
    days.size == 7 -> "Every day"
    days == setOf(1, 2, 3, 4, 5) -> "Weekdays"
    days == setOf(6, 7) -> "Weekends"
    days.isEmpty() -> "No days"
    else -> days.sorted().joinToString(" ") { DAY_SHORT[it - 1] }
}

fun describeConflict(c: Conflict, use24h: Boolean): String =
    "${c.a.name} and ${c.b.name} overlap on ${DAY_SHORT[c.dow - 1]} ${fmtMinutes(c.start, use24h)}–${fmtMinutes(c.end, use24h)}"
