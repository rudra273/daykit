package com.daykit.feature.reminder.data

data class Reminder(
    val reminderId: String,
    val title: String,
    val scheduledAtMillis: Long,
    val completed: Boolean,
    val acknowledgedAtMillis: Long?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val recurrence: ReminderRecurrence? = null,
    val pendingOccurrenceMillis: Long? = null,
    val paused: Boolean = false,
    val snoozedUntilMillis: Long? = null,
)

enum class ReminderOccurrenceAction { COMPLETED, SKIPPED }

data class ReminderOccurrence(
    val occurrenceMillis: Long,
    val action: ReminderOccurrenceAction,
    val actionAtMillis: Long,
)
