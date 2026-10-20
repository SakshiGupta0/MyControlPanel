package app.mycontrolpanel.ui

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

// ---------------------------------------------------------------------------
// Phases of the day. Each has its own sky, palette and little world — taken from the app icon:
// a peach sunrise, a clear working day, a rosy sunset, a navy night with a moon, a green day off.
// ---------------------------------------------------------------------------

enum class Phase { MORNING, WORK, EVENING, NIGHT, DAYOFF, CALM }

@androidx.compose.runtime.Immutable
data class Sky(
    val top: Color, val mid: Color, val bottom: Color,
    val hillFar: Color, val hillNear: Color, val leaf: Color, val leafDark: Color,
    val cloud: Color, val glow: Color, val sun: Color,
)

val LocalPhase = staticCompositionLocalOf { Phase.CALM }

/** Built-in modes have their own phase; custom modes pick one from their colour; free time follows the clock. */
fun phaseFor(modeId: String?, tint: Int?, hour: Int, dimmed: Boolean): Phase = when {
    dimmed || modeId == "bedtime" -> Phase.NIGHT
    modeId == "morning" -> Phase.MORNING
    modeId == "work" -> Phase.WORK
    modeId == "evening" -> Phase.EVENING
    modeId == "dayoff" -> Phase.DAYOFF
    modeId != null && tint != null -> when (tint) {
        0, 7 -> Phase.MORNING; 1, 2 -> Phase.DAYOFF; 3, 5 -> Phase.EVENING; else -> Phase.WORK
    }
    else -> when (hour) { in 5..10 -> Phase.MORNING; in 11..16 -> Phase.CALM; in 17..20 -> Phase.EVENING; else -> Phase.NIGHT }
}

private fun c(hex: Long) = Color(hex)

val Phase.sky: Sky get() = when (this) {
    Phase.MORNING -> Sky(c(0xFFF6C7A6), c(0xFFFBE0CB), c(0xFFFDF3E7), c(0xFFD6DDB8), c(0xFFAFC08F), c(0xFF7E9A64), c(0xFF5F7A4B), c(0xFFFFF7EF), c(0xFFFFE3A0), c(0xFFF9C85E))
    Phase.WORK -> Sky(c(0xFFC9E1EB), c(0xFFE6F0EE), c(0xFFF8F5EC), c(0xFFD3DFC4), c(0xFFAFC39A), c(0xFF7F9C68), c(0xFF5D7A4C), c(0xFFFFFFFF), c(0xFFFFF1C2), c(0xFFF9D36E))
    Phase.EVENING -> Sky(c(0xFFB9A3CC), c(0xFFF0B9A2), c(0xFFF9E6DA), c(0xFFC4B79E), c(0xFF9AA084), c(0xFF6F8A5C), c(0xFF52693F), c(0xFFF8D3C7), c(0xFFFFC98E), c(0xFFF4A066))
    Phase.NIGHT -> Sky(c(0xFF1B2143), c(0xFF2D3668), c(0xFF4A4679), c(0xFF2C3B49), c(0xFF22313B), c(0xFF2F4A3C), c(0xFF213629), c(0xFF3A4377), c(0xFFF6E3A1), c(0xFFF6E3A1))
    Phase.DAYOFF -> Sky(c(0xFFD3E7C8), c(0xFFEAF2DE), c(0xFFFBF6EA), c(0xFFC9DBB0), c(0xFF9DBA80), c(0xFF739A57), c(0xFF557A3E), c(0xFFFFFFFF), c(0xFFFFEDB0), c(0xFFF9CF63))
    Phase.CALM -> Sky(c(0xFFFBE4CF), c(0xFFFDF0E2), c(0xFFFBF6EE), c(0xFFD9DDBE), c(0xFFB3C097), c(0xFF7D8F63), c(0xFF5E714A), c(0xFFFFFAF3), c(0xFFFFE8B0), c(0xFFF9CB66))
}

private fun light(bg: Long, bg2: Long, card: Long, cardStrong: Long, line: Long, text: Long, muted: Long, accent: Long, accentSoft: Long, warm: Long, warmSoft: Long) =
    Palette(bg = c(bg), bg2 = c(bg2), card = c(card), cardStrong = c(cardStrong), line = c(line), text = c(text), muted = c(muted),
        accent = c(accent), onAccent = Color.White, accentSoft = c(accentSoft), warm = c(warm), warmSoft = c(warmSoft),
        warn = c(0xFF9C6326), danger = c(0xFFA8463A), shadow = c(0xFF6B5340), dark = false)

