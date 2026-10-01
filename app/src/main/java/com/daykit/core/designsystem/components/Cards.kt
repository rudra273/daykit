package com.daykit.core.designsystem.components

import com.daykit.core.designsystem.isAppInDarkTheme
import com.daykit.core.designsystem.LocalClayCards
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.asAccentContainer
import com.daykit.core.designsystem.extendedColors

/**
 * The core surface primitive. Cards separate from the page by color (white on the
 * light gray page, lighter charcoal on the dark one); with "Clay cards" on (the
 * default, Settings › Appearance) they also get the [clay] lift.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.extendedColors.card,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    val surface = if (LocalClayCards.current) modifier.clay(shape, isAppInDarkTheme()) else modifier

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
        .drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            val path = Path().apply { addOutline(outline) }
            // Drawn twice as wide and clipped to the shape, so exactly the inner
            // half (the bevel width) shows and the edge stays crisp.
            val stroke = Stroke(width = ClayBevelWidth.toPx() * 2)
            val bevel = Brush.verticalGradient(
                0.0f to style.highlight,
                0.5f to Color.Transparent,
                1.0f to style.shade,
            )
            onDrawWithContent {
                drawContent()
                clipPath(path) { drawPath(path, bevel, style = stroke) }
            }
        }
}

private val ClayElevation = 4.dp
private val ClayBevelWidth = 1.dp

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
