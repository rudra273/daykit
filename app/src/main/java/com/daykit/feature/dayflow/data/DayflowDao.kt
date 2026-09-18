package com.daykit.feature.dayflow.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface DayflowDao {
    @Query("SELECT * FROM dayflow_days WHERE date = :date LIMIT 1")
    fun observeDay(date: String): Flow<DayflowDayEntity?>

    @Query("SELECT * FROM dayflow_days WHERE mood != '' ORDER BY date DESC")
    fun observeMoodHistory(): Flow<List<DayflowDayEntity>>

    @Query("SELECT * FROM dayflow_days WHERE journal != '' OR journalTitle != '' ORDER BY date DESC")
    fun observeJournalHistory(): Flow<List<DayflowDayEntity>>

    @Query("SELECT * FROM dayflow_days WHERE date = :date LIMIT 1")
    suspend fun getDay(date: String): DayflowDayEntity?

    @Query("SELECT * FROM dayflow_days ORDER BY date DESC")
    suspend fun getDays(): List<DayflowDayEntity>

    @Upsert suspend fun upsertDay(day: DayflowDayEntity)

    @Query("SELECT * FROM dayflow_sessions ORDER BY startedAtMillis DESC")
    fun observeSessions(): Flow<List<PomodoroSessionEntity>>

    @Query("SELECT * FROM dayflow_sessions ORDER BY startedAtMillis DESC")
    suspend fun getSessions(): List<PomodoroSessionEntity>

    @Query("SELECT * FROM dayflow_sessions WHERE state IN ('running', 'paused') ORDER BY startedAtMillis DESC LIMIT 1")
    suspend fun getActiveSession(): PomodoroSessionEntity?

    @Upsert suspend fun upsertSession(session: PomodoroSessionEntity)
}