val Phase.palette: Palette get() = when (this) {
    Phase.MORNING -> light(0xFFF6C7A6, 0xFFFDF3E7, 0xFFFFFBF6, 0xFFFBEEE2, 0xFFF0DCCB, 0xFF3B2B21, 0xFF86705F, 0xFFC4734B, 0xFFFAE0D0, 0xFFC4734B, 0xFFFAE3D6)
    Phase.WORK -> light(0xFFC9E1EB, 0xFFF8F5EC, 0xFFFFFEFB, 0xFFEEF2EE, 0xFFDCE4E2, 0xFF24303A, 0xFF66727C, 0xFF56785F, 0xFFDDE8DB, 0xFFBE7656, 0xFFF7E3D7)
    Phase.EVENING -> light(0xFFB9A3CC, 0xFFF9E6DA, 0xFFFFF8F4, 0xFFF8E8E2, 0xFFEFD8D0, 0xFF3A2830, 0xFF85707A, 0xFFB15E5A, 0xFFF6DAD5, 0xFFB15E5A, 0xFFF6DAD5)
    Phase.DAYOFF -> light(0xFFD3E7C8, 0xFFFBF6EA, 0xFFFFFDF6, 0xFFF0F2E6, 0xFFE0E6D2, 0xFF2B3324, 0xFF717A66, 0xFF65864A, 0xFFE1ECD2, 0xFFBE7656, 0xFFF7E3D7)
    Phase.CALM -> light(0xFFFBE4CF, 0xFFFBF6EE, 0xFFFFFCF7, 0xFFF4ECE0, 0xFFE8DCCB, 0xFF2E2A24, 0xFF7A7067, 0xFF667A57, 0xFFE0E7D4, 0xFFBE7656, 0xFFF7E3D7)
    Phase.NIGHT -> Palette(
        bg = c(0xFF1B2143), bg2 = c(0xFF262E5A), card = c(0xFF262E5C), cardStrong = c(0xFF303869), line = c(0xFF3B4478),
        text = c(0xFFF3EADB), muted = c(0xFFB4B2CF), accent = c(0xFFF2CF7E), onAccent = c(0xFF1B2143), accentSoft = c(0xFF3A3F6E),
        warm = c(0xFFF0A88E), warmSoft = c(0xFF4A3A55), warn = c(0xFFE9C27A), danger = c(0xFFF09A8E), shadow = Color.Black, dark = true,
    )
}

// ---------------------------------------------------------------------------
// The backdrop: sky, sun or moon, drifting clouds, hills and big soft leaves, like the icon.
// ---------------------------------------------------------------------------

