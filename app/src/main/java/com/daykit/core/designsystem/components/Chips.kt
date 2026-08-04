package com.daykit.core.designsystem.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.extendedColors

/**
 * Pill filter chip. Selected = primaryContainer fill + primary text.
 * Unselected = inputField fill + onSurface. Matches the old GlassFilterButton signature.
 */
@Composable
fun FilterChipButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val container = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.extendedColors.inputField
    }
    val content = when {
        !enabled -> MaterialTheme.extendedColors.textMuted
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(container)
            .then(
                if (selected) Modifier.border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    CircleShape,
                ) else Modifier
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = content,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
