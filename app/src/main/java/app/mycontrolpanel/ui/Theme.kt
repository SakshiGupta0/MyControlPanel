package app.mycontrolpanel.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

/** Calm-minimal palette (PRD §41): cream background, muted sage accent, charcoal text, warm-neutral cards. */
@Immutable
data class Palette(
    val bg: Color,
    val card: Color,
    val cardStrong: Color,
    val line: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val warn: Color,
    val danger: Color,
    val dark: Boolean,
)

val DayPalette = Palette(
    bg = Color(0xFFF4EFE6), card = Color(0xFFFBF8F2), cardStrong = Color(0xFFEAE3D6), line = Color(0xFFDCD3C3),
    text = Color(0xFF2E2B28), muted = Color(0xFF6B645C), accent = Color(0xFF5F7F67), onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFDDE6DA), warn = Color(0xFF9A6B1F), danger = Color(0xFFA2463B), dark = false,
)

val ContrastPalette = Palette(
    bg = Color(0xFFFFFFFF), card = Color(0xFFFFFFFF), cardStrong = Color(0xFFF0F0F0), line = Color(0xFF1A1A1A),
    text = Color(0xFF000000), muted = Color(0xFF262626), accent = Color(0xFF2B5535), onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFD5E6D6), warn = Color(0xFF6B4300), danger = Color(0xFF8A1C12), dark = false,
)

/** Used while a dimmed mode (Bedtime) is active: warm and dark so the room stays dark. */
val NightPalette = Palette(
    bg = Color(0xFF191715), card = Color(0xFF221F1C), cardStrong = Color(0xFF2B2723), line = Color(0xFF3A3530),
    text = Color(0xFFD8CFC1), muted = Color(0xFF9A9083), accent = Color(0xFF7E9A84), onAccent = Color(0xFF121110),
    accentSoft = Color(0xFF2C3A2F), warn = Color(0xFFC59A55), danger = Color(0xFFC77A6F), dark = true,
)

val LocalPalette = staticCompositionLocalOf { DayPalette }
val LocalReduceMotion = staticCompositionLocalOf { false }

val Serif = FontFamily.Serif

object Type {
    val clock = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 120.sp, lineHeight = 120.sp, letterSpacing = (-2).sp)
    val display = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Normal, fontSize = 44.sp, lineHeight = 52.sp)
    val title = TextStyle(fontFamily = Serif, fontWeight = FontWeight.Medium, fontSize = 30.sp, lineHeight = 36.sp)
    val heading = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp)
    val body = TextStyle(fontWeight = FontWeight.Normal, fontSize = 20.sp, lineHeight = 28.sp)
    val label = TextStyle(fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp)
    val small = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp) // smallest size used anywhere
}

@Composable
fun PanelTheme(palette: Palette, textScale: Float, reduceMotion: Boolean, content: @Composable () -> Unit) {
    val scheme = if (palette.dark) darkColorScheme(
        primary = palette.accent, onPrimary = palette.onAccent, background = palette.bg, surface = palette.card,
        onSurface = palette.text, onBackground = palette.text, surfaceVariant = palette.cardStrong, outline = palette.line,
        error = palette.danger,
    ) else lightColorScheme(
        primary = palette.accent, onPrimary = palette.onAccent, background = palette.bg, surface = palette.card,
        onSurface = palette.text, onBackground = palette.text, surfaceVariant = palette.cardStrong, outline = palette.line,
        error = palette.danger, secondaryContainer = palette.accentSoft, primaryContainer = palette.accentSoft,
    )
    val d = LocalDensity.current
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalReduceMotion provides reduceMotion,
        LocalDensity provides Density(d.density, d.fontScale * textScale),
    ) {
        MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
    }
}
