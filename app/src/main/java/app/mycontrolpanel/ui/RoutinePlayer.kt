package app.mycontrolpanel.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

fun fmtTimer(ms: Long): String {
    val total = (ms + 999) / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

/** Full-screen guided routine (PRD §16–19): exactly one step at a time. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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

    val tint = LocalModeTint.current
    val padH = if (LocalWindow.current.compact) LocalWindow.current.gutter else 48.dp
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(p.bg2.copy(alpha = 0.25f), p.bg2.copy(alpha = 0.75f))))
        .padding(horizontal = padH, vertical = 24.dp).testTag("routineScreen")) {
        if (run.completed) {
            // Return automatically after a moment so the panel never gets stuck here.
            LaunchedEffect(run.startedAt) { delay(12_000); dispatch(Action.FinishRoutine) }
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Box(Modifier.size(150.dp).clip(PillShape).background(p.accentSoft), contentAlignment = Alignment.Center) {
                    T("✓", Type.clock.copy(fontSize = 96.sp, lineHeight = 96.sp), p.accent)
                }
                Spacer(Modifier.height(20.dp))
                T("${r.name} — done", Type.display, align = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                T("You did the whole thing. Back to your dashboard in a moment.", Type.body, p.muted)
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

            // the one step — words on the left, the visual (timer ring / breathing) on the right
            val compactUi = LocalWindow.current.compact
            val visualSize = if (compactUi) 220.dp else 300.dp
            // Wide: words left, visual right. Phone: stacked, scrollable.
            androidx.compose.foundation.layout.FlowRow(
                Modifier.weight(1f).fillMaxWidth().let { if (compactUi) it.verticalScroll(rememberScrollState()) else it },
                horizontalArrangement = Arrangement.spacedBy(40.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                maxItemsInEachRow = if (compactUi) 1 else 2,
            ) {
                val visual = remaining != null || step.breathing
                Column(Modifier.weight(1f), horizontalAlignment = if (visual) Alignment.Start else Alignment.CenterHorizontally) {
                    T(step.title, Type.display.copy(fontSize = 56.sp, lineHeight = 64.sp), align = if (visual) TextAlign.Start else TextAlign.Center,
                        modifier = Modifier.testTag("stepTitle"))
                    if (step.instruction.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        T(step.instruction, Type.body.copy(fontSize = 26.sp, lineHeight = 36.sp), p.muted, align = if (visual) TextAlign.Start else TextAlign.Center)
                    }
                    if (remaining != null) {
                        val finished = remaining == 0L
                        Spacer(Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            when {
                                run.runningSince != null -> SecondaryButton("Pause", { dispatch(Action.RoutinePause) }, tag = "pauseTimer")
                                finished -> SecondaryButton("Restart timer", { dispatch(Action.RoutineResume) })
                                else -> SecondaryButton("Resume", { dispatch(Action.RoutineResume) }, tag = "resumeTimer")
                            }
                        }
                        if (step.autoAdvance && !finished) T("Moves on by itself when the time's up", Type.small, p.muted, Modifier.padding(top = 8.dp))
                    }
                }
                if (step.breathing) {
                    BreathingGuide(Modifier.size(visualSize).align(Alignment.CenterVertically))
                } else if (remaining != null) {
                    val total = step.durationSec * 1000L
                    val finished = remaining == 0L
                    Box(Modifier.size(visualSize).align(Alignment.CenterVertically), contentAlignment = Alignment.Center) {
                        ProgressRing(remaining.toFloat() / total, Modifier.fillMaxSize(), stroke = 18.dp,
                            color = if (finished) p.warm else p.accent, track = p.card.copy(alpha = 0.8f))
                        T(if (finished) "Time's up" else fmtTimer(remaining),
                            if (finished) Type.title else Type.clock.copy(fontSize = 76.sp, lineHeight = 80.sp),
                            if (finished) p.warm else p.text, modifier = Modifier.testTag("timer"))
                    }
                }
            }

            // controls: DONE is the biggest, easiest target; Back is smaller and to the side
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                SecondaryButton("‹  Back", { dispatch(Action.RoutineBack) }, Modifier.widthIn(min = if (LocalWindow.current.compact) 100.dp else 160.dp), tag = "backStep", enabled = run.index > 0)
                PrimaryButton(if (run.index == r.steps.size - 1) "DONE  ·  finish" else "DONE",
                    { dispatch(Action.RoutineDone) }, Modifier.weight(1f), tag = "doneButton", tall = 96.dp,
                    style = Type.display.copy(fontSize = 34.sp))
            }
        }
    }
}
