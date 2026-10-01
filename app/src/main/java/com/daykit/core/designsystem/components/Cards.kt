package com.daykit.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.asAccentContainer
import com.daykit.core.designsystem.background.CardStyle
import com.daykit.core.designsystem.background.LocalCardStyle
import com.daykit.core.designsystem.background.frostedBackdrop
import com.daykit.core.designsystem.extendedColors
import com.daykit.core.designsystem.isAppInDarkTheme

/**
 * The core surface primitive. Cards separate from the page by color (white on the
 * light gray page, lighter charcoal on the dark one), plus the user's card style
 * (Settings › Appearance): [CardStyle.Clay] adds a soft lift, [CardStyle.Glass]
 * makes the card see-through to the page background. Default is flat.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val style = LocalCardStyle.current
    val dark = isAppInDarkTheme()
    val colors = CardDefaults.cardColors(
        containerColor = if (style == CardStyle.Glass) Color.Transparent else MaterialTheme.extendedColors.card,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    val surface = when (style) {
        CardStyle.Flat -> modifier
        CardStyle.Clay -> modifier.clay(shape, dark)
        CardStyle.Glass -> modifier.liquidGlass(shape, dark)
    }

    val body: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(contentPadding), content = content)
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = surface, shape = shape, colors = colors, elevation = elevation, content = body)
    } else {
        Card(modifier = surface, shape = shape, colors = colors, elevation = elevation, content = body)
    }
}

/**
 * Soft "clay" volume built only from short transitions, so it never bands.
 *
 * A wide, faint shadow or fill gradient covers just a few 8-bit color steps over
 * many pixels; each step becomes a visible band, and the display's dithering turns
 * the bands into grain. Instead:
 *  - the lift is a *tight* shadow (4dp), whose falloff is only a few pixels long;
 *  - the volume is a 1dp inner bevel: a highlight along the top edge, a shade
 *    along the bottom. It is a hairline, not a fill, so there is no area to band.
 */
private fun Modifier.clay(shape: Shape, dark: Boolean): Modifier {
    val style = if (dark) DarkClay else LightClay
    return this
        .shadow(
            elevation = ClayElevation,
            shape = shape,
            clip = false,
            ambientColor = style.shadow.copy(alpha = style.ambientAlpha),
            spotColor = style.shadow.copy(alpha = style.spotAlpha),
        )
        .innerBevel(shape, Brush.verticalGradient(0.0f to style.highlight, 0.5f to Color.Transparent, 1.0f to style.shade))
}

/**
 * Liquid glass: the frosted page background behind the card (see
 * [frostedBackdrop]), a tint for legibility, a dithered specular sheen across the
 * top and a bright rim that catches light at the top-left, fading around the card.
 */
@Composable
private fun Modifier.liquidGlass(shape: Shape, dark: Boolean): Modifier {
    val style = if (dark) DarkGlass else LightGlass
    return this
        .shadow(elevation = GlassElevation, shape = shape, clip = false, ambientColor = style.shadow, spotColor = style.shadow)
        .innerBevel(
            shape,
            Brush.linearGradient(
                0.0f to Color.White.copy(alpha = style.rimLight),
                0.45f to Color.White.copy(alpha = style.rimLight * 0.15f),
                1.0f to Color.White.copy(alpha = style.rimShade),
            ),
        )
        .drawWithCache {
            val sheen = ditheredVerticalFade(Color.White, style.sheen, height = size.height * 0.45f)
            val outline = shape.createOutline(size, layoutDirection, this)
            val clip = Path().apply { addOutline(outline) }
            onDrawWithContent {
                // Under the content (text stays crisp), over the backdrop.
                clipPath(clip) {
                    drawIntoCanvas { it.nativeCanvas.drawRect(0f, 0f, size.width, size.height, sheen) }
                }
                drawContent()
            }
        }
        .frostedBackdrop(shape, style.tint)
}

/**
 * A 1dp inner edge painted with [brush], drawn over the card. The stroke is twice
 * the width and clipped to the shape, so exactly the inner half shows and the
 * edge stays crisp.
 */
private fun Modifier.innerBevel(shape: Shape, brush: Brush): Modifier = drawWithCache {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    val stroke = Stroke(width = BevelWidth.toPx() * 2)
    onDrawWithContent {
        drawContent()
        clipPath(path) { drawPath(path, brush, style = stroke) }
    }
}

/** A dithered top-down fade from [color] at [alpha] to clear over [height]px. */
private fun ditheredVerticalFade(color: Color, alpha: Float, height: Float) =
    android.graphics.Paint(android.graphics.Paint.DITHER_FLAG).apply {
        shader = android.graphics.LinearGradient(
            0f, 0f, 0f, height.coerceAtLeast(1f),
            color.copy(alpha = alpha).toArgb(), color.copy(alpha = 0f).toArgb(),
            android.graphics.Shader.TileMode.CLAMP,
        )
    }

private val BevelWidth = 1.dp
private val GlassElevation = 2.dp

private class GlassStyle(
    val tint: Color,
    val sheen: Float,
    val rimLight: Float,
    val rimShade: Float,
    val shadow: Color,
)

private val LightGlass = GlassStyle(
    tint = Color.White.copy(alpha = 0.45f),
    sheen = 0.22f,
    rimLight = 0.85f,
    rimShade = 0.30f,
    shadow = Color(0xFF3A4A6B).copy(alpha = 0.18f),
)

private val DarkGlass = GlassStyle(
    tint = Color(0xFF16171A).copy(alpha = 0.50f),
    sheen = 0.08f,
    rimLight = 0.32f,
    rimShade = 0.10f,
    shadow = Color.Black.copy(alpha = 0.35f),
)

private val ClayElevation = 4.dp

private class ClayStyle(
    val shadow: Color,
    val ambientAlpha: Float,
    val spotAlpha: Float,
    val highlight: Color,
    val shade: Color,
)

/** Slate-navy shadow so the lift reads cool and soft rather than grey. */
private val LightClay = ClayStyle(
    shadow = Color(0xFF3A4A6B),
    ambientAlpha = 0.16f,
    spotAlpha = 0.30f,
    // The card is already white, so only the bottom lip carries the volume.
    highlight = Color.Transparent,
    shade = Color(0xFF3A4A6B).copy(alpha = 0.10f),
)

/** Shadows barely read on charcoal, so dark clay leans on the top highlight. */
private val DarkClay = ClayStyle(
    shadow = Color.Black,
    ambientAlpha = 0.30f,
    spotAlpha = 0.45f,
    highlight = Color.White.copy(alpha = 0.07f),
    shade = Color.Black.copy(alpha = 0.22f),
)

/** A small rounded icon tile filled with the accent's tinted container color. */
@Composable
fun AccentIconTile(
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 30.dp,
    iconSize: androidx.compose.ui.unit.Dp = 17.dp,
    contentDescription: String? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(MaterialTheme.shapes.medium)
            .background(accent.asAccentContainer()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = accent, modifier = Modifier.size(iconSize))
    }
}

/** A compact metric surface: big value, small muted label, optional accent icon chip. */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    icon: ImageVector? = null,
) {
    AppCard(modifier = modifier, contentPadding = PaddingValues(12.dp)) {
        if (icon != null) {
            AccentIconTile(icon = icon, accent = accent, size = 28.dp, iconSize = 16.dp)
            Spacer(Modifier.height(6.dp))
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.extendedColors.textMuted,
        )
    }
}
