package com.daykit.feature.reminder.data

data class Reminder(
    val reminderId: String,
    val title: String,
    val scheduledAtMillis: Long,
    val completed: Boolean,
    val acknowledgedAtMillis: Long?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
