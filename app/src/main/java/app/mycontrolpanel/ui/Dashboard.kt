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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.mycontrolpanel.core.Action
import app.mycontrolpanel.core.AppState
import app.mycontrolpanel.core.Derive
import app.mycontrolpanel.core.Dialog
import app.mycontrolpanel.core.Effective
import app.mycontrolpanel.core.Env
import app.mycontrolpanel.core.Mode
import app.mycontrolpanel.core.ModeWidget
import app.mycontrolpanel.core.Routine
import app.mycontrolpanel.core.Scheduler
import app.mycontrolpanel.core.ScheduleResult
import app.mycontrolpanel.core.SettingsSection
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

@Composable
fun Dashboard(s: AppState, env: Env, dispatch: Dispatch) {
    val p = P()
    val eff = Derive.effective(s, env)
    val mode = eff.modeOrNull()
    val prefs = s.data.prefs
    val local = Scheduler.toLocal(s.now, env.zone)
    val (time, ampm) = clockText(s.now, env, prefs.use24h)

    Row(Modifier.fillMaxSize().background(p.bg).padding(horizontal = 36.dp, vertical = 28.dp).testTag("dashboard"),
        horizontalArrangement = Arrangement.spacedBy(32.dp)) {

        // ---------------- left: time, mode, featured routine ----------------
        Column(Modifier.weight(0.43f).fillMaxHeight()) {
            ModeHeader(eff, s, env, dispatch)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                T(time, Type.clock, modifier = Modifier.testTag("clock"))
                if (ampm != null) T(ampm, Type.title, p.muted, Modifier.padding(start = 10.dp, bottom = 20.dp))
            }
            T(local.format(DATE_FMT), Type.title, p.muted)
            Spacer(Modifier.weight(1f))
            FeaturedCard(eff, s, dispatch)
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PrimaryButton("✦  Quick routines", { dispatch(Action.OpenDialog(Dialog.QuickRoutines)) },
                    Modifier.weight(1f), tag = "quickRoutinesButton", tall = 64.dp, style = Type.heading)
                RoundIconButton("⚙", { dispatch(Action.OpenSettings(SettingsSection.MODES)) }, size = 64.dp, tag = "settingsButton")
            }
        }

        // ---------------- right: alerts + widgets ----------------
        Column(Modifier.weight(0.57f).fillMaxHeight().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Banners(s, eff, env, dispatch)
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                WaterCard(s, dispatch, Modifier.weight(1f).fillMaxHeight())
                MealsCard(s, dispatch, Modifier.weight(1f).fillMaxHeight())
                CalendarCard(Modifier.weight(1f).fillMaxHeight())
            }
            if (mode != null) {
                val slots = mode.widgets.filter { it.enabled }.map { it.widget }
                slots.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        row.forEach { w -> ModeWidgetCard(w, mode, s, env, dispatch, Modifier.weight(1f).fillMaxHeight()) }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            T("My Control Panel · v${LocalPlatform.current.versionName}", Type.small, p.muted.copy(alpha = 0.6f), Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun ModeHeader(eff: Effective, s: AppState, env: Env, dispatch: Dispatch) {
    val p = P()
    val (icon, name) = when (eff) {
        is Effective.Scheduled -> eff.mode.icon to eff.mode.name
        is Effective.Overridden -> eff.mode.icon to eff.mode.name
        is Effective.Conflict -> "⚠️" to "Schedule conflict"
        Effective.Free -> "○" to "No mode scheduled"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            Modifier.clip(PillShape).background(p.accentSoft).clickable { dispatch(Action.OpenDialog(Dialog.ModeSwitcher)) }
                .heightIn(min = 56.dp).padding(horizontal = 22.dp, vertical = 10.dp).testTag("modeChip"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            T(icon, Type.heading)
            Spacer(Modifier.width(10.dp))
            T(name, Type.heading, modifier = Modifier.testTag("modeName"))
            Spacer(Modifier.width(10.dp))
            T("▾", Type.label, p.muted)
        }
        if (eff is Effective.Overridden) {
            QuietButton("Back to schedule", { dispatch(Action.ReturnToSchedule) }, tag = "returnToSchedule")
        }
    }
    val sub = when (eff) {
        is Effective.Overridden -> "Manual · " + (eff.until?.let { "until ${shortTime(it, env, s.data.prefs.use24h)}" } ?: "until you switch back")
        else -> Derive.nextChange(s, env)?.let { (t, r) ->
            val mins = (Scheduler.toEpoch(t, env.zone) - s.now) / 60_000
            val what = when (r) {
                is ScheduleResult.Active -> "${r.mode.icon} ${r.mode.name}"
                is ScheduleResult.Conflict -> "a conflict"
                ScheduleResult.Free -> "free time"
            }
            "Next: $what at ${shortTime(Scheduler.toEpoch(t, env.zone), env, s.data.prefs.use24h)} (${inText(mins)})"
        }
    }
    if (sub != null) T(sub, Type.label, p.muted, Modifier.padding(top = 10.dp, start = 6.dp))
}

@Composable
private fun FeaturedCard(eff: Effective, s: AppState, dispatch: Dispatch) {
    val p = P()
    val mode = eff.modeOrNull()
    val r = Derive.featured(s, mode)
    PanelCard(Modifier.fillMaxWidth(), strong = true, padding = 26.dp) {
        if (r == null) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                T(if (mode == null) "Pick a mode to see its routine" else "No featured routine for ${mode.name}", Type.heading)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (mode == null) SecondaryButton("Choose a mode", { dispatch(Action.OpenDialog(Dialog.ModeSwitcher)) })
                    else SecondaryButton("Choose one", { dispatch(Action.EditMode(mode.id)) })
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T("FEATURED", Type.small.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(2f, androidx.compose.ui.unit.TextUnitType.Sp)), p.muted)
                    T("${r.icon}  ${r.name}", Type.title)
                    val mins = routineMinutes(r)
                    T(listOfNotNull(r.description.ifBlank { null }, "${r.steps.size} steps" + if (mins > 0) " · ~$mins min" else "").joinToString(" · "),
                        Type.label, p.muted)
                }
                Spacer(Modifier.width(16.dp))
                PrimaryButton("Start", { dispatch(Action.StartRoutine(r.id)) }, tag = "featuredStart", tall = 64.dp, style = Type.heading)
            }
        }
    }
}

