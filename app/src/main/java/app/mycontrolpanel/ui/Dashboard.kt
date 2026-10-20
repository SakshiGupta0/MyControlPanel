package app.mycontrolpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.mycontrolpanel.core.Action
import app.mycontrolpanel.core.AppState
import app.mycontrolpanel.core.CalEvent
import app.mycontrolpanel.core.CalendarStatus
import app.mycontrolpanel.core.Derive
import app.mycontrolpanel.core.Dialog
import app.mycontrolpanel.core.Effective
import app.mycontrolpanel.core.Env
import app.mycontrolpanel.data.SpotifyEngine.Status as SpStatus
import app.mycontrolpanel.core.Feature
import androidx.compose.ui.unit.sp
import app.mycontrolpanel.core.Focus
import app.mycontrolpanel.core.Mode
import app.mycontrolpanel.core.ModeWidget
import app.mycontrolpanel.core.NowItem
import app.mycontrolpanel.core.Routine
import app.mycontrolpanel.core.ScheduleResult
import app.mycontrolpanel.core.Scheduler
import app.mycontrolpanel.core.SettingsSection
import app.mycontrolpanel.core.TaskFilter
import app.mycontrolpanel.core.modeOrNull
import java.time.format.DateTimeFormatter
import java.util.Locale

typealias Dispatch = (Action) -> Unit

private val DATE_FMT = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())

fun clockText(epoch: Long, env: Env, use24h: Boolean): Pair<String, String?> {
    val t = Scheduler.toLocal(epoch, env.zone)
    return if (use24h) "%02d:%02d".format(t.hour, t.minute) to null
    else "%d:%02d".format(if (t.hour % 12 == 0) 12 else t.hour % 12, t.minute) to (if (t.hour < 12) "AM" else "PM")
}

fun shortTime(epoch: Long, env: Env, use24h: Boolean): String = clockText(epoch, env, use24h).let { (a, b) -> if (b == null) a else "$a $b" }

fun routineMinutes(r: Routine): Int = r.steps.filter { it.hasTimer }.sumOf { it.durationSec } / 60

fun inText(minutes: Long): String = when {
    minutes < 1 -> "now"
    minutes < 60 -> "in $minutes min"
    minutes % 60 == 0L -> "in ${minutes / 60} h"
    else -> "in ${minutes / 60} h ${minutes % 60} min"
}

fun durText(minutes: Long): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0L -> "${minutes / 60} h"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

fun resultLabel(r: ScheduleResult) = when (r) {
    is ScheduleResult.Active -> "${r.mode.icon} ${r.mode.name}"
    is ScheduleResult.Conflict -> "a schedule conflict"
    ScheduleResult.Free -> "free time"
}

@Composable
fun Dashboard(s: AppState, env: Env, dispatch: Dispatch) {
    val p = P()
    val win = LocalWindow.current
    val f = s.data.prefs.features
    val bg = Modifier.fillMaxSize().testTag("dashboard")

    if (!win.twoColumn) {
        // Portrait (phones and tablets): one scrolling column / short screens: one scrolling column, actions pinned at the bottom (always reachable).
        Column(bg) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = win.gutter, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TimePanel(s, env, dispatch, Modifier.fillMaxWidth(), compact = true)
                Banners(s, env, dispatch)
                if (Feature.NOW in f) NowCard(s, env, dispatch)
                RightColumnContent(s, env, dispatch, oneColumn = true)
                Spacer(Modifier.height(72.dp))   // room to scroll the last card clear of the breather bubble
            }
            ActionBar(s, dispatch, Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, p.bg2.copy(alpha = 0.92f), p.bg2.copy(alpha = 0.92f)))).padding(horizontal = win.gutter, vertical = 10.dp))
        }
        return
    }

    val short = win.short
    Row(bg.padding(horizontal = if (short) 18.dp else 32.dp, vertical = if (short) 12.dp else 24.dp),
        horizontalArrangement = Arrangement.spacedBy(if (short) 16.dp else 28.dp)) {
        // ---------------- left: where am I in the day + the one thing ----------------
        Column(Modifier.weight(if (short) 0.48f else 0.44f).fillMaxHeight().then(if (short) Modifier.verticalScroll(rememberScrollState()) else Modifier),
            verticalArrangement = Arrangement.spacedBy(if (short) 10.dp else 16.dp)) {
            TimePanel(s, env, dispatch, if (short) Modifier.fillMaxWidth() else Modifier.fillMaxWidth().weight(1f), compact = short)
            if (Feature.NOW in f) NowCard(s, env, dispatch)
            ActionBar(s, dispatch, Modifier.fillMaxWidth())
        }
        // ---------------- right: today, body basics, this mode ----------------
        Column(Modifier.weight(0.56f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Banners(s, env, dispatch)
            RightColumnContent(s, env, dispatch, oneColumn = false)
            Spacer(Modifier.height(84.dp))   // room to scroll clear of the breather bubble
        }
    }
}

