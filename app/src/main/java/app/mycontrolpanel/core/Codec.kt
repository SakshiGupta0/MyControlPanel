package app.mycontrolpanel.core

/**
 * AppData <-> JSON. Decoding is defensive: any missing/odd field falls back to a sane default,
 * so an older or partially-written file never crashes the app (v0.1 files load fine).
 * A file that is not JSON at all throws; the repository then keeps a backup and starts from defaults.
 */
object Codec {

    fun encode(d: AppData): String = Json.write(mapOf(
        "schema" to d.schema,
        "modes" to d.modes.map(::modeOut),
        "routines" to d.routines.map(::routineOut),
        "tasks" to d.tasks.map(::taskOut),
        "prefs" to prefsOut(d.prefs),
        "override" to d.override?.let { mapOf("modeId" to it.modeId, "until" to it.until, "setAt" to it.setAt) },
        "daily" to dailyOut(d.daily),
        "run" to d.run?.let(::runOut),
        "focus" to d.focus?.let(::focusOut),
        "lastModeKey" to d.lastModeKey,
        "modeEnteredAt" to d.modeEnteredAt,
        "meta" to d.meta, "tombs" to d.tombs, "synced" to d.synced, "tombSpaces" to d.tombSpaces,
        "gDeletes" to d.gDeletes.toList(), "gLastSync" to d.gLastSync, "gTasksOn" to d.gTasksOn,
        "gPushAfter" to d.gPushAfter, "onboarded" to d.onboarded,
    ))