@Composable
private fun Banners(s: AppState, eff: Effective, env: Env, dispatch: Dispatch) {
    val p = P()
    s.ui.saveError?.let { err ->
        PanelCard(Modifier.fillMaxWidth().testTag("saveErrorBanner")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("⚠️  $err", Type.label, p.danger, Modifier.weight(1f))
                PrimaryButton("Retry", { dispatch(Action.RetrySave) })
            }
        }
    }
    if (eff is Effective.Conflict) {
        PanelCard(Modifier.fillMaxWidth().testTag("conflictBanner")) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                T("Two modes are scheduled right now", Type.heading)
                T("Pick one for now, and fix the schedule in Settings → Modes so it doesn't happen again.", Type.label, p.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    eff.modes.forEach { m -> SecondaryButton("${m.icon} ${m.name}", { dispatch(Action.PickConflictMode(m.id)) }) }
                    QuietButton("Fix schedule", { dispatch(Action.OpenSettings(SettingsSection.MODES)) })
                }
            }
        }
    }
    Derive.prompts(s, env).forEach { r ->
        PanelCard(Modifier.fillMaxWidth().testTag("prompt_${r.id}")) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    T("It's time for", Type.small, p.muted)
                    T("${r.icon}  ${r.name}", Type.heading)
                }
                QuietButton("Not now", { dispatch(Action.DismissPrompt(r.id)) }, color = p.muted)
                PrimaryButton("Start", { dispatch(Action.StartRoutine(r.id)) })
            }
        }
    }
}

@Composable
private fun WidgetTitle(text: String) = T(text.uppercase(), Type.small.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(1.5f, androidx.compose.ui.unit.TextUnitType.Sp)), P().muted)

@Composable
private fun WaterCard(s: AppState, dispatch: Dispatch, modifier: Modifier) {
    val p = P()
    val prefs = s.data.prefs
    val n = s.data.daily.water
    PanelCard(modifier.testTag("waterCard")) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            WidgetTitle("Water")
            Row(verticalAlignment = Alignment.Bottom) {
                T("$n", Type.display, modifier = Modifier.testTag("waterCount"))
                T(" / ${prefs.waterTarget} glasses", Type.label, p.muted, Modifier.padding(bottom = 8.dp))
            }
            LinearProgressIndicator(
                progress = { (n.toFloat() / prefs.waterTarget).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(10.dp).clip(PillShape),
                color = p.accent, trackColor = p.cardStrong, drawStopIndicator = {},
            )
            T("${n * prefs.glassMl} ml", Type.small, p.muted)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("+ Glass", { dispatch(Action.AddWater(1)) }, Modifier.weight(1f), tag = "addWater")
                RoundIconButton("−", { dispatch(Action.AddWater(-1)) }, enabled = n > 0, tag = "removeWater")
            }
        }
    }
}