@Composable
private fun ActionBar(s: AppState, dispatch: Dispatch, modifier: Modifier) {
    val f = s.data.prefs.features
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val win = LocalWindow.current
        val narrow = win.width < 420.dp || (win.twoColumn && win.short)
        if (Feature.BRAIN_DUMP in f) PrimaryButton(if (narrow) "＋ Brain dump" else "＋  Brain dump", { dispatch(Action.OpenDialog(Dialog.Capture)) }, Modifier.weight(1f),
            tag = "captureButton", tall = if (narrow) 56.dp else 60.dp, style = if (narrow) Type.label else Type.heading)
        else Spacer(Modifier.weight(1f))
        if (Feature.QUICK_ROUTINES in f) {
            if (narrow) RoundIconButton("✦", { dispatch(Action.OpenDialog(Dialog.QuickRoutines)) }, size = 56.dp, tag = "quickRoutinesButton")
            else SecondaryButton("✦ Quick", { dispatch(Action.OpenDialog(Dialog.QuickRoutines)) }, Modifier.heightIn(min = 60.dp), tag = "quickRoutinesButton")
        }
        RoundIconButton("⚙", { dispatch(Action.OpenSettings(SettingsSection.WIDGETS)) }, size = if (narrow) 56.dp else 60.dp, tag = "settingsButton")
    }
}

/** Today, body basics, this mode's widgets, overwhelmed shortcut, footer. */
@Composable
private fun RightColumnContent(s: AppState, env: Env, dispatch: Dispatch, oneColumn: Boolean) {
    val p = P()
    val f = s.data.prefs.features
    if (Feature.TODAY in f) TodayCard(s, env, dispatch)
    val small = listOfNotNull(Feature.WATER.takeIf { it in f }, Feature.MEALS.takeIf { it in f }, Feature.DONE_TALLY.takeIf { it in f })
    if (small.isNotEmpty()) {
        val win = LocalWindow.current
        val narrow = win.width < 420.dp
        (if (narrow) small.chunked(1) else if (win.twoColumn && win.short) small.chunked(2) else listOf(small)).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { feat ->
                    val m = Modifier.weight(if (feat == Feature.DONE_TALLY) 0.8f else 1f).fillMaxHeight()
                    when (feat) {
                        Feature.WATER -> WaterCard(s, dispatch, m)
                        Feature.MEALS -> MealsCard(s, dispatch, m)
                        else -> DoneTodayCard(s, env, m)
                    }
                }
            }
        }
    }
    val mode = Derive.effective(s, env).modeOrNull()
    if (mode != null && Feature.MODE_WIDGETS in f) {
        val widgets = mode.widgets.filter { it.enabled }.map { it.widget }
            .filter { it != ModeWidget.MUSIC || s.data.prefs.music.provider != app.mycontrolpanel.core.MusicProvider.NONE }
        val single = (oneColumn && LocalWindow.current.width < 640.dp) || (LocalWindow.current.twoColumn && LocalWindow.current.short)
        (if (single) widgets.chunked(1) else widgets.chunked(2)).forEach { row ->
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { w -> ModeWidgetCard(w, mode, s, env, dispatch, Modifier.weight(1f).fillMaxHeight()) }
                if (row.size == 1 && widgets.size > 1 && !single) Spacer(Modifier.weight(1f))
            }
        }
    }
    val sync = when (s.live.syncStatus) {
        app.mycontrolpanel.core.SyncStatus.SYNCED -> " · synced ✓"
        app.mycontrolpanel.core.SyncStatus.OFFLINE -> " · offline, will sync later"
        app.mycontrolpanel.core.SyncStatus.ERROR -> " · sync problem (Settings → Account)"
        app.mycontrolpanel.core.SyncStatus.CONNECTING -> " · connecting…"
        app.mycontrolpanel.core.SyncStatus.LOCAL_ONLY -> ""
    }
    T("My Control Panel · v${LocalPlatform.current.versionName}$sync", Type.small, p.muted.copy(alpha = 0.6f), Modifier.testTag("footer"))
}

