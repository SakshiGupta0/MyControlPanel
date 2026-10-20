package app.mycontrolpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.mycontrolpanel.core.Action
import app.mycontrolpanel.core.AppState
import app.mycontrolpanel.core.DeviceUse
import app.mycontrolpanel.core.MusicProvider
import app.mycontrolpanel.core.Need
import app.mycontrolpanel.core.OnbStep
import app.mycontrolpanel.core.Onboarding
import app.mycontrolpanel.core.OnboardingAnswers
import app.mycontrolpanel.core.Profile
import app.mycontrolpanel.core.Screen
import app.mycontrolpanel.core.fmtMinutes

/**
 * First-run setup in five friendly screens: hello + your name → this device → what helps (+ music)
 * → your day → all set (with where to change each thing later). It sits on today's sky with Mochi.
 */
@Composable
fun OnboardingScreen(s: AppState, sc: Screen.Onboarding, dispatch: Dispatch) {
    val p = P()
    val win = LocalWindow.current
    val a = sc.answers
    fun set(n: OnboardingAnswers) = dispatch(Action.OnbUpdate(n))
    Column(Modifier.fillMaxSize().testTag("onboarding")) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = win.gutter, vertical = if (win.short) 14.dp else 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 720.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sc.steps.indices.forEach { i -> Dot(if (i <= sc.step) p.accent else p.line, if (i == sc.step) 14.dp else 10.dp) }
                    Spacer(Modifier.padding(start = 4.dp))
                    T("${sc.step + 1} of ${sc.steps.size}", Type.small, p.muted)
                }
                when (sc.current) {
                    OnbStep.WELCOME -> { Welcome(s); NameField(s, a, ::set) }
                    OnbStep.DEVICE -> DeviceStep(a, ::set)
                    OnbStep.NEEDS -> NeedsStep(a, sc.returning, ::set)
                    OnbStep.DAY -> DayStep(s, a, ::set)
                    OnbStep.READY -> Ready(sc)
                }
            }
        }
        // footer — always visible, floating over the hills
        Row(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, p.bg2.copy(alpha = 0.92f), p.bg2.copy(alpha = 0.92f))))
                .padding(horizontal = win.gutter, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (sc.step > 0) SecondaryButton("‹ Back", { dispatch(Action.OnbBack) }, tag = "onbBack")
            else QuietButton("Skip setup", { dispatch(Action.OnbSkip) }, color = p.muted, tag = "onbSkip")
            Spacer(Modifier.weight(1f))
            PrimaryButton(
                when (sc.current) { OnbStep.WELCOME -> if (s.live.account == null) "Continue" else "Next"; OnbStep.READY -> "Let's go ✓"; else -> "Next" },
                { dispatch(Action.OnbNext) }, tag = "onbNext", tall = 58.dp,
            )
        }
    }
}

@Composable
private fun Title(title: String, sub: String? = null) {
    T(title, if (LocalWindow.current.compact) Type.title else Type.display)
    if (sub != null) T(sub, Type.body, P().muted)
}

@Composable
private fun Section(title: String) = T(title, Type.heading, modifier = Modifier.padding(top = 6.dp))

@Composable
private fun Welcome(s: AppState) {
    val p = P()
    val platform = LocalPlatform.current
    val compact = LocalWindow.current.compact
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        MochiHome(BeanMood.HAPPY, size = if (LocalWindow.current.short) 120.dp else if (compact) 150.dp else 190.dp)
    }
    T("Hi, I'm Mochi!", if (compact) Type.title else Type.display, modifier = Modifier.fillMaxWidth(), align = androidx.compose.ui.text.style.TextAlign.Center)
    T("Let's make you a calm little home screen: what time of day it is, the one thing to do next, and help with routines.",
        Type.body, p.muted)
    val acct = s.live.account
    if (acct != null) {
        PanelCard(Modifier.fillMaxWidth(), tint = p.accentSoft) { T("✓  Signed in as ${acct.name}. Your setup syncs across devices.", Type.label) }
    } else if (s.live.firebaseReady) {
        PanelCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                T("Sign in to sync your phone and tablet, and share lists with people. Optional.", Type.label)
                PrimaryButton("Sign in with Google", { platform.signIn() }, Modifier.fillMaxWidth(), tag = "onbSignIn", tall = 56.dp)
            }
        }
    }
}

@Composable
private fun NameField(s: AppState, a: OnboardingAnswers, set: (OnboardingAnswers) -> Unit) {
    val fallback = s.live.account?.name?.trim()?.substringBefore(' ')
    PanelCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            T("What should I call you?", Type.heading)
            PanelTextField(a.nickname, { set(a.copy(nickname = it.take(30))) }, "Your name or nickname", tag = "onbNickname")
            T(if (fallback.isNullOrBlank()) "Used in the greeting, like “Good morning, Sakshi”." else "Leave blank to use “$fallback”.", Type.small, P().muted)
        }
    }
}

@Composable
private fun ChoiceCard(title: String, blurb: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val p = P()
    PanelCard(Modifier.fillMaxWidth().clip(CardShape).clickable(onClick = onClick).testTag(tag), padding = 18.dp, tint = if (selected) p.accentSoft else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { T(title, Type.heading); T(blurb, Type.small, p.muted) }
            Box(Modifier.padding(start = 12.dp)) { CheckCircle(selected, onClick, size = 34.dp) }
        }
    }
}

