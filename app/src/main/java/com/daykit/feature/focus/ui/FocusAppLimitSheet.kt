package com.daykit.feature.focus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppBottomSheet
import com.daykit.core.designsystem.components.AppIconOrMonogram
import com.daykit.core.designsystem.components.AppTextButton
import com.daykit.core.designsystem.components.AppTextField
import com.daykit.core.designsystem.components.FilterChipButton
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.components.SecondaryButton
import com.daykit.core.designsystem.extendedColors
import com.daykit.feature.applock.domain.InstalledApp
import com.daykit.feature.focus.data.FocusAppLimit

private data class LimitPreset(val label: String, val minutes: Int)

private val PRESETS = listOf(
    LimitPreset("15m", 15),
    LimitPreset("30m", 30),
    LimitPreset("45m", 45),
    LimitPreset("1h", 60),
    LimitPreset("1h 30m", 90),
    LimitPreset("2h", 120),
    LimitPreset("3h", 180),
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FocusAppLimitSheet(
    app: InstalledApp,
    existingLimit: FocusAppLimit?,
    onSave: (dailyLimitMinutes: Int) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val initialPreset = PRESETS.firstOrNull { it.minutes == existingLimit?.dailyLimitMinutes }
    var selectedPreset by remember {
        mutableStateOf<Int?>(initialPreset?.minutes ?: if (existingLimit == null) 30 else null)
    }

    var customHours by remember {
        mutableStateOf(
            if (existingLimit != null && initialPreset == null) {
                val h = existingLimit.dailyLimitMinutes / 60
                if (h > 0) h.toString() else ""
            } else "",
        )
    }
    var customMinutes by remember {
        mutableStateOf(
            if (existingLimit != null && initialPreset == null) {
                val m = existingLimit.dailyLimitMinutes % 60
                if (m > 0) m.toString() else ""
            } else "",
        )
    }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    val customTotalMinutes = run {
        val h = customHours.toIntOrNull() ?: 0
        val m = customMinutes.toIntOrNull() ?: 0
        h * 60 + m
    }
    val usingCustom = customHours.isNotBlank() || customMinutes.isNotBlank()
    val totalMinutes = if (usingCustom) customTotalMinutes else (selectedPreset ?: 0)
    val isValid = totalMinutes > 0

    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                AppIconOrMonogram(
                    icon = app.icon,
                    label = app.label,
                    packageName = app.packageName,
                )
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (existingLimit != null) "Edit daily limit" else "Set daily limit",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${app.label} · Resets at midnight",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.extendedColors.textMuted,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.lg))

            Text(
                text = "Daily usage allowance",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "Once you reach this time today, DayKit blocks the app until 12:00 AM.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extendedColors.textMuted,
            )

            Spacer(Modifier.height(Spacing.md))

            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PRESETS.forEach { preset ->
                    FilterChipButton(
                        text = preset.label,
                        selected = !usingCustom && selectedPreset == preset.minutes,
                        onClick = {
                            selectedPreset = preset.minutes
                            customHours = ""
                            customMinutes = ""
                        },
                    )
                }
            }

            Spacer(Modifier.height(Spacing.md))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                AppTextField(
                    value = customHours,
                    onValueChange = {
                        customHours = it.filter { ch -> ch.isDigit() }.take(2)
                        if (customHours.isNotEmpty()) selectedPreset = null
                    },
                    label = "Custom hours",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                AppTextField(
                    value = customMinutes,
                    onValueChange = {
                        customMinutes = it.filter { ch -> ch.isDigit() }.take(2)
                        if (customMinutes.isNotEmpty()) selectedPreset = null
                    },
                    label = "Custom minutes",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(Spacing.lg))

            PrimaryButton(
                text = if (existingLimit != null) "Save limit" else "Set limit",
                enabled = isValid,
                onClick = { onSave(totalMinutes) },
            )

            if (existingLimit != null && onDelete != null) {
                Spacer(Modifier.height(Spacing.sm))
                SecondaryButton(
                    text = "Delete limit",
                    onClick = { showDeleteConfirm = true },
                )
            }
        }
    }

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete daily limit?") },
            text = {
                Text("DayKit will stop automatically blocking ${app.label} based on usage.")
            },
            confirmButton = {
                AppTextButton(
                    text = "Delete",
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                )
            },
            dismissButton = {
                AppTextButton(
                    text = "Cancel",
                    onClick = { showDeleteConfirm = false },
                )
            },
        )
    }
}