/** The big tinted panel: mode, clock, date, next event, time left. */
@Composable
private fun TimePanel(s: AppState, env: Env, dispatch: Dispatch, modifier: Modifier, compact: Boolean) {
    val p = P()
    val tint = LocalModeTint.current
    val eff = Derive.effective(s, env)
    val prefs = s.data.prefs
    val (time, ampm) = clockText(s.now, env, prefs.use24h)
    val phone = LocalWindow.current.width < 420.dp || LocalWindow.current.short
    val clockStyle = when {
        phone -> Type.clock.copy(fontSize = 70.sp, lineHeight = 74.sp, letterSpacing = (-2).sp)
        compact -> Type.clock.copy(fontSize = 92.sp, lineHeight = 96.sp, letterSpacing = (-2).sp)
        else -> Type.clock
    }
    val modeId = eff.modeOrNull()?.id
    val mood = beanMoodFor(modeId, focusing = s.data.focus?.let { !it.finished } == true, caughtUp = Derive.now(s, env) is NowItem.Rest)
    // No box: the clock sits right on today's sky, with Mochi's little house beside it.
    Column(modifier.padding(horizontal = 4.dp, vertical = if (compact) 4.dp else 8.dp)) {
        ModeHeader(eff, s, env, dispatch)
        Row(Modifier.fillMaxWidth().then(if (compact) Modifier else Modifier.weight(1f)), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Greeting(s, env, Modifier.padding(top = 12.dp, start = 4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    T(time, clockStyle, modifier = Modifier.testTag("clock"), maxLines = 1)
                    if (ampm != null) T(ampm, if (phone) Type.heading else Type.title, p.muted, Modifier.padding(start = 6.dp, bottom = if (compact) 12.dp else 22.dp), maxLines = 1)
                }
                T(Scheduler.toLocal(s.now, env.zone).format(DATE_FMT), if (compact) Type.label else Type.title, p.text.copy(alpha = 0.78f))
                if (Feature.NEXT_EVENT in prefs.features) NextEventLine(s, env)
            }
            MochiHome(mood, size = when { phone -> 118.dp; compact -> 150.dp; else -> 210.dp })
        }
        if (Feature.TIME_LEFT in prefs.features) { Spacer(Modifier.height(if (compact) 10.dp else 14.dp)); TimeLeftBar(s, env, eff) }
    }
}

/** A soft hello that follows the day: "Good morning, Sakshi". */
@Composable
private fun Greeting(s: AppState, env: Env, modifier: Modifier) {
    val p = P()
    val h = Scheduler.toLocal(s.now, env.zone).hour
    val name = app.mycontrolpanel.core.displayName(s.data.prefs, s.live.account)
    val hello = when (h) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Sleep well"
    }
    val doneToday = s.data.tasks.count { it.done && it.doneAt != null && it.doneAt >= s.now - 24 * 3_600_000L }
    val cheer = when {
        doneToday >= 5 -> " · you're on a roll ✨"
        doneToday >= 1 -> " · nice work today"
        else -> ""
    }
    T(hello + (name?.let { ", $it" } ?: "") + cheer, Type.label, p.text.copy(alpha = 0.7f), modifier.testTag("greeting"), maxLines = 1)
}