    fun decode(text: String): AppData {
        val root = Json.parse(text) as? Map<*, *> ?: throw Json.ParseException("Root is not an object")
        val defaults = Defaults.data()
        val modes = (root["modes"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.let(::modeIn) } ?: defaults.modes
        val routines = (root["routines"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.let(::routineIn) } ?: defaults.routines
        val fileSchema = (root["schema"] as? Number)?.toInt() ?: 0
        val prefs0 = (root["prefs"] as? Map<*, *>)?.let(::prefsIn) ?: Prefs()
        // v4: the breather bubble is on for everyone by default. v5: so is Mochi wandering about.
        val prefs = prefs0.copy(features = prefs0.features +
            (if (fileSchema in 1..3) setOf(Feature.OVERWHELMED) else emptySet()) +
            (if (fileSchema in 1..4) setOf(Feature.MOCHI) else emptySet()))
        return AppData(
            schema = 5,
            modes = modes,
            routines = routines,
            tasks = (root["tasks"] as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.let(::taskIn) } ?: emptyList(),
            prefs = prefs,
            override = (root["override"] as? Map<*, *>)?.let { m ->
                m.str("modeId")?.let { Override(it, m.long("until"), m.long("setAt") ?: 0) }
            },
            daily = (root["daily"] as? Map<*, *>)?.let(::dailyIn) ?: Daily(),
            run = (root["run"] as? Map<*, *>)?.let(::runIn),
            focus = (root["focus"] as? Map<*, *>)?.let(::focusIn),
            lastModeKey = root.str("lastModeKey"),
            modeEnteredAt = root.long("modeEnteredAt"),
            meta = root.longMap("meta"), tombs = root.longMap("tombs"), synced = root.longMap("synced"),
            tombSpaces = (root["tombSpaces"] as? Map<*, *>)?.entries?.mapNotNull { (k, v) -> (k as? String)?.let { kk -> (v as? String)?.let { kk to it } } }?.toMap() ?: emptyMap(),
            gDeletes = root.strSet("gDeletes"), gLastSync = root.long("gLastSync") ?: 0, gTasksOn = root.bool("gTasksOn", false),
            gPushAfter = root.long("gPushAfter") ?: 0,
            // Installs from before onboarding existed are already set up — don't send them through it again.
            onboarded = root.bool("onboarded", (root["schema"] as? Number)?.toInt()?.let { it < 3 } ?: false),
        )
    }

    // ---- modes ----
    fun modeOut(m: Mode) = mapOf(
        "id" to m.id, "name" to m.name, "icon" to m.icon, "start" to m.start, "end" to m.end,
        "days" to m.days.sorted(), "scheduled" to m.scheduled,
        "widgets" to m.widgets.map { mapOf("w" to it.widget.name, "on" to it.enabled) },
        "featured" to m.featuredRoutineId, "order" to m.order, "winsOver" to m.winsOver.toList(), "tint" to m.tint,
    )

    private val LEGACY_WIDGETS = mapOf("SLEEP_AUDIO" to ModeWidget.MUSIC)

    fun modeIn(m: Map<*, *>): Mode? {
        val id = m.str("id") ?: return null
        val slots = (m["widgets"] as? List<*>)?.mapNotNull { w ->
            val mw = w as? Map<*, *> ?: return@mapNotNull null
            val name = mw.str("w")
            val widget = ModeWidget.values().find { it.name == name } ?: LEGACY_WIDGETS[name] ?: return@mapNotNull null
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
            winsOver = m.strSet("winsOver"),
            tint = m.int("tint", Defaults.tintFor(id)).coerceIn(0, TINT_COUNT - 1),
        )
    }

    // ---- routines ----
    fun routineOut(r: Routine) = mapOf(
        "id" to r.id, "name" to r.name, "description" to r.description, "icon" to r.icon,
        "steps" to r.steps.map { s ->
            mapOf("id" to s.id, "title" to s.title, "instruction" to s.instruction, "duration" to s.durationSec,
                "timer" to s.timerEnabled, "auto" to s.autoAdvance, "alert" to s.alert, "breathing" to s.breathing)
        },
        "modeIds" to r.modeIds.toList(),
        "schedule" to r.schedule?.let { mapOf("time" to it.time, "days" to it.days.sorted(), "notify" to it.notify.name) },
        "quick" to r.quick, "enabled" to r.enabled, "order" to r.order,
    )

    fun routineIn(m: Map<*, *>): Routine? {
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
                breathing = s.bool("breathing", false),
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
            icon = m.str("icon") ?: "✨", steps = steps, modeIds = m.strSet("modeIds"),
            schedule = sched, quick = m.bool("quick", false), enabled = m.bool("enabled", true), order = m.int("order", 0),
        )
    }

    // ---- tasks ----
    fun taskOut(t: Task) = mapOf(
        "id" to t.id, "title" to t.title, "notes" to t.notes, "modeIds" to t.modeIds.toList(), "today" to t.today,
        "done" to t.done, "doneAt" to t.doneAt, "createdAt" to t.createdAt, "order" to t.order,
        "spaceId" to t.spaceId, "googleId" to t.googleId, "doneBy" to t.doneBy,
    )

    fun taskIn(m: Map<*, *>): Task? {
        val id = m.str("id") ?: return null
        val title = m.str("title") ?: return null
        return taskOf(m, id, title)
    }

    private fun taskOf(m: Map<*, *>, id: String, title: String) = Task(
        id = id,
        title = title,
        notes = m.str("notes") ?: "",
        modeIds = m.strSet("modeIds"),
        today = m.bool("today", false),
        done = m.bool("done", false),
        doneAt = m.long("doneAt"),
        createdAt = m.long("createdAt") ?: 0,
        order = m.long("order") ?: 0,
        spaceId = m.str("spaceId"),
        googleId = m.str("googleId"),
        doneBy = m.str("doneBy"),
    )

    // ---- prefs / daily / run / focus ----
    fun prefsOut(p: Prefs) = mapOf(
        "use24h" to p.use24h, "waterTarget" to p.waterTarget, "glassMl" to p.glassMl, "mealNames" to p.mealNames,
        "breakIntervalMin" to p.breakIntervalMin, "textScale" to p.textScale, "highContrast" to p.highContrast,
        "reduceMotion" to p.reduceMotion, "keepScreenOn" to p.keepScreenOn, "fullscreen" to p.fullscreen,
        "nightDim" to p.nightDim, "dimModeIds" to p.dimModeIds.toList(), "sleepAudioUri" to p.sleepAudioUri,
        "todayLimit" to p.todayLimit, "focusMin" to p.focusMin, "transitionWarnMin" to p.transitionWarnMin,
        "chimeOnTransition" to p.chimeOnTransition, "calendarOn" to p.calendarOn,
        "features" to p.features.map { it.name }.sorted(), "gListId" to p.gListId, "gListTitle" to p.gListTitle, "nickname" to p.nickname,
        "music" to p.music.let { m -> mapOf("provider" to m.provider.name, "ambient" to m.ambient.name, "sleepTimerMin" to m.sleepTimerMin,
            "appPackage" to m.appPackage, "appName" to m.appName, "link" to m.link, "fileUri" to m.fileUri, "fileName" to m.fileName, "spotifyClientId" to m.spotifyClientId) },
    )

    fun prefsIn(m: Map<*, *>): Prefs {
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
            dimModeIds = if (m["dimModeIds"] is List<*>) m.strSet("dimModeIds") else d.dimModeIds,
            sleepAudioUri = m.str("sleepAudioUri") ?: "",
            todayLimit = m.int("todayLimit", d.todayLimit).coerceIn(1, 10),
            focusMin = m.int("focusMin", d.focusMin).coerceIn(5, 120),
            transitionWarnMin = m.int("transitionWarnMin", d.transitionWarnMin).coerceIn(0, 60),
            chimeOnTransition = m.bool("chimeOnTransition", d.chimeOnTransition),
            calendarOn = m.bool("calendarOn", d.calendarOn),
            features = (m["features"] as? List<*>)?.mapNotNull { n -> Feature.values().find { it.name == n } }?.toSet() ?: d.features,
            gListId = m.str("gListId") ?: d.gListId,
            gListTitle = m.str("gListTitle") ?: d.gListTitle,
            nickname = m.str("nickname") ?: d.nickname,
            music = (m["music"] as? Map<*, *>)?.let { x ->
                MusicPrefs(
                    provider = MusicProvider.values().find { it.name == x.str("provider") } ?: MusicProvider.BUILT_IN,
                    ambient = Ambient.values().find { it.name == x.str("ambient") } ?: Ambient.RAIN,
                    sleepTimerMin = x.int("sleepTimerMin", 30).coerceIn(0, 240),
                    appPackage = x.str("appPackage") ?: "", appName = x.str("appName") ?: "", link = x.str("link") ?: "",
                    fileUri = x.str("fileUri") ?: "", fileName = x.str("fileName") ?: "",
                    spotifyClientId = x.str("spotifyClientId") ?: "",
                )
            } ?: d.music,
        )
    }

