package app.mycontrolpanel.ui

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Screen-size facts every screen lays out from. "Compact" = phones (either way round), short landscape
 * screens, and tablets held upright: one column, actions pinned where thumbs reach.
 * Wide = tablets/large screens in landscape: two columns.
 */
@Immutable
data class WindowInfo(val width: Dp, val height: Dp) {
    val portrait: Boolean get() = height > width
    val compact: Boolean get() = width < 600.dp || height < 560.dp || (portrait && width < 840.dp)
    val gutter: Dp get() = if (width < 420.dp) 16.dp else 24.dp
}

val LocalWindow = staticCompositionLocalOf { WindowInfo(1280.dp, 800.dp) }
