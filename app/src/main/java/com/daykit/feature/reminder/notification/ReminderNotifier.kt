package com.daykit.feature.reminder.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.daykit.MainActivity
import com.daykit.R
import com.daykit.feature.reminder.ui.ReminderAlarmActivity

object ReminderNotifier {
    private const val CHANNEL_ID = "reminders"

    fun show(context: Context, reminderId: String, title: String) {
        val appContext = context.applicationContext
        ensureChannel(appContext)
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val openPendingIntent = PendingIntent.getActivity(
            appContext,
            reminderId.hashCode(),
            Intent(appContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Full-screen intent launches the alarm-style page over the lock screen when the
        // device is idle; otherwise it surfaces as a heads-up notification the user taps.
        val fullScreenPendingIntent = PendingIntent.getActivity(
            appContext,
            reminderId.hashCode() * 17,
            ReminderAlarmActivity.intent(appContext, reminderId, title)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val completePendingIntent = PendingIntent.getBroadcast(
            appContext,
            reminderId.hashCode(),
            Intent(appContext, ReminderActionReceiver::class.java)
                .setAction(ReminderActionReceiver.ACTION_COMPLETE)
                .putExtra(ReminderActionReceiver.EXTRA_REMINDER_ID, reminderId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismissedPendingIntent = PendingIntent.getBroadcast(
            appContext,
            reminderId.hashCode() * 31,
            Intent(appContext, ReminderActionReceiver::class.java)
                .setAction(ReminderActionReceiver.ACTION_DISMISSED)
                .putExtra(ReminderActionReceiver.EXTRA_REMINDER_ID, reminderId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("Tap Complete after you acknowledge this reminder.")
            .setContentIntent(openPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setDeleteIntent(dismissedPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(R.drawable.ic_todo_tracker, "Complete", completePendingIntent)
            .build()
        NotificationManagerCompat.from(appContext).notify(notificationId(reminderId), notification)
    }

    fun notificationId(reminderId: String): Int = reminderId.hashCode()

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Reminders",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Reminder notifications that stay until completed."
        }
        manager.createNotificationChannel(channel)
    }
}
