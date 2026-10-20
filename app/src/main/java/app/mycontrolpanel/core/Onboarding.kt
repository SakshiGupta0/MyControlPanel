package app.mycontrolpanel.core

/** Things the dashboard can show. Chosen per device (a phone and a wall panel want different amounts). */
enum class Feature(val label: String, val hint: String) {
    NOW("Right now card", "One thing to do next"),
    TODAY("Today's list", "A short list for today"),
    BRAIN_DUMP("Brain dump button", "Get thoughts out of your head fast"),
    TIME_LEFT("Time left in this mode", "A bar showing how much of the phase is left"),
    HEADS_UP("Heads-up before changes", "Gentle card + chime before the next mode"),
    NEXT_EVENT("Next calendar event", "Under the clock"),
    ROUTINE_PROMPTS("Routine reminders", "“It's time for…” cards"),
    QUICK_ROUTINES("Quick routines button", "Short routines any time"),
    MODE_WIDGETS("Mode widgets", "Tasks, routines, focus, calendar, music per mode"),
    WATER("Water", "Glasses through the day"),
    MEALS("Meals", "Breakfast, lunch, dinner"),
    DONE_TALLY("Done today", "A gentle count of what you finished"),
    OVERWHELMED("Breather bubble", "A floating bubble on every screen: tap it to breathe when it's a lot"),
}

enum class Profile(val label: String, val blurb: String) {
    SIMPLE("Super simple", "Clock, one thing to do, a short list. Nothing else."),
    BALANCED("Balanced", "The essentials plus the things you said help."),
    EVERYTHING("Everything", "Track it all — water, meals, breaks, tallies, widgets."),
}

enum class DeviceUse(val label: String, val blurb: String) {
    PANEL("A panel on my wall or desk", "Always on, full screen, dims at night"),
    PHONE("My phone", "Check in on the go; normal screen behaviour"),
}

enum class Need(val label: String, val icon: String) {
    START_TASKS("Getting started on tasks", "🚀"),
    ROUTINES("Remembering routines", "🔁"),
    TIME_BLINDNESS("Losing track of time", "⏳"),
    CALM("Calming down when it's a lot", "🫧"),
    FOCUS("Staying focused", "🎯"),
    BREAKS("Taking breaks while working", "☕"),
    WATER("Drinking enough water", "💧"),
    MEALS("Eating regularly", "🍽️"),
    SLEEP("Winding down for sleep", "🌙"),
}

data class OnboardingAnswers(
    val use: DeviceUse = DeviceUse.PANEL,
    val profile: Profile = Profile.BALANCED,
    val needs: Set<Need> = setOf(Need.START_TASKS, Need.ROUTINES, Need.TIME_BLINDNESS),
    val worksWeekdays: Boolean = true,
    val wake: Int = 7 * 60,
    val workStart: Int = 10 * 60,
    val workEnd: Int = 18 * 60 + 30,
    val sleep: Int = 23 * 60,
    val music: MusicProvider = MusicProvider.BUILT_IN,
    val musicAppPackage: String = "",
    val musicAppName: String = "",
    val nickname: String = "",
)

/** Five friendly screens: hello + name, this device + how much, what helps + music, your day, all set. */
enum class OnbStep { WELCOME, DEVICE, NEEDS, DAY, READY }

/** The name to greet someone by: their chosen nickname, else the account's first name, else nothing. */
fun displayName(p: Prefs, account: Account?): String? =
    p.nickname.trim().ifBlank { account?.name?.trim()?.substringBefore(' ') ?: "" }.takeIf { it.isNotBlank() }

object Onboarding {
    val SIMPLE_CORE = setOf(Feature.NOW, Feature.TODAY, Feature.BRAIN_DUMP, Feature.OVERWHELMED)
    val BALANCED_CORE = SIMPLE_CORE + setOf(Feature.TIME_LEFT, Feature.NEXT_EVENT, Feature.MODE_WIDGETS, Feature.QUICK_ROUTINES)

    fun preset(p: Profile): Set<Feature> = when (p) {
        Profile.SIMPLE -> SIMPLE_CORE
        Profile.BALANCED -> BALANCED_CORE + setOf(Feature.HEADS_UP, Feature.ROUTINE_PROMPTS, Feature.WATER, Feature.MEALS, Feature.OVERWHELMED)
        Profile.EVERYTHING -> Feature.values().toSet()
    }

    /** Which preset (if any) a feature set matches exactly. */
    fun profileOf(f: Set<Feature>): Profile? = Profile.values().firstOrNull { preset(it) == f }

    fun steps(returning: Boolean): List<OnbStep> =
        if (returning) listOf(OnbStep.WELCOME, OnbStep.DEVICE, OnbStep.NEEDS, OnbStep.READY) else OnbStep.values().toList()

    /** Where to land in the short (returning-account) path from a step of the full path. */
    fun returningStepFor(current: OnbStep): Int {
        val short = steps(returning = true)
        val target = when (current) { OnbStep.DAY -> OnbStep.READY; else -> current }
        return short.indexOf(target).coerceAtLeast(1)
    }

