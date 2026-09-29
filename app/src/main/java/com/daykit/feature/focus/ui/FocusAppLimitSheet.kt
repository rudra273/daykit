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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppBottomSheet
import com.daykit.core.designsystem.components.AppIconOrMonogram
import com.daykit.core.designsystem.components.AppTextButton
import com.daykit.core.designsystem.components.AppTextField
import com.daykit.core.designsystem.components.FilterChipButton
import com.daykit.core.designsystem.extendedColors
import com.daykit.feature.applock.domain.InstalledApp
import com.daykit.feature.focus.data.FocusAppLimit
import com.daykit.feature.focus.data.FocusUsageTracker

private data class LimitPreset(val label: String, val minutes: Int)

private val PRESETS = listOf(
    LimitPreset("15m", 15),
    LimitPreset("30m", 30),
    LimitPreset("45m", 45),
    LimitPreset("1h", 60),
    LimitPreset("2h", 120),
    LimitPreset("3h", 180),
)

/**
 * Sheet to set or edit an app's Daily limit.
 *
 * Framed as a budget rather than a timer — the thing that makes it read
 * differently from Lock now, which also offers "30m". The bar is prefilled with
 * [usedTodayMillis], so the user sees what the chosen allowance leaves them
 * today before saving. Their daily average (from [weekUsageMillis]) sits under
 * the presets as the reference point for picking a number.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FocusAppLimitSheet(
    app: InstalledApp,
    existingLimit: FocusAppLimit?,
    usedTodayMillis: Long,
    weekUsageMillis: Long,
    onSave: (dailyLimitMinutes: Int) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val accent = FocusMode.DailyLimit.accent
    val initialPreset = PRESETS.firstOrNull { it.minutes == existingLimit?.dailyLimitMinutes }
    val initialCustom = existingLimit?.takeIf { initialPreset == null }?.dailyLimitMinutes
    var selectedPreset by remember { mutableStateOf(initialPreset?.minutes ?: 30) }
    var customOpen by remember { mutableStateOf(initialCustom != null) }
    var customHours by remember {
        mutableStateOf(initialCustom?.let { it / 60 }?.takeIf { it > 0 }?.toString().orEmpty())
    }
    var customMinutes by remember {
        mutableStateOf(initialCustom?.let { it % 60 }?.takeIf { it > 0 }?.toString().orEmpty())
    }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val customTotalMinutes = (customHours.toIntOrNull() ?: 0) * 60 + (customMinutes.toIntOrNull() ?: 0)
    val totalMinutes = if (customOpen) customTotalMinutes else selectedPreset
    val isValid = totalMinutes > 0
    val limitMillis = totalMinutes * 60_000L
    val leftMillis = (limitMillis - usedTodayMillis).coerceAtLeast(0L)
    val exhausted = isValid && leftMillis == 0L

    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                AppIconOrMonogram(icon = app.icon, label = app.label, packageName = app.packageName)
                Spacer(Modifier.width(Spacing.md))
                Text(
                    text = "${app.label} each day",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(Spacing.lg))
            Text(
                text = if (isValid) FocusUsageTracker.formatUsage(limitMillis) else "–",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "a day",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.extendedColors.textMuted,
            )

            Spacer(Modifier.height(Spacing.md))
            FocusBudgetBar(
                progress = if (limitMillis > 0) usedTodayMillis.toFloat() / limitMillis else 0f,
                color = if (exhausted) MaterialTheme.extendedColors.danger else accent,
                height = 10.dp,
            )
            Spacer(Modifier.height(Spacing.xs))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "${FocusUsageTracker.formatUsage(usedTodayMillis)} used today",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.textMuted,
                )
                if (isValid) {
                    Text(
                        text = if (exhausted) "Locks now" else "${FocusUsageTracker.formatUsage(leftMillis)} left",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (exhausted) MaterialTheme.extendedColors.danger else accent,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                modifier = Modifier.fillMaxWidth(),
            ) {
                PRESETS.forEach { preset ->
                    FilterChipButton(
                        text = preset.label,
                        selected = !customOpen && selectedPreset == preset.minutes,
                        onClick = {
                            selectedPreset = preset.minutes
                            customOpen = false
                        },
                    )
                }
                FilterChipButton(text = "Custom", selected = customOpen, onClick = { customOpen = true })
            }
            formatDailyAverage(weekUsageMillis)?.let { average ->
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = "You average $average",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.textMuted,
                )
            }

            if (customOpen) {
                Spacer(Modifier.height(Spacing.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    AppTextField(
                        value = customHours,
                        onValueChange = { customHours = it.filter(Char::isDigit).take(2) },
                        label = "Hours",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    AppTextField(
                        value = customMinutes,
                        onValueChange = { customMinutes = it.filter(Char::isDigit).take(2) },
                        label = "Minutes",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Bedtime,
                    contentDescription = null,
                    tint = MaterialTheme.extendedColors.textMuted,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = "Refills at midnight",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.textMuted,
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            FocusAccentButton(
                text = if (existingLimit != null) "Save limit" else "Set limit",
                accent = accent,
                enabled = isValid,
                onClick = { onSave(totalMinutes) },
            )

            if (existingLimit != null && onDelete != null) {
                AppTextButton(
                    text = "Remove limit",
                    color = MaterialTheme.extendedColors.danger,
                    onClick = { showDeleteConfirm = true },
                )
            }
        }
    }

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove daily limit?") },
            text = { Text("${app.label} will no longer lock after a set time each day.") },
            confirmButton = {
                AppTextButton(
                    text = "Remove",
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                )
            },
            dismissButton = {
                AppTextButton(text = "Cancel", onClick = { showDeleteConfirm = false })
            },
        )
    }
}