@Composable
private fun DeviceStep(a: OnboardingAnswers, set: (OnboardingAnswers) -> Unit) {
    Title("This device")
    Section("How will you use it?")
    DeviceUse.values().forEach { u -> ChoiceCard(u.label, u.blurb, a.use == u, "use_${u.name}") { set(a.copy(use = u)) } }
    Section("How much on screen?")
    Profile.values().forEach { pr -> ChoiceCard(pr.label, pr.blurb, a.profile == pr, "profile_${pr.name}") { set(a.copy(profile = pr)) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NeedsStep(a: OnboardingAnswers, returning: Boolean, set: (OnboardingAnswers) -> Unit) {
    val p = P()
    val platform = LocalPlatform.current
    if (!returning) {
        Title("What would help?", "Pick any that fit — the dashboard adds just those things.")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Need.values().forEach { n ->
                val on = n in a.needs
                Chip("${n.icon}  ${n.label}", on, { set(a.copy(needs = if (on) a.needs - n else a.needs + n)) }, tag = "need_${n.name}")
            }
        }
        Section("Music & calm sounds")
    } else Title("Music & calm sounds")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MusicProvider.values().forEach { m -> Chip("${m.icon}  ${m.label}", a.music == m, { set(a.copy(music = m)) }, tag = "music_${m.name}") }
    }
    T(a.music.blurb, Type.small, p.muted)
    if (a.music == MusicProvider.APP) {
        val apps = remember { platform.musicApps() }
        if (apps.isEmpty()) T("No music apps found — you can pick one later.", Type.small, p.muted)
        else FlowRowChips(apps.map { it.second }, apps.indexOfFirst { it.first == a.musicAppPackage }) { i ->
            set(a.copy(musicAppPackage = apps[i].first, musicAppName = apps[i].second))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRowChips(labels: List<String>, selected: Int, tagPrefix: String = "chip", onPick: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, l -> Chip(l, i == selected, { onPick(i) }, tag = "${tagPrefix}_$i") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayStep(s: AppState, a: OnboardingAnswers, set: (OnboardingAnswers) -> Unit) {
    val use24 = s.data.prefs.use24h
    Title("Your day", "Roughly is fine — this sets when Morning, Work, Evening and Bedtime switch on.")
    ToggleRow("I work on weekdays", a.worksWeekdays, { set(a.copy(worksWeekdays = it)) },
        sub = if (a.worksWeekdays) "Work on weekdays, Day Off at weekends" else "No fixed work days — every day uses the Day Off shape", tag = "worksWeekdays")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TimeField("I wake up", a.wake, use24, { set(a.copy(wake = it)) }, tag = "wakeTime")
        TimeField(if (a.worksWeekdays) "Work starts" else "My day starts", a.workStart, use24, { set(a.copy(workStart = it)) })
        TimeField(if (a.worksWeekdays) "Work ends" else "My day ends", a.workEnd, use24, { set(a.copy(workEnd = it)) })
        TimeField("Bedtime", a.sleep, use24, { set(a.copy(sleep = it)) }, tag = "bedTime")
    }
    if (!Onboarding.validDay(a)) T("These need to be in order: wake-up → start → finish → bedtime.", Type.label, P().warn)
}

@Composable
private fun Ready(sc: Screen.Onboarding) {
    val p = P()
    val a = sc.answers
    val compact = LocalWindow.current.compact
    val features = if (sc.returning) Onboarding.preset(a.profile) else Onboarding.featuresFor(a)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { MochiHome(BeanMood.CHEER, size = if (compact) 130.dp else 170.dp) }
    T("You're all set${a.nickname.trim().takeIf { it.isNotBlank() }?.let { ", $it" } ?: ""}!", if (compact) Type.title else Type.display)
    PanelCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T("Your dashboard", Type.heading)
            T(features.sortedBy { it.ordinal }.joinToString(" · ") { it.label }, Type.small, p.muted)
            if (!sc.returning) {
                Spacer(Modifier.height(6.dp))
                T("Your day", Type.heading)
                T("☀️ Morning ${fmtMinutes(a.wake, false)} · " + (if (a.worksWeekdays) "💼 Work " else "🌿 Day ") +
                    "${fmtMinutes(a.workStart, false)}–${fmtMinutes(a.workEnd, false)} · 🌇 Evening · 🌙 Bedtime ${fmtMinutes(a.sleep, false)}", Type.small, p.muted)
            }
            Spacer(Modifier.height(6.dp))
            T("Music", Type.heading)
            T("${a.music.icon} ${a.music.label}" + if (a.music == MusicProvider.APP && a.musicAppName.isNotBlank()) " · ${a.musicAppName}" else "", Type.small, p.muted)
        }
    }
    PanelCard(Modifier.fillMaxWidth(), tint = p.accentSoft) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            T("Change anything later", Type.heading)
            listOf(
                "⚙  is at the bottom of the dashboard. In it:",
                "•  What's on screen → Dashboard",
                "•  Day times and phases → Modes",
                "•  Music and calm sounds → Music",
                "•  Your name, text size → Appearance",
                "•  This setup again → Dashboard → Set up again",
                "🫧  Feeling like it's a lot? Tap the bubble, any time.",
            ).forEach { T(it, Type.small, p.text.copy(alpha = 0.85f)) }
        }
    }
}
