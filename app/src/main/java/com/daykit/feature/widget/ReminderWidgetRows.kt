package com.daykit.feature.widget

import com.daykit.feature.reminder.data.Reminder

internal val Reminder.widgetOccurrence: Long
    get() = pendingOccurrenceMillis ?: scheduledAtMillis

internal val Reminder.widgetDisplayTime: Long
    get() = snoozedUntilMillis ?: widgetOccurrence

internal fun reminderWidgetRows(reminders: List<Reminder>): List<Reminder> = reminders
    .filter { !it.completed && !it.paused }
    .sortedWith(compareBy<Reminder> { it.widgetDisplayTime }.thenBy { it.reminderId })
