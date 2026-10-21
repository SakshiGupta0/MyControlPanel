package app.mycontrolpanel.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.mycontrolpanel.core.AppState
import app.mycontrolpanel.core.Derive
import app.mycontrolpanel.core.Env
import app.mycontrolpanel.core.NowItem
import app.mycontrolpanel.core.Scheduler
import app.mycontrolpanel.core.displayName
import app.mycontrolpanel.core.modeOrNull
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin

// ---------------------------------------------------------------------------
// Mochi, the companion. She wanders around the page, hops up onto cards, buttons and tabs, sits a while,
// and now and then says something small in a speech bubble. She never takes a tap: everything under her
// still works. Where she can sit is marked with Modifier.perch("name").
// ---------------------------------------------------------------------------

/** Places Mochi can sit, in root coordinates. "home" is her cushion in the little house. */
class Perches { val rects = mutableStateMapOf<String, Rect>() }

val LocalPerches = staticCompositionLocalOf<Perches?> { null }

/** Marks this element as somewhere Mochi may sit (only on screens where she wanders). */
fun Modifier.perch(id: String): Modifier = composed {
    val perches = LocalPerches.current ?: return@composed this
    DisposableEffect(id) { onDispose { perches.rects.remove(id) } }
    this.onGloballyPositioned { perches.rects[id] = it.boundsInRoot() }
}

private const val SEGMENTS = 24            // perches per cycle
private const val SEGMENT_MS = 26_000      // how long she stays put, roughly
private const val WALK = 0.075f            // share of a segment spent walking (~2 s)

private fun hash(k: Int, salt: Int): Int {
    var x = k * 374761393 + salt * 668265263
    x = (x xor (x ushr 13)) * 1274126177
    return (x xor (x ushr 16)) and 0x7fffffff
}

/** Little things Mochi says, depending on the time of day and how things are going. */
fun mochiLines(s: AppState, env: Env, phase: Phase): List<String> {
    val name = displayName(s.data.prefs, s.live.account)
    val out = mutableListOf("One thing at a time 🌱", "You're doing great ✨", "Tiny steps count", "Remember to breathe 🫧", "I'm right here 💛")
    when (phase) {
        Phase.MORNING -> out += listOf("Good morning${name?.let { ", $it" } ?: ""}! ☀️", "Water first? 💧", "Slow start is still a start")
        Phase.WORK -> out += listOf("Just the next small bit 💪", "Shoulders down, jaw soft", "Stretch break soon?")
        Phase.EVENING -> out += listOf("Time to wind down a little 🌇", "What went well today?", "You did enough today")
        Phase.NIGHT -> out += listOf("Sleepy time soon… 🌙", "Screens off soon? 😴", "Tomorrow can wait")
        Phase.DAYOFF -> out += listOf("Rest is productive too 🌿", "Do one nice thing for you")
        Phase.CALM -> out += listOf("Hi${name?.let { " $it" } ?: ""}! 👋", "Want to pick one thing?")
    }
    val prefs = s.data.prefs
    val hour = Scheduler.toLocal(s.now, env.zone).hour
    if (hour >= 12 && s.data.daily.water * 2 < prefs.waterTarget) out += "Sip of water? 💧"
    if (s.data.focus?.let { !it.finished } == true) { out.clear(); out += listOf("Shh… you're focusing 🤫", "You've got this 💪") }
    val done = s.data.tasks.count { it.done && (it.doneAt ?: 0L) > s.now - 18 * 3_600_000L }
    if (done > 0) out += listOf("Look at you go! ⭐", "$done done today 🎉")
    if (Derive.now(s, env) is NowItem.Rest) out += "All caught up! Rest is allowed"
    return out
}

