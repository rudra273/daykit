package com.daykit.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.daykit.core.designsystem.background.CardStyle
import com.daykit.core.designsystem.background.LocalCardStyle
import com.daykit.core.designsystem.background.LocalPageBackground
import com.daykit.core.designsystem.background.PageBackground

/** The theme's colors before any wallpaper or Liquid glass is applied; provided by [DayKitTheme]. */
@Immutable
internal data class SolidColors(val scheme: ColorScheme, val extended: ExtendedColors)

internal val LocalSolidColors = staticCompositionLocalOf { SolidColors(DayKitLightColorScheme, LightExtendedColors) }

/**
 * Opts [content] out of the wallpaper and Liquid glass: a plain page behind it,
 * solid fills and non-glass controls. Clay cards stay clay. Used where content
 * must sit on something solid — bottom sheets and full-page typing screens.
 *
 * [page] is what counts as "the page" inside (e.g. the sheet color); by default
 * the theme's plain background.
 */
@Composable
fun SolidSurface(page: Color? = null, content: @Composable () -> Unit) {
    val solid = LocalSolidColors.current
    val fill = page ?: solid.scheme.background
    val style = LocalCardStyle.current
    CompositionLocalProvider(
        LocalExtendedColors provides solid.extended,
        LocalCardStyle provides if (style == CardStyle.Glass) CardStyle.Flat else style,
        LocalPageBackground provides PageBackground.Plain(fill),
    ) {
        MaterialTheme(colorScheme = solid.scheme.copy(background = fill), content = content)
    }
}

/** A full page on the plain theme background, whatever wallpaper is set. For typing screens. */
@Composable
fun SolidPage(content: @Composable () -> Unit) {
    SolidSurface {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { content() }
    }
}
