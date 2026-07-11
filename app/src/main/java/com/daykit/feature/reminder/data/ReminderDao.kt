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

    @Query("SELECT * FROM reminders WHERE completed = 0 AND scheduledAtMillis > :nowMillis ORDER BY scheduledAtMillis ASC")
    suspend fun getPendingFutureReminders(nowMillis: Long): List<ReminderEntity>

    @Upsert
    suspend fun upsertReminder(entity: ReminderEntity)

    @Query(
        """
        UPDATE reminders
        SET completed = 1,
            acknowledgedAtMillis = :acknowledgedAtMillis,
            updatedAtMillis = :acknowledgedAtMillis
        WHERE reminderId = :reminderId
        """,
    )
    suspend fun markComplete(reminderId: String, acknowledgedAtMillis: Long)

    @Query(
        """
        UPDATE reminders
        SET title = :title,
            scheduledAtMillis = :scheduledAtMillis,
            completed = 0,
            acknowledgedAtMillis = NULL,
            updatedAtMillis = :updatedAtMillis
        WHERE reminderId = :reminderId
        """,
    )
    suspend fun updateReminder(
        reminderId: String,
        title: String,
        scheduledAtMillis: Long,
        updatedAtMillis: Long,
    )

    @Query("DELETE FROM reminders WHERE reminderId = :reminderId")
    suspend fun deleteReminder(reminderId: String)
}
