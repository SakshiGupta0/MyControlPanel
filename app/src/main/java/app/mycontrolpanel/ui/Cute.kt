package app.mycontrolpanel.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------------------
// Mochi — the panel's little calico cat (the same cat that naps in the app icon).
// An original character drawn in code; her face and accessories follow your day.
// ---------------------------------------------------------------------------

enum class BeanMood { CALM, HAPPY, SLEEPY, FOCUS, MORNING, CHEER, BREATHE }

@Composable
fun Bean(mood: BeanMood, modifier: Modifier = Modifier, size: Dp = 72.dp, color: Color? = null) {
    val p = P()
    val reduce = LocalReduceMotion.current
    val fur = color ?: Color(0xFFFBF1E4)
    val ginger = Color(0xFFE9A766)
    val cocoa = Color(0xFFA9774F)
    val ink = Color(0xFF4A372B)
    val pink = Color(0xFFF2A7A0)
    val t = rememberInfiniteTransition(label = "mochi")
    val bob by t.animateFloat(0f, 1f, infiniteRepeatable(tween(if (mood == BeanMood.SLEEPY) 3600 else 2400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val blink by t.animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "blink")
    val tail by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "tail")
    Canvas(modifier.size(size).testTag("bean")) {
        val w = this.size.width; val h = this.size.height
        val breathe = if (reduce) 0f else (bob - 0.5f)
        val outline = Stroke(w * 0.018f)
        val line = ink.copy(alpha = if (p.dark) 0.9f else 0.75f)
        // shadow
        drawOval(Color.Black.copy(alpha = 0.08f), Offset(w * 0.12f, h * 0.88f), Size(w * 0.76f, h * 0.09f))
        // tail curling round the front, gently swishing
        val sw = if (reduce) 0f else tail * w * 0.03f
        val tailPath = Path().apply {
            moveTo(w * 0.80f, h * 0.84f)
            cubicTo(w * 0.98f + sw, h * 0.80f, w * 0.96f + sw, h * 0.58f, w * 0.84f + sw, h * 0.56f)
        }
        drawPath(tailPath, cocoa, style = Stroke(w * 0.075f, cap = StrokeCap.Round))
        // body loaf (breathes a little)
        val bodyTop = h * (0.55f - breathe * 0.012f)
        drawOval(fur, Offset(w * 0.10f, bodyTop), Size(w * 0.78f, h * 0.92f - bodyTop))
        drawArc(cocoa.copy(alpha = 0.85f), 200f, 90f, true, Offset(w * 0.10f, bodyTop), Size(w * 0.78f, h * 0.92f - bodyTop))
        drawOval(line, Offset(w * 0.10f, bodyTop), Size(w * 0.78f, h * 0.92f - bodyTop), style = outline)
        // front paws
        for (px in listOf(0.34f, 0.54f)) {
            drawOval(fur, Offset(w * px, h * 0.83f), Size(w * 0.14f, h * 0.09f))
            drawOval(line, Offset(w * px, h * 0.83f), Size(w * 0.14f, h * 0.09f), style = outline)
        }
        // head
        val hx = w * 0.47f; val hy = h * (0.47f + breathe * 0.01f)
        val hw = w * 0.60f; val hh = h * 0.46f
        val headTl = Offset(hx - hw / 2, hy - hh / 2)
        // ears (left ginger, right cream) with pink insides
        fun ear(tip: Offset, a: Offset, b: Offset, c: Color) {
            val e = Path().apply { moveTo(a.x, a.y); lineTo(tip.x, tip.y); lineTo(b.x, b.y); close() }
            drawPath(e, c); drawPath(e, line, style = outline)
            val inner = Path().apply {
                moveTo(lerpOff(a, tip, 0.25f).x + (b.x - a.x) * 0.18f, lerpOff(a, tip, 0.25f).y)
                lineTo(lerpOff(tip, Offset((a.x + b.x) / 2, a.y), 0.18f).x, lerpOff(tip, Offset((a.x + b.x) / 2, a.y), 0.18f).y)
                lineTo(lerpOff(b, tip, 0.25f).x - (b.x - a.x) * 0.18f, lerpOff(b, tip, 0.25f).y)
                close()
            }
            drawPath(inner, pink.copy(alpha = 0.8f))
        }
        val earY = hy - hh * 0.30f
        ear(Offset(hx - hw * 0.36f, hy - hh * 0.66f), Offset(hx - hw * 0.48f, earY), Offset(hx - hw * 0.10f, hy - hh * 0.44f), ginger)
        ear(Offset(hx + hw * 0.36f, hy - hh * 0.66f), Offset(hx + hw * 0.10f, hy - hh * 0.44f), Offset(hx + hw * 0.48f, earY), fur)
        drawOval(fur, headTl, Size(hw, hh))
        // calico patch over one eye
        drawArc(ginger, 160f, 110f, true, headTl, Size(hw, hh))
        drawOval(line, headTl, Size(hw, hh), style = outline)

        val eyeY = hy + hh * 0.02f
        val eyeDx = hw * 0.20f
        val r = w * 0.032f
        val blinking = !reduce && blink > 0.97f
        val lw = w * 0.022f
        for (sx in listOf(-1f, 1f)) {
            val ex = hx + sx * eyeDx
            when {
                mood == BeanMood.HAPPY || mood == BeanMood.CHEER ->       // ^ ^
                    drawArc(ink, 200f, 140f, false, Offset(ex - r * 1.4f, eyeY - r * 0.6f), Size(r * 2.8f, r * 2.4f), style = Stroke(lw, cap = StrokeCap.Round))
                mood == BeanMood.SLEEPY || mood == BeanMood.BREATHE || blinking ->                       // ‿ ‿ sleepy
                    drawArc(ink, 20f, 140f, false, Offset(ex - r * 1.4f, eyeY - r * 1.4f), Size(r * 2.8f, r * 2.2f), style = Stroke(lw, cap = StrokeCap.Round))
                else -> {
                    drawOval(ink, Offset(ex - r, eyeY - r * 1.25f), Size(r * 2f, r * 2.5f))
                    drawCircle(Color.White, r * 0.38f, Offset(ex + r * 0.35f, eyeY - r * 0.55f))
                }
            }
            if (mood == BeanMood.FOCUS)
                drawLine(ink, Offset(ex - sx * r * 1.4f, eyeY - r * 2.6f), Offset(ex + sx * r * 1.0f, eyeY - r * 2.1f), strokeWidth = lw * 0.9f, cap = StrokeCap.Round)
            // cheeks + whiskers
            drawOval(pink.copy(alpha = 0.5f), Offset(ex + sx * r * 0.9f - r * 1.3f, eyeY + r * 1.6f), Size(r * 2.6f, r * 1.4f))
            for (k in -1..1) {
                val wx = hx + sx * hw * 0.36f
                drawLine(line.copy(alpha = 0.55f), Offset(wx, eyeY + r * (2.4f + k * 0.9f)), Offset(wx + sx * hw * 0.22f, eyeY + r * (2.0f + k * 1.6f)),
                    strokeWidth = w * 0.010f, cap = StrokeCap.Round)
            }
        }
        // nose + mouth
        val nY = eyeY + r * 1.7f
        val nose = Path().apply { moveTo(hx - r * 0.6f, nY); lineTo(hx + r * 0.6f, nY); lineTo(hx, nY + r * 0.6f); close() }
        drawPath(nose, pink)
        if (mood == BeanMood.CHEER) {
            drawArc(ink, 0f, 180f, true, Offset(hx - r * 0.9f, nY + r * 0.6f), Size(r * 1.8f, r * 1.8f))
        } else if (mood == BeanMood.FOCUS) {
            drawLine(ink, Offset(hx - r * 0.6f, nY + r * 1.3f), Offset(hx + r * 0.6f, nY + r * 1.3f), strokeWidth = lw * 0.8f, cap = StrokeCap.Round)
        } else for (sx in listOf(-1f, 1f))                                    // little "ω"
            drawArc(ink, 0f, 180f, false, Offset(hx + (if (sx < 0) -r * 1.2f else 0f), nY + r * 0.2f), Size(r * 1.2f, r * 1.1f), style = Stroke(lw * 0.8f, cap = StrokeCap.Round))

        // accessories
        when (mood) {
            BeanMood.SLEEPY -> {                                              // nightcap + Zzz
                val cap = Path().apply {
                    moveTo(hx + hw * 0.02f, hy - hh * 0.46f); lineTo(hx + hw * 0.62f, hy - hh * 0.80f); lineTo(hx + hw * 0.46f, hy - hh * 0.22f); close()
                }
                drawPath(cap, Color(0xFF8E9CD6)); drawPath(cap, line, style = outline)
                drawCircle(Color.White, w * 0.045f, Offset(hx + hw * 0.62f, hy - hh * 0.80f))
                if (!reduce) {
                    val z0 = bob * h * 0.04f
                    zed(Offset(w * 0.86f, h * 0.24f - z0), w * 0.07f, ink.copy(alpha = 0.55f))
                    zed(Offset(w * 0.94f, h * 0.10f - z0), w * 0.05f, ink.copy(alpha = 0.35f))
                }
            }
            BeanMood.MORNING -> {
                val c = Offset(w * 0.88f, h * 0.16f)
                drawCircle(Color(0xFFF4C46A), w * 0.06f, c)
                for (k in 0 until 8) {
                    val a = k * PI / 4
                    drawLine(Color(0xFFF4C46A), Offset(c.x + cos(a).toFloat() * w * 0.09f, c.y + sin(a).toFloat() * w * 0.09f),
                        Offset(c.x + cos(a).toFloat() * w * 0.13f, c.y + sin(a).toFloat() * w * 0.13f), strokeWidth = w * 0.02f, cap = StrokeCap.Round)
                }
            }
            BeanMood.HAPPY -> {
                sparkle(Offset(w * 0.10f, h * 0.30f), w * 0.06f, Color(0xFFF4C46A))
                sparkle(Offset(w * 0.90f, h * 0.36f), w * 0.045f, pink)
            }
            BeanMood.CHEER -> {
                heart(Offset(w * 0.86f, h * 0.18f), w * 0.07f, Color(0xFFEE8E86))
                sparkle(Offset(w * 0.10f, h * 0.26f), w * 0.06f, Color(0xFFF4C46A))
            }
            else -> {}
        }
    }
}

private fun lerpOff(a: Offset, b: Offset, t: Float) = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

private fun DrawScope.zed(at: Offset, s: Float, c: Color) {
    val path = Path().apply { moveTo(at.x - s / 2, at.y - s / 2); lineTo(at.x + s / 2, at.y - s / 2); lineTo(at.x - s / 2, at.y + s / 2); lineTo(at.x + s / 2, at.y + s / 2) }
    drawPath(path, c, style = Stroke(s * 0.22f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
}

fun DrawScope.heart(c: Offset, s: Float, color: Color) {
    val path = Path().apply {
        moveTo(c.x, c.y + s * 0.9f)
        cubicTo(c.x - s * 1.4f, c.y, c.x - s * 0.7f, c.y - s * 1.0f, c.x, c.y - s * 0.35f)
        cubicTo(c.x + s * 0.7f, c.y - s * 1.0f, c.x + s * 1.4f, c.y, c.x, c.y + s * 0.9f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.leaf(at: Offset, len: Float, angle: Float, c: Color) {
    rotate(angle, at) {
        val path = Path().apply {
            moveTo(at.x, at.y)
            quadraticBezierTo(at.x + len * 0.5f, at.y - len * 0.45f, at.x + len, at.y)
            quadraticBezierTo(at.x + len * 0.5f, at.y + len * 0.45f, at.x, at.y)
        }
        drawPath(path, c)
    }
}

private fun DrawScope.sparkle(c: Offset, r: Float, color: Color) {
    val path = Path().apply {
        moveTo(c.x, c.y - r); quadraticBezierTo(c.x, c.y, c.x + r, c.y); quadraticBezierTo(c.x, c.y, c.x, c.y + r)
        quadraticBezierTo(c.x, c.y, c.x - r, c.y); quadraticBezierTo(c.x, c.y, c.x, c.y - r); close()
    }
    drawPath(path, color)
}

/** Which Bean fits this moment. */
fun beanMoodFor(modeId: String?, focusing: Boolean, caughtUp: Boolean): BeanMood = when {
    focusing -> BeanMood.FOCUS
    modeId == "bedtime" -> BeanMood.SLEEPY
    caughtUp -> BeanMood.HAPPY
    modeId == "morning" -> BeanMood.MORNING
    else -> BeanMood.CALM
}

// ---------------------------------------------------------------------------
// Mode doodles: faint hand-drawn-ish shapes behind the clock.
// ---------------------------------------------------------------------------

@Composable
fun ModeDoodles(modeId: String?, modifier: Modifier = Modifier) {
    val p = P()
    val reduce = LocalReduceMotion.current
    val ink = (if (p.dark) Color.White else Color(0xFF3A2E24)).copy(alpha = if (p.dark) 0.08f else 0.07f)
    val t = rememberInfiniteTransition(label = "doodles")
    val tw by t.animateFloat(0f, 1f, infiniteRepeatable(tween(5000, easing = LinearEasing), RepeatMode.Reverse), label = "tw")
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val rnd = Random(modeId?.hashCode() ?: 7)
        when (modeId) {
            "morning" -> {                       // big soft sun rising in the corner
                val c = Offset(w * 0.92f, h * 0.15f)
                drawCircle(ink, w * 0.10f, c)
                for (k in 0 until 12) {
                    val a = k * PI / 6
                    drawLine(ink, Offset(c.x + cos(a).toFloat() * w * 0.14f, c.y + sin(a).toFloat() * w * 0.14f),
                        Offset(c.x + cos(a).toFloat() * w * 0.19f, c.y + sin(a).toFloat() * w * 0.19f), strokeWidth = 6f, cap = StrokeCap.Round)
                }
            }
            "bedtime", "evening" -> {            // crescent + twinkling stars
                val c = Offset(w * 0.88f, h * 0.16f)
                val rr = w * 0.07f
                val moon = Path.combine(androidx.compose.ui.graphics.PathOperation.Difference,
                    Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, rr)) },
                    Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(c.x + rr * 0.45f, c.y - rr * 0.3f), rr)) })
                drawPath(moon, ink.copy(alpha = ink.alpha * 1.8f))
                for (i in 0 until 9) {
                    val pos = Offset(rnd.nextFloat() * w, rnd.nextFloat() * h * 0.7f)
                    val a = if (reduce) 1f else 0.5f + 0.5f * sin((tw + i * 0.37f) * 2 * PI).toFloat()
                    sparkle(pos, 6f + rnd.nextFloat() * 8f, ink.copy(alpha = ink.alpha * (0.8f + a)))
                }
            }
            "dayoff" -> for (i in 0 until 7) {   // drifting leaves
                leaf(Offset(rnd.nextFloat() * w, rnd.nextFloat() * h), 26f + rnd.nextFloat() * 22f, rnd.nextFloat() * 360f, ink.copy(alpha = ink.alpha * 1.5f))
            }
            "work" -> {                          // calm dot grid
                val step = 34f
                var y = step
                while (y < h * 0.6f) { var x = w * 0.55f; while (x < w) { drawCircle(ink, 2.4f, Offset(x, y)); x += step }; y += step }
            }
            else -> for (i in 0 until 6) sparkle(Offset(rnd.nextFloat() * w, rnd.nextFloat() * h), 8f, ink)
        }
    }
}

