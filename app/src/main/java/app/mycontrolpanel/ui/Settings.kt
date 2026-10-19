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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
    val win = LocalWindow.current
    val content: @Composable () -> Unit = {
        T(section.label, if (win.compact) Type.title else Type.display)
        when (section) {
            SettingsSection.MODES -> ModesSection(s, dispatch)
            SettingsSection.ROUTINES -> RoutinesSection(s, dispatch)
            SettingsSection.WIDGETS -> WidgetsSection(s, dispatch)
            SettingsSection.TASKS -> TasksSection(s, dispatch)
            SettingsSection.CALENDAR -> CalendarSection(s, dispatch)
            SettingsSection.MUSIC -> MusicSection(s, dispatch)
            SettingsSection.APPEARANCE -> AppearanceSection(s, dispatch)
            SettingsSection.DEVICE -> DeviceSection(s, dispatch)
            SettingsSection.ACCOUNT -> AccountSection(s, dispatch)
            SettingsSection.RESET -> ResetSection(dispatch)
            SettingsSection.ABOUT -> AboutSection()
        }
        Spacer(Modifier.height(40.dp))
    }
    if (win.compact) {
        // Phone: back button + a swipeable row of sections on top, content below.
        Column(Modifier.fillMaxSize().background(p.bg).testTag("settingsScreen")) {
            Column(Modifier.fillMaxWidth().background(p.cardStrong).padding(horizontal = win.gutter, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton("‹  Dashboard", { dispatch(Action.GoDashboard) }, tag = "closeSettings")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsSection.values().forEach { sec ->
                        Chip(sec.label, sec == section, { dispatch(Action.OpenSettings(sec)) }, tag = "settingsSection_${sec.name}")
                    }
                }
            }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = win.gutter, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
        }
        return
    }
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
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)) { content() }
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WidgetsSection(s: AppState, dispatch: Dispatch) {
    val p = P()
    val pr = s.data.prefs
    val current = app.mycontrolpanel.core.Onboarding.profileOf(pr.features)
    T("Choose how much this device shows. Each device keeps its own choice — your phone can be simple while the wall panel shows everything.", Type.body, p.muted)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        app.mycontrolpanel.core.Profile.values().forEach { prof ->
            val sel = prof == current
            PanelCard(Modifier.widthIn(min = 220.dp, max = 340.dp).clickable {
                dispatch(Action.SetFeatures(app.mycontrolpanel.core.Onboarding.preset(prof)))
            }.testTag("preset_${prof.name}"), tint = if (sel) p.accentSoft else null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    T((if (sel) "✓  " else "") + prof.label, Type.heading)
                    T(prof.blurb, Type.small, p.muted)
                }
            }
        }
    }
    if (current == null) T("Custom mix", Type.label, p.accent)
    PanelCard(Modifier.fillMaxWidth()) {
        Column {
            T("What's on the dashboard", Type.heading)
            app.mycontrolpanel.core.Feature.values().forEach { feat ->
                ToggleRow(feat.label, feat in pr.features, { on ->
                    dispatch(Action.SetFeatures(if (on) pr.features + feat else pr.features - feat))
                }, sub = feat.hint, tag = "feature_${feat.name}")
            }
            T("Mode widgets (tasks, routines, focus, calendar, music) are chosen per mode in Settings → Modes → Edit.", Type.small, p.muted)
        }
    }
    PanelCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            T("Set up again", Type.heading)
            T("Answer the setup questions again. This can change your mode times and dashboard.", Type.small, p.muted)
            SecondaryButton("Run setup again", { dispatch(Action.StartOnboarding) }, tag = "runSetup")
        }
    }
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
private fun TasksSection(s: AppState, dispatch: Dispatch) {
    val p = P()
    val pr = s.data.prefs
    T("Built for brains that juggle a lot: a small list for today, an inbox for everything else, and one timer at a time.", Type.body, p.muted)
    PanelCard(Modifier.fillMaxWidth()) {
        Column {
            Stepper("Today's list holds", pr.todayLimit, { v -> upd(s, dispatch) { it.copy(todayLimit = v) } }, 1..7, format = { "$it things" })
            Stepper("Focus timer length", pr.focusMin, { v -> upd(s, dispatch) { it.copy(focusMin = v) } }, 5..90, step = 5, format = { "$it min" })
        }
    }
    PanelCard(Modifier.fillMaxWidth()) {
        Column {
            T("Heads-up before the mode changes", Type.heading)
            T("Helps with time blindness: a gentle card (and optional soft chime) before Work ends, Bedtime starts, and so on.", Type.small, p.muted)
            Stepper("Warn me", pr.transitionWarnMin, { v -> upd(s, dispatch) { it.copy(transitionWarnMin = v) } }, 0..30, step = 5,
                format = { if (it == 0) "Off" else "$it min before" })
            ToggleRow("Soft chime with the heads-up", pr.chimeOnTransition, { v -> upd(s, dispatch) { it.copy(chimeOnTransition = v) } })
        }
    }
    PanelCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            T("Google Tasks", Type.heading)
            T("Two-way sync with Google Tasks lives in Settings → Account & sharing.", Type.body, p.muted)
        }
    }
}

