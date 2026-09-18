package com.daykit.core.designsystem.components

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
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
        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.extendedColors.actionFill),
    )
}
