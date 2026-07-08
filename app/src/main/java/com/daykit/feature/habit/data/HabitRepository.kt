package com.rudra.daykit.feature.habit.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import java.util.UUID

class HabitRepository(
    private val dao: HabitDao,
) {
    fun observeDashboard(): Flow<HabitDashboard> {
        return combine(
            dao.observeHabits(),
            dao.observeLogs(),
        ) { habits, logs ->
            HabitDashboard(
                habits = habits.map { it.toDomain() },
                logs = logs.map { it.toDomain() },
                today = LocalDate.now(),
            )
        }
    }

    suspend fun addHabit(
        name: String,
        kind: HabitKind,
        goalType: HabitGoalType,
        targetMinutes: Int,
        targetCount: Int,
        unitLabel: String,
        colorIndex: Int,
        reminderEnabled: Boolean,
        reminderHour: Int,
        reminderMinute: Int,
    ): Habit {
        require(name.trim().isNotBlank()) { "Habit name cannot be empty" }
        val now = System.currentTimeMillis()
        val habit = Habit(
            habitId = UUID.randomUUID().toString(),
            name = name.trim(),
            kind = kind,
            goalType = goalType,
            targetMinutes = targetMinutes.coerceAtLeast(0),
            targetCount = targetCount.coerceAtLeast(0),
            unitLabel = unitLabel.trim().ifBlank { "times" },
            colorIndex = colorIndex,
            reminderEnabled = reminderEnabled,
            reminderHour = reminderHour.coerceIn(0, 23),
            reminderMinute = reminderMinute.coerceIn(0, 59),
            active = true,
            createdAtMillis = now,
            updatedAtMillis = now,
        )
        dao.upsertHabit(habit.toEntity())
        return habit
    }

    suspend fun updateHabit(
        habitId: String,
        name: String,
        goalType: HabitGoalType,
        targetMinutes: Int,
        targetCount: Int,
        unitLabel: String,
        colorIndex: Int,
        reminderEnabled: Boolean,
        reminderHour: Int,
        reminderMinute: Int,
        active: Boolean,
    ) {
        require(name.trim().isNotBlank()) { "Habit name cannot be empty" }
        val existing = dao.getHabit(habitId) ?: return
        dao.upsertHabit(
            existing.copy(
                name = name.trim(),
                goalType = goalType.name,
                targetMinutes = targetMinutes.coerceAtLeast(0),
                targetCount = targetCount.coerceAtLeast(0),
                unitLabel = unitLabel.trim().ifBlank { "times" },
                colorIndex = colorIndex,
                reminderEnabled = reminderEnabled,
                reminderHour = reminderHour.coerceIn(0, 23),
                reminderMinute = reminderMinute.coerceIn(0, 59),
                active = active,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun saveDailyProgress(
        habitId: String,
        date: LocalDate,
        minutes: Int,
        progressCount: Int,
        completed: Boolean,
        note: String,
    ) {
        val now = System.currentTimeMillis()
        val existing = dao.getDailyLog(habitId, date.toString())
        dao.upsertLog(
            HabitLogEntity(
                id = existing?.id ?: 0,
                logId = existing?.logId ?: UUID.randomUUID().toString(),
                habitId = habitId,
                date = date.toString(),
                minutes = minutes.coerceAtLeast(0),
                progressCount = progressCount.coerceAtLeast(0),
                completed = completed,
                relapse = false,
                note = note.trim(),
                createdAtMillis = existing?.createdAtMillis ?: now,
                updatedAtMillis = now,
            ),
        )
    }

    suspend fun addRelapse(habitId: String, date: LocalDate, note: String) {
        val now = System.currentTimeMillis()
        dao.upsertLog(
            HabitLogEntity(
                logId = UUID.randomUUID().toString(),
                habitId = habitId,
                date = date.toString(),
                minutes = 0,
                progressCount = 0,
                completed = false,
                relapse = true,
                note = note.trim(),
                createdAtMillis = now,
                updatedAtMillis = now,
            ),
        )
    }

    suspend fun deleteHabit(habitId: String) {
        dao.deleteLogsForHabit(habitId)
        dao.deleteHabit(habitId)
    }

    suspend fun exportRecords(): HabitBackupRecord {
        return HabitBackupRecord(
            habits = dao.getAllHabits().map { it.toDomain() },
            logs = dao.getAllLogs().map { it.toDomain() },
        )
    }

    suspend fun importRecords(record: HabitBackupRecord) {
        record.habits.forEach { dao.upsertHabit(it.toEntity()) }
        record.logs.forEach { dao.upsertLog(it.toEntity()) }
    }
}

fun HabitEntity.toDomain(): Habit {
    return Habit(
        habitId = habitId,
        name = name,
        kind = HabitKind.valueOf(kind),
        goalType = HabitGoalType.valueOf(goalType),
        targetMinutes = targetMinutes,
        targetCount = targetCount,
        unitLabel = unitLabel,
        colorIndex = colorIndex,
        reminderEnabled = reminderEnabled,
        reminderHour = reminderHour,
        reminderMinute = reminderMinute,
        active = active,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )
}

fun Habit.toEntity(): HabitEntity {
    return HabitEntity(
        habitId = habitId,
        name = name,
        kind = kind.name,
        goalType = goalType.name,
        targetMinutes = targetMinutes,
        targetCount = targetCount,
        unitLabel = unitLabel,
        colorIndex = colorIndex,
        reminderEnabled = reminderEnabled,
        reminderHour = reminderHour,
        reminderMinute = reminderMinute,
        active = active,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )
}

fun HabitLogEntity.toDomain(): HabitLog {
    return HabitLog(
        logId = logId,
        habitId = habitId,
        date = date,
        minutes = minutes,
        progressCount = progressCount,
        completed = completed,
        relapse = relapse,
        note = note,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )
}

fun HabitLog.toEntity(): HabitLogEntity {
    return HabitLogEntity(
        logId = logId,
        habitId = habitId,
        date = date,
        minutes = minutes,
        progressCount = progressCount,
        completed = completed,
        relapse = relapse,
        note = note,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )
}