// ---------------------------------------------------------------------------
// Petals: a short, soft shower when something gets finished.
// ---------------------------------------------------------------------------

/** Bumped by MainActivity on Effect.Celebrate; the overlay plays once per bump. */
val LocalCelebrate = compositionLocalOf { mutableIntStateOf(0) }

@Composable
fun PetalShower(modifier: Modifier = Modifier) {
    val counter = LocalCelebrate.current
    val n = counter.intValue
    if (n == 0 || LocalReduceMotion.current) return
    val progress = remember(n) { Animatable(0f) }
    LaunchedEffect(n) { progress.snapTo(0f); progress.animateTo(1f, tween(1900, easing = LinearEasing)) }
    if (progress.value >= 1f) return
    val colors = listOf(Color(0xFFF2A7A0), Color(0xFFF4C46A), Color(0xFFA9C9A0), Color(0xFFC9B6E4), Color(0xFFF7D6C4))
    val seeds = remember(n) { List(26) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()) } }
    Box(modifier.fillMaxSize().testTag("petals")) {
        Canvas(Modifier.fillMaxSize()) {
            val pr = progress.value
            seeds.forEachIndexed { i, (sx, sd, sr) ->
                val x = size.width * sx + sin((pr * 3 + sd) * 2 * PI).toFloat() * 40f
                val y = -40f + (size.height * 0.85f) * (pr * (0.7f + sd * 0.5f))
                val alpha = (1f - pr).coerceIn(0f, 1f)
                val c = colors[i % colors.size].copy(alpha = alpha)
                rotate(pr * 360f * (sr - 0.5f) * 2, Offset(x, y)) {
                    if (i % 5 == 0) heart(Offset(x, y), 12f, c) else if (i % 3 == 0) sparkle(Offset(x, y), 14f, c)
                    else drawOval(c, Offset(x - 10f, y - 6f), Size(20f, 12f))
                }
            }
        }
    }
}
