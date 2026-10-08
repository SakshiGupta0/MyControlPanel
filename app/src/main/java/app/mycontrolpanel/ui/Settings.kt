package app.mycontrolpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.mycontrolpanel.core.Action
import app.mycontrolpanel.core.AppState
import app.mycontrolpanel.core.Dialog
import app.mycontrolpanel.core.Prefs
import app.mycontrolpanel.core.Scheduler
import app.mycontrolpanel.core.SettingsSection
import app.mycontrolpanel.core.describeConflict
import app.mycontrolpanel.core.fmtDays
import app.mycontrolpanel.core.fmtMinutes
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(s: AppState, section: SettingsSection, dispatch: Dispatch) {
    val p = P()
    Row(Modifier.fillMaxSize().background(p.bg).testTag("settingsScreen")) {
        // left rail
        Column(Modifier.width(270.dp).fillMaxHeight().background(p.cardStrong).padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PrimaryButton("‹  Dashboard", { dispatch(Action.GoDashboard) }, Modifier.fillMaxWidth(), tag = "closeSettings")
            Spacer(Modifier.height(14.dp))
            SettingsSection.values().forEach { sec ->
                val sel = sec == section
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (sel) p.card else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { dispatch(Action.OpenSettings(sec)) }
                        .padding(horizontal = 16.dp).testTag("settingsSection_${sec.name}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) { T(sec.label, if (sel) Type.heading else Type.label, if (sel) p.text else p.muted) }
            }
        }
        // content
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) {
            T(section.label, Type.display)
            when (section) {
                SettingsSection.MODES -> ModesSection(s, dispatch)
                SettingsSection.ROUTINES -> RoutinesSection(s, dispatch)
                SettingsSection.WIDGETS -> WidgetsSection(s, dispatch)
                SettingsSection.TASKS_CALENDAR -> TasksCalendarSection()
                SettingsSection.MUSIC -> MusicSection(s, dispatch)
                SettingsSection.APPEARANCE -> AppearanceSection(s, dispatch)
                SettingsSection.DEVICE -> DeviceSection(s, dispatch)
                SettingsSection.RESET -> ResetSection(dispatch)
                SettingsSection.ABOUT -> AboutSection()
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

private fun upd(s: AppState, dispatch: Dispatch, f: (Prefs) -> Prefs) = dispatch(Action.UpdatePrefs(f(s.data.prefs)))

@Composable
private fun ModesSection(s: AppState, dispatch: Dispatch) {
    val p = P()
    val use24h = s.data.prefs.use24h
    val conflicts = Scheduler.conflicts(s.data.modes)
    T("Modes are the phases of your day. The dashboard switches automatically on schedule; you can always switch by hand from the mode button.", Type.body, p.muted)
    if (conflicts.isNotEmpty()) {
        PanelCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                T("⚠️  Schedule overlaps need fixing", Type.heading, p.warn)
                conflicts.take(6).forEach { T(describeConflict(it, use24h), Type.label, p.muted) }
                T("Open either mode to change its times or choose which one wins.", Type.small, p.muted)
            }
        }
    }
    PrimaryButton("+  New mode", { dispatch(Action.NewMode) }, tag = "newModeButton")
    s.data.modes.sortedBy { it.order }.forEach { m ->
        PanelCard(Modifier.fillMaxWidth().clickable { dispatch(Action.EditMode(m.id)) }.testTag("modeRow_${m.name}")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(m.icon, Type.display, modifier = Modifier.padding(end = 18.dp))
                Column(Modifier.weight(1f)) {
                    T(m.name, Type.heading)
                    T(if (m.scheduled) "${fmtMinutes(m.start, use24h)} – ${fmtMinutes(m.end, use24h)} · ${fmtDays(m.days)}" else "Manual only — not on the schedule",
                        Type.label, p.muted)
                    val wins = m.winsOver.mapNotNull { id -> s.data.modes.find { it.id == id }?.name }
                    if (wins.isNotEmpty()) T("Takes priority over ${wins.joinToString()}", Type.small, p.muted)
                }
                SecondaryButton("Edit", { dispatch(Action.EditMode(m.id)) })
            }
        }
    }
}

@Composable
private fun RoutinesSection(s: AppState, dispatch: Dispatch) {
    val p = P()
    val use24h = s.data.prefs.use24h
    T("Routines are guided, one step at a time. Quick Routines are available from the dashboard in any mode.", Type.body, p.muted)
    PrimaryButton("+  New routine", { dispatch(Action.NewRoutine) }, tag = "newRoutineButton")
    s.data.routines.sortedBy { it.order }.forEach { r ->
        PanelCard(Modifier.fillMaxWidth().clickable { dispatch(Action.EditRoutine(r.id)) }.testTag("routineRow_${r.name}")) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                T(r.icon, Type.display, modifier = Modifier.padding(end = 18.dp))
                Column(Modifier.weight(1f)) {
                    T(r.name + if (!r.enabled) "  (off)" else "", Type.heading, if (r.enabled) p.text else p.muted)
                    val modes = r.modeIds.mapNotNull { id -> s.data.modes.find { it.id == id }?.name }
                    val bits = listOfNotNull(
                        "${r.steps.size} steps",
                        if (r.quick) "Quick" else null,
                        modes.takeIf { it.isNotEmpty() }?.joinToString(),
                        r.schedule?.let { "${fmtMinutes(it.time, use24h)} ${fmtDays(it.days)}" },
                    )
                    T(bits.joinToString(" · "), Type.label, p.muted)
                }
                SecondaryButton("Edit", { dispatch(Action.EditRoutine(r.id)) })
            }
        }
    }
}