@Composable
private fun CalendarSection(s: AppState, dispatch: Dispatch) {
    val p = P()
    val platform = LocalPlatform.current
    val pr = s.data.prefs
    T("Reads the calendars already synced to this tablet (your Google Calendar included). Read-only, and nothing leaves the device.", Type.body, p.muted)
    PanelCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ToggleRow("Show my calendar", pr.calendarOn, { on ->
                upd(s, dispatch) { it.copy(calendarOn = on) }
                if (on && !platform.calendarAllowed()) platform.requestCalendar()
            }, tag = "calendarSwitch")
            if (pr.calendarOn) {
                val status = when (s.live.calendarStatus) {
                    app.mycontrolpanel.core.CalendarStatus.OK -> "Connected · ${s.live.calendar.size} upcoming event(s) loaded"
                    app.mycontrolpanel.core.CalendarStatus.NEEDS_PERMISSION -> "Needs permission to read your calendar"
                    app.mycontrolpanel.core.CalendarStatus.ERROR -> "Couldn't read the calendar — will retry"
                    app.mycontrolpanel.core.CalendarStatus.OFF -> "Starting…"
                }
                T(status, Type.label, p.muted)
                if (!platform.calendarAllowed()) PrimaryButton("Allow calendar access", { platform.requestCalendar() })
                T("Don't see your events? On the tablet: Settings → Accounts → Google → turn on Calendar sync.", Type.small, p.muted)
            }
        }
    }
}

