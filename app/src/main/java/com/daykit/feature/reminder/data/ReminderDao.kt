package com.daykit.feature.reminder.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY completed ASC, scheduledAtMillis ASC")
    fun observeReminders(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE reminderId = :reminderId LIMIT 1")
    suspend fun getReminder(reminderId: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE completed = 0 AND paused = 0 ORDER BY scheduledAtMillis ASC")
    suspend fun getPendingReminders(): List<ReminderEntity>

    @Query("SELECT * FROM reminders ORDER BY scheduledAtMillis ASC")
    suspend fun getAllReminders(): List<ReminderEntity>

    @Upsert
    suspend fun upsertReminders(entities: List<ReminderEntity>)

    @Upsert
    suspend fun upsertReminder(entity: ReminderEntity)

    @Query("DELETE FROM reminders WHERE reminderId = :reminderId")
    suspend fun deleteReminder(reminderId: String)

    @Query("SELECT * FROM reminder_occurrences WHERE reminderId = :reminderId ORDER BY occurrenceMillis DESC LIMIT 50")
    suspend fun getOccurrenceHistory(reminderId: String): List<ReminderOccurrenceEntity>

    @Upsert
    suspend fun upsertOccurrence(entity: ReminderOccurrenceEntity)

    @Transaction
    suspend fun upsertReminderAndOccurrence(
        reminder: ReminderEntity,
        occurrence: ReminderOccurrenceEntity,
    ) {
        upsertReminder(reminder)
        upsertOccurrence(occurrence)
    }

    @Query("DELETE FROM reminder_occurrences WHERE reminderId = :reminderId")
    suspend fun deleteOccurrences(reminderId: String)

    @Transaction
    suspend fun deleteReminderAndOccurrences(reminderId: String) {
        deleteOccurrences(reminderId)
        deleteReminder(reminderId)
    }
}
