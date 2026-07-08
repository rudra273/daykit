package com.daykit.feature.habit.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "habits",
    indices = [Index(value = ["habitId"], unique = true)],
)
data class HabitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val habitId: String,
    val name: String,
    val kind: String,
    val goalType: String,
    val targetMinutes: Int,
    val targetCount: Int,
    val unitLabel: String,
    val colorIndex: Int,
    val reminderEnabled: Boolean,
    val reminderHour: Int,
    val reminderMinute: Int,
    val active: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "habit_logs",
    indices = [
        Index(value = ["logId"], unique = true),
        Index(value = ["habitId", "date"]),
    ],
)
data class HabitLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val logId: String,
    val habitId: String,
    val date: String,
    val minutes: Int,
    val progressCount: Int,
    val completed: Boolean,
    val relapse: Boolean,
    val note: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