    fun musicPrefs(p: MusicPrefs, a: OnboardingAnswers): MusicPrefs = p.copy(
        provider = a.music,
        appPackage = if (a.music == MusicProvider.APP) a.musicAppPackage else p.appPackage,
        appName = if (a.music == MusicProvider.APP) a.musicAppName else p.appName,
    )

    fun featuresFor(a: OnboardingAnswers): Set<Feature> {
        if (a.profile == Profile.EVERYTHING) return Feature.values().toSet()
        val base = if (a.profile == Profile.SIMPLE) SIMPLE_CORE else BALANCED_CORE
        val extra = HashSet<Feature>()
        for (n in a.needs) when (n) {
            Need.ROUTINES -> { extra += Feature.ROUTINE_PROMPTS; extra += Feature.QUICK_ROUTINES }
            Need.TIME_BLINDNESS -> { extra += Feature.TIME_LEFT; extra += Feature.HEADS_UP }
            Need.CALM -> extra += Feature.OVERWHELMED
            Need.WATER -> extra += Feature.WATER
            Need.MEALS -> extra += Feature.MEALS
            Need.FOCUS, Need.BREAKS -> if (a.profile != Profile.SIMPLE) extra += Feature.MODE_WIDGETS
            Need.START_TASKS, Need.SLEEP -> {}
        }
        return base + extra
    }

    /** Device-only settings from "how will you use this device". */
    fun devicePrefs(p: Prefs, use: DeviceUse): Prefs = when (use) {
        DeviceUse.PANEL -> p.copy(keepScreenOn = true, fullscreen = true, nightDim = true)
        DeviceUse.PHONE -> p.copy(keepScreenOn = false, fullscreen = false, nightDim = false)
    }

    /** Is the day shape usable? wake < workStart < workEnd, and bedtime after the day ends (may cross midnight). */
    fun validDay(a: OnboardingAnswers): Boolean {
        val dayEnd = a.workEnd
        val sleepLinear = if (a.sleep <= a.wake) a.sleep + DAY_MIN else a.sleep
        return a.wake < a.workStart && a.workStart < a.workEnd && dayEnd < sleepLinear && sleepLinear - a.wake < DAY_MIN
    }

    /**
     * Turn answers into a dashboard. For a returning account (setup already synced) only this device's
     * settings and features change; modes and routines are left exactly as they are.
     */
    fun apply(d: AppData, a: OnboardingAnswers, returning: Boolean): AppData {
        val prefs = devicePrefs(d.prefs, a.use).copy(
            features = if (returning) preset(a.profile) else featuresFor(a),
            music = musicPrefs(d.prefs.music, a),
            nickname = a.nickname.trim().take(30),
        )
        if (returning) return d.copy(prefs = prefs, onboarded = true)

        var modes = d.modes
        if (validDay(a)) {
            val sleepMod = a.sleep % DAY_MIN
            fun set(id: String, f: (Mode) -> Mode) { modes = modes.map { if (it.id == id) f(it) else it } }
            set("morning") { it.copy(start = a.wake, end = a.workStart, scheduled = true) }
            set("evening") { it.copy(start = a.workEnd, end = sleepMod, scheduled = true) }
            set("bedtime") { it.copy(start = sleepMod, end = a.wake, scheduled = true) }
            if (a.worksWeekdays) {
                set("work") { it.copy(start = a.workStart, end = a.workEnd, days = setOf(1, 2, 3, 4, 5), scheduled = true) }
                set("dayoff") { it.copy(start = a.workStart, end = a.workEnd, days = setOf(6, 7), scheduled = true) }
            } else {
                set("work") { it.copy(start = a.workStart, end = a.workEnd, scheduled = false) }
                set("dayoff") { it.copy(start = a.workStart, end = a.workEnd, days = (1..7).toSet(), scheduled = true) }
            }
        }
        // Work-mode widgets follow what helps
        modes = modes.map { m ->
            if (m.id != "work") m else m.copy(widgets = m.widgets.map { s ->
                when (s.widget) {
                    ModeWidget.FOCUS -> s.copy(enabled = Need.FOCUS in a.needs || a.profile == Profile.EVERYTHING)
                    ModeWidget.BREAK_REMINDER -> s.copy(enabled = Need.BREAKS in a.needs || a.profile == Profile.EVERYTHING)
                    else -> s
                }
            })
        }
        // Routine reminders: gentle prompts only if routines help; bedtime prompt follows the new bedtime
        val sleepMod = a.sleep % DAY_MIN
        val routines = d.routines.map { r ->
            when (r.id) {
                "r_morning" -> r.copy(schedule = r.schedule?.copy(time = (a.wake + 30) % DAY_MIN,
                    notify = if (Need.ROUTINES in a.needs) NotifyStyle.PROMPT else NotifyStyle.NONE))
                "r_bedtime" -> r.copy(schedule = r.schedule?.copy(time = sleepMod,
                    notify = if (Need.ROUTINES in a.needs || Need.SLEEP in a.needs) NotifyStyle.PROMPT else NotifyStyle.NONE))
                else -> r
            }
        }
        return d.copy(modes = modes, routines = routines, prefs = prefs, onboarded = true)
    }
}
