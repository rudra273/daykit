package com.daykit.feature.reminder.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY completed ASC, scheduledAtMillis ASC")
    fun observeReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE reminderId = :reminderId LIMIT 1")
    suspend fun getReminder(reminderId: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE completed = 0 ORDER BY scheduledAtMillis ASC")
    suspend fun getPendingReminders(): List<ReminderEntity>

    @Query("SELECT * FROM reminders ORDER BY scheduledAtMillis ASC")
    suspend fun getAllReminders(): List<ReminderEntity>

    @Upsert
    suspend fun upsertReminders(entities: List<ReminderEntity>)

    @Upsert
    suspend fun upsertReminder(entity: ReminderEntity)

    @Query("DELETE FROM reminders WHERE reminderId = :reminderId")
    suspend fun deleteReminder(reminderId: String)
}
