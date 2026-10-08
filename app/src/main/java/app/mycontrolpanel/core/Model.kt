package app.mycontrolpanel.core

// ---------------------------------------------------------------------------
// Pure domain model. NOTHING in the `core` package may import android.*:
// it is compiled and tested on a plain JVM (see CoreSuite).
// ---------------------------------------------------------------------------

/** Mode-specific dashboard widgets. Persistent widgets (clock, water, meals, calendar) are not in here. */
enum class ModeWidget(val label: String) {
    ROUTINES("Routines for this mode"),
    NEXT_MODE("Coming up next"),
    BREAK_REMINDER("Break reminder"),
    SLEEP_AUDIO("Sleep audio"),
    TASKS("Tasks"),
    CALENDAR("Calendar"),
}

data class WidgetSlot(val widget: ModeWidget, val enabled: Boolean)

/**
 * A phase of the day.
 * start/end are minutes since midnight. end < start means the mode crosses midnight;
 * in that case [days] refers to the day the mode STARTS on (Bedtime Mon 23:00 → Tue 07:00 belongs to Monday).
 * [scheduled] = participates in automatic scheduling. Every mode can always be chosen manually.
 */
data class Mode(
    val id: String,
    val name: String,
    val icon: String,
    val start: Int,
    val end: Int,
    val days: Set<Int>,          // ISO day-of-week: 1 = Monday … 7 = Sunday
    val scheduled: Boolean = true,
    val widgets: List<WidgetSlot> = defaultWidgetSlots(emptyList()),
    val featuredRoutineId: String? = null,
    val order: Int = 0,
    /** Explicit conflict resolution: when this mode overlaps a mode listed here, this mode wins. */
    val winsOver: Set<String> = emptySet(),
)

/** Always returns every ModeWidget exactly once: the given ones (in order) first, the rest appended disabled. */
fun defaultWidgetSlots(enabledInOrder: List<ModeWidget>): List<WidgetSlot> =
    enabledInOrder.distinct().map { WidgetSlot(it, true) } +
        ModeWidget.values().filter { it !in enabledInOrder }.map { WidgetSlot(it, false) }

fun normalizeSlots(slots: List<WidgetSlot>): List<WidgetSlot> {
    val seen = LinkedHashMap<ModeWidget, WidgetSlot>()
    slots.forEach { if (it.widget !in seen) seen[it.widget] = it }
    ModeWidget.values().forEach { if (it !in seen) seen[it] = WidgetSlot(it, false) }
    return seen.values.toList()
}

data class Step(
    val id: String,
    val title: String,
    val instruction: String = "",
    val durationSec: Int = 0,
    val timerEnabled: Boolean = false,
    val autoAdvance: Boolean = false,
    val alert: Boolean = true,
) {
    val hasTimer: Boolean get() = timerEnabled && durationSec > 0
}

enum class NotifyStyle(val label: String) {
    NONE("Silent — no reminder"),
    PROMPT("Gentle card on the dashboard"),
    NOTIFY("Dashboard card + quiet notification"),
}

data class RoutineSchedule(val time: Int, val days: Set<Int>, val notify: NotifyStyle = NotifyStyle.PROMPT)

data class Routine(
    val id: String,
    val name: String,
    val description: String = "",
    val icon: String = "✨",
    val steps: List<Step> = emptyList(),
    val modeIds: Set<String> = emptySet(),
    val schedule: RoutineSchedule? = null,
    val quick: Boolean = false,
    val enabled: Boolean = true,
    val order: Int = 0,
)

data class Prefs(
    val use24h: Boolean = false,
    val waterTarget: Int = 8,          // glasses
    val glassMl: Int = 250,
    val mealNames: List<String> = listOf("Breakfast", "Lunch", "Dinner"),
    val breakIntervalMin: Int = 50,
    val textScale: Float = 1.0f,
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    val keepScreenOn: Boolean = true,
    val fullscreen: Boolean = true,
    val nightDim: Boolean = true,
    val dimModeIds: Set<String> = setOf("bedtime"),
    val sleepAudioUri: String = "",
)

/** Manual mode override. until = epoch millis, null = until the user returns to schedule. */
data class Override(val modeId: String, val until: Long?)

/** Per-day counters; reset automatically when the date changes. */
data class Daily(
    val date: String = "",            // ISO yyyy-MM-dd
    val water: Int = 0,
    val mealsDone: Set<Int> = emptySet(),
    val lastBreakAt: Long? = null,
    val prompted: Set<String> = emptySet(),   // routine ids already prompted today
    val dismissed: Set<String> = emptySet(),  // routine prompts the user dismissed today
)

/** A routine in progress. Timer = elapsedMs (banked) + time since runningSince (if running). */
data class RoutineRun(
    val routineId: String,
    val index: Int = 0,
    val elapsedMs: Long = 0,
    val runningSince: Long? = null,
    val alerted: Boolean = false,
    val completed: Boolean = false,
    val startedAt: Long = 0,
)

/** Everything that is persisted to disk. */
data class AppData(
    val schema: Int = 1,
    val modes: List<Mode>,
    val routines: List<Routine>,
    val prefs: Prefs = Prefs(),
    val override: Override? = null,
    val daily: Daily = Daily(),
    val run: RoutineRun? = null,
    val lastModeKey: String? = null,     // effective mode key last seen by the tick ("free" / mode id / "conflict")
    val modeEnteredAt: Long? = null,
)
