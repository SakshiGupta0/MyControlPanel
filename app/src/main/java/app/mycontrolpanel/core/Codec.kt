package app.mycontrolpanel.core

/**
 * AppData <-> JSON. Decoding is defensive: any missing/odd field falls back to a sane default,
 * so an older or partially-written file never crashes the app. A file that is not JSON at all
 * throws, and the repository then keeps a backup and starts from defaults with a visible message.
 */
object Codec {

    fun encode(d: AppData): String = Json.write(mapOf(
        "schema" to d.schema,
        "modes" to d.modes.map(::modeOut),
        "routines" to d.routines.map(::routineOut),
        "prefs" to prefsOut(d.prefs),
        "override" to d.override?.let { mapOf("modeId" to it.modeId, "until" to it.until) },
        "daily" to dailyOut(d.daily),
        "run" to d.run?.let(::runOut),
        "lastModeKey" to d.lastModeKey,
        "modeEnteredAt" to d.modeEnteredAt,
    ))

    fun decode(text: String): AppData {
        val root = Json.parse(text) as? Map<*, *> ?: throw Json.ParseException("Root is not an object")
        val defaults = Defaults.data()
        val modes = (root["modes"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.let(::modeIn) } ?: defaults.modes
        val routines = (root["routines"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.let(::routineIn) } ?: defaults.routines
        return AppData(
            schema = root.int("schema", 1),
            modes = modes,
            routines = routines,
            prefs = (root["prefs"] as? Map<*, *>)?.let(::prefsIn) ?: Prefs(),
            override = (root["override"] as? Map<*, *>)?.let { m ->
                m.str("modeId")?.let { Override(it, m.long("until")) }
            },
            daily = (root["daily"] as? Map<*, *>)?.let(::dailyIn) ?: Daily(),
            run = (root["run"] as? Map<*, *>)?.let(::runIn),
            lastModeKey = root.str("lastModeKey"),
            modeEnteredAt = root.long("modeEnteredAt"),
        )
    }

    // ---- modes ----
    private fun modeOut(m: Mode) = mapOf(
        "id" to m.id, "name" to m.name, "icon" to m.icon, "start" to m.start, "end" to m.end,
        "days" to m.days.sorted(), "scheduled" to m.scheduled,
        "widgets" to m.widgets.map { mapOf("w" to it.widget.name, "on" to it.enabled) },
        "featured" to m.featuredRoutineId, "order" to m.order, "winsOver" to m.winsOver.toList(),
    )

    private fun modeIn(m: Map<*, *>): Mode? {
        val id = m.str("id") ?: return null
        val slots = (m["widgets"] as? List<*>)?.mapNotNull { w ->
            val mw = w as? Map<*, *> ?: return@mapNotNull null
            val widget = ModeWidget.values().find { it.name == mw.str("w") } ?: return@mapNotNull null
            WidgetSlot(widget, mw.bool("on", true))
        } ?: emptyList()
        return Mode(
            id = id,
            name = m.str("name") ?: "Mode",
            icon = m.str("icon") ?: "⭐",
            start = m.int("start", 0).coerceIn(0, DAY_MIN - 1),
            end = m.int("end", 60).coerceIn(0, DAY_MIN - 1),
            days = m.days("days"),
            scheduled = m.bool("scheduled", true),
            widgets = normalizeSlots(slots),
            featuredRoutineId = m.str("featured"),
            order = m.int("order", 0),
            winsOver = (m["winsOver"] as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet(),
        )
    }

    // ---- routines ----
    private fun routineOut(r: Routine) = mapOf(
        "id" to r.id, "name" to r.name, "description" to r.description, "icon" to r.icon,
        "steps" to r.steps.map { s ->
            mapOf("id" to s.id, "title" to s.title, "instruction" to s.instruction, "duration" to s.durationSec,
                "timer" to s.timerEnabled, "auto" to s.autoAdvance, "alert" to s.alert)
        },
        "modeIds" to r.modeIds.toList(),
        "schedule" to r.schedule?.let { mapOf("time" to it.time, "days" to it.days.sorted(), "notify" to it.notify.name) },
        "quick" to r.quick, "enabled" to r.enabled, "order" to r.order,
    )

    private fun routineIn(m: Map<*, *>): Routine? {
        val id = m.str("id") ?: return null
        val steps = (m["steps"] as? List<*>)?.mapNotNull { st ->
            val s = st as? Map<*, *> ?: return@mapNotNull null
            Step(
                id = s.str("id") ?: return@mapNotNull null,
                title = s.str("title") ?: "Step",
                instruction = s.str("instruction") ?: "",
                durationSec = s.int("duration", 0).coerceIn(0, 24 * 3600),
                timerEnabled = s.bool("timer", false),
                autoAdvance = s.bool("auto", false),
                alert = s.bool("alert", true),
            )
        } ?: emptyList()
        val sched = (m["schedule"] as? Map<*, *>)?.let { s ->
            RoutineSchedule(
                time = s.int("time", 0).coerceIn(0, DAY_MIN - 1),
                days = s.days("days"),
                notify = NotifyStyle.values().find { it.name == s.str("notify") } ?: NotifyStyle.PROMPT,
            )
        }
        return Routine(
            id = id, name = m.str("name") ?: "Routine", description = m.str("description") ?: "",
            icon = m.str("icon") ?: "✨", steps = steps,
            modeIds = (m["modeIds"] as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet(),
            schedule = sched, quick = m.bool("quick", false), enabled = m.bool("enabled", true), order = m.int("order", 0),
        )
    }

    // ---- prefs / daily / run ----
    private fun prefsOut(p: Prefs) = mapOf(
        "use24h" to p.use24h, "waterTarget" to p.waterTarget, "glassMl" to p.glassMl, "mealNames" to p.mealNames,
        "breakIntervalMin" to p.breakIntervalMin, "textScale" to p.textScale, "highContrast" to p.highContrast,
        "reduceMotion" to p.reduceMotion, "keepScreenOn" to p.keepScreenOn, "fullscreen" to p.fullscreen,
        "nightDim" to p.nightDim, "dimModeIds" to p.dimModeIds.toList(), "sleepAudioUri" to p.sleepAudioUri,
    )

    private fun prefsIn(m: Map<*, *>): Prefs {
        val d = Prefs()
        return Prefs(
            use24h = m.bool("use24h", d.use24h),
            waterTarget = m.int("waterTarget", d.waterTarget).coerceIn(1, 30),
            glassMl = m.int("glassMl", d.glassMl).coerceIn(50, 2000),
            mealNames = (m["mealNames"] as? List<*>)?.mapNotNull { it as? String }?.takeIf { it.isNotEmpty() } ?: d.mealNames,
            breakIntervalMin = m.int("breakIntervalMin", d.breakIntervalMin).coerceIn(10, 240),
            textScale = (m.dbl("textScale") ?: d.textScale.toDouble()).toFloat().coerceIn(0.8f, 1.6f),
            highContrast = m.bool("highContrast", d.highContrast),
            reduceMotion = m.bool("reduceMotion", d.reduceMotion),
            keepScreenOn = m.bool("keepScreenOn", d.keepScreenOn),
            fullscreen = m.bool("fullscreen", d.fullscreen),
            nightDim = m.bool("nightDim", d.nightDim),
            dimModeIds = (m["dimModeIds"] as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: d.dimModeIds,
            sleepAudioUri = m.str("sleepAudioUri") ?: "",
        )
    }

    private fun dailyOut(d: Daily) = mapOf(
        "date" to d.date, "water" to d.water, "meals" to d.mealsDone.sorted(), "lastBreakAt" to d.lastBreakAt,
        "prompted" to d.prompted.toList(), "dismissed" to d.dismissed.toList(),
    )

    private fun dailyIn(m: Map<*, *>) = Daily(
        date = m.str("date") ?: "",
        water = m.int("water", 0).coerceIn(0, 99),
        mealsDone = (m["meals"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.toSet() ?: emptySet(),
        lastBreakAt = m.long("lastBreakAt"),
        prompted = (m["prompted"] as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet(),
        dismissed = (m["dismissed"] as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet(),
    )

    private fun runOut(r: RoutineRun) = mapOf(
        "routineId" to r.routineId, "index" to r.index, "elapsedMs" to r.elapsedMs, "runningSince" to r.runningSince,
        "alerted" to r.alerted, "completed" to r.completed, "startedAt" to r.startedAt,
    )

    private fun runIn(m: Map<*, *>): RoutineRun? = m.str("routineId")?.let { id -> RoutineRun(
        routineId = id,
        index = m.int("index", 0).coerceAtLeast(0),
        elapsedMs = m.long("elapsedMs") ?: 0,
        runningSince = m.long("runningSince"),
        alerted = m.bool("alerted", false),
        completed = m.bool("completed", false),
        startedAt = m.long("startedAt") ?: 0,
    ) }

    // ---- defensive accessors ----
    private fun Map<*, *>.str(k: String) = this[k] as? String
    private fun Map<*, *>.int(k: String, def: Int) = (this[k] as? Number)?.toInt() ?: def
    private fun Map<*, *>.long(k: String) = (this[k] as? Number)?.toLong()
    private fun Map<*, *>.dbl(k: String) = (this[k] as? Number)?.toDouble()
    private fun Map<*, *>.bool(k: String, def: Boolean) = this[k] as? Boolean ?: def
    private fun Map<*, *>.days(k: String) =
        (this[k] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.filter { it in 1..7 }?.toSet() ?: emptySet()
}