@Composable
private fun MealsCard(s: AppState, dispatch: Dispatch, modifier: Modifier) {
    val p = P()
    val names = s.data.prefs.mealNames
    val done = s.data.daily.mealsDone
    val next = names.indices.firstOrNull { it !in done }
    PanelCard(modifier.testTag("mealsCard")) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetTitle("Meals")
            T(if (next == null) "All eaten 🎉" else "Next: ${names[next]}", Type.heading)
            names.forEachIndexed { i, name ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                        .clickable { dispatch(Action.ToggleMeal(i)) }.padding(horizontal = 4.dp).testTag("meal_$i"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(30.dp)) { T(if (i in done) "✓" else "○", Type.heading, if (i in done) p.accent else p.muted) }
                    T(name, Type.label, if (i in done) p.muted else p.text)
                }
            }
        }
    }
}

@Composable
private fun CalendarCard(modifier: Modifier) {
    val p = P()
    PanelCard(modifier.testTag("calendarCard")) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WidgetTitle("Calendar")
            T("Not connected yet", Type.heading)
            T("Google Calendar arrives in the next build. Your events will show here.", Type.label, p.muted)
        }
    }
}

@Composable
private fun ModeWidgetCard(w: ModeWidget, mode: Mode, s: AppState, env: Env, dispatch: Dispatch, modifier: Modifier) {
    val p = P()
    val platform = LocalPlatform.current
    PanelCard(modifier.testTag("widget_${w.name}")) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (w) {
                ModeWidget.ROUTINES -> {
                    WidgetTitle("${mode.name} routines")
                    val list = Derive.routinesFor(s, mode.id).filter { it.id != mode.featuredRoutineId }
                    if (list.isEmpty()) T("No other routines assigned to ${mode.name}.", Type.label, p.muted)
                    list.take(4).forEach { r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            T("${r.icon}  ${r.name}", Type.label, modifier = Modifier.weight(1f))
                            SecondaryButton("Start", { dispatch(Action.StartRoutine(r.id)) })
                        }
                    }
                }
                ModeWidget.NEXT_MODE -> {
                    WidgetTitle("Coming up")
                    val nc = Derive.nextChange(s, env)
                    if (nc == null) T("Nothing else scheduled.", Type.label, p.muted)
                    else {
                        val (t, r) = nc
                        val at = Scheduler.toEpoch(t, env.zone)
                        T(when (r) { is ScheduleResult.Active -> "${r.mode.icon}  ${r.mode.name}"; is ScheduleResult.Conflict -> "⚠️ Conflict"; ScheduleResult.Free -> "Free time" }, Type.heading)
                        T("${shortTime(at, env, s.data.prefs.use24h)} · ${inText((at - s.now) / 60_000)}", Type.label, p.muted)
                    }
                }
                ModeWidget.BREAK_REMINDER -> {
                    WidgetTitle("Breaks")
                    val mins = Derive.minutesSinceBreak(s)
                    val due = mins >= s.data.prefs.breakIntervalMin
                    T(if (due) "Time for a short break" else "Last break ${mins} min ago", Type.heading, if (due) p.warn else p.text)
                    T("Every ${s.data.prefs.breakIntervalMin} min", Type.small, p.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("I took a break", { dispatch(Action.TookBreak) }, tag = "tookBreak")
                        if (due && s.data.routines.any { it.id == "r_hydrate" && it.enabled }) PrimaryButton("Hydrate + Reset", { dispatch(Action.StartRoutine("r_hydrate")) })
                    }
                }
                ModeWidget.SLEEP_AUDIO -> {
                    WidgetTitle("Sleep audio")
                    val uri = s.data.prefs.sleepAudioUri.trim()
                    T(if (uri.isEmpty()) "Add a playlist link in Settings → Music" else "Your sleep playlist", Type.label, p.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (uri.isNotEmpty()) PrimaryButton("▶  Play", {
                            if (!platform.openUri(uri)) dispatch(Action.ShowMessage("Couldn't open that link. Is Spotify installed?"))
                        })
                        SecondaryButton("Open Spotify", {
                            if (!platform.openSpotify()) dispatch(Action.ShowMessage("Spotify isn't installed on this tablet."))
                        })
                    }
                }
                ModeWidget.TASKS -> {
                    WidgetTitle("Tasks")
                    T("Not connected yet", Type.heading)
                    T("Google Tasks arrives in the next build, filtered for ${mode.name}.", Type.label, p.muted)
                }
                ModeWidget.CALENDAR -> {
                    WidgetTitle(if (mode.id == "bedtime") "Tomorrow" else "Next event")
                    T("Not connected yet", Type.heading)
                    T("Comes with Google Calendar in the next build.", Type.label, p.muted)
                }
            }
        }
    }
}
