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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.drawscope.scale as drawScale
import androidx.compose.ui.graphics.drawscope.rotate as drawRotate
import kotlin.math.cos
import kotlin.math.sin

/** Space left at the end of scrolling screens so the last button can always scroll clear of the bubbles. */
val BubbleRoom = 120.dp

private val BubbleLight = Color(0xFFE4E6FA)
private val BubbleMid = Color(0xFFC3CDF2)
private val BubbleDeep = Color(0xFF9FB0E6)

/**
 * Two or three see-through soap bubbles drifting slowly around the page, wobbling as they go.
 * Tap them to breathe. Only the bubbles themselves take taps; everything else passes through.
 */
@Composable
fun FloatingBubbles(onOpen: () -> Unit, modifier: Modifier = Modifier, topInset: androidx.compose.ui.unit.Dp = 72.dp,
                    bottomInset: androidx.compose.ui.unit.Dp = 100.dp, roam: Boolean = true) {
    val reduce = LocalReduceMotion.current
    val tr = rememberInfiniteTransition(label = "bubbles")
    val drift = tr.animateFloat(0f, 1f, infiniteRepeatable(tween(150_000, easing = LinearEasing)), label = "drift")
    val wob = tr.animateFloat(0f, 1f, infiniteRepeatable(tween(5_000, easing = LinearEasing)), label = "wob")
    val shimmer = tr.animateFloat(0f, 360f, infiniteRepeatable(tween(12_000, easing = LinearEasing)), label = "shimmer")
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val cluster = if (LocalWindow.current.compact) 92.dp else 116.dp
        val d = androidx.compose.ui.platform.LocalDensity.current
        val sPx = with(d) { cluster.toPx() }
        val left = with(d) { 8.dp.toPx() }
        val right = with(d) { maxWidth.toPx() } - sPx - left
        val top = with(d) { topInset.toPx() }
        val bottom = (with(d) { maxHeight.toPx() } - with(d) { bottomInset.toPx() } - sPx).coerceAtLeast(top)
        Box(
            Modifier
                .offset {
                    val t = if (reduce) 0.25f else drift.value
                    val (x, y) = if (roam) {
                        // a slow figure-of-eight across the whole page
                        (left + (right - left) * (0.5f + 0.5f * sin(2 * PI * t + 0.6).toFloat())) to
                            (top + (bottom - top) * (0.5f + 0.5f * sin(4 * PI * t + 1.2).toFloat()))
                    } else {
                        // busy screens (settings, editors, lists): float gently in the bottom corner instead of crossing buttons
                        val b = sin(2 * PI * t * 12).toFloat()
                        (right - sPx * 0.05f + b * sPx * 0.04f) to (bottom - sPx * 0.08f + sin(2 * PI * t * 9 + 1).toFloat() * sPx * 0.08f)
                    }
                    androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt())
                }
                .size(cluster)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onOpen)
                .semantics { contentDescription = "Take a breather" }
                .testTag("breatherBubble"),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val w = size.minDimension
                val k = if (reduce) 0f else wob.value
                val sh = if (reduce) 0f else shimmer.value
                fun bob(ph: Float, amp: Float) = sin((k + ph) * 2 * PI).toFloat() * amp * w
                soapBubble(Offset(w * 0.40f + bob(0.0f, 0.015f), w * 0.44f + bob(0.25f, 0.03f)), w * 0.31f, sh, k)
                soapBubble(Offset(w * 0.80f + bob(0.4f, 0.02f), w * 0.26f + bob(0.65f, 0.04f)), w * 0.17f, sh + 90f, k + 0.3f)
                soapBubble(Offset(w * 0.70f + bob(0.7f, 0.02f), w * 0.82f + bob(0.1f, 0.035f)), w * 0.12f, sh + 200f, k + 0.6f)
            }
        }
    }
}

