package app.mycontrolpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mycontrolpanel.core.Action
import app.mycontrolpanel.core.AppState
import app.mycontrolpanel.core.Runner
import kotlinx.coroutines.delay

/** Things the UI needs from Android that are not app state. */
interface Platform {
    val versionName: String
    fun openUri(uri: String): Boolean
    fun openSpotify(): Boolean
    fun notificationsAllowed(): Boolean
    fun requestNotifications()
}

val LocalPlatform = staticCompositionLocalOf<Platform> { error("Platform not provided") }

fun fmtTimer(ms: Long): String {
    val total = (ms + 999) / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

/** Full-screen guided routine (PRD §16–19): exactly one step at a time. */
@Composable
fun RoutinePlayer(s: AppState, dispatch: Dispatch) {
    val p = P()
    val run = s.data.run
    val r = run?.let { rr -> s.data.routines.find { it.id == rr.routineId } }
    if (run == null || r == null) {
        // Defensive: should be unreachable (reducer only shows this screen with a valid run).
        LaunchedEffect(Unit) { dispatch(Action.ExitRoutine) }
        return
    }

    Box(Modifier.fillMaxSize().background(p.bg).padding(horizontal = 48.dp, vertical = 32.dp).testTag("routineScreen")) {
        if (run.completed) {
            // Return automatically after a moment so the panel never gets stuck here.
            LaunchedEffect(run.startedAt) { delay(12_000); dispatch(Action.FinishRoutine) }
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                T("✓", Type.clock, p.accent)
                T("${r.name} — done", Type.display, align = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                T("Nicely done. Back to your dashboard in a moment.", Type.body, p.muted)
                Spacer(Modifier.height(32.dp))
                PrimaryButton("Back to dashboard", { dispatch(Action.FinishRoutine) }, Modifier.widthIn(min = 360.dp),
                    tag = "finishButton", tall = 80.dp, style = Type.heading)
            }
            return@Box
        }

        val step = r.steps.getOrNull(run.index) ?: return@Box
        val remaining = Runner.remainingMs(run, r, s.now)

        Column(Modifier.fillMaxSize()) {
            // top bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    T("${r.icon}  ${r.name}", Type.heading, p.muted)
                    T("Step ${run.index + 1} of ${r.steps.size}", Type.label, p.muted, Modifier.testTag("stepCounter"))
                }
                HoldToExitButton({ dispatch(Action.ExitRoutine) })
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                r.steps.indices.forEach { i ->
                    Dot(if (i <= run.index) p.accent else p.line, if (i == run.index) 14.dp else 10.dp)
                }
            }

            // the one step
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                T(step.title, Type.display.copy(fontSize = 56.sp, lineHeight = 64.sp), align = TextAlign.Center, modifier = Modifier.testTag("stepTitle"))
                if (step.instruction.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    T(step.instruction, Type.body.copy(fontSize = 26.sp, lineHeight = 36.sp), p.muted, align = TextAlign.Center)
                }
                if (remaining != null) {
                    Spacer(Modifier.height(24.dp))
                    val finished = remaining == 0L
                    T(if (finished) "Time's up" else fmtTimer(remaining), Type.clock.copy(fontSize = 96.sp, lineHeight = 100.sp),
                        if (finished) p.accent else p.text, modifier = Modifier.testTag("timer"))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        when {
                            run.runningSince != null -> SecondaryButton("Pause", { dispatch(Action.RoutinePause) }, tag = "pauseTimer")
                            finished -> SecondaryButton("Restart timer", { dispatch(Action.RoutineResume) })
                            else -> SecondaryButton("Resume", { dispatch(Action.RoutineResume) }, tag = "resumeTimer")
                        }
                    }
                    if (step.autoAdvance && !finished) T("Moves on automatically", Type.small, p.muted, Modifier.padding(top = 6.dp))
                }
            }

            // controls: DONE is the biggest, easiest target; Back is smaller and to the side
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                SecondaryButton("‹  Back", { dispatch(Action.RoutineBack) }, Modifier.widthIn(min = 160.dp), tag = "backStep", enabled = run.index > 0)
                PrimaryButton(if (run.index == r.steps.size - 1) "DONE  ·  finish" else "DONE",
                    { dispatch(Action.RoutineDone) }, Modifier.weight(1f), tag = "doneButton", tall = 96.dp,
                    style = Type.display.copy(fontSize = 34.sp))
            }
        }
    }
}
