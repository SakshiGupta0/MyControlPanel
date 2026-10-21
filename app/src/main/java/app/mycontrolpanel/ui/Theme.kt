package app.mycontrolpanel.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import app.mycontrolpanel.R

/**
 * Calm, warm, slightly tactile (PRD §41): paper-cream backgrounds, sage accent, a terracotta warm note,
 * charcoal text, soft cards with gentle shadows. Each mode wears its own soft colour wash.
 */
@Immutable
data class Palette(
    val bg: Color,
    val bg2: Color,          // bottom of the background gradient
    val card: Color,
    val cardStrong: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val warm: Color,         // terracotta — celebrations, gentle highlights
    val warmSoft: Color,
    val warn: Color,
    val danger: Color,
    val shadow: Color,
    val dark: Boolean,
)

val DayPalette = Palette(
    bg = Color(0xFFF6F0E7), bg2 = Color(0xFFEEE5D7), card = Color(0xFFFFFCF7), cardStrong = Color(0xFFF1E9DD),
    line = Color(0xFFE4DACB), text = Color(0xFF2A2622), muted = Color(0xFF7A7067), accent = Color(0xFF5B7C64),
    onAccent = Color(0xFFFFFFFF), accentSoft = Color(0xFFDDE8DB), warm = Color(0xFFBE7656), warmSoft = Color(0xFFF7E3D7),
    warn = Color(0xFF9C6326), danger = Color(0xFFA8463A), shadow = Color(0xFF6B5340), dark = false,
)

val ContrastPalette = Palette(
    bg = Color(0xFFFFFFFF), bg2 = Color(0xFFFFFFFF), card = Color(0xFFFFFFFF), cardStrong = Color(0xFFF0F0F0),
    line = Color(0xFF1A1A1A), text = Color(0xFF000000), muted = Color(0xFF262626), accent = Color(0xFF2B5535),
    onAccent = Color(0xFFFFFFFF), accentSoft = Color(0xFFD5E6D6), warm = Color(0xFF8A3E1E), warmSoft = Color(0xFFF5DCCD),
    warn = Color(0xFF6B4300), danger = Color(0xFF8A1C12), shadow = Color(0xFF000000), dark = false,
)

/** Used while a dimmed mode (Bedtime) is active: warm and dark so the room stays dark. */
val NightPalette = Palette(
    bg = Color(0xFF181614), bg2 = Color(0xFF13110F), card = Color(0xFF221F1C), cardStrong = Color(0xFF2A2622),
    line = Color(0xFF38322C), text = Color(0xFFDCD2C4), muted = Color(0xFF9A8F82), accent = Color(0xFF86A38C),
    onAccent = Color(0xFF111110), accentSoft = Color(0xFF2B382E), warm = Color(0xFFD09272), warmSoft = Color(0xFF3A2A22),
    warn = Color(0xFFC89A5A), danger = Color(0xFFCF7E72), shadow = Color(0xFF000000), dark = true,
)

/** Soft mode colour washes (index = Mode.tint): apricot, sage, moss, lavender, periwinkle, rose, sky, sand. */
val TINTS = listOf(
    Color(0xFFF6DCC6), Color(0xFFD9E7D5), Color(0xFFE2E8CC), Color(0xFFE7DCEE),
    Color(0xFFD9DEF2), Color(0xFFF3D8DC), Color(0xFFD4E7EE), Color(0xFFF0E2C4),
)
val TINT_NAMES = listOf("Apricot", "Sage", "Moss", "Lavender", "Periwinkle", "Rose", "Sky", "Sand")

fun tintColor(index: Int, p: Palette): Color {
    val base = TINTS[index.coerceIn(0, TINTS.size - 1)]
    return if (p.dark) lerp(p.bg, base, 0.16f) else if (p == ContrastPalette) lerp(Color.White, base, 0.6f) else base
}

val LocalPalette = staticCompositionLocalOf { DayPalette }
val LocalReduceMotion = staticCompositionLocalOf { false }
/** The current mode's colour wash (already adjusted for the palette). */
val LocalModeTint = staticCompositionLocalOf { TINTS[1] }

/** Soft, rounded and bubbly: Fredoka for the clock and titles, Nunito for everything you read (both OFL, see licenses/). */
val Display = FontFamily(
    Font(R.font.fredoka_regular, FontWeight.Light),
    Font(R.font.fredoka_medium, FontWeight.Normal),
    Font(R.font.fredoka_semibold, FontWeight.SemiBold),
)
val Sans = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.Medium),
    Font(R.font.nunito_bold, FontWeight.SemiBold),
)

object Type {
    val clock = TextStyle(fontFamily = Display, fontWeight = FontWeight.Light, fontSize = 124.sp, lineHeight = 128.sp, letterSpacing = (-2).sp)
    val display = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 40.sp, lineHeight = 48.sp, letterSpacing = 0.sp)
    val title = TextStyle(fontFamily = Display, fontWeight = FontWeight.Normal, fontSize = 28.sp, lineHeight = 36.sp)
    val heading = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp)
    val body = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 29.sp)
    val label = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp)
    val small = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp) // smallest body size
    val eyebrow = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 1.8.sp)
}

@Composable
fun PanelTheme(palette: Palette, tintIndex: Int, textScale: Float, reduceMotion: Boolean, content: @Composable () -> Unit) {
    val scheme = if (palette.dark) darkColorScheme(
        primary = palette.accent, onPrimary = palette.onAccent, background = palette.bg, surface = palette.card,
        onSurface = palette.text, onBackground = palette.text, surfaceVariant = palette.cardStrong, outline = palette.line,
        error = palette.danger, surfaceContainerHigh = palette.card, surfaceContainer = palette.card,
    ) else lightColorScheme(
        primary = palette.accent, onPrimary = palette.onAccent, background = palette.bg, surface = palette.card,
        onSurface = palette.text, onBackground = palette.text, surfaceVariant = palette.cardStrong, outline = palette.line,
        error = palette.danger, secondaryContainer = palette.accentSoft, primaryContainer = palette.accentSoft,
        surfaceContainerHigh = palette.card, surfaceContainer = palette.card,
    )
    val d = LocalDensity.current
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalReduceMotion provides reduceMotion,
        LocalModeTint provides tintColor(tintIndex, palette),
        LocalDensity provides Density(d.density, d.fontScale * textScale),
    ) {
        MaterialTheme(colorScheme = scheme, typography = Typography(bodyLarge = Type.body, bodyMedium = Type.small, labelLarge = Type.label), content = content)
    }
}
