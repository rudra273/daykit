@file:OptIn(ExperimentalMaterial3Api::class)

package com.daykit.feature.reminder.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daykit.AppContainer
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AccentIconTile
import com.daykit.core.designsystem.components.AppAlertDialog
import com.daykit.core.designsystem.components.AppBottomSheet
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppFab
import com.daykit.core.designsystem.components.AppTextField
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.DestructiveButton
import com.daykit.core.designsystem.components.EmptyState
import com.daykit.core.designsystem.components.LoadingIndicator
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.components.SecondaryButton
import com.daykit.core.designsystem.extendedColors
import com.daykit.feature.reminder.data.Reminder
import com.daykit.feature.reminder.notification.ReminderNotifier
import com.daykit.feature.reminder.notification.ReminderScheduler
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

@Composable
fun ReminderScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheduler = remember(context) { ReminderScheduler(context) }
    val reminders by container.reminderRepository
        .observeReminders()
        .collectAsStateWithLifecycle(initialValue = null)
    var addOpen by remember { mutableStateOf(false) }
    var editReminder by remember { mutableStateOf<Reminder?>(null) }
    var actionReminder by remember { mutableStateOf<Reminder?>(null) }
    var deleteReminder by remember { mutableStateOf<Reminder?>(null) }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    fun complete(reminder: Reminder) {
        scope.launch {
            container.reminderRepository.markComplete(reminder.reminderId)
            scheduler.cancel(reminder.reminderId)
            NotificationManagerCompat.from(context).cancel(ReminderNotifier.notificationId(reminder.reminderId))
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = "Reminders", onBack = onBack) },
        floatingActionButton = {
            AppFab(icon = Icons.Rounded.Add, contentDescription = "Add reminder", onClick = { addOpen = true })
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (val current = reminders) {
                null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
                else -> ReminderContent(
                    reminders = current,
                    listState = listState,
                    onComplete = ::complete,
                    onLongPress = { actionReminder = it },
                )
            }
        }
    }

    if (addOpen) {
        ReminderFormSheet(
            heading = "New reminder",
            confirmText = "Add reminder",
            initial = null,
            onDismiss = { addOpen = false },
            onSave = { title, scheduledAtMillis ->
                scope.launch {
                    val reminder = container.reminderRepository.addReminder(title, scheduledAtMillis)
                    scheduler.schedule(reminder)
                    requestNotificationPermissionIfNeeded(context as? Activity)
                    addOpen = false
                }
            },
        )
    }

    editReminder?.let { editing ->
        ReminderFormSheet(
            heading = "Edit reminder",
            confirmText = "Save changes",
            initial = editing,
            onDismiss = { editReminder = null },
            onSave = { title, scheduledAtMillis ->
                scope.launch {
                    val updated = container.reminderRepository
                        .updateReminder(editing.reminderId, title, scheduledAtMillis)
                    if (updated != null) {
                        scheduler.cancel(updated.reminderId)
                        NotificationManagerCompat.from(context)
                            .cancel(ReminderNotifier.notificationId(updated.reminderId))
                        scheduler.schedule(updated)
                        requestNotificationPermissionIfNeeded(context as? Activity)
                    }
                    editReminder = null
                }
            },
        )
    }

    actionReminder?.let { reminder ->
        ReminderActionSheet(
            reminder = reminder,
            onDismiss = { actionReminder = null },
            onEdit = {
                actionReminder = null
                editReminder = reminder
            },
            onDelete = {
                actionReminder = null
                deleteReminder = reminder
            },
        )
    }

    deleteReminder?.let { reminder ->
        AppAlertDialog(
            onDismissRequest = { deleteReminder = null },
            title = "Delete reminder",
            text = "Remove \"${reminder.title}\"?",
            confirmText = "Delete",
            destructiveConfirm = true,
            onConfirm = {
                deleteReminder = null
                scope.launch {
                    container.reminderRepository.deleteReminder(reminder.reminderId)
                    scheduler.cancel(reminder.reminderId)
                    NotificationManagerCompat.from(context).cancel(ReminderNotifier.notificationId(reminder.reminderId))
                }
            },
        )
    }
}