@Composable
fun PhaseBackdrop(phase: Phase, modifier: Modifier = Modifier) {
    val sky = phase.sky
    val reduce = LocalReduceMotion.current
    val tr = rememberInfiniteTransition(label = "sky")
    val drift: State<Float> = tr.animateFloat(0f, 1f, infiniteRepeatable(tween(90_000, easing = LinearEasing)), label = "drift")
    val pulse: State<Float> = tr.animateFloat(0f, 1f, infiniteRepeatable(tween(3_200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    val stars = remember { val r = Random(11); List(46) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) } }
    Canvas(modifier.fillMaxSize().testTag("backdrop")) {
        val w = size.width; val h = size.height; val u = min(w, h)
        val d = if (reduce) 0.3f else drift.value
        val pu = if (reduce) 0.5f else pulse.value
        drawRect(Brush.verticalGradient(0f to sky.top, 0.55f to sky.mid, 1f to sky.bottom))

        when (phase) {
            Phase.NIGHT -> {
                stars.forEachIndexed { i, (x, y, k) ->
                    val tw = 0.35f + 0.65f * (0.5f + 0.5f * sin((pu + k) * PI).toFloat())
                    val pos = Offset(x * w, y * h * 0.62f)
                    if (i % 7 == 0) fourStar(pos, u * (0.010f + k * 0.012f), sky.glow.copy(alpha = tw))
                    else drawCircle(Color.White.copy(alpha = 0.25f + 0.55f * tw * k), u * 0.0025f + k * u * 0.002f, pos)
                }
                val mc = if (w > h) Offset(w * 0.30f, h * 0.11f) else Offset(w * 0.53f, h * 0.13f); val r = u * (if (w > h) 0.065f else 0.05f)
                drawCircle(Brush.radialGradient(listOf(sky.glow.copy(alpha = 0.35f), Color.Transparent), mc, r * 4f), r * 4f, mc)
                crescent(mc, r, sky.glow)
            }
            else -> {
                // Landscape: in the open sky above the clock (left). Portrait: tucked into the top-right corner.
                val land = w > h
                val (sc, sr) = when (phase) {
                    Phase.EVENING -> (if (land) Offset(w * 0.28f, h * 0.74f) else Offset(w * 0.78f, h * 0.76f)) to u * 0.13f
                    // portrait: between the clock and Mochi's house, like the sun beside the house in the icon
                    Phase.MORNING -> (if (land) Offset(w * 0.30f, h * 0.11f) else Offset(w * 0.53f, h * 0.13f)) to u * (if (land) 0.075f else 0.05f)
                    else -> (if (land) Offset(w * 0.30f, h * 0.10f) else Offset(w * 0.53f, h * 0.13f)) to u * (if (land) 0.058f else 0.042f)
                }
                drawCircle(Brush.radialGradient(listOf(sky.glow.copy(alpha = 0.75f), sky.glow.copy(alpha = 0f)), sc, sr * 5f), sr * 5f, sc)
                drawCircle(sky.sun, sr, sc)
                if (phase != Phase.EVENING) {
                    val spin = d * 360f
                    for (k in 0 until 10) {
                        val a = (k * 36f + spin) * PI / 180
                        val r1 = sr * 1.45f; val r2 = sr * (1.85f + 0.12f * pu)
                        drawLine(sky.sun.copy(alpha = 0.8f), Offset(sc.x + cos(a).toFloat() * r1, sc.y + sin(a).toFloat() * r1),
                            Offset(sc.x + cos(a).toFloat() * r2, sc.y + sin(a).toFloat() * r2), strokeWidth = sr * 0.22f, cap = StrokeCap.Round)
                    }
                }
            }
        }
        // clouds drift slowly across
        val clouds = if (phase == Phase.NIGHT) listOf(0.22f to 0.30f, 0.70f to 0.42f) else listOf(0.10f to 0.16f, 0.52f to 0.30f, 0.86f to 0.46f, 0.32f to 0.52f)
        clouds.forEachIndexed { i, (cx, cy) ->
            val x = ((cx + d * (0.6f + i * 0.15f)) % 1.25f) * w * 1.0f - w * 0.12f
            cloud(Offset(x, cy * h), u * (0.10f + (i % 2) * 0.04f), sky.cloud.copy(alpha = if (phase == Phase.NIGHT) 0.55f else 0.85f))
        }
        // hills
        val far = Path().apply {
            moveTo(0f, h * 0.80f)
            cubicTo(w * 0.25f, h * 0.70f, w * 0.45f, h * 0.84f, w * 0.68f, h * 0.76f)
            cubicTo(w * 0.85f, h * 0.71f, w * 0.95f, h * 0.76f, w, h * 0.74f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(far, sky.hillFar)
        val near = Path().apply {
            moveTo(0f, h * 0.90f)
            cubicTo(w * 0.30f, h * 0.82f, w * 0.55f, h * 0.95f, w * 0.80f, h * 0.87f)
            cubicTo(w * 0.90f, h * 0.84f, w * 0.96f, h * 0.86f, w, h * 0.85f)
            lineTo(w, h); lineTo(0f, h); close()
        }
        drawPath(near, sky.hillNear)
        if (phase == Phase.DAYOFF) for (i in 0 until 6) {          // leaves drifting on the breeze
            val lx = ((i * 0.19f + d * 2f) % 1.1f) * w
            val ly = h * (0.25f + 0.08f * i) + sin((d * 6 + i) * PI).toFloat() * u * 0.03f
            leafShape(Offset(lx, ly), u * 0.035f, d * 720f + i * 60f, sky.leaf.copy(alpha = 0.55f))
        }
        // big soft leaves in the bottom corners, swaying a little
        val sway = (pu - 0.5f) * 4f
        cornerLeaves(Offset(0f, h), u * 0.20f, -1f, sway, sky)
        cornerLeaves(Offset(w, h), u * 0.20f, 1f, -sway, sky)
    }
}

private fun DrawScope.cloud(at: Offset, s: Float, color: Color) {
    drawCircle(color, s * 0.55f, Offset(at.x - s * 0.55f, at.y + s * 0.1f))
    drawCircle(color, s * 0.75f, Offset(at.x, at.y - s * 0.12f))
    drawCircle(color, s * 0.6f, Offset(at.x + s * 0.65f, at.y + s * 0.05f))
    drawRoundRect(color, Offset(at.x - s * 1.05f, at.y), Size(s * 2.2f, s * 0.62f), CornerRadius(s * 0.31f))
}

private fun DrawScope.crescent(c: Offset, r: Float, color: Color) {
    val moon = Path.combine(androidx.compose.ui.graphics.PathOperation.Difference,
        Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) },
        Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(c.x + r * 0.5f, c.y - r * 0.32f), r * 0.9f)) })
    drawPath(moon, color)
}