    fun dailyOut(d: Daily) = mapOf(
        "date" to d.date, "water" to d.water, "meals" to d.mealsDone.sorted(), "lastBreakAt" to d.lastBreakAt,
        "prompted" to d.prompted.toList(), "dismissed" to d.dismissed.toList(), "routinesDone" to d.routinesDone.toList(),
        "focusDone" to d.focusDone, "warnedFor" to d.warnedFor,
    )

    fun dailyIn(m: Map<*, *>) = Daily(
        date = m.str("date") ?: "",
        water = m.int("water", 0).coerceIn(0, 99),
        mealsDone = (m["meals"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.toSet() ?: emptySet(),
        lastBreakAt = m.long("lastBreakAt"),
        prompted = m.strSet("prompted"),
        dismissed = m.strSet("dismissed"),
        routinesDone = m.strSet("routinesDone"),
        focusDone = m.int("focusDone", 0),
        warnedFor = m.long("warnedFor"),
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

    private fun focusOut(f: FocusSession) = mapOf(
        "taskId" to f.taskId, "label" to f.label, "durationMs" to f.durationMs, "elapsedMs" to f.elapsedMs,
        "runningSince" to f.runningSince, "finished" to f.finished, "startedAt" to f.startedAt,
    )

    private fun focusIn(m: Map<*, *>): FocusSession? {
        val dur = m.long("durationMs") ?: return null
        if (dur <= 0) return null
        return FocusSession(
            taskId = m.str("taskId"), label = m.str("label") ?: "Focus", durationMs = dur,
            elapsedMs = m.long("elapsedMs") ?: 0, runningSince = m.long("runningSince"),
            finished = m.bool("finished", false), startedAt = m.long("startedAt") ?: 0,
        )
    }

    // ---- defensive accessors ----
    private fun Map<*, *>.longMap(k: String): Map<String, Long> =
        (this[k] as? Map<*, *>)?.entries?.mapNotNull { (key, v) -> (key as? String)?.let { kk -> (v as? Number)?.toLong()?.let { kk to it } } }?.toMap() ?: emptyMap()
    private fun Map<*, *>.str(k: String) = this[k] as? String
    private fun Map<*, *>.int(k: String, def: Int) = (this[k] as? Number)?.toInt() ?: def
    private fun Map<*, *>.long(k: String) = (this[k] as? Number)?.toLong()
    private fun Map<*, *>.dbl(k: String) = (this[k] as? Number)?.toDouble()
    private fun Map<*, *>.bool(k: String, def: Boolean) = this[k] as? Boolean ?: def
    private fun Map<*, *>.strSet(k: String) = (this[k] as? List<*>)?.mapNotNull { it as? String }?.toSet() ?: emptySet()
    private fun Map<*, *>.days(k: String) =
        (this[k] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }?.filter { it in 1..7 }?.toSet() ?: emptySet()
}
