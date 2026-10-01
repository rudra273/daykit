package com.daykit.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import com.daykit.core.designsystem.background.CardStyle
import com.daykit.core.designsystem.background.LocalCardStyle
import com.daykit.core.designsystem.background.LocalPageBackground
import com.daykit.core.designsystem.background.frostedBackdrop
import com.daykit.core.designsystem.background.pageBackground
import com.daykit.core.designsystem.extendedColors

/** An even translucent scrim; a gradient can show banding over the system bars. */
@Composable
fun glassChromeBrush(): Brush {
    val base = LocalPageBackground.current.solid
    val dark = MaterialTheme.extendedColors.isDark
    return SolidColor(base.copy(alpha = if (dark) 0.94f else 0.92f))
}

/**
 * Background for the top and bottom bars. Over a plain page it is the familiar
 * translucent scrim; over a wallpaper the bars turn to frosted glass, so they sit
 * on the background instead of cutting a flat band across it.
 */
@Composable
fun Modifier.chromeBackground(): Modifier {
    val page = LocalPageBackground.current
    return if (page.isDecorated) {
        frostedBackdrop(RectangleShape, tint = page.solid.copy(alpha = if (MaterialTheme.extendedColors.isDark) 0.62f else 0.58f))
    } else {
        background(glassChromeBrush())
    }
}

/**
 * Background for the top bar. With Liquid glass over a wallpaper, the header has
 * no surface of its own: it paints exactly the page background under it, so it is
 * indistinguishable from the page while still hiding content scrolled beneath it.
 * Otherwise it is the regular [chromeBackground].
 */
@Composable
fun Modifier.headerBackground(): Modifier =
    if (LocalCardStyle.current == CardStyle.Glass && LocalPageBackground.current.isDecorated) {
        pageBackground()
    } else {
        chromeBackground()
    }