fun DrawScope.fourStar(c: Offset, r: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y - r); quadraticBezierTo(c.x, c.y, c.x + r, c.y); quadraticBezierTo(c.x, c.y, c.x, c.y + r)
        quadraticBezierTo(c.x, c.y, c.x - r, c.y); quadraticBezierTo(c.x, c.y, c.x, c.y - r); close()
    }
    drawPath(p, color)
}

/** A plump leaf, base at [at], pointing along [angle] degrees (0 = right). */
fun DrawScope.leafShape(at: Offset, len: Float, angle: Float, color: Color, vein: Color? = null) {
    rotate(angle, at) {
        val p = Path().apply {
            moveTo(at.x, at.y)
            cubicTo(at.x + len * 0.30f, at.y - len * 0.42f, at.x + len * 0.80f, at.y - len * 0.30f, at.x + len, at.y)
            cubicTo(at.x + len * 0.80f, at.y + len * 0.30f, at.x + len * 0.30f, at.y + len * 0.42f, at.x, at.y)
            close()
        }
        drawPath(p, color)
        if (vein != null) drawLine(vein, at, Offset(at.x + len * 0.85f, at.y), strokeWidth = len * 0.03f, cap = StrokeCap.Round)
    }
}

private fun DrawScope.cornerLeaves(base: Offset, s: Float, dir: Float, sway: Float, sky: Sky) {
    // dir = -1: bottom-left corner (leaves point up-right); +1: bottom-right (up-left)
    val angles = listOf(-80f, -62f, -44f, -26f, -96f)
    angles.forEachIndexed { i, a ->
        val ang = if (dir < 0) a + sway else -180f - a + sway
        val col = if (i % 2 == 0) sky.leaf else sky.leafDark
        leafShape(Offset(base.x - dir * s * 0.12f * i, base.y + s * 0.05f), s * (1.0f - i * 0.08f), ang, col, vein = sky.leafDark.copy(alpha = 0.5f))
    }
}

// ---------------------------------------------------------------------------
// Mochi's cosy house (from the icon): sage roof, lamp-lit room, books, a to-do board,
// a heart rising from the chimney — and Mochi on her cushion, whose mood follows your day.
// ---------------------------------------------------------------------------

