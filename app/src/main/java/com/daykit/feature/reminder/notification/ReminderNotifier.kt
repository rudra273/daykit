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
            .setDeleteIntent(dismissedPendingIntent)
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
