package com.daykit.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.RemoteViews
import com.daykit.DayKitApplication
import com.daykit.MainActivity
import com.daykit.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = updateWidgets(context, manager, ids)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action in setOf(Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) {
            updateReminderWidgets(context)
            return
        }
        if (intent.action != ACTION_INTERACT) return
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        if (!intent.hasExtra(EXTRA_OCCURRENCE)) return
        val occurrence = intent.getLongExtra(EXTRA_OCCURRENCE, Long.MIN_VALUE)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = (context.applicationContext as DayKitApplication).container.reminderRepository
                when (intent.getStringExtra(EXTRA_COMMAND)) {
                    "complete" -> repository.markComplete(id, expectedOccurrenceMillis = occurrence)
                    "snooze" -> repository.snooze(id, occurrence, 10 * 60_000L)
                }
                updateReminderWidgets(context)
            } catch (error: Exception) {
                Log.w("ReminderWidget", "Could not update reminder", error)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_INTERACT = "com.daykit.widget.REMINDER_ACTION"
        const val EXTRA_ID = "reminder_id"
        const val EXTRA_OCCURRENCE = "occurrence"
        const val EXTRA_COMMAND = "command"

        fun updateWidgets(context: Context, manager: AppWidgetManager, ids: IntArray) {
            ids.forEach { id ->
                val adapter = Intent(context, ReminderWidgetRemoteViewsService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    data = Uri.parse("daykit://widget/reminders/$id")
                }
                val actions = PendingIntent.getBroadcast(context, id,
                    Intent(context, ReminderWidgetProvider::class.java).setAction(ACTION_INTERACT),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
                val open = PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val views = RemoteViews(context.packageName, R.layout.widget_reminders).apply {
                    setRemoteAdapter(R.id.widget_reminder_list, adapter)
                    setEmptyView(R.id.widget_reminder_list, R.id.widget_reminder_empty)
                    setPendingIntentTemplate(R.id.widget_reminder_list, actions)
                    setOnClickPendingIntent(R.id.widget_reminder_title, open)
                    setOnClickPendingIntent(R.id.widget_reminder_empty, open)
                }
                manager.updateAppWidget(id, views)
                manager.notifyAppWidgetViewDataChanged(id, R.id.widget_reminder_list)
            }
        }
    }
}