@Composable
fun MochiCompanion(s: AppState, env: Env, perches: Perches) {
    val reduce = LocalReduceMotion.current
    val phase = LocalPhase.current
    val win = LocalWindow.current
    val density = LocalDensity.current
    val base = if (win.compact) 60.dp else 76.dp
    val basePx = with(density) { base.toPx() }
    val tr = rememberInfiniteTransition(label = "mochi-walk")
    val clock: State<Float> = tr.animateFloat(0f, 1f, infiniteRepeatable(tween(SEGMENTS * SEGMENT_MS, easing = LinearEasing)), label = "clock")
    val lines = mochiLines(s, env, phase)

    // A happy "Yay!" whenever something gets finished.
    val celebrate = LocalCelebrate.current.intValue
    var cheer by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(celebrate) {
        if (celebrate > 0) { cheer = listOf("Yay!! 🎉", "You did it! ✨", "So proud of you 💛", "Woohoo! ⭐")[celebrate % 4]; delay(4_500); cheer = null }
    }

    fun visible(): List<Pair<String, Rect>> = perches.rects.entries
        .filter { it.value.width > 40f && it.value.height > 20f && it.value.top > -1f }
        .sortedBy { it.key }.map { it.key to it.value }

    /** Where Mochi's top-left goes, and how big she is, sitting on perch (id, rect). */
    fun seat(id: String, r: Rect, k: Int): Pair<Offset, Float> {
        if (id == "home") {
            val size = r.width * 0.40f
            return Offset(r.center.x - size / 2, r.bottom - r.width * 0.085f - size) to size
        }
        val frac = listOf(0.32f, 0.46f, 0.60f)[hash(k, 3) % 3]
        val x = (r.left + (r.width - basePx) * frac).coerceAtLeast(4f)
        val y = (r.top - basePx * 0.80f).coerceAtLeast(4f)
        return Offset(x, y) to basePx
    }

    fun pick(k: Int, list: List<Pair<String, Rect>>): Pair<String, Rect>? {
        if (list.isEmpty()) return null
        val home = list.firstOrNull { it.first == "home" }
        if (reduce) return home ?: list.first()
        // she goes home a little more often than anywhere else
        val pool = list + listOfNotNull(home)
        return pool[hash(k, 1) % pool.size]
    }

    // Where she is right now, worked out from the clock — so it's cheap (layout/draw only) and stops in tests.
    fun now(): Triple<Offset, Float, Float>? {   // position, size, hop height
        val list = visible()
        val t = clock.value * SEGMENTS
        val k = floor(t).toInt()
        val u = t - k
        val cur = pick(k, list) ?: return null
        val (to, toSize) = seat(cur.first, cur.second, k)
        if (reduce || u >= WALK) {
            val idle = if (reduce) 0f else sin(u * 2 * PI * 6).toFloat() * basePx * 0.015f
            return Triple(to + Offset(0f, idle), toSize, 0f)
        }
        val prev = pick(k - 1, list) ?: cur
        val (from, fromSize) = seat(prev.first, prev.second, k - 1)
        val q = FastOutSlowInEasing.transform(u / WALK)
        val pos = Offset(from.x + (to.x - from.x) * q, from.y + (to.y - from.y) * q)
        val hop = abs(sin(q * PI * 4).toFloat()) * basePx * 0.22f
        return Triple(pos, fromSize + (toSize - fromSize) * q, hop)
    }

    // These only change a few times a minute, so recomposition stays rare (position updates happen in layout).
    val segNow by remember { androidx.compose.runtime.derivedStateOf { floor(clock.value * SEGMENTS).toInt() } }
    val talkNow by remember { androidx.compose.runtime.derivedStateOf {
        val t = clock.value * SEGMENTS; val k = floor(t).toInt(); val u = t - k
        hash(k, 7) % 3 == 0 && u in 0.14f..0.42f } }
    val walkingNow by remember { androidx.compose.runtime.derivedStateOf { val t = clock.value * SEGMENTS; t - floor(t) < WALK } }
    val speaking = cheer ?: if (!reduce && talkNow) lines[hash(segNow, 9) % lines.size] else null
    val walking = !reduce && walkingNow
    val mood = when {
        cheer != null -> BeanMood.CHEER
        speaking != null -> BeanMood.HAPPY
        walking -> BeanMood.CALM
        else -> beanMoodFor(Derive.effective(s, env).modeOrNull()?.id, s.data.focus?.let { !it.finished } == true, Derive.now(s, env) is NowItem.Rest)
    }

    Box(Modifier.fillMaxSize().testTag("mochi")) {
        // Mochi herself
        Box(
            Modifier
                .layout { m, c ->
                    val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
                    layout(c.maxWidth, c.maxHeight) {
                        val at = now()
                        if (at != null) p.placeWithLayer(at.first.x.toInt(), (at.first.y - at.third).toInt()) {
                            val sc = at.second / basePx
                            scaleX = sc; scaleY = sc; transformOrigin = TransformOrigin(0f, 0f)
                        }
                    }
                }
                .size(base),
        ) { Bean(mood, size = base) }

        // her speech bubble, above her head, kept on screen
        if (speaking != null) {
            val ink = Color(0xFF3A2C22)
            Column(
                Modifier
                    .layout { m, c ->
                        val p = m.measure(c.copy(minWidth = 0, minHeight = 0))
                        layout(c.maxWidth, c.maxHeight) {
                            val at = now() ?: return@layout
                            val headX = at.first.x + at.second / 2
                            val x = (headX - p.width / 2).coerceIn(8f, (c.maxWidth - p.width - 8).toFloat().coerceAtLeast(8f))
                            var y = at.first.y - at.third - p.height + at.second * 0.08f
                            if (y < 8f) y = at.first.y + at.second + 4f        // no room above: say it below
                            p.place(x.toInt(), y.toInt())
                        }
                    }
                    .testTag("mochiSays"),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.widthIn(max = 220.dp)
                        .background(Color(0xFFFFFBF5), RoundedCornerShape(18.dp))
                        .border(1.5.dp, Color(0xFFEBD9C4), RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                ) { T(speaking, Type.label, ink) }
                // little tail pointing down at her
                Canvas(Modifier.size(width = 18.dp, height = 10.dp)) {
                    val tail = Path().apply { moveTo(0f, 0f); lineTo(size.width, 0f); lineTo(size.width / 2, size.height); close() }
                    drawPath(tail, Color(0xFFFFFBF5))
                }
            }
        }
    }
}
