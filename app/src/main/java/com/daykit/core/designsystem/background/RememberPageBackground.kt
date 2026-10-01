package com.daykit.core.designsystem.background

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Resolves the chosen [kind] for the current theme. A photo loads off the main
 * thread; until it is ready (or if it is missing) the plain page shows, so there
 * is never a blank frame.
 */
@Composable
internal fun rememberPageBackground(kind: PageBackgroundKind, dark: Boolean, plain: Color): PageBackground {
    val fallback = remember(plain) { PageBackground.Plain(plain) }
    return when (kind) {
        PageBackgroundKind.Plain -> fallback
        PageBackgroundKind.Aurora, PageBackgroundKind.Dusk, PageBackgroundKind.Lagoon ->
            remember(kind, dark) { meshPalette(kind, dark)?.let(PageBackground::Mesh) } ?: fallback
        PageBackgroundKind.Waves, PageBackgroundKind.Orbit, PageBackgroundKind.Contour -> {
            val metrics = LocalContext.current.resources.displayMetrics
            // Display size, not this window's: the art is drawn to cover, so small
            // differences (system bars, split screen) only crop, never distort.
            val window = Size(metrics.widthPixels.toFloat(), metrics.heightPixels.toFloat())
            val art by produceState<PageBackground?>(null, kind, dark, window) {
                value = runCatching { GeneratedArt.load(kind, dark, window) }.getOrNull()
            }
            art ?: fallback
        }
        PageBackgroundKind.Custom -> {
            val context = LocalContext.current
            val revision by CustomWallpaper.revision.collectAsState()
            val image by produceState<PageBackground?>(null, dark, plain, revision) {
                value = runCatching {
                    CustomWallpaper.load(context, solid = plain, scrim = photoScrim(dark))
                }.getOrNull()
            }
            image ?: fallback
        }
    }
}

/** Washes the photo toward the page color so text drawn on it stays readable. */
private fun photoScrim(dark: Boolean): Color =
    if (dark) Color.Black.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.40f)
