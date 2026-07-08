package com.rudra.daykit.feature.habit.data

import java.time.LocalDate

data class Habit(
    val habitId: String,
    val name: String,
    val kind: HabitKind,
    val goalType: HabitGoalType,
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

enum class HabitKind {
    Build,
    Quit,
}

enum class HabitGoalType {
    Time,
    Count,
    Check,
}

data class HabitLog(
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

data class HabitDashboard(
    val habits: List<Habit>,
    val logs: List<HabitLog>,
    val today: LocalDate,
) {
    val buildHabits: List<Habit> = habits.filter { it.kind == HabitKind.Build && it.active }
    val quitHabits: List<Habit> = habits.filter { it.kind == HabitKind.Quit && it.active }
    val todayLogs: List<HabitLog> = logs.filter { it.date == today.toString() }
    val todayMinutes: Int = todayLogs.filterNot { it.relapse }.sumOf { it.minutes }

    fun logFor(habitId: String, date: LocalDate = today): HabitLog? {
        return logs.lastOrNull { it.habitId == habitId && it.date == date.toString() && !it.relapse }
    }

    fun relapsesFor(habitId: String): List<HabitLog> {
        return logs.filter { it.habitId == habitId && it.relapse }
    }
}

data class HabitBackupRecord(
    val habits: List<Habit>,
    val logs: List<HabitLog>,
)
