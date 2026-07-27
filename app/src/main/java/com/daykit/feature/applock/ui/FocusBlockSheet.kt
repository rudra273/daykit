package com.daykit.feature.applock.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppBottomSheet
import com.daykit.core.designsystem.components.AppTextButton
import com.daykit.core.designsystem.components.FilterChipButton
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.extendedColors

private data class DurationPreset(val label: String, val millis: Long)

/** Steps of the sheet: pick a duration, read the warning, then type to confirm. */
private enum class FocusStep { Duration, Warn, Confirm }

private val PRESETS = listOf(
    DurationPreset("30m", 30 * 60_000L),
    DurationPreset("1h", 60 * 60_000L),
    DurationPreset("3h", 3 * 60 * 60_000L),
    DurationPreset("6h", 6 * 60 * 60_000L),
    DurationPreset("12h", 12 * 60 * 60_000L),
)

/** Word the user must type on the final step. Guards an irreversible action. */
private const val CONFIRM_WORD = "LOCK"

/**
 * Bottom sheet to start a strict timed lock ("focus block") on [appLabel].
 * Presents preset durations plus a custom hours+minutes entry, then two confirm
 * steps — a warning, then a typed [CONFIRM_WORD] acknowledgement — because the
 * block is irreversible (no early cancel, not even with the PIN). On confirm
 * invokes [onConfirm] with the chosen duration in millis.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FocusBlockSheet(
    appLabel: String,
    onConfirm: (durationMillis: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedPreset by remember { mutableStateOf<Long?>(PRESETS[2].millis) }
    var customHours by remember { mutableStateOf("") }
    var customMinutes by remember { mutableStateOf("") }
    var step by remember { mutableStateOf(FocusStep.Duration) }
    var typedConfirm by remember { mutableStateOf("") }

    val customMillis = run {
        val h = customHours.toLongOrNull() ?: 0L
        val m = customMinutes.toLongOrNull() ?: 0L
        (h * 60 + m) * 60_000L
    }
    val usingCustom = customHours.isNotBlank() || customMinutes.isNotBlank()
    val durationMillis = if (usingCustom) customMillis else (selectedPreset ?: 0L)
    val valid = durationMillis > 0L

    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        ) {
            if (step == FocusStep.Duration) {
                Text(
                    text = "Lock $appLabel for…",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.md))

                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    PRESETS.forEach { preset ->
                        FilterChipButton(
                            text = preset.label,
                            selected = !usingCustom && selectedPreset == preset.millis,
                            onClick = {
                                selectedPreset = preset.millis
                                customHours = ""
                                customMinutes = ""
                            },
                        )
                    }
                }

                Spacer(Modifier.height(Spacing.lg))
                Text(
                    text = "Custom",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.extendedColors.textMuted,
                )
                Spacer(Modifier.height(Spacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = customHours,
                        onValueChange = { customHours = it.filter(Char::isDigit).take(2) },
                        label = { Text("Hours") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(110.dp),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    OutlinedTextField(
                        value = customMinutes,
                        onValueChange = { customMinutes = it.filter(Char::isDigit).take(2) },
                        label = { Text("Minutes") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(110.dp),
                    )
                }

                Spacer(Modifier.height(Spacing.lg))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppTextButton(text = "Cancel", onClick = onDismiss)
                    Spacer(Modifier.width(Spacing.sm))
                    PrimaryButton(
                        text = "Continue",
                        enabled = valid,
                        onClick = { step = FocusStep.Warn },
                    )
                }
            } else if (step == FocusStep.Warn) {
                Text(
                    text = "Start focus block?",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = "You won't be able to open $appLabel for ${formatFocusDuration(durationMillis)} — " +
                        "not even with your PIN. This can't be undone until the timer ends.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.extendedColors.textMuted,
                )
                Spacer(Modifier.height(Spacing.lg))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppTextButton(text = "Back", onClick = { step = FocusStep.Duration })
                    Spacer(Modifier.width(Spacing.sm))
                    PrimaryButton(
                        text = "Continue",
                        onClick = { step = FocusStep.Confirm },
                    )
                }
            } else {
                Text(
                    text = "Type $CONFIRM_WORD to confirm",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = "Last check: $appLabel will be blocked for " +
                        "${formatFocusDuration(durationMillis)} with no way out. " +
                        "Type $CONFIRM_WORD below to start.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.extendedColors.textMuted,
                )
                Spacer(Modifier.height(Spacing.md))
                OutlinedTextField(
                    value = typedConfirm,
                    onValueChange = { typedConfirm = it.take(CONFIRM_WORD.length) },
                    label = { Text(CONFIRM_WORD) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrectEnabled = false,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.lg))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppTextButton(
                        text = "Back",
                        onClick = { typedConfirm = ""; step = FocusStep.Warn },
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    PrimaryButton(
                        text = "Start focus block",
                        enabled = typedConfirm.trim().equals(CONFIRM_WORD, ignoreCase = true),
                        onClick = { onConfirm(durationMillis) },
                    )
                }
            }
        }
    }
}