fun lerpColor(a: Color, b: Color, t: Float) = androidx.compose.ui.graphics.lerp(a, b, t)

@Composable
private fun ModeHeader(eff: Effective, s: AppState, env: Env, dispatch: Dispatch) {
    val p = P()
    val (icon, name) = when (eff) {
        is Effective.Scheduled -> eff.mode.icon to eff.mode.name
        is Effective.Overridden -> eff.mode.icon to eff.mode.name
        is Effective.Conflict -> "⚠️" to "Schedule conflict"
        Effective.Free -> "○" to "Free time"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.clip(PillShape).background(p.card.copy(alpha = if (p.dark) 0.9f else 0.75f))
                .clickable { dispatch(Action.OpenDialog(Dialog.ModeSwitcher)) }
                .heightIn(min = 56.dp).padding(horizontal = 22.dp, vertical = 10.dp).testTag("modeChip"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            T(icon, Type.heading); Spacer(Modifier.width(10.dp))
            T(name, Type.heading, modifier = Modifier.testTag("modeName")); Spacer(Modifier.width(10.dp))
            T("▾", Type.label, p.muted)
        }
        if (eff is Effective.Overridden) QuietButton("Back to schedule", { dispatch(Action.ReturnToSchedule) }, tag = "returnToSchedule")
    }
}

@Composable
private fun NextEventLine(s: AppState, env: Env) {
    val p = P()
    if (s.live.calendarStatus != CalendarStatus.OK) return
    val cur = Derive.currentEvent(s)
    val next = Derive.nextEvent(s, env)
    val text = when {
        cur != null -> "Now: ${cur.title} · until ${shortTime(cur.end, env, s.data.prefs.use24h)}"
        next != null -> "Next: ${next.title} · ${shortTime(next.begin, env, s.data.prefs.use24h)} (${inText((next.begin - s.now) / 60_000)})"
        else -> "No more events today"
    }
    T("📅  $text", Type.label, p.text.copy(alpha = 0.8f), Modifier.padding(top = 10.dp).testTag("nextEvent"), maxLines = 1)
}

/** Time-blindness aid: how much of this mode is left, as words and a bar. */
@Composable
private fun TimeLeftBar(s: AppState, env: Env, eff: Effective) {
    val p = P()
    val mode = eff.modeOrNull() ?: return
    val w = Derive.modeWindow(s, env)
    if (w == null) {
        if (eff is Effective.Overridden) T("${mode.name} until you switch back", Type.label, p.muted)
        return
    }
    val (start, end) = w
    val leftMin = ((end - s.now) / 60_000).coerceAtLeast(0)
    val frac = ((s.now - start).toFloat() / (end - start).coerceAtLeast(1)).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("timeLeft")) {
        T("${durText(leftMin)} left in ${mode.name}", Type.label, p.text.copy(alpha = 0.8f))
        Box(Modifier.fillMaxWidth().height(10.dp).clip(PillShape).background(p.card.copy(alpha = 0.7f))) {
            Box(Modifier.fillMaxWidth(frac).height(10.dp).clip(PillShape).background(p.accent.copy(alpha = 0.85f)))
        }
    }
}