@Composable
private fun WidgetsSection(s: AppState, dispatch: Dispatch) {
    val p = P()
    val pr = s.data.prefs
    T("These widgets are on the dashboard in every mode. Mode-specific widgets are chosen and ordered inside each mode (Settings → Modes → Edit).", Type.body, p.muted)
    PanelCard(Modifier.fillMaxWidth()) {
        Column {
            T("Water", Type.heading)
            Stepper("Daily target", pr.waterTarget, { v -> upd(s, dispatch) { it.copy(waterTarget = v) } }, 1..30, format = { "$it glasses" })
            Stepper("Glass size", pr.glassMl, { v -> upd(s, dispatch) { it.copy(glassMl = v) } }, 100..1000, step = 50, format = { "$it ml" })
        }
    }
    PanelCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            T("Meals", Type.heading)
            T("Comma-separated, in order. Resets every morning at midnight.", Type.small, p.muted)
            var text by remember { mutableStateOf(pr.mealNames.joinToString(", ")) }
            PanelTextField(text, { txt ->
                text = txt
                val names = txt.split(",").map { it.trim() }.filter { it.isNotEmpty() }.take(6)
                if (names.isNotEmpty()) upd(s, dispatch) { it.copy(mealNames = names) }
            }, "Meals")
        }
    }
    PanelCard(Modifier.fillMaxWidth()) {
        Column {
            T("Break reminder (Work)", Type.heading)
            Stepper("Remind me every", pr.breakIntervalMin, { v -> upd(s, dispatch) { it.copy(breakIntervalMin = v) } }, 10..240, step = 5, format = { "$it min" })
        }
    }
}

@Composable
private fun TasksCalendarSection() {
    val p = P()
    PanelCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            T("Not connected in this version", Type.heading)
            T("Google Tasks and Google Calendar come in the next build (PRD stage V4). They need Google sign-in on the tablet, set up through a Google Cloud project — I'll walk you through that when we get there.", Type.body, p.muted)
            T("Until then, the Tasks and Calendar widgets say “Not connected yet” rather than showing anything made up.", Type.body, p.muted)
        }
    }
}

@Composable
private fun MusicSection(s: AppState, dispatch: Dispatch) {
    val p = P()
    val platform = LocalPlatform.current
    T("Spotify is opened with Android's normal links. This app doesn't control playback or log in to Spotify.", Type.body, p.muted)
    PanelCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            T("Sleep playlist", Type.heading)
            T("In Spotify: ⋯ → Share → Copy link to playlist, then paste here.", Type.small, p.muted)
            PanelTextField(s.data.prefs.sleepAudioUri, { v -> upd(s, dispatch) { it.copy(sleepAudioUri = v) } }, "Playlist link", tag = "sleepUri")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PrimaryButton("▶  Test link", {
                    val uri = s.data.prefs.sleepAudioUri.trim()
                    if (uri.isEmpty()) dispatch(Action.ShowMessage("Paste a playlist link first."))
                    else if (!platform.openUri(uri)) dispatch(Action.ShowMessage("Couldn't open that link."))
                })
                SecondaryButton("Open Spotify", { if (!platform.openSpotify()) dispatch(Action.ShowMessage("Spotify isn't installed on this tablet.")) })
            }
        }
    }
}

