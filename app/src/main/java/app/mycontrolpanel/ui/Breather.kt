package app.mycontrolpanel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.mycontrolpanel.core.Action
import app.mycontrolpanel.core.AppState
import app.mycontrolpanel.system.SoundService
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val BubbleLight = Color(0xFFE4E6FA)
private val BubbleMid = Color(0xFFC3CDF2)
private val BubbleDeep = Color(0xFF9FB0E6)

/** A soft bubble floating at the edge of every screen. One tap → breathe. */
@Composable
fun BreatherBubble(onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val wide = !LocalWindow.current.compact
    val tr = rememberInfiniteTransition(label = "bubble")
    val breathe by tr.animateFloat(0.94f, 1.06f, infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val orbit by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "o")
    val p = P()
    Row(
        modifier
            .shadow(14.dp, PillShape, ambientColor = Color(0xFF3B4790).copy(alpha = 0.25f), spotColor = Color(0xFF3B4790).copy(alpha = 0.3f))
            .clip(PillShape)
            .background(Brush.linearGradient(listOf(BubbleLight, BubbleMid)))
            .clickable(onClick = onOpen)
            .semantics { contentDescription = "Take a breather" }
            .padding(start = 8.dp, end = if (wide) 22.dp else 8.dp, top = 8.dp, bottom = 8.dp)
            .testTag("breatherBubble"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(if (wide) 62.dp else 58.dp).scale(if (reduce) 1f else breathe)) {
            val r = size.minDimension / 2
            val c = center
            drawCircle(Brush.radialGradient(listOf(Color.White, BubbleMid, BubbleDeep), Offset(c.x - r * 0.3f, c.y - r * 0.35f), r * 1.3f), r * 0.82f, c)
            drawCircle(Color.White.copy(alpha = 0.9f), r * 0.82f, c, style = Stroke(r * 0.06f))
            drawCircle(Color.White.copy(alpha = 0.85f), r * 0.16f, Offset(c.x - r * 0.32f, c.y - r * 0.34f))
            // two tiny bubbles orbiting
            for (k in 0 until 2) {
                val a = (orbit + k * 0.5f) * 2 * PI
                val o = Offset(c.x + cos(a).toFloat() * r * 0.95f, c.y + sin(a).toFloat() * r * 0.95f)
                drawCircle(Color.White.copy(alpha = 0.95f), r * 0.10f, o)
                drawCircle(BubbleDeep, r * 0.10f, o, style = Stroke(r * 0.03f))
            }
        }
        if (wide) { Spacer(Modifier.width(10.dp)); T("Take a breather", Type.label, Color(0xFF2E3566)) }
    }
}

/** Full-screen calm: a bubble that grows as you breathe in, Mochi resting inside it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BreatherScreen(s: AppState, dispatch: Dispatch, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val platform = LocalPlatform.current
    val win = LocalWindow.current
    val sound by SoundService.state.collectAsState()
    // One 16-second box-breathing cycle: in 4 · hold 4 · out 4 · rest 4.
    val cycle = rememberInfiniteTransition(label = "breath")
    val t by cycle.animateFloat(0f, 1f, infiniteRepeatable(tween(16_000, easing = LinearEasing)), label = "t")
    var breaths by remember { mutableIntStateOf(0) }
    val lastT = remember { floatArrayOf(0f) }
    androidx.compose.runtime.SideEffect { if (t < lastT[0] - 0.5f) breaths++; lastT[0] = t }
    fun ease(x: Float) = FastOutSlowInEasing.transform(x.coerceIn(0f, 1f))
    val grow = when {
        t < 0.25f -> 0.55f + 0.45f * ease(t / 0.25f)
        t < 0.50f -> 1f
        t < 0.75f -> 1f - 0.45f * ease((t - 0.5f) / 0.25f)
        else -> 0.55f
    }
    val cue = when { t < 0.25f -> "Breathe in…"; t < 0.5f -> "Hold"; t < 0.75f -> "Breathe out…"; else -> "Rest" }
    val ink = Color(0xFF2B3160)
    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFDCD8F3), Color(0xFFEDE4F4), Color(0xFFFBEFE6))))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}   // nothing behind is tappable
            .testTag("breather"),
    ) {
        PhaseBackdrop(Phase.CALM, Modifier.graphicsLayer { alpha = 0.35f })
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = win.gutter, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            T("Take a breather", Type.title, ink)
            T("Nothing else needs you right now. Follow the bubble.", Type.body, ink.copy(alpha = 0.7f), align = TextAlign.Center)
            val dia = if (win.compact) 260.dp else 340.dp
            Box(Modifier.size(dia), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val r = size.minDimension / 2 * grow
                    drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.0f), BubbleMid.copy(alpha = 0.35f)), center, size.minDimension / 2), size.minDimension / 2, center)
                    drawCircle(Brush.radialGradient(listOf(Color.White, BubbleLight, BubbleMid), Offset(center.x - r * 0.3f, center.y - r * 0.35f), r * 1.4f), r, center)
                    drawCircle(Color.White, r, center, style = Stroke(6f))
                    drawCircle(Color.White.copy(alpha = 0.8f), r * 0.14f, Offset(center.x - r * 0.45f, center.y - r * 0.45f))
                }
                Bean(BeanMood.BREATHE, Modifier.scale(0.75f + grow * 0.3f), size = dia * 0.42f)
            }
            T(cue, Type.display, ink, Modifier.testTag("breathCue"))
            T(if (breaths == 0) "4 in · 4 hold · 4 out · 4 rest" else "$breaths slow breath${if (breaths == 1) "" else "s"}", Type.label, ink.copy(alpha = 0.6f))
            Spacer(Modifier.height(4.dp))
            FlowRow(Modifier.widthIn(max = 640.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (sound.playing) SecondaryButton("■  Stop sound", { platform.stopSound() })
                else SecondaryButton("🌧️  Add rain", { platform.playAmbient(app.mycontrolpanel.core.Ambient.RAIN, 15) }, tag = "breatherRain")
                if (s.data.routines.any { it.id == "r_calm_down" && it.enabled })
                    SecondaryButton("Guided calm-down", { onClose(); dispatch(Action.StartRoutine("r_calm_down")) })
                PrimaryButton("I feel a bit better ♡", onClose, tag = "breatherClose", tall = 60.dp)
            }
        }
    }
}

/** A short hello on launch: Mochi's house pops in on today's sky. No taps are blocked. */
@Composable
fun SplashIntro(phase: Phase, onDone: () -> Unit) {
    val reduce = LocalReduceMotion.current
    val pop = remember { Animatable(if (reduce) 1f else 0.6f) }
    val fade = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        if (!reduce) pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        delay(if (reduce) 600 else 900)
        fade.animateTo(0f, tween(450))
        onDone()
    }
    val pal = phase.palette
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = fade.value }.testTag("splash"), contentAlignment = Alignment.Center) {
        PhaseBackdrop(phase)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MochiHome(if (phase == Phase.NIGHT) BeanMood.SLEEPY else BeanMood.HAPPY,
                Modifier.graphicsLayer { scaleX = pop.value; scaleY = pop.value }, size = 220.dp)
            T("My Control Panel", Type.display, pal.text)
            T("One calm thing at a time", Type.label, pal.muted)
        }
    }
}