/** One decision at a time: the single most useful next thing. */
@Composable
private fun NowCard(s: AppState, env: Env, dispatch: Dispatch) {
    val p = P()
    val item = Derive.now(s, env)
    PanelCard(Modifier.fillMaxWidth().testTag("nowCard"), padding = 24.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Eyebrow("Right now", p.accent)
            when (item) {
                is NowItem.FocusActive -> {
                    val left = Focus.remaining(item.focus, s.now) / 60_000
                    NowBody(item.focus.label, if (item.focus.finished) "Focus time's up" else "Focusing · $left min left") {
                        PrimaryButton("Return", { dispatch(Action.ShowFocus) }, tag = "nowAction")
                    }
                }
                is NowItem.Prompt -> NowBody("${item.routine.icon}  ${item.routine.name}", "It's time for this") {
                    QuietButton("Not now", { dispatch(Action.DismissPrompt(item.routine.id)) }, color = p.muted)
                    PrimaryButton("Start", { dispatch(Action.StartRoutine(item.routine.id)) }, tag = "nowAction")
                }
                is NowItem.Spotlight -> NowBody(item.task.title, "Just this one. Nothing else.") {
                    QuietButton("Pick another", { dispatch(Action.PickOne) }, color = p.muted)
                    SecondaryButton("Focus ${s.data.prefs.focusMin}m", { dispatch(Action.StartFocus(item.task.id)) })
                    PrimaryButton("Done ✓", { dispatch(Action.ToggleTaskDone(item.task.id)) }, tag = "nowAction")
                }
                is NowItem.Featured -> {
                    val mins = routineMinutes(item.routine)
                    NowBody("${item.routine.icon}  ${item.routine.name}", "${item.routine.steps.size} small steps" + if (mins > 0) " · about $mins min" else "") {
                        PrimaryButton("Start", { dispatch(Action.StartRoutine(item.routine.id)) }, tag = "featuredStart", tall = 60.dp, style = Type.heading)
                    }
                }
                is NowItem.TodayTask -> NowBody(item.task.title, "First on today's list") {
                    SecondaryButton("Focus ${s.data.prefs.focusMin}m", { dispatch(Action.StartFocus(item.task.id)) })
                    PrimaryButton("Done ✓", { dispatch(Action.ToggleTaskDone(item.task.id)) }, tag = "nowAction")
                }
                NowItem.Rest -> NowBody("You're all caught up ♡", "Nothing urgent. Rest is allowed.") {
                    SecondaryButton("Pick one for me", { dispatch(Action.PickOne) })
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun NowBody(title: String, sub: String, actions: @Composable () -> Unit) {
    val p = P()
    T(title, if (LocalWindow.current.compact) Type.title else Type.display, maxLines = 3, modifier = Modifier.testTag("nowTitle"))
    T(sub, Type.label, p.muted)
    Spacer(Modifier.height(8.dp))
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(8.dp)) { actions() }
}

@Composable
private fun Banners(s: AppState, env: Env, dispatch: Dispatch) {
    val p = P()
    val eff = Derive.effective(s, env)
    s.ui.saveError?.let { err ->
        PanelCard(Modifier.fillMaxWidth().testTag("saveErrorBanner"), tint = p.warmSoft) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("⚠️  $err", Type.label, p.danger, Modifier.weight(1f))
                PrimaryButton("Retry", { dispatch(Action.RetrySave) })
            }
        }
    }
    if (eff is Effective.Conflict) {
        PanelCard(Modifier.fillMaxWidth().testTag("conflictBanner"), tint = p.warmSoft) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                T("Two modes want this time", Type.heading)
                T("Pick one for now. Fix the schedule later in Settings → Modes.", Type.label, p.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    eff.modes.forEach { m -> SecondaryButton("${m.icon} ${m.name}", { dispatch(Action.PickConflictMode(m.id)) }) }
                }
            }
        }
    }
    Derive.transitionSoon(s, env)?.let { (mins, next) ->
        PanelCard(Modifier.fillMaxWidth().testTag("headsUp"), tint = p.warmSoft) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("⏳", Type.title, modifier = Modifier.padding(end = 14.dp))
                Column(Modifier.weight(1f)) {
                    T("${resultLabel(next)} ${inText(mins)}", Type.heading)
                    T("Start wrapping up — finish the sentence, not the chapter.", Type.label, p.muted)
                }
            }
        }
    }
    // Further routine prompts (the first one is on the Now card).
    Derive.prompts(s, env).drop(1).forEach { r ->
        PanelCard(Modifier.fillMaxWidth().testTag("prompt_${r.id}")) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                T("${r.icon}  ${r.name}", Type.heading, modifier = Modifier.weight(1f))
                QuietButton("Not now", { dispatch(Action.DismissPrompt(r.id)) }, color = p.muted)
                PrimaryButton("Start", { dispatch(Action.StartRoutine(r.id)) })
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun TodayCard(s: AppState, env: Env, dispatch: Dispatch) {
    val p = P()
    val list = Derive.tasksFor(s, TaskFilter.TODAY, env)
    PanelCard(Modifier.fillMaxWidth().testTag("todayCard")) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Title on its own line; the two links wrap underneath when space is short (phones, big text).
            androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center,
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Eyebrow("Today's ${s.data.prefs.todayLimit}", modifier = Modifier.align(Alignment.CenterVertically).padding(end = 8.dp))
                Spacer(Modifier.weight(1f))
                QuietButton("Pick one for me", { dispatch(Action.PickOne) }, tag = "pickOne")
                QuietButton("All tasks →", { dispatch(Action.OpenTasks(TaskFilter.TODAY)) }, tag = "openTasks")
            }
            if (list.isEmpty()) {
                T("Choose up to ${s.data.prefs.todayLimit} things for today. Small lists get done.", Type.body, p.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 6.dp)) {
                    SecondaryButton("Choose from my tasks", { dispatch(Action.OpenTasks(TaskFilter.ALL)) })
                }
            }
            list.forEach { t -> TaskRow(t, s, dispatch, compact = true) }
        }
    }
}

