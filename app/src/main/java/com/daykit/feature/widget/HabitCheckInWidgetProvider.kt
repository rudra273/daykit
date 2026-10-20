package com.daykit.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.daykit.MainActivity
import com.daykit.R

class HabitCheckInWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action in setOf(Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)) {
            updateHabitWidgets(context)
        }
    }

    companion object {
        const val ACTION_TOGGLE_HABIT = "com.daykit.widget.TOGGLE_HABIT"
        const val EXTRA_HABIT_ID = "extra_habit_id"
        const val EXTRA_DATE = "extra_date"
        const val EXTRA_COMPLETED = "extra_completed"

        fun updateWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
            appWidgetIds.forEach { appWidgetId ->
                val serviceIntent = Intent(context, HabitWidgetRemoteViewsService::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                    data = android.net.Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
                }
                // Toggles go to the non-exported HabitToggleReceiver: this provider has to stay
                // exported for widget updates, and another app must not be able to tick a habit.
                val toggleIntent = Intent(context, HabitToggleReceiver::class.java).apply {
                    action = ACTION_TOGGLE_HABIT
                }
                val togglePendingIntent = PendingIntent.getBroadcast(
                    context,
                    appWidgetId,
                    toggleIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                val views = RemoteViews(context.packageName, R.layout.widget_habit_checkin).apply {
                    val openApp = PendingIntent.getActivity(context, appWidgetId,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    setOnClickPendingIntent(R.id.widget_title, openApp)
                    setOnClickPendingIntent(R.id.widget_empty, openApp)
                    setRemoteAdapter(R.id.widget_habit_list, serviceIntent)
                    setEmptyView(R.id.widget_habit_list, R.id.widget_empty)
                    setPendingIntentTemplate(R.id.widget_habit_list, togglePendingIntent)
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
                appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_habit_list)
            }
        }
    }
}
