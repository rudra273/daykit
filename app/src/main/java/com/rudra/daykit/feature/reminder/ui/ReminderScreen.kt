@file:OptIn(ExperimentalMaterial3Api::class)

package com.rudra.daykit.feature.reminder.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rudra.daykit.AppContainer
import com.rudra.daykit.core.ui.AppBackButton
import com.rudra.daykit.core.ui.Cyan
import com.rudra.daykit.core.ui.DangerRed
import com.rudra.daykit.core.ui.GlassBackground
import com.rudra.daykit.core.ui.GlassLoadingIndicator
import com.rudra.daykit.core.ui.MutedText
import com.rudra.daykit.core.ui.PanelAlt
import com.rudra.daykit.core.ui.PrimaryButton
import com.rudra.daykit.core.ui.SecondaryButton
import com.rudra.daykit.core.ui.SoftText
import com.rudra.daykit.core.ui.Stroke
import com.rudra.daykit.core.ui.glassSurface
import com.rudra.daykit.feature.reminder.data.Reminder
import com.rudra.daykit.feature.reminder.notification.ReminderNotifier
import com.rudra.daykit.feature.reminder.notification.ReminderScheduler
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    var deleteReminder by remember { mutableStateOf<Reminder?>(null) }

    BackHandler(enabled = !addOpen && deleteReminder == null, onBack = onBack)

    GlassBackground {
        if (addOpen) {
            ReminderEditorPage(
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
            return@GlassBackground
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ReminderTopBar(onBack = onBack, onAdd = { addOpen = true })

            when (val current = reminders) {
                null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GlassLoadingIndicator()
                }

                else -> ReminderList(
                    reminders = current,
                    onComplete = { reminder ->
                        scope.launch {
                            container.reminderRepository.markComplete(reminder.reminderId)
                            scheduler.cancel(reminder.reminderId)
                            NotificationManagerCompat.from(context).cancel(ReminderNotifier.notificationId(reminder.reminderId))
                        }
                    },
                    onDelete = { reminder ->
                        deleteReminder = reminder
                    },
                )
            }
        }

        deleteReminder?.let { reminder ->
            DeleteReminderDialog(
                reminder = reminder,
                onDismiss = { deleteReminder = null },
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
}

@Composable
private fun ReminderTopBar(onBack: () -> Unit, onAdd: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AppBackButton(onClick = onBack)
        Column(modifier = Modifier.weight(1f)) {
            Text("Reminder", color = SoftText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Notifications stay until you complete them.", color = MutedText, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onAdd, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.Add, contentDescription = "Add reminder", tint = Cyan)
        }
    }
}

@Composable
private fun ReminderList(
    reminders: List<Reminder>,
    onComplete: (Reminder) -> Unit,
    onDelete: (Reminder) -> Unit,
) {
    if (reminders.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No reminders yet", color = MutedText, style = MaterialTheme.typography.bodyMedium)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(reminders, key = { it.reminderId }) { reminder ->
            ReminderRow(
                reminder = reminder,
                onComplete = { onComplete(reminder) },
                onDelete = { onDelete(reminder) },
            )
        }
    }
}

@Composable
private fun ReminderRow(
    reminder: Reminder,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(16.dp), selected = !reminder.completed, tintStrength = 0.10f)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            if (reminder.completed) Icons.Rounded.Check else Icons.Rounded.Notifications,
            contentDescription = null,
            tint = if (reminder.completed) MutedText else Cyan,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                reminder.title,
                color = SoftText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                reminder.scheduledAtMillis.toReminderText(),
                color = MutedText,
                style = MaterialTheme.typography.bodySmall,
            )
            if (reminder.completed) {
                Text("Acknowledged", color = MutedText, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (!reminder.completed) {
            IconButton(onClick = onComplete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Check, contentDescription = "Complete", tint = Cyan, modifier = Modifier.size(20.dp))
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MutedText, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ReminderEditorPage(
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var hour by remember { mutableStateOf(hour12(LocalDateTime.now().plusMinutes(5).hour).toString()) }
    var minute by remember { mutableStateOf(LocalDateTime.now().plusMinutes(5).minute.toString().padStart(2, '0')) }
    var period by remember { mutableStateOf(if (LocalDateTime.now().plusMinutes(5).hour < 12) DayPeriod.AM else DayPeriod.PM) }

    val scheduledAtMillis = remember(date, hour, minute, period) {
        val localDateTime = LocalDateTime.of(
            date,
            java.time.LocalTime.of(
                hour24(hour.toIntOrNull() ?: 8, period),
                (minute.toIntOrNull() ?: 0).coerceIn(0, 59),
            ),
        )
        localDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
    val canSave = title.trim().isNotBlank() && scheduledAtMillis > System.currentTimeMillis()

    BackHandler(onBack = onDismiss)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.statusBars.asPaddingValues())
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppBackButton(onClick = onDismiss)
            Text(
                "Add Reminder",
                color = SoftText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(80) },
            label = { Text("Reminder") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            colors = reminderFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )

        ReminderDateField(date = date, onDateChange = { date = it })

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = hour,
                onValueChange = { hour = it.filter(Char::isDigit).take(2) },
                label = { Text("Hour") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = reminderFieldColors(),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = minute,
                onValueChange = { minute = it.filter(Char::isDigit).take(2) },
                label = { Text("Minute") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = reminderFieldColors(),
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = period.name,
                modifier = Modifier.height(56.dp),
                onClick = { period = if (period == DayPeriod.AM) DayPeriod.PM else DayPeriod.AM },
            )
        }

        Text(
            if (canSave) "Notification will stay until Complete is tapped." else "Choose a future date and time.",
            color = MutedText,
            style = MaterialTheme.typography.bodySmall,
        )

        Spacer(Modifier.weight(1f))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton(text = "Cancel", modifier = Modifier.weight(1f), onClick = onDismiss)
            PrimaryButton(
                text = "Save",
                modifier = Modifier.weight(1f),
                enabled = canSave,
                onClick = { onSave(title, scheduledAtMillis) },
            )
        }
    }
}

@Composable
private fun DeleteReminderDialog(
    reminder: Reminder,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Reminder", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "Remove '${reminder.title}'?",
                color = MutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = DangerRed, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MutedText)
            }
        },
        containerColor = PanelAlt,
        titleContentColor = SoftText,
        textContentColor = SoftText,
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun ReminderDateField(
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val state = rememberDatePickerState(initialSelectedDateMillis = date.toMillis())

    SecondaryButton(
        text = date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = {
            Icon(Icons.Rounded.Event, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
        },
        onClick = { open = true },
    )

    if (open) {
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { onDateChange(it.toLocalDate()) }
                        open = false
                    },
                ) {
                    Text("Select", color = Cyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) {
                    Text("Cancel", color = MutedText)
                }
            },
            colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = PanelAlt),
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun reminderFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Cyan,
    unfocusedBorderColor = Stroke,
    focusedTextColor = SoftText,
    unfocusedTextColor = SoftText,
    focusedLabelColor = Cyan,
    unfocusedLabelColor = MutedText,
    cursorColor = Cyan,
)

private enum class DayPeriod {
    AM,
    PM,
}

private fun hour12(hour: Int): Int {
    return when (val value = hour.coerceIn(0, 23) % 12) {
        0 -> 12
        else -> value
    }
}

private fun hour24(hour: Int, period: DayPeriod): Int {
    val normalized = hour.coerceIn(1, 12)
    return when (period) {
        DayPeriod.AM -> if (normalized == 12) 0 else normalized
        DayPeriod.PM -> if (normalized == 12) 12 else normalized + 12
    }
}

private fun Long.toReminderText(): String {
    return Instant.ofEpochMilli(this)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
        .format(DateTimeFormatter.ofPattern("dd MMM yyyy, h:mm a"))
}

private fun LocalDate.toMillis(): Long {
    return atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

private fun Long.toLocalDate(): LocalDate {
    return Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
}

private fun requestNotificationPermissionIfNeeded(activity: Activity?) {
    if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
    ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 51)
}