@Composable
fun TaskRow(t: app.mycontrolpanel.core.Task, s: AppState, dispatch: Dispatch, compact: Boolean = false) {
    val p = P()
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).testTag("task_${t.title}"), verticalAlignment = Alignment.CenterVertically) {
        CheckCircle(t.done, { dispatch(Action.ToggleTaskDone(t.id)) }, tag = "check_${t.title}")
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { dispatch(Action.EditTask(t.id)) }.padding(vertical = 6.dp)) {
            T(t.title, Type.body, if (t.done) p.muted else p.text, maxLines = 2)
            if (!compact) {
                val modes = t.modeIds.mapNotNull { id -> s.data.modes.find { it.id == id }?.let { "${it.icon} ${it.name}" } }
                val space = t.spaceId?.let { id -> s.live.spaces.find { it.id == id }?.let { "🏠 ${it.name}" } }
                val bits = listOfNotNull(space, modes.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                    t.doneBy?.takeIf { t.done }?.let { "done by $it" }, t.notes.takeIf { it.isNotBlank() })
                if (bits.isNotEmpty()) T(bits.joinToString("  ·  "), Type.small, p.muted, maxLines = 1)
            }
        }
        if (!t.done) {
            if (!compact) QuietButton(if (t.today) "★" else "☆", { dispatch(Action.ToggleToday(t.id)) }, tag = "star_${t.title}", color = if (t.today) p.warm else p.muted)
            QuietButton("Focus", { dispatch(Action.StartFocus(t.id)) }, color = p.accent)
        }
    }
}

@Composable
private fun WaterCard(s: AppState, dispatch: Dispatch, modifier: Modifier) {
    val p = P()
    val prefs = s.data.prefs
    val n = s.data.daily.water
    PanelCard(modifier.testTag("waterCard")) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Eyebrow("Water")
            Row(verticalAlignment = Alignment.Bottom) {
                T("$n", Type.display, modifier = Modifier.testTag("waterCount"))
                T(" / ${prefs.waterTarget}", Type.label, p.muted, Modifier.padding(bottom = 8.dp))
            }
            // one drop per glass — easier to "see" than a number
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(prefs.waterTarget.coerceAtMost(12)) { i ->
                    Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(if (i < n) p.accent else p.cardStrong))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("+ Glass", { dispatch(Action.AddWater(1)) }, Modifier.weight(1f), tag = "addWater")
                RoundIconButton("−", { dispatch(Action.AddWater(-1)) }, enabled = n > 0, tag = "removeWater", size = 48.dp)
            }
        }
    }
}