@Composable
private fun AppearanceSection(s: AppState, dispatch: Dispatch) {
    val p = P()
    val pr = s.data.prefs
    PanelCard(Modifier.fillMaxWidth()) {
        Column {
            ToggleRow("24-hour clock", pr.use24h, { v -> upd(s, dispatch) { it.copy(use24h = v) } }, tag = "toggle24h")
            ToggleRow("High contrast", pr.highContrast, { v -> upd(s, dispatch) { it.copy(highContrast = v) } }, sub = "Pure white background and black text")
            ToggleRow("Reduce motion", pr.reduceMotion, { v -> upd(s, dispatch) { it.copy(reduceMotion = v) } }, sub = "No screen transitions")
            Spacer(Modifier.height(10.dp))
            T("Text size  ·  ${(pr.textScale * 100).roundToInt()}%", Type.label)
            Slider(
                value = pr.textScale, onValueChange = { v -> upd(s, dispatch) { it.copy(textScale = (v * 20).roundToInt() / 20f) } },
                valueRange = 0.8f..1.4f, steps = 11,
                colors = SliderDefaults.colors(thumbColor = p.accent, activeTrackColor = p.accent, inactiveTrackColor = p.cardStrong),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceSection(s: AppState, dispatch: Dispatch) {
    val p = P()
    val pr = s.data.prefs
    val platform = LocalPlatform.current
    PanelCard(Modifier.fillMaxWidth()) {
        Column {
            ToggleRow("Keep screen on", pr.keepScreenOn, { v -> upd(s, dispatch) { it.copy(keepScreenOn = v) } }, sub = "While the panel is open")
            ToggleRow("Full screen", pr.fullscreen, { v -> upd(s, dispatch) { it.copy(fullscreen = v) } }, sub = "Hide status and navigation bars (swipe from an edge to see them)")
            ToggleRow("Night dimming", pr.nightDim, { v -> upd(s, dispatch) { it.copy(nightDim = v) } }, sub = "Low brightness and a dark palette in the modes below")
            if (pr.nightDim) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    s.data.modes.forEach { m ->
                        val on = m.id in pr.dimModeIds
                        Chip("${m.icon} ${m.name}", on, { upd(s, dispatch) { it.copy(dimModeIds = if (on) it.dimModeIds - m.id else it.dimModeIds + m.id) } })
                    }
                }
            }
        }
    }
    PanelCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            T("Routine notifications", Type.heading)
            val allowed = platform.notificationsAllowed()
            T(if (allowed) "Allowed. Routines set to “card + quiet notification” will post a silent notification when the panel isn't on screen."
              else "Not allowed yet. Needed only for routines set to “card + quiet notification”.", Type.body, p.muted)
            if (!allowed) PrimaryButton("Allow notifications", { platform.requestNotifications() })
            T("Tip: on Samsung, set this app's battery usage to “Unrestricted” (Settings → Apps → My Control Panel → Battery) so reminders aren't delayed.", Type.small, p.muted)
        }
    }
}

@Composable
private fun ResetSection(dispatch: Dispatch) {
    val p = P()
    PanelCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            T("Reset to defaults", Type.heading)
            T("Replaces all your modes, routines, widget choices and preferences with the original set, and clears today's water and meals. This can't be undone.", Type.body, p.muted)
            SecondaryButton("Reset everything…", { dispatch(Action.OpenDialog(Dialog.ConfirmReset)) }, tag = "resetButton", color = p.danger)
        }
    }
}

@Composable
private fun AboutSection() {
    val p = P()
    val v = LocalPlatform.current.versionName
    PanelCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            T("My Control Panel  v$v", Type.heading)
            T("Core engine build: dashboard, modes & schedule, manual override, routines with timers, Quick Routines, editors, persistence, reset, always-on display.", Type.body, p.muted)
            T("Not yet: Google Tasks, Google Calendar, Spotify playback control, weather.", Type.body, p.muted)
            T("Everything stays on this tablet. Nothing is sent anywhere.", Type.body, p.muted)
        }
    }
}
