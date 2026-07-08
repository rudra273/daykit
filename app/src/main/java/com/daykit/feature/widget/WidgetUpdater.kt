package com.daykit.feature.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context

internal fun updateExpenseWidgets(context: Context) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, ExpenseQuickAddWidgetProvider::class.java))
    ExpenseQuickAddWidgetProvider.updateWidgets(context, appWidgetManager, ids)
}

internal fun updateHabitWidgets(context: Context) {
    val appWidgetManager = AppWidgetManager.getInstance(context)
    val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, HabitCheckInWidgetProvider::class.java))
    if (ids.isNotEmpty()) {
        appWidgetManager.notifyAppWidgetViewDataChanged(ids, com.daykit.R.id.widget_habit_list)
        HabitCheckInWidgetProvider.updateWidgets(context, appWidgetManager, ids)
    }
}