@Composable
private fun MusicSection(s: AppState, dispatch: Dispatch) {
    val p = P()
    val platform = LocalPlatform.current
    T("Add the Music widget to a mode (Settings → Modes → Edit) to see what's playing on Spotify, with album art and controls, and to pick something to play. It uses Spotify's official connection through the Spotify app — playback control needs Spotify Premium. Your Spotify password never touches this app.", Type.body, p.muted)
    LocalSpotify.current?.let { eng ->
        val st = eng.state.collectAsState().value
        T("Spotify: " + when (st.status) {
            app.mycontrolpanel.data.SpotifyEngine.Status.CONNECTED -> "connected ✓"
            app.mycontrolpanel.data.SpotifyEngine.Status.NOT_CONFIGURED -> "not set up in this build"
            app.mycontrolpanel.data.SpotifyEngine.Status.NOT_INSTALLED -> "app not installed"
            else -> st.message ?: "not connected"
        }, Type.label, p.muted)
        if (st.status != app.mycontrolpanel.data.SpotifyEngine.Status.CONNECTED && st.status != app.mycontrolpanel.data.SpotifyEngine.Status.NOT_CONFIGURED)
            PrimaryButton("Connect Spotify", { eng.connect(interactive = true) })
    }
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
    PanelCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            T("Play / pause / skip", Type.heading)
            T("The Music widget's buttons control whatever is playing (Spotify, YouTube Music…). No permission needed.", Type.body, p.muted)
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
private fun AccountSection(s: AppState, dispatch: Dispatch) {
    val p = P()
    val platform = LocalPlatform.current
    val acct = s.live.account
    if (!s.live.firebaseReady) {
        T("Accounts aren't switched on in this build.", Type.body, p.muted); return
    }
    if (acct == null) {
        PanelCard(Modifier.fillMaxWidth(), tint = p.accentSoft) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                T("Use your panel everywhere", Type.title)
                T("Sign in with Google to sync your modes, routines, tasks and settings between this tablet and your phone — and to share lists with people you choose.", Type.body, p.muted)
                T("The first time you sign in, everything set up on this tablet goes to your account. Signing in on another device later brings it there.", Type.small, p.muted)
                PrimaryButton("Sign in with Google", { platform.signIn() }, tag = "signIn", tall = 60.dp, style = Type.heading)
            }
        }
        T("You can keep using the panel without an account — everything works on this tablet alone.", Type.small, p.muted)
        return
    }
    PanelCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Box(Modifier.padding(end = 16.dp).clip(PillShape).background(p.accentSoft).padding(horizontal = 18.dp, vertical = 10.dp)) {
                T(acct.name.take(1).uppercase(), Type.title, p.accent)
            }
            Column(Modifier.weight(1f)) {
                T(acct.name, Type.heading)
                T(acct.email, Type.small, p.muted)
                T(when (s.live.syncStatus) {
                    app.mycontrolpanel.core.SyncStatus.SYNCED -> "✓ Synced"
                    app.mycontrolpanel.core.SyncStatus.CONNECTING -> "Connecting…"
                    app.mycontrolpanel.core.SyncStatus.OFFLINE -> "Offline — changes will sync when you're back online"
                    app.mycontrolpanel.core.SyncStatus.ERROR -> "⚠️ ${s.live.syncError ?: "Sync problem"}"
                    app.mycontrolpanel.core.SyncStatus.LOCAL_ONLY -> "Not syncing"
                }, Type.label, if (s.live.syncStatus == app.mycontrolpanel.core.SyncStatus.ERROR) p.danger else p.muted, Modifier.testTag("syncStatus"))
            }
            SecondaryButton("Sign out", { platform.signOut() }, tag = "signOut")
        }
    }

    // ---- shared spaces ----
    T("Shared spaces", Type.title)
    T("A shared space is a task list you share — flat chores, a trip, a project. Everyone in it sees the same tasks and who ticked them off. Your own modes, routines and tasks stay private.", Type.body, p.muted)
    s.live.spaces.forEach { sp ->
        PanelCard(Modifier.fillMaxWidth().testTag("space_${sp.name}")) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("🏠  ${sp.name}", Type.heading, modifier = Modifier.weight(1f))
                    QuietButton("Open list", { dispatch(Action.OpenTasks("space:${sp.id}")) })
                }
                T(sp.members.values.joinToString(" · "), Type.small, p.muted)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    T("Invite code  ", Type.label, p.muted)
                    T(sp.inviteCode, Type.title.copy(letterSpacing = androidx.compose.ui.unit.TextUnit(4f, androidx.compose.ui.unit.TextUnitType.Sp)), p.accent)
                    Spacer(Modifier.weight(1f))
                    SecondaryButton("Share invite", { platform.share(
                        "Join my “${sp.name}” list on My Control Panel: open the app → Settings → Account & sharing → Join, and enter code ${sp.inviteCode}") })
                    QuietButton("Leave", { platform.leaveSpace(sp.id) }, color = p.danger)
                }
            }
        }
    }
    var newName by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    AdaptivePair(
        { m -> PanelCard(m) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                T("Create a space", Type.heading)
                PanelTextField(newName, { newName = it }, "Name, e.g. Flat chores", tag = "spaceName")
                PrimaryButton("Create", { platform.createSpace(newName); newName = "" }, enabled = newName.isNotBlank(), tag = "createSpace")
            }
        } },
        { m -> PanelCard(m) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                T("Join with a code", Type.heading)
                PanelTextField(code, { code = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(6) }, "6-letter code", tag = "joinCode")
                PrimaryButton("Join", { platform.joinSpace(code); code = "" }, enabled = code.length == 6, tag = "joinSpace")
            }
        } },
    )

    // ---- Google Tasks ----
    T("Google Tasks", Type.title)
    GoogleTasksCard(s, dispatch)
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
            T("Dashboard with one Right-now card, modes & schedule, heads-up before changes, routines with timers and breathing, Quick Routines, built-in tasks (inbox, today's three, pick-one), focus timer, calendar, music controls.", Type.body, p.muted)
            T("Accounts (Sign in with Google), sync across your devices, shared task spaces, Google Tasks two-way sync.", Type.body, p.muted)
            T("Fonts: Fraunces and DM Sans (SIL Open Font License).", Type.small, p.muted)
            T("Without an account everything stays on this tablet. Signed in, your data is stored in your own Firebase project and only you (and people in spaces you share) can read it.", Type.body, p.muted)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GoogleTasksCard(s: AppState, dispatch: Dispatch) {
    val p = P()
    val platform = LocalPlatform.current
    var picking by remember { mutableStateOf(false) }
    var copyExisting by remember { mutableStateOf(false) }
    val pr = s.data.prefs
    val showPicker = picking || (!s.data.gTasksOn && s.live.gLists.isNotEmpty())
    PanelCard(Modifier.fillMaxWidth().testTag("gtasksCard")) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            T("Sync one Google Tasks list with the app. Organise everything else in Google Tasks however you like — only the list you pick shows up here, and tasks you add here go into that list.", Type.body, p.muted)
            if (s.data.gTasksOn) T("Syncing with:  ${pr.gListTitle}", Type.heading)
            s.live.gTasksStatus?.let { T(it, Type.label, p.muted, Modifier.testTag("gtasksStatus")) }
            if (showPicker) {
                T("Which list?", Type.label, p.muted)
                if (s.live.gLists.isEmpty()) T("Loading your lists…", Type.small, p.muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.live.gLists.forEach { (id, title) ->
                        Chip(title, s.data.gTasksOn && id == pr.gListId, {
                            dispatch(Action.ChooseGList(id, title, copyExisting)); picking = false
                        }, tag = "glist_$title")
                    }
                    if (s.live.gLists.none { it.second == "Control Panel" })
                        Chip("＋ New list “Control Panel”", false, { platform.createGList("Control Panel", copyExisting); picking = false })
                }
                ToggleRow("Also copy tasks I already have in the app into this list", copyExisting, { copyExisting = it })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!s.data.gTasksOn && s.live.gLists.isEmpty()) PrimaryButton("Connect Google Tasks", { platform.connectGoogleTasks() }, tag = "connectGTasks")
                if (s.data.gTasksOn) {
                    SecondaryButton("Sync now", { platform.syncGoogleTasksNow() })
                    SecondaryButton(if (picking) "Cancel" else "Change list", { if (!picking) platform.loadGLists(); picking = !picking })
                    QuietButton("Turn off", { dispatch(Action.SetGoogleTasks(false)) }, color = p.muted)
                }
            }
        }
    }
}
