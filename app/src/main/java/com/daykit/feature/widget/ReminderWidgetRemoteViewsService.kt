package com.daykit.feature.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.daykit.DayKitApplication
import com.daykit.R
import com.daykit.feature.reminder.data.Reminder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.Date

class ReminderWidgetRemoteViewsService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = ReminderFactory(applicationContext)
}

private class ReminderFactory(private val context: Context) : RemoteViewsService.RemoteViewsFactory {
    private var rows = emptyList<Reminder>()
    private var failed = false
    override fun onCreate() = Unit
    override fun onDestroy() { rows = emptyList() }
    override fun onDataSetChanged() {
        runCatching {
            runBlocking {
                (context as DayKitApplication).container.reminderRepository.observeReminders().first()
            }
        }.onSuccess { rows = reminderWidgetRows(it); failed = false }
            .onFailure { rows = emptyList(); failed = true }
    }
    override fun getCount() = if (failed) 1 else rows.size
    override fun getViewTypeCount() = 2
    override fun hasStableIds() = false
    override fun getItemId(position: Int) = position.toLong()
    override fun getLoadingView(): RemoteViews? = null
    override fun getViewAt(position: Int): RemoteViews {
        val reminder = rows.getOrNull(position)
            ?: return RemoteViews(context.packageName, R.layout.widget_reminder_error)
        val time = reminder.widgetDisplayTime
        val formatted = android.text.format.DateFormat.getMediumDateFormat(context).format(Date(time)) +
            " · " + android.text.format.DateFormat.getTimeFormat(context).format(Date(time))
        val overdue = time <= System.currentTimeMillis()
        val status = when {
            reminder.snoozedUntilMillis != null -> R.string.widget_reminder_snoozed
            overdue -> R.string.widget_reminder_overdue
            else -> R.string.widget_reminder_upcoming
        }
        val completeLabel = if (reminder.recurrence != null && reminder.pendingOccurrenceMillis == null)
            R.string.widget_reminder_skip else R.string.widget_reminder_complete
        fun action(command: String) = Intent().apply {
            putExtra(ReminderWidgetProvider.EXTRA_ID, reminder.reminderId)
            putExtra(ReminderWidgetProvider.EXTRA_OCCURRENCE, reminder.widgetOccurrence)
            putExtra(ReminderWidgetProvider.EXTRA_COMMAND, command)
        }
        return RemoteViews(context.packageName, R.layout.widget_reminder_row).apply {
            setTextViewText(R.id.widget_reminder_name, reminder.title)
            setTextViewText(R.id.widget_reminder_time, context.getString(status, formatted))
            setTextColor(R.id.widget_reminder_time, context.getColor(if (overdue) R.color.danger else R.color.text_muted))
            setTextViewText(R.id.widget_reminder_complete, context.getString(completeLabel))
            setContentDescription(R.id.widget_reminder_complete, context.getString(completeLabel) + ": " + reminder.title)
            setContentDescription(R.id.widget_reminder_snooze, context.getString(R.string.widget_reminder_snooze) + ": " + reminder.title)
            setOnClickFillInIntent(R.id.widget_reminder_complete, action("complete"))
            setOnClickFillInIntent(R.id.widget_reminder_snooze, action("snooze"))
            setViewVisibility(R.id.widget_reminder_snooze,
                if (reminder.pendingOccurrenceMillis != null && reminder.snoozedUntilMillis == null) View.VISIBLE else View.GONE)
        }
    }
}
