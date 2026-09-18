package com.daykit.feature.dayflow.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dayflow_days")
data class DayflowDayEntity(
    @PrimaryKey val date: String,
    val journal: String = "",
    val mood: String = "",
    val updatedAtMillis: Long = 0,
    val journalTitle: String = "",
)

@Entity(tableName = "dayflow_sessions")
data class PomodoroSessionEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val startedAtMillis: Long,
    val endAtMillis: Long,
    val remainingMillis: Long,
    val state: String,
    val finishedAtMillis: Long? = null,
)
