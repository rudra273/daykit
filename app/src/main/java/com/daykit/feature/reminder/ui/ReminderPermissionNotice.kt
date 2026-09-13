package com.daykit.feature.reminder.ui

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.daykit.core.designsystem.Spacing

@Composable
internal fun ReminderPermissionNotice() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) revision++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val notificationsEnabled = remember(revision) { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val exactEnabled = remember(revision) { context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() }
    if (!notificationsEnabled || !exactEnabled) {
        Column(Modifier.padding(horizontal = Spacing.lg)) {
            Text(if (!notificationsEnabled) "Notifications are off. Reminders cannot alert you." else "Allow precise alarms to receive reminders on time.")
            TextButton(onClick = {
                val intent = if (!notificationsEnabled) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                else Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                context.startActivity(intent)
            }) { Text(if (!notificationsEnabled) "Enable notifications" else "Allow precise alarms") }
        }
    }
}
