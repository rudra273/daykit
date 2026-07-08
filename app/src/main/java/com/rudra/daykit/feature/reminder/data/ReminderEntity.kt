package com.rudra.daykit.feature.reminder.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminders",
    indices = [Index(value = ["reminderId"], unique = true)],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val reminderId: String,
    val title: String,
    val scheduledAtMillis: Long,
    val completed: Boolean,
    val acknowledgedAtMillis: Long?,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