@Composable
private fun MealsCard(s: AppState, dispatch: Dispatch, modifier: Modifier) {
    val p = P()
    val names = s.data.prefs.mealNames
    val done = s.data.daily.mealsDone
    PanelCard(modifier.testTag("mealsCard")) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Eyebrow("Meals")
            names.forEachIndexed { i, name ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 50.dp).clip(RoundedCornerShape(12.dp))
                        .clickable { dispatch(Action.ToggleMeal(i)) }.testTag("meal_$i"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(30.dp).clip(PillShape).background(if (i in done) p.accent else p.cardStrong), contentAlignment = Alignment.Center) {
                        if (i in done) T("✓", Type.small, p.onAccent)
                    }
                    Spacer(Modifier.width(12.dp))
                    T(name, Type.label, if (i in done) p.muted else p.text)
                }
            }
        }
    }
}

/** Gentle tally — no streaks, no scores, never shows a zero as failure. */
@Composable
private fun DoneTodayCard(s: AppState, env: Env, modifier: Modifier) {
    val p = P()
    val n = Derive.doneTodayCount(s, env)
    PanelCard(modifier.testTag("doneToday"), tint = p.warmSoft) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Eyebrow("Done today", p.warm)
            T(if (n == 0) "✿" else "$n", Type.display, p.warm)
            T(if (n == 0) "The day's still young." else if (n == 1) "thing finished" else "things finished", Type.small, p.muted)
        }
    }
}

@Composable
private fun ModeWidgetCard(w: ModeWidget, mode: Mode, s: AppState, env: Env, dispatch: Dispatch, modifier: Modifier) {
    val p = P()
    val platform = LocalPlatform.current
    val use24h = s.data.prefs.use24h
    PanelCard(modifier.testTag("widget_${w.name}")) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (w) {
                ModeWidget.TASKS -> {
                    Eyebrow("${mode.name} tasks")
                    val list = Derive.modeTasks(s, mode.id)
                    if (list.isEmpty()) T("Nothing tagged for ${mode.name}. Tag tasks with a mode to see them here.", Type.label, p.muted)
                    list.take(4).forEach { t ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CheckCircle(false, { dispatch(Action.ToggleTaskDone(t.id)) }, size = 36.dp)
                            Spacer(Modifier.width(8.dp))
                            T(t.title, Type.label, maxLines = 1, modifier = Modifier.weight(1f))
                        }
                    }
                    if (list.size > 4) QuietButton("+${list.size - 4} more", { dispatch(Action.OpenTasks(mode.id)) })
                }
                ModeWidget.ROUTINES -> {
                    Eyebrow("${mode.name} routines")
                    val list = Derive.routinesFor(s, mode.id).filter { it.id != mode.featuredRoutineId }
                    if (list.isEmpty()) T("No other routines for ${mode.name}.", Type.label, p.muted)
                    list.take(4).forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            T("${r.icon}  ${r.name}" + if (r.id in s.data.daily.routinesDone) "  ✓" else "", Type.label, modifier = Modifier.weight(1f))
                            SecondaryButton("Start", { dispatch(Action.StartRoutine(r.id)) })
                        }
                    }
                }
                ModeWidget.FOCUS -> {
                    Eyebrow("Focus")
                    T("One thing, one timer", Type.heading)
                    T("${s.data.daily.focusDone} session${if (s.data.daily.focusDone == 1) "" else "s"} today", Type.small, p.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PrimaryButton("${s.data.prefs.focusMin} min", { dispatch(Action.StartFocus(s.ui.spotlightTaskId ?: Derive.todayTasks(s).firstOrNull()?.id)) }, tag = "focusWidgetStart")
                        SecondaryButton("10 min", { dispatch(Action.StartFocus(null, 10)) })
                    }
                }
                ModeWidget.NEXT_MODE -> {
                    Eyebrow("Coming up")
                    val nc = Derive.nextChange(s, env)
                    if (nc == null) T("Nothing else scheduled.", Type.label, p.muted)
                    else {
                        val at = Scheduler.toEpoch(nc.first, env.zone)
                        T(resultLabel(nc.second), Type.heading)
                        T("${shortTime(at, env, use24h)} · ${inText((at - s.now) / 60_000)}", Type.label, p.muted)
                    }
                }
                ModeWidget.BREAK_REMINDER -> {
                    Eyebrow("Breaks")
                    val mins = Derive.minutesSinceBreak(s)
                    val due = mins >= s.data.prefs.breakIntervalMin
                    T(if (due) "Time for a short break" else "Last break ${durText(mins)} ago", Type.heading, if (due) p.warm else p.text)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("I took one", { dispatch(Action.TookBreak) }, tag = "tookBreak")
                        if (due && s.data.routines.any { it.id == "r_hydrate" && it.enabled }) PrimaryButton("2-min reset", { dispatch(Action.StartRoutine("r_hydrate")) })
                    }
                }
                ModeWidget.CALENDAR -> CalendarWidget(s, env, mode, dispatch)
                ModeWidget.MUSIC -> MusicWidget(s, mode, dispatch)
            }
        }
    }
}

