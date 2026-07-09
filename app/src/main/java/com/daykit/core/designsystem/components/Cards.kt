package com.daykit.core.designsystem.components

import androidx.compose.foundation.BorderStroke
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
import com.daykit.core.designsystem.isAppInDarkTheme

/**
 * The core surface primitive. Light mode: 1dp divider border + faint shadow.
 * Dark mode: no border/shadow — contrast comes from the surface-color step.
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
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.extendedColors.card,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    val elevation = if (dark) CardDefaults.cardElevation()
    else CardDefaults.cardElevation(defaultElevation = 1.dp)
    val border: BorderStroke? = if (dark) null
    else BorderStroke(1.dp, MaterialTheme.extendedColors.divider)

    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = modifier,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Card(
            modifier = modifier,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
        ) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

/** A small rounded icon tile filled with the accent's tinted container color. */
@Composable
fun AccentIconTile(
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 36.dp,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
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
    AppCard(modifier = modifier, contentPadding = PaddingValues(14.dp)) {
        if (icon != null) {
            AccentIconTile(icon = icon, accent = accent, size = 32.dp, iconSize = 18.dp)
            Spacer(Modifier.height(8.dp))
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
