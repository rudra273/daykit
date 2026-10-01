package com.daykit.core.designsystem.components

import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.ExtendedColors
import com.daykit.core.designsystem.background.CardStyle
import com.daykit.core.designsystem.background.LocalCardStyle
import com.daykit.core.designsystem.background.frostedBackdrop
import com.daykit.core.designsystem.isAppInDarkTheme

/*
 * Liquid glass, in one place. Every glass surface is the same recipe at a
 * different strength:
 *   frosted page background (window-aligned, see frostedBackdrop)
 *   + a tint for legibility
 *   + a dithered sheen across the top (cards only)
 *   + a 1dp rim that catches light at the top-left.
 * Cards, controls and grouped panels differ only in tint and rim.
 */

/** True while the user's card style is Liquid glass. */
@Composable
@ReadOnlyComposable
fun isLiquidGlass(): Boolean = LocalCardStyle.current == CardStyle.Glass

/**
 * Glass variants of the shared fills, applied by `DayKitTheme` in Liquid glass
 * mode. Translucent control fills, primary fills and dividers make every control
 * built on them, including screen-specific ones, read as glass automatically.
 * Text-field boxes ([ExtendedColors.fieldFill]) deliberately stay solid.
 */
fun ExtendedColors.forLiquidGlass(): ExtendedColors = copy(
    inputField = if (isDark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.55f),
    divider = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.07f),
    actionFill = actionFill.copy(alpha = 0.88f),
)

/** A glass card: the full recipe. */
@Composable
internal fun Modifier.liquidGlassCard(shape: Shape): Modifier {
    val tokens = GlassTokens.of(isAppInDarkTheme())
    return this
        .shadow(elevation = 2.dp, shape = shape, clip = false, ambientColor = tokens.shadow, spotColor = tokens.shadow)
        .innerBevel(shape, tokens.rim(strength = 1f))
        .glassSheen(shape, tokens.sheen)
        .frostedBackdrop(shape, tokens.cardTint)
}

/**
 * Backing for a control (button, chip, FAB) in Liquid glass mode; a no-op
 * otherwise. The control keeps drawing its own (now translucent) fill on top, so
 * this only adds what lies behind it and the rim, and the control stays legible
 * even sitting directly on a wallpaper.
 */
@Composable
fun Modifier.glassControl(shape: Shape): Modifier {
    if (!isLiquidGlass()) return this
    val tokens = GlassTokens.of(isAppInDarkTheme())
    return this
        .innerBevel(shape, tokens.rim(strength = 0.8f))
        .frostedBackdrop(shape, Color.Transparent)
}

/** Where a row sits in a run of rows that share one glass panel. */
enum class GroupPosition { Single, First, Middle, Last }

/**
 * One row's slice of a larger glass panel (e.g. a list of apps). Each row draws
 * its own part, but the backdrop is window-aligned and the tint uniform, so the
 * rows join seamlessly into one panel; only the panel's outer corners round and
 * only its outer edges get the rim. A no-op outside Liquid glass mode.
 */
@Composable
fun Modifier.glassGroupItem(position: GroupPosition, cornerRadius: Dp = 20.dp): Modifier {
    if (!isLiquidGlass()) return this
    val tokens = GlassTokens.of(isAppInDarkTheme())
    val top = position == GroupPosition.Single || position == GroupPosition.First
    val bottom = position == GroupPosition.Single || position == GroupPosition.Last
    val shape = RoundedCornerShape(
        topStart = if (top) cornerRadius else 0.dp,
        topEnd = if (top) cornerRadius else 0.dp,
        bottomStart = if (bottom) cornerRadius else 0.dp,
        bottomEnd = if (bottom) cornerRadius else 0.dp,
    )
    return this
        .drawWithCache {
            // The rim of the *whole* panel: a rounded rect that extends past this
            // row wherever the panel continues, clipped to the row, so inner seams
            // get no line and only the panel's outer edge is drawn.
            val radius = cornerRadius.toPx()
            val overhang = radius * 2
            val panel = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = if (top) 0f else -overhang,
                        right = size.width,
                        bottom = if (bottom) size.height else size.height + overhang,
                        cornerRadius = CornerRadius(radius),
                    ),
                )
            }
            val stroke = Stroke(width = BevelWidth.toPx() * 2)
            val edge = when (position) {
                GroupPosition.Single -> tokens.rim(1f)
                GroupPosition.First -> Brush.verticalGradient(listOf(tokens.rimLight, tokens.rimMid))
                GroupPosition.Middle -> Brush.verticalGradient(listOf(tokens.rimMid, tokens.rimMid))
                GroupPosition.Last -> Brush.verticalGradient(listOf(tokens.rimMid, tokens.rimShade))
            }
            onDrawWithContent {
                drawContent()
                clipRect { clipPath(panel) { drawPath(panel, edge, style = stroke) } }
            }
        }
        .frostedBackdrop(shape, tokens.cardTint)
}

/**
 * A 1dp inner edge painted with [brush], drawn over the surface. The stroke is
 * twice the width and clipped to the shape, so exactly the inner half shows and
 * the edge stays crisp.
 */
internal fun Modifier.innerBevel(shape: Shape, brush: Brush): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    val stroke = Stroke(width = BevelWidth.toPx() * 2)
    onDrawWithContent {
        drawContent()
        clipPath(path) { drawPath(path, brush, style = stroke) }
    }
}

/** A dithered white fade across the top of the surface, under its content. */
private fun Modifier.glassSheen(shape: Shape, alpha: Float): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val clip = Path().apply { addOutline(outline) }
    val paint = Paint(Paint.DITHER_FLAG).apply {
        shader = LinearGradient(
            0f, 0f, 0f, (size.height * 0.45f).coerceAtLeast(1f),
            Color.White.copy(alpha = alpha).toArgb(), Color.White.copy(alpha = 0f).toArgb(),
            Shader.TileMode.CLAMP,
        )
    }
    onDrawWithContent {
        clipPath(clip) { drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint) } }
        drawContent()
    }
}

internal val BevelWidth = 1.dp

/** Strengths of the glass recipe for one theme. */
private class GlassTokens(
    val cardTint: Color,
    val sheen: Float,
    val rimLight: Color,
    val rimMid: Color,
    val rimShade: Color,
    val shadow: Color,
) {
    /** The rim: bright at the top-left, fading around the surface. [strength] scales it. */
    fun rim(strength: Float): Brush = Brush.linearGradient(
        0.0f to rimLight.copy(alpha = rimLight.alpha * strength),
        0.45f to rimMid.copy(alpha = rimMid.alpha * strength),
        1.0f to rimShade.copy(alpha = rimShade.alpha * strength),
    )

    companion object {
        private val Light = GlassTokens(
            cardTint = Color.White.copy(alpha = 0.45f),
            sheen = 0.22f,
            rimLight = Color.White.copy(alpha = 0.85f),
            rimMid = Color.White.copy(alpha = 0.13f),
            rimShade = Color.White.copy(alpha = 0.30f),
            shadow = Color(0xFF3A4A6B).copy(alpha = 0.18f),
        )
        private val Dark = GlassTokens(
            cardTint = Color(0xFF16171A).copy(alpha = 0.50f),
            sheen = 0.08f,
            rimLight = Color.White.copy(alpha = 0.32f),
            rimMid = Color.White.copy(alpha = 0.05f),
            rimShade = Color.White.copy(alpha = 0.10f),
            shadow = Color.Black.copy(alpha = 0.35f),
        )

        fun of(dark: Boolean) = if (dark) Dark else Light
    }
}