@Composable
fun MochiHome(mood: BeanMood, modifier: Modifier = Modifier, size: Dp = 160.dp) {
    val reduce = LocalReduceMotion.current
    val tr = rememberInfiniteTransition(label = "home")
    val heart: State<Float> = tr.animateFloat(0f, 1f, infiniteRepeatable(tween(3_600, easing = LinearEasing)), label = "heart")
    val lamp: State<Float> = tr.animateFloat(0f, 1f, infiniteRepeatable(tween(2_800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "lamp")
    Box(modifier.size(size).testTag("mochiHome")) {
        Canvas(Modifier.fillMaxSize()) {
            val w = this.size.width
            fun p(x: Float, y: Float) = Offset(w * x, w * y)
            val frame = Color(0xFFF8E3C4); val frameEdge = Color(0xFFE9C99E)
            val roof = Color(0xFF6F8159); val roofHi = Color(0xFF8A9B70)
            // ground shadow + side bushes
            drawOval(Color.Black.copy(alpha = 0.10f), p(0.10f, 0.88f), Size(w * 0.80f, w * 0.08f))
            leafShape(p(0.16f, 0.92f), w * 0.20f, -120f, Color(0xFF6E8B55), Color(0xFF4E6A3B))
            leafShape(p(0.14f, 0.92f), w * 0.17f, -150f, Color(0xFF85A068))
            leafShape(p(0.84f, 0.92f), w * 0.20f, -60f, Color(0xFF6E8B55), Color(0xFF4E6A3B))
            leafShape(p(0.86f, 0.92f), w * 0.17f, -30f, Color(0xFF85A068))
            // chimney + rising heart
            drawRoundRect(roof, p(0.66f, 0.17f), Size(w * 0.09f, w * 0.17f), CornerRadius(w * 0.02f))
            if (!reduce) {
                val hv = heart.value
                val hc = p(0.705f, 0.14f - hv * 0.12f)
                heartShape(hc, w * (0.035f + hv * 0.015f), Color(0xFFEE8E86).copy(alpha = (1f - hv).coerceIn(0f, 1f)))
            } else heartShape(p(0.705f, 0.10f), w * 0.04f, Color(0xFFEE8E86))
            // walls (a pentagon) and the lit room inside
            val body = Path().apply {
                moveTo(w * 0.17f, w * 0.48f); lineTo(w * 0.50f, w * 0.20f); lineTo(w * 0.83f, w * 0.48f)
                lineTo(w * 0.83f, w * 0.86f); quadraticBezierTo(w * 0.83f, w * 0.91f, w * 0.78f, w * 0.91f)
                lineTo(w * 0.22f, w * 0.91f); quadraticBezierTo(w * 0.17f, w * 0.91f, w * 0.17f, w * 0.86f); close()
            }
            drawPath(body, frame)
            drawPath(body, frameEdge, style = Stroke(w * 0.012f))
            val room = Path().apply {
                moveTo(w * 0.25f, w * 0.51f); lineTo(w * 0.50f, w * 0.30f); lineTo(w * 0.75f, w * 0.51f)
                lineTo(w * 0.75f, w * 0.86f); lineTo(w * 0.25f, w * 0.86f); close()
            }
            drawPath(room, Brush.verticalGradient(listOf(Color(0xFFFCDDA0), Color(0xFFF3BE78)), w * 0.30f, w * 0.86f))
            // lamp glow
            val glow = 0.55f + 0.25f * (if (reduce) 0.5f else lamp.value)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF4C8).copy(alpha = glow), Color.Transparent), p(0.5f, 0.47f), w * 0.22f), w * 0.22f, p(0.5f, 0.47f))
            drawLine(roof, p(0.5f, 0.31f), p(0.5f, 0.41f), strokeWidth = w * 0.010f)
            drawArc(roof, 180f, 180f, true, p(0.44f, 0.405f), Size(w * 0.12f, w * 0.08f))
            drawCircle(Color(0xFFFFF6D6), w * 0.022f, p(0.5f, 0.455f))
            // shelf with books
            drawRoundRect(Color(0xFFE6A869), p(0.28f, 0.66f), Size(w * 0.20f, w * 0.015f))
            listOf(Color(0xFFC9827A), Color(0xFFE7B06F), Color(0xFFB98B9E), Color(0xFFD6A47F)).forEachIndexed { i, col ->
                drawRoundRect(col, p(0.30f + i * 0.04f, 0.57f + (i % 2) * 0.012f), Size(w * 0.032f, w * (0.09f - (i % 2) * 0.012f)), CornerRadius(w * 0.006f))
            }
            // to-do board with ticks
            drawRoundRect(Color(0xFFFFF6E6), p(0.56f, 0.53f), Size(w * 0.15f, w * 0.17f), CornerRadius(w * 0.015f))
            for (i in 0 until 3) {
                val y = 0.575f + i * 0.045f
                val tick = Path().apply { moveTo(w * 0.575f, w * y); lineTo(w * 0.588f, w * (y + 0.012f)); lineTo(w * 0.608f, w * (y - 0.012f)) }
                drawPath(tick, Color(0xFF7C8E63), style = Stroke(w * 0.010f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawLine(Color(0xFFE2C9A5), p(0.625f, y), p(0.69f, y), strokeWidth = w * 0.010f, cap = StrokeCap.Round)
            }
            // cushion
            drawOval(Color(0xFF7F906A), p(0.30f, 0.76f), Size(w * 0.46f, w * 0.12f))
            drawOval(Color(0xFF94A57D), p(0.33f, 0.755f), Size(w * 0.40f, w * 0.07f))
            // roof: a thick soft chevron
            val roofPath = Path().apply { moveTo(w * 0.08f, w * 0.50f); lineTo(w * 0.50f, w * 0.14f); lineTo(w * 0.92f, w * 0.50f) }
            drawPath(roofPath, roof, style = Stroke(w * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            val roofHiPath = Path().apply { moveTo(w * 0.12f, w * 0.465f); lineTo(w * 0.50f, w * 0.135f); lineTo(w * 0.88f, w * 0.465f) }
            drawPath(roofHiPath, roofHi, style = Stroke(w * 0.035f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        Bean(mood, Modifier.align(Alignment.BottomCenter).offset(y = -size * 0.085f), size = size * 0.40f)
    }
}

fun DrawScope.heartShape(c: Offset, s: Float, color: Color) {
    val path = Path().apply {
        moveTo(c.x, c.y + s * 0.9f)
        cubicTo(c.x - s * 1.4f, c.y, c.x - s * 0.7f, c.y - s * 1.0f, c.x, c.y - s * 0.35f)
        cubicTo(c.x + s * 0.7f, c.y - s * 1.0f, c.x + s * 1.4f, c.y, c.x, c.y + s * 0.9f)
        close()
    }
    drawPath(path, color)
}