@Composable
private fun CalendarWidget(s: AppState, env: Env, mode: Mode, dispatch: Dispatch) {
    val p = P()
    val use24h = s.data.prefs.use24h
    val bedtime = mode.id == "bedtime" || Scheduler.toLocal(s.now, env.zone).hour >= 21
    Eyebrow(if (bedtime) "Tomorrow" else "Today")
    when (s.live.calendarStatus) {
        CalendarStatus.OK -> {
            if (bedtime) {
                val f = Derive.tomorrowFirst(s, env)
                if (f == null) T("Nothing scheduled first thing. Sleep in your own time.", Type.label, p.muted)
                else { T(f.title, Type.heading, maxLines = 2); T("First thing: ${shortTime(f.begin, env, use24h)}", Type.label, p.muted) }
            } else {
                val list = Derive.todayEvents(s, env)
                if (list.isEmpty()) T("Nothing else on the calendar today.", Type.label, p.muted)
                list.take(4).forEach { e -> EventRow(e, s, env) }
            }
        }
        CalendarStatus.NEEDS_PERMISSION -> {
            T("Allow calendar access to see your events here.", Type.label, p.muted)
            SecondaryButton("Allow", { dispatch(Action.OpenSettings(SettingsSection.CALENDAR)) })
        }
        CalendarStatus.ERROR -> T("Couldn't read the calendar just now. It will try again shortly.", Type.label, p.muted)
        CalendarStatus.OFF -> {
            T("Show your Google Calendar events from this tablet.", Type.label, p.muted)
            SecondaryButton("Turn on", { dispatch(Action.OpenSettings(SettingsSection.CALENDAR)) }, tag = "calendarTurnOn")
        }
    }
}

@Composable
private fun EventRow(e: CalEvent, s: AppState, env: Env) {
    val p = P()
    val now = e.begin <= s.now && e.end > s.now
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(PillShape).background(if (now) p.warm else p.accent))
        Spacer(Modifier.width(10.dp))
        T(if (e.allDay) "All day" else shortTime(e.begin, env, s.data.prefs.use24h), Type.small, p.muted, Modifier.width(92.dp))
        Text1(e.title, if (now) p.text else p.text.copy(alpha = 0.85f))
    }
}

@Composable
private fun Text1(text: String, color: Color) =
    androidx.compose.material3.Text(text, style = Type.label, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)

/** Spotify, embedded: album art, what's playing, controls, and a picker. Falls back to plain media keys. */
