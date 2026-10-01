package com.daykit.feature.focus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.MinTouchTarget
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.extendedColors

/**
 * Custom duration picker for the Focus sheets: [−] 1h 15m [+], no keyboard.
 * Steps by 5 minutes up to an hour and by 15 minutes beyond it.
 */
@Composable
internal fun FocusDurationStepper(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    maxMinutes: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg, Alignment.CenterHorizontally),
    ) {
        StepButton(Icons.Rounded.Remove, "Shorter", enabled = minutes > MIN_MINUTES) {
            onMinutesChange((minutes - if (minutes <= 60) 5 else 15).coerceAtLeast(MIN_MINUTES))
        }
        Text(
            text = formatStepperMinutes(minutes),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = 96.dp),
        )
        StepButton(Icons.Rounded.Add, "Longer", enabled = minutes < maxMinutes) {
            onMinutesChange((minutes + if (minutes < 60) 5 else 15).coerceAtMost(maxMinutes))
        }
    }
}

private const val MIN_MINUTES = 5

private fun formatStepperMinutes(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

@Composable
private fun StepButton(icon: ImageVector, contentDescription: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(MinTouchTarget)
            .clip(CircleShape)
            .background(MaterialTheme.extendedColors.inputField),
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.extendedColors.textMuted,
            modifier = Modifier.size(22.dp),
        )
    }
}
