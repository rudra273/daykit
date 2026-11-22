package com.daykit.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.asAccentContainer
import com.daykit.core.designsystem.background.CardStyle
import com.daykit.core.designsystem.background.LocalCardStyle
import com.daykit.core.designsystem.extendedColors
import com.daykit.core.designsystem.isAppInDarkTheme
import com.daykit.core.designsystem.tabularNums

/**
 * The core surface primitive. Cards separate from the page by color (white on the
 * `systemGray6` light page, `systemGray5` #2C2C2E on the `systemGray6` #1C1C1E dark page),
 * plus the user's card style (Settings › Appearance): [CardStyle.Clay] adds a soft lift,
 * [CardStyle.Glass] makes the card see-through to the page background. Default is flat.
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
        CardStyle.Flat -> if (dark) {
            // Subtle 0.5dp top/edge rim in Dark Mode (like Apple System Settings cards)
            modifier.border(0.5.dp, Color.White.copy(alpha = 0.07f), shape)
        } else {
            modifier
        }
        CardStyle.Clay -> modifier.clay(shape, dark)
        CardStyle.Glass -> modifier.liquidGlassCard(shape)
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
    highlight = Color.Transparent,
    shade = Color(0xFF3A4A6B).copy(alpha = 0.10f),
)

/** Shadows barely read on charcoal, so dark clay leans on the top highlight. */
private val DarkClay = ClayStyle(
    shadow = Color.Black,
    ambientAlpha = 0.30f,
    spotAlpha = 0.45f,
    highlight = Color.White.copy(alpha = 0.09f),
    shade = Color.Black.copy(alpha = 0.22f),
)

/**
 * Apple HIG icon tile with concentric corner radius (`size * 0.28f`).
 *
 * When [vibrant] is true (default), renders an Apple Settings / Control Center
 * style vibrant squircle (rich top-to-bottom subtle gradient fill + 0.5dp top
 * specular rim + crisp pure white glyph), eliminating muddy low-alpha dark tiles.
 */
@Composable
fun AccentIconTile(
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    iconSize: Dp = 18.dp,
    contentDescription: String? = null,
    vibrant: Boolean = true,
) {
    val cornerRadius = (size.value * 0.28f).coerceIn(8f, 14f).dp
    val tileShape = RoundedCornerShape(cornerRadius)
    if (vibrant) {
        val topColor = lerp(accent, Color.White, 0.16f)
        val bottomColor = lerp(accent, Color.Black, 0.06f)
        Box(
            modifier = modifier
                .size(size)
                .clip(tileShape)
                .background(Brush.verticalGradient(listOf(topColor, bottomColor)))
                .border(
                    width = 0.5.dp,
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.06f)),
                    ),
                    shape = tileShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(iconSize),
            )
        }
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(tileShape)
                .background(accent.asAccentContainer()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = accent,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

/** A compact metric surface: big tabular value, small muted label, optional accent icon chip. */
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
            style = MaterialTheme.typography.titleLarge.tabularNums,
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