@Composable
private fun ReminderContent(
    reminders: List<Reminder>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onComplete: (Reminder) -> Unit,
    onLongPress: (Reminder) -> Unit,
) {
    if (reminders.isEmpty()) {
        Column(Modifier.fillMaxSize()) {
            EmptyState(
                icon = Icons.Rounded.NotificationsActive,
                title = "No reminders yet",
                description = "Tap + to add one. Notifications stay until you complete them.",
                modifier = Modifier.padding(top = Spacing.xxl),
            )
        }
        return
    }

    val now = System.currentTimeMillis()
    val active = reminders.filter { !it.completed }.sortedBy { it.scheduledAtMillis }
    val completed = reminders.filter { it.completed }.sortedByDescending { it.scheduledAtMillis }
    val startOfTomorrow = LocalDate.now().plusDays(1)
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val overdue = active.filter { it.scheduledAtMillis < now }
    val today = active.filter { it.scheduledAtMillis in now until startOfTomorrow }
    val upcoming = active.filter { it.scheduledAtMillis >= startOfTomorrow }
    val next = active.firstOrNull()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.lg, end = Spacing.lg,
            top = Spacing.sm, bottom = Spacing.xxl + 72.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (next != null) {
            item { UpNextCard(reminder = next, onComplete = { onComplete(next) }) }
        }
        section("Overdue", overdue, accentDanger = true, onComplete, onLongPress)
        section("Today", today, accentDanger = false, onComplete, onLongPress)
        section("Upcoming", upcoming, accentDanger = false, onComplete, onLongPress)
        if (completed.isNotEmpty()) {
            item {
                Text(
                    text = "Completed",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.extendedColors.textMuted,
                    modifier = Modifier.padding(top = Spacing.sm, start = Spacing.xs),
                )
            }
            items(completed, key = { it.reminderId }) { r ->
                ReminderRow(reminder = r, accentDanger = false, onComplete = { onComplete(r) }, onLongPress = { onLongPress(r) })
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    reminders: List<Reminder>,
    accentDanger: Boolean,
    onComplete: (Reminder) -> Unit,
    onLongPress: (Reminder) -> Unit,
) {
    if (reminders.isEmpty()) return
    item(key = "header-$title") {
        SectionHeaderRow(title = title, count = reminders.size, danger = accentDanger)
    }
    items(reminders, key = { it.reminderId }) { r ->
        ReminderRow(reminder = r, accentDanger = accentDanger, onComplete = { onComplete(r) }, onLongPress = { onLongPress(r) })
    }
}

@Composable
private fun SectionHeaderRow(title: String, count: Int, danger: Boolean) {
    Row(
        modifier = Modifier.padding(top = Spacing.sm, start = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.extendedColors.textMuted,
        )
        Spacer(Modifier.size(Spacing.sm))
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extendedColors.textMuted,
        )
    }
}

@Composable
private fun UpNextCard(reminder: Reminder, onComplete: () -> Unit) {
    AppCard {
        Text(
            text = "Up next",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.extendedColors.textMuted,
        )
        Spacer(Modifier.height(Spacing.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentIconTile(
                icon = Icons.Rounded.NotificationsActive,
                accent = MaterialTheme.extendedColors.accents.orange,
                size = 44.dp,
                iconSize = 24.dp,
            )
            Spacer(Modifier.size(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = relativeText(reminder.scheduledAtMillis),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.extendedColors.textMuted,
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        PrimaryButton(
            text = "Mark complete",
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp)) },
            onClick = onComplete,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReminderRow(
    reminder: Reminder,
    accentDanger: Boolean,
    onComplete: () -> Unit,
    onLongPress: () -> Unit,
) {
    AppCard(
        modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress),
        contentPadding = PaddingValues(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Tap-to-complete circle
            if (reminder.completed) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .border(
                            2.dp,
                            if (accentDanger) MaterialTheme.colorScheme.error else MaterialTheme.extendedColors.textMuted,
                            CircleShape,
                        )
                        .clickable(onClick = onComplete),
                )
            }
            Spacer(Modifier.size(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (reminder.completed) MaterialTheme.extendedColors.textMuted else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (reminder.completed) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = reminder.scheduledAtMillis.toAbsoluteText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (accentDanger) MaterialTheme.colorScheme.error else MaterialTheme.extendedColors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun ReminderActionSheet(
    reminder: Reminder,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                reminder.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                reminder.scheduledAtMillis.toAbsoluteText(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.extendedColors.textMuted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(text = "Edit", modifier = Modifier.weight(1f), onClick = onEdit)
                DestructiveButton(text = "Delete", modifier = Modifier.weight(1f), onClick = onDelete)
            }
        }
    }
}

@Composable
private fun ReminderFormSheet(
    heading: String,
    confirmText: String,
    initial: Reminder?,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit,
) {
    val default = remember(initial) {
        initial?.let {
            Instant.ofEpochMilli(it.scheduledAtMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
        } ?: LocalDateTime.now().plusMinutes(5)
    }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var date by remember { mutableStateOf(default.toLocalDate()) }
    var time by remember { mutableStateOf(default.toLocalTime()) }

    var dateOpen by remember { mutableStateOf(false) }
    var timeOpen by remember { mutableStateOf(false) }

    val scheduledAtMillis = remember(date, time) {
        LocalDateTime.of(date, time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    val canSave = title.trim().isNotBlank() && scheduledAtMillis > System.currentTimeMillis()

    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.lg).padding(bottom = Spacing.lg)) {
            Text(
                text = heading,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.lg))
            AppTextField(
                value = title,
                onValueChange = { title = it.take(80) },
                label = "Reminder",
                placeholder = "What should we remind you about?",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Spacer(Modifier.height(Spacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SecondaryButton(
                    text = date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                    modifier = Modifier.weight(1f),
                    leadingIcon = { Icon(Icons.Rounded.Event, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    onClick = { dateOpen = true },
                )
                SecondaryButton(
                    text = time.format(DateTimeFormatter.ofPattern("h:mm a")),
                    modifier = Modifier.weight(1f),
                    leadingIcon = { Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    onClick = { timeOpen = true },
                )
            }
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = if (canSave) "Notification stays until you tap complete." else "Choose a future date and time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extendedColors.textMuted,
            )
            Spacer(Modifier.height(Spacing.lg))
            PrimaryButton(
                text = confirmText,
                modifier = Modifier.fillMaxWidth(),
                enabled = canSave,
                onClick = { onSave(title, scheduledAtMillis) },
            )
        }
    }

    if (dateOpen) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.toMillis())
        DatePickerDialog(
            onDismissRequest = { dateOpen = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = it.toLocalDate() }
                    dateOpen = false
                }) { Text("Select") }
            },
            dismissButton = { TextButton(onClick = { dateOpen = false }) { Text("Cancel") } },
            colors = DatePickerDefaults.colors(containerColor = MaterialTheme.extendedColors.card),
        ) {
            DatePicker(state = state)
        }
    }

    if (timeOpen) {
        TimePickerDialog(
            onDismiss = { timeOpen = false },
            onConfirm = { h, m ->
                time = LocalTime.of(h, m)
                timeOpen = false
            },
            initialHour = time.hour,
            initialMinute = time.minute,
        )
    }
}

@Composable
private fun TimePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
    initialHour: Int,
    initialMinute: Int,
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute)
    androidx.compose.material3.AlertDialog(
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

private fun relativeText(millis: Long): String {
    val now = System.currentTimeMillis()
    val diff = millis - now
    val abs = kotlin.math.abs(diff)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(abs)
    val hours = TimeUnit.MILLISECONDS.toHours(abs)
    val days = TimeUnit.MILLISECONDS.toDays(abs)
    val phrase = when {
        minutes < 1 -> "now"
        minutes < 60 -> "$minutes min"
        hours < 24 -> "$hours hr"
        else -> "$days day${if (days == 1L) "" else "s"}"
    }
    return if (diff < 0) "$phrase overdue" else "in $phrase"
}

private fun Long.toAbsoluteText(): String {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a"))
}

private fun LocalDate.toMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

private fun requestNotificationPermissionIfNeeded(activity: Activity?) {
    if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
    ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 51)
}
