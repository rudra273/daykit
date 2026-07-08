package com.daykit.feature.habit.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY active DESC, kind ASC, name COLLATE NOCASE")
    fun observeHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit_logs ORDER BY date ASC, createdAtMillis ASC")
    fun observeLogs(): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habits ORDER BY createdAtMillis ASC")
    suspend fun getAllHabits(): List<HabitEntity>

    @Query("SELECT * FROM habit_logs ORDER BY createdAtMillis ASC")
    suspend fun getAllLogs(): List<HabitLogEntity>

    @Query("SELECT * FROM habits WHERE habitId = :habitId LIMIT 1")
    suspend fun getHabit(habitId: String): HabitEntity?

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND date = :date AND relapse = 0 ORDER BY createdAtMillis DESC LIMIT 1")
    suspend fun getDailyLog(habitId: String, date: String): HabitLogEntity?

    @Upsert
    suspend fun upsertHabit(entity: HabitEntity)

    @Upsert
    suspend fun upsertLog(entity: HabitLogEntity)

    @Query("DELETE FROM habits WHERE habitId = :habitId")
    suspend fun deleteHabit(habitId: String)

    @Query("DELETE FROM habit_logs WHERE habitId = :habitId")
    suspend fun deleteLogsForHabit(habitId: String)
}
