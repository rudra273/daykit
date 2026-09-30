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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.asAccentContainer
import com.daykit.core.designsystem.extendedColors
import com.daykit.core.designsystem.isAppInDarkTheme

/**
 * The core surface primitive.
 * Light cards are soft clay: no outline, a wide cool-tinted shadow, and a faint
 * inner shade toward the bottom edge. Dark cards separate from the page through
 * their surface color alone.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val dark = isAppInDarkTheme()
    val cardColor = MaterialTheme.extendedColors.card
    // Light mode paints the fill itself so the inner shade spans the whole card.
    val colors = CardDefaults.cardColors(
        containerColor = if (dark) cardColor else Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    // Material's shadow is kept flat; the clay lift is drawn by [clay] instead.
    val elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    val surface = if (dark) modifier
    else modifier
        .clay(shape)
        .background(cardColor, shape)
        .background(ClayInnerShade, shape)

    val body: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(contentPadding), content = content)
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = surface, shape = shape, colors = colors, elevation = elevation, content = body)
    } else {
        Card(modifier = surface, shape = shape, colors = colors, elevation = elevation, content = body)
    }
}

/** Slate-navy tint so clay shadows read cool and soft rather than grey. */
private val ClayTint = Color(0xFF3A4A6B)

/** Barely-there darkening toward the bottom edge: the "pressed clay" volume. */
private val ClayInnerShade = Brush.verticalGradient(
    0.0f to Color.Transparent,
    0.6f to Color.Transparent,
    1.0f to ClayTint.copy(alpha = 0.035f),
)

/**
 * A wide, low-contrast drop shadow. The color alphas multiply with the platform's
 * shadow alpha, so these values stay diffuse rather than drawing a rim.
 */
private fun Modifier.clay(shape: Shape): Modifier = shadow(
    elevation = 14.dp,
    shape = shape,
    clip = false,
    ambientColor = ClayTint.copy(alpha = 0.18f),
    spotColor = ClayTint.copy(alpha = 0.32f),
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
