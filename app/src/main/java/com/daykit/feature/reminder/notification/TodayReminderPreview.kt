package com.daykit.feature.reminder.notification

import com.daykit.feature.reminder.data.Reminder
import java.time.Instant
import java.time.ZoneId

internal data class TodayReminderPreview(val reminderId: String, val title: String, val dueAtMillis: Long)

/** Use the next future occurrence, including snoozes, and never show another day's reminder. */
internal fun nextReminderToday(
    reminders: List<Reminder>,
    nowMillis: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): TodayReminderPreview? {
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return reminders.asSequence()
        .filter { !it.completed && !it.paused }
        .mapNotNull { reminder ->
            val pending = reminder.pendingOccurrenceMillis
            val scheduled = reminder.scheduledAtMillis.takeIf { pending == null || it > pending }
            val dueAt = listOfNotNull(
                reminder.snoozedUntilMillis ?: pending,
                scheduled,
            ).filter { it > nowMillis }.minOrNull() ?: return@mapNotNull null
            if (Instant.ofEpochMilli(dueAt).atZone(zone).toLocalDate() != today) return@mapNotNull null
            TodayReminderPreview(reminder.reminderId, reminder.title, dueAt)
        }
        .minWithOrNull(compareBy<TodayReminderPreview> { it.dueAtMillis }.thenBy { it.reminderId })
}
