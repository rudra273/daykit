@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.daykit.feature.settings.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AlarmOn
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.daykit.core.data.AppPreferences
import com.daykit.core.data.ReminderSnooze
import com.daykit.core.designsystem.AccentColors
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppListRow
import com.daykit.core.designsystem.components.AppSwitch
import com.daykit.core.designsystem.components.RowDivider
import com.daykit.core.designsystem.components.SectionHeader
import com.daykit.core.designsystem.extendedColors
import com.daykit.core.permissions.AppLockPermissionChecker
import com.daykit.core.permissions.PermissionIntents
import com.daykit.feature.reminder.notification.ReminderNotifier

private data class PermissionRow(
    val title: String,
    val purpose: String,
    val icon: ImageVector,
    val accent: Color,
    val granted: Boolean,
    val intent: (Context) -> Intent,
)

@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val accents = MaterialTheme.extendedColors.accents
    var showSnoozePicker by remember { mutableStateOf(false) }
    // Permissions change in system Settings, so re-check every time the page resumes.
    var permissions by remember { mutableStateOf(permissionRows(context, accents)) }

    val snoozeMinutes by AppPreferences.rememberPreference(AppPreferences.KEY_SNOOZE_MINUTES) {
        AppPreferences.snoozeMinutes
    }
    val fullScreen by AppPreferences.rememberPreference(AppPreferences.KEY_REMINDER_FULL_SCREEN) {
        AppPreferences.reminderFullScreen
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissions = permissionRows(context, accents)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    SettingsSubPage(title = "Notifications & Permissions", onBack = onBack) {
        item { SectionHeader("Reminders", topPadding = 0.dp) }
        item {
            AppCard(contentPadding = PaddingValues(0.dp)) {
                AppListRow(
                    headline = "Snooze Length",
                    supporting = "$snoozeMinutes minutes",
                    leadingIcon = Icons.Rounded.Snooze,
                    leadingAccent = accents.orange,
                    trailing = { NavChevron() },
                    onClick = { showSnoozePicker = true },
                )
                RowDivider(startIndent = Spacing.lg)
                AppListRow(
                    headline = "Full-Screen Alarm",
                    supporting = if (fullScreen) "Ring over the lock screen" else "Notification only",
                    leadingIcon = Icons.Rounded.AlarmOn,
                    leadingAccent = accents.red,
                    onClick = { AppPreferences.reminderFullScreen = !fullScreen },
                    trailing = {
                        AppSwitch(
                            checked = fullScreen,
                            onCheckedChange = { AppPreferences.reminderFullScreen = it },
                        )
                    },
                )
                RowDivider(startIndent = Spacing.lg)
                AppListRow(
                    headline = "Sound & Vibration",
                    supporting = "Android's settings for reminder alerts",
                    leadingIcon = Icons.Rounded.VolumeUp,
                    leadingAccent = accents.purple,
                    trailing = { NavChevron() },
                    onClick = { context.startActivity(reminderChannelSettings(context)) },
                )
            }
        }

        item { SectionHeader("Permissions") }
        item {
            AppCard(contentPadding = PaddingValues(0.dp)) {
                permissions.forEachIndexed { index, row ->
                    AppListRow(
                        headline = row.title,
                        supporting = row.purpose,
                        leadingIcon = row.icon,
                        leadingAccent = row.accent,
                        trailing = { StatusPill(if (row.granted) "Allowed" else "Off", ok = row.granted) },
                        onClick = { runCatching { context.startActivity(row.intent(context)) } },
                    )
                    if (index < permissions.lastIndex) RowDivider(startIndent = Spacing.lg)
                }
            }
        }
        item { SettingsFootnote("Tap a permission to change it in Android settings.") }
    }

    if (showSnoozePicker) {
        OptionSheet(
            title = "Snooze Length",
            description = "Used by the Snooze button on reminder notifications.",
            options = ReminderSnooze.OPTIONS_MINUTES,
            selected = snoozeMinutes,
            label = { "$it minutes" },
            onDismiss = { showSnoozePicker = false },
            onSelect = {
                AppPreferences.snoozeMinutes = it
                showSnoozePicker = false
            },
        )
    }
}

private fun permissionRows(context: Context, accents: AccentColors): List<PermissionRow> {
    val appDetails: (Context) -> Intent = {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${it.packageName}".toUri())
    }
    return buildList {
        add(
            PermissionRow(
                "Notifications", "Reminders, habits and App Lock status",
                Icons.Rounded.Notifications, accents.pink,
                NotificationManagerCompat.from(context).areNotificationsEnabled(),
            ) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, it.packageName)
            },
        )
        add(
            PermissionRow(
                "Usage Access", "App Lock and Focus see which app is open",
                Icons.Rounded.DataUsage, accents.blue,
                AppLockPermissionChecker.hasUsageAccess(context),
            ) { PermissionIntents.usageAccessSettings() },
        )
        add(
            PermissionRow(
                "Display Over Other Apps", "App Lock shows its lock screen",
                Icons.Rounded.Layers, accents.indigo,
                Settings.canDrawOverlays(context),
            ) { PermissionIntents.overlaySettings(it) },
        )
        add(
            PermissionRow(
                "Alarms & Reminders", "Reminders and Focus routines start on time",
                Icons.Rounded.Alarm, accents.orange,
                AppLockPermissionChecker.canScheduleExactAlarms(context),
            ) { PermissionIntents.exactAlarmSettings(it) },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            add(
                PermissionRow(
                    "Full-Screen Alerts", "Reminder alarms over the lock screen",
                    Icons.Rounded.Fullscreen, accents.red,
                    context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent(),
                ) {
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, "package:${it.packageName}".toUri())
                },
            )
        }
        add(
            PermissionRow(
                "Unrestricted Battery", "Keeps App Lock running in the background",
                Icons.Rounded.BatteryChargingFull, accents.green,
                context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
                appDetails,
            ),
        )
    }
}

private fun reminderChannelSettings(context: Context): Intent {
    // The channel only exists once a reminder has fired; create it so the page isn't empty.
    ReminderNotifier.ensureChannel(context)
    return Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .putExtra(Settings.EXTRA_CHANNEL_ID, ReminderNotifier.CHANNEL_ID)
}
