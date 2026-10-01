package com.daykit.core.designsystem.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import com.daykit.core.designsystem.extendedColors

/** Compact switch — the M3 Switch visually scaled down to sit well in dense list rows. */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier.scale(0.78f),
        colors = if (isLiquidGlass()) {
            // Translucent track with a light rim, so the switch reads as glass.
            SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.extendedColors.actionFill,
                uncheckedTrackColor = MaterialTheme.extendedColors.inputField,
                uncheckedBorderColor = Color.White.copy(alpha = if (MaterialTheme.extendedColors.isDark) 0.22f else 0.75f),
            )
        } else {
            SwitchDefaults.colors(checkedTrackColor = MaterialTheme.extendedColors.actionFill)
        },
    )
}
