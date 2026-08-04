package com.daykit.feature.focus.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppTextField
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.FilterChipButton
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.components.SecondaryButton
import com.daykit.core.designsystem.extendedColors
import com.daykit.feature.focus.data.FocusGroup
import com.daykit.feature.focus.data.FocusRecurrence
import com.daykit.feature.focus.data.FocusSchedule

/** Everything the editor collects, handed back on save. */
data class FocusScheduleDraft(
    val groupId: String,
    val label: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val daysMask: Int,
    val strict: Boolean,
)

/**
 * Full-screen editor for a recurring focus schedule.
 *
 * A full page rather than a sheet because this collects as many fields as a
 * habit does, and habits use a page ([com.daykit.feature.habit.ui] `HabitEditorPage`).
 * Structure follows that one: `AppTopBar` inside a `Column`, a scrolling list of
 * section cards, and a pinned Cancel/Save row.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FocusScheduleEditorPage(
    existing: FocusSchedule?,
    groups: List<FocusGroup>,
    onSave: (FocusScheduleDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    var groupId by remember { mutableStateOf(existing?.groupId ?: groups.firstOrNull()?.groupId) }
    var label by remember { mutableStateOf(existing?.label.orEmpty()) }
    var startHour by remember { mutableStateOf(existing?.startHour ?: 9) }
    var startMinute by remember { mutableStateOf(existing?.startMinute ?: 0) }
    var endHour by remember { mutableStateOf(existing?.endHour ?: 11) }
    var endMinute by remember { mutableStateOf(existing?.endMinute ?: 0) }
    var daysMask by remember {
        mutableStateOf(existing?.daysMask ?: FocusRecurrence.maskOf(WEEKDAYS))
    }
    var strict by remember { mutableStateOf(existing?.strict ?: false) }

    var pickingStart by remember { mutableStateOf(false) }
    var pickingEnd by remember { mutableStateOf(false) }
    var confirmStrict by remember { mutableStateOf(false) }

    BackHandler { onDismiss() }

    val canSave = groupId != null && daysMask != 0

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(
                title = if (existing == null) "New schedule" else "Edit schedule",
                onBack = onDismiss,
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    top = Spacing.sm,
                    bottom = Spacing.lg,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item("group") {
                    EditorSection(title = "Apps") {
                        if (groups.isEmpty()) {
                            Text(
                                text = "Create a group first — a schedule blocks a group of apps.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.extendedColors.textMuted,
                            )
                        } else {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                groups.forEach { group ->
                                    FilterChipButton(
                                        text = "${group.name} · ${group.packageNames.size}",
                                        selected = group.groupId == groupId,
                                        onClick = { groupId = group.groupId },
                                    )
                                }
                            }
                        }
                    }
                }

                item("when") {
                    EditorSection(title = "When") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            SecondaryButton(
                                text = FocusRecurrence.formatTime(startHour, startMinute),
                                leadingIcon = {
                                    Icon(Icons.Rounded.Schedule, contentDescription = null)
                                },
                                onClick = { pickingStart = true },
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            Text(
                                text = "to",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.extendedColors.textMuted,
                            )
                            Spacer(Modifier.width(Spacing.sm))
                            SecondaryButton(
                                text = FocusRecurrence.formatTime(endHour, endMinute),
                                onClick = { pickingEnd = true },
                            )
                        }
                        Spacer(Modifier.height(Spacing.sm))
                        val crossesMidnight = endHour * 60 + endMinute <= startHour * 60 + startMinute
                        if (crossesMidnight) {
                            Text(
                                text = "Ends the next day.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.extendedColors.textMuted,
                            )
                            Spacer(Modifier.height(Spacing.sm))
                        }
                        FocusWeekdayPicker(
                            daysMask = daysMask,
                            onDaysMaskChange = { daysMask = it },
                        )
                        if (daysMask == 0) {
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                text = "Pick at least one day.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }

                item("mode") {
                    EditorSection(title = "Strictness") {
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            FilterChipButton(
                                text = "Normal",
                                selected = !strict,
                                onClick = { strict = false },
                            )
                            FilterChipButton(
                                text = "Strict",
                                selected = strict,
                                onClick = { if (!strict) confirmStrict = true },
                            )
                        }
                        Spacer(Modifier.height(Spacing.sm))
                        Text(
                            text = if (strict) {
                                "Strict: you cannot end a session early — not even with your PIN. " +
                                    "It repeats on every day you picked."
                            } else {
                                "Normal: you can end a session early with your PIN."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (strict) {
                                MaterialTheme.extendedColors.danger
                            } else {
                                MaterialTheme.extendedColors.textMuted
                            },
                        )
                    }
                }

                item("label") {
                    EditorSection(title = "Name (optional)") {
                        AppTextField(
                            value = label,
                            onValueChange = { label = it },
                            placeholder = "Deep work, Wind down…",
                            keyboardOptions = KeyboardOptions(
                                capitalization = KeyboardCapitalization.Sentences,
                            ),
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = innerPadding.calculateBottomPadding() + Spacing.md, top = Spacing.sm),
            ) {
                SecondaryButton(
                    text = "Cancel",
                    modifier = Modifier.weight(1f),
                    onClick = onDismiss,
                )
                PrimaryButton(
                    text = "Save",
                    modifier = Modifier.weight(1f),
                    enabled = canSave,
                    onClick = {
                        val id = groupId ?: return@PrimaryButton
                        onSave(
                            FocusScheduleDraft(
                                groupId = id,
                                label = label.trim(),
                                startHour = startHour,
                                startMinute = startMinute,
                                endHour = endHour,
                                endMinute = endMinute,
                                daysMask = daysMask,
                                strict = strict,
                            ),
                        )
                    },
                )
            }
        }
    }

    if (pickingStart) {
        FocusTimePickerDialog(
            initialHour = startHour,
            initialMinute = startMinute,
            onConfirm = { h, m -> startHour = h; startMinute = m; pickingStart = false },
            onDismiss = { pickingStart = false },
        )
    }
    if (pickingEnd) {
        FocusTimePickerDialog(
            initialHour = endHour,
            initialMinute = endMinute,
            onConfirm = { h, m -> endHour = h; endMinute = m; pickingEnd = false },
            onDismiss = { pickingEnd = false },
        )
    }
    if (confirmStrict) {
        AlertDialog(
            onDismissRequest = { confirmStrict = false },
            containerColor = MaterialTheme.extendedColors.card,
            shape = MaterialTheme.shapes.large,
            title = { Text("Use strict mode?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    text = "A strict session can't be ended early — not with your PIN, not by " +
                        "restarting your phone. It will repeat on every day you picked. " +
                        "Make sure the times are right.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.extendedColors.textMuted,
                )
            },
            confirmButton = {
                TextButton(onClick = { strict = true; confirmStrict = false }) {
                    Text("Use strict", color = MaterialTheme.extendedColors.danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmStrict = false }) {
                    Text("Cancel", color = MaterialTheme.extendedColors.textMuted)
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FocusTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.extendedColors.card,
        shape = MaterialTheme.shapes.large,
        title = { Text("Pick a time", style = MaterialTheme.typography.titleLarge) },
        text = {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("Set") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.extendedColors.textMuted)
            }
        },
    )
}

/** Titled card wrapping one group of form fields, as the habit editor does. */
@Composable
private fun EditorSection(title: String, content: @Composable () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.extendedColors.textMuted,
        )
        Spacer(Modifier.height(Spacing.sm))
        content()
    }
}

private val WEEKDAYS = setOf(
    java.time.DayOfWeek.MONDAY,
    java.time.DayOfWeek.TUESDAY,
    java.time.DayOfWeek.WEDNESDAY,
    java.time.DayOfWeek.THURSDAY,
    java.time.DayOfWeek.FRIDAY,
)
