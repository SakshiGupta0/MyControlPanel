package app.mycontrolpanel.core

import app.mycontrolpanel.core.ModeWidget.*

/** First-launch defaults (PRD §40). Stable ids so featured routines and dim settings line up. */
object Defaults {
    private val ALL = setOf(1, 2, 3, 4, 5, 6, 7)
    private val WEEKDAYS = setOf(1, 2, 3, 4, 5)
    private val WEEKEND = setOf(6, 7)
    private fun hm(h: Int, m: Int = 0) = h * 60 + m

    fun modes(): List<Mode> = listOf(
        Mode("morning", "Morning", "☀️", hm(7), hm(10), ALL,
            widgets = defaultWidgetSlots(listOf(ROUTINES, NEXT_MODE, TASKS)), featuredRoutineId = "r_morning", order = 0),
        Mode("work", "Work", "💼", hm(10), hm(18, 30), WEEKDAYS,
            widgets = defaultWidgetSlots(listOf(TASKS, CALENDAR, BREAK_REMINDER, ROUTINES)), featuredRoutineId = "r_start_work", order = 1),
        Mode("dayoff", "Day Off", "🌿", hm(10), hm(18, 30), WEEKEND,
            widgets = defaultWidgetSlots(listOf(TASKS, ROUTINES, NEXT_MODE)), featuredRoutineId = "r_room_reset", order = 2),
        Mode("evening", "Evening", "🌇", hm(18, 30), hm(23), ALL,
            widgets = defaultWidgetSlots(listOf(ROUTINES, TASKS, NEXT_MODE)), featuredRoutineId = "r_evening", order = 3),
        Mode("bedtime", "Bedtime", "🌙", hm(23), hm(7), ALL,
            widgets = defaultWidgetSlots(listOf(CALENDAR, SLEEP_AUDIO, ROUTINES)), featuredRoutineId = "r_bedtime", order = 4),
    )

    private var stepSeq = 0
    private fun s(title: String, instruction: String = "", min: Int = 0, sec: Int = 0, auto: Boolean = false): Step {
        val d = min * 60 + sec
        return Step("s${++stepSeq}", title, instruction, d, timerEnabled = d > 0, autoAdvance = auto)
    }

    fun routines(): List<Routine> {
        stepSeq = 0
        return listOf(
            Routine("r_morning", "Morning Routine", "Wake up gently and set up the day", "☀️", listOf(
                s("Open the curtains", "Let the daylight in."),
                s("Drink a glass of water", "Before coffee or your phone."),
                s("Stretch", "Neck, shoulders, back. Slow and easy.", min = 5),
                s("Wash up & get dressed"),
                s("Make the bed", "Two minutes. Pillows, duvet, done.", min = 2),
                s("Glance at today", "Check your first event and top task."),
            ), modeIds = setOf("morning"), schedule = RoutineSchedule(hm(7, 30), WEEKDAYS, NotifyStyle.PROMPT), quick = false, order = 0),
            Routine("r_start_work", "Start Work", "Settle in and pick the one thing", "💻", listOf(
                s("Clear the desk", "Only what you need for the next task."),
                s("Fill your water bottle"),
                s("Pick today's top 3", "What would make today a good day?"),
                s("Silence distractions", "Phone face down, notifications off."),
                s("Start the first task", "Just the first 10 minutes.", min = 10),
            ), modeIds = setOf("work"), order = 1),
            Routine("r_lunch", "Lunch", "Step away and actually eat", "🥗", listOf(
                s("Step away from the screen"),
                s("Eat lunch", "No scrolling. Taste the food.", min = 20),
                s("Short walk or stretch", min = 5),
                s("Refill water"),
            ), modeIds = setOf("work", "dayoff"), quick = true, order = 2),
            Routine("r_end_work", "End Work", "Close the loop so work stays at work", "📕", listOf(
                s("Write down open loops", "Anything still in your head goes on paper."),
                s("Pick tomorrow's first task"),
                s("Close all work tabs and apps"),
                s("Tidy the desk", min = 3),
                s("Say it out loud: work is done"),
            ), modeIds = setOf("work"), quick = true, order = 3),
            Routine("r_evening", "Evening", "Wind down the day", "🌇", listOf(
                s("Plan dinner", "Cook, order, or leftovers?"),
                s("10-minute tidy", min = 10),
                s("Lay out tomorrow's clothes"),
                s("Something just for you", "Read, call a friend, a hobby.", min = 30),
            ), modeIds = setOf("evening"), order = 4),
            Routine("r_bedtime", "Bedtime", "Slow down and get to sleep", "🌙", listOf(
                s("Plug in your phone — away from the bed"),
                s("Brush teeth & skincare"),
                s("Dim the lights"),
                s("Check tomorrow's first event"),
                s("Read or breathe", "No screens. A few pages or slow breaths.", min = 10),
                s("Lights out", "Goodnight."),
            ), modeIds = setOf("bedtime"), schedule = RoutineSchedule(hm(23), ALL, NotifyStyle.PROMPT), quick = false, order = 5),
            Routine("r_room_reset", "Room Reset", "Ten minutes to a calmer room", "🧺", listOf(
                s("Clothes to hamper or hangers", min = 3),
                s("Dishes to the kitchen", min = 2),
                s("Clear one surface", min = 3),
                s("Bin anything that's trash", min = 2),
            ), modeIds = setOf("dayoff", "evening"), quick = true, order = 6),
            Routine("r_calm_down", "Calm Down", "When it's all a bit much", "🫧", listOf(
                s("Sit down somewhere comfortable"),
                s("Box breathing", "In 4 · hold 4 · out 4 · hold 4.", min = 2, auto = true),
                s("Name 5 things you can see"),
                s("Drink some water"),
                s("Choose one small next step"),
            ), quick = true, order = 7),
            Routine("r_hydrate", "Hydrate + Reset", "A two-minute reset", "💧", listOf(
                s("Drink a full glass of water"),
                s("Stand up and stretch", min = 1, auto = true),
                s("Look out of the window", sec = 30),
            ), quick = true, order = 8),
            Routine("r_leaving", "Leaving Home", "Don't forget anything", "🔑", listOf(
                s("Phone, wallet, keys"),
                s("Charger / earphones"),
                s("Water bottle"),
                s("Lights and fans off"),
                s("Lock up"),
            ), quick = true, order = 9),
            Routine("r_emergency_bed", "Emergency Bedtime", "Too tired — just the essentials", "😴", listOf(
                s("Brush teeth"),
                s("Phone on charge, away from bed"),
                s("Lights out"),
            ), modeIds = setOf("bedtime"), quick = true, order = 10),
        )
    }

    fun data(): AppData = AppData(modes = modes(), routines = routines())
}