/** A clear soap bubble: almost invisible inside, a rainbow sheen on the rim, a bright window highlight. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.soapBubble(c: Offset, r0: Float, shimmer: Float, wob: Float) {
    // a gentle squish so it feels like soap film
    val sx = 1f + 0.03f * sin(wob * 2 * PI).toFloat()
    val sy = 2f - sx
    drawScale(sx, sy, c) {
        val r = r0
        drawCircle(Brush.radialGradient(
            0f to Color.White.copy(alpha = 0.02f), 0.72f to Color(0xFFB9C6F5).copy(alpha = 0.07f), 1f to Color(0xFF9FE3E8).copy(alpha = 0.20f),
            center = c, radius = r), r, c)
        drawRotate(shimmer, c) {
            drawCircle(Brush.sweepGradient(listOf(
                Color(0xFFF7A8C8).copy(alpha = 0.65f), Color(0xFFF9DB8B).copy(alpha = 0.5f), Color(0xFF8FE0D6).copy(alpha = 0.65f),
                Color(0xFFA9B6F5).copy(alpha = 0.7f), Color(0xFFD9A8F2).copy(alpha = 0.6f), Color(0xFFF7A8C8).copy(alpha = 0.65f)), c), r, c,
                style = Stroke(r * 0.07f))
        }
        drawCircle(Color.White.copy(alpha = 0.45f), r * 0.965f, c, style = Stroke(r * 0.02f))
        // window highlight (top-left) and a soft reflection (bottom-right)
        drawArc(Color.White.copy(alpha = 0.85f), 200f, 55f, false, Offset(c.x - r * 0.72f, c.y - r * 0.72f), androidx.compose.ui.geometry.Size(r * 1.44f, r * 1.44f),
            style = Stroke(r * 0.10f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        drawCircle(Color.White.copy(alpha = 0.9f), r * 0.07f, Offset(c.x - r * 0.30f, c.y - r * 0.62f))
        drawArc(Color.White.copy(alpha = 0.35f), 20f, 45f, false, Offset(c.x - r * 0.78f, c.y - r * 0.78f), androidx.compose.ui.geometry.Size(r * 1.56f, r * 1.56f),
            style = Stroke(r * 0.05f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
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
        val bubble: @Composable (androidx.compose.ui.unit.Dp) -> Unit = { dia ->
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
        }
        val words: @Composable () -> Unit = {
            T(cue, Type.display, ink, Modifier.testTag("breathCue"))
            T(if (breaths == 0) "4 in · 4 hold · 4 out · 4 rest" else "$breaths slow breath${if (breaths == 1) "" else "s"}", Type.label, ink.copy(alpha = 0.6f))
        }
        val buttons: @Composable () -> Unit = {
            FlowRow(Modifier.widthIn(max = 640.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (sound.playing) SecondaryButton("■  Stop sound", { platform.stopSound() })
                else SecondaryButton("🌧️  Add rain", { platform.playAmbient(app.mycontrolpanel.core.Ambient.RAIN, 15) }, tag = "breatherRain")
                if (s.data.routines.any { it.id == "r_calm_down" && it.enabled })
                    SecondaryButton("Guided calm-down", { onClose(); dispatch(Action.StartRoutine("r_calm_down")) })
                PrimaryButton("I feel a bit better ♡", onClose, tag = "breatherClose", tall = 60.dp)
            }
        }
        if (win.short && win.twoColumn) {
            // Phone on its side: bubble on the left, words and buttons on the right — all on one screen.
            Row(Modifier.fillMaxSize().padding(horizontal = win.gutter, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Box(Modifier.weight(0.45f), contentAlignment = Alignment.Center) { bubble(win.height * 0.78f) }
                Column(Modifier.weight(0.55f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    T("Take a breather", Type.title, ink)
                    words(); buttons()
                }
            }
        } else Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = win.gutter, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            T("Take a breather", Type.title, ink)
            T("Nothing else needs you right now. Follow the bubble.", Type.body, ink.copy(alpha = 0.7f), align = TextAlign.Center)
            bubble(if (win.compact) 260.dp else 340.dp)
            words()
            Spacer(Modifier.height(4.dp))
            buttons()
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
