package com.daykit.feature.reminder.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class ReminderRepository(
    private val dao: ReminderDao,
) {
    fun observeReminders(): Flow<List<Reminder>> {
        return dao.observeReminders().map { entities -> entities.map { it.toDomain() } }
    }

    suspend fun addReminder(title: String, scheduledAtMillis: Long): Reminder {
        require(title.trim().isNotBlank()) { "Reminder title cannot be empty" }
        val now = System.currentTimeMillis()
        val reminder = Reminder(
            reminderId = UUID.randomUUID().toString(),
            title = title.trim(),
            scheduledAtMillis = scheduledAtMillis,
            completed = false,
            acknowledgedAtMillis = null,
            createdAtMillis = now,
            updatedAtMillis = now,
        )
        dao.upsertReminder(reminder.toEntity())
        return reminder
    }

    suspend fun markComplete(reminderId: String) {
        dao.markComplete(reminderId, System.currentTimeMillis())
    }

    suspend fun deleteReminder(reminderId: String) {
        dao.deleteReminder(reminderId)
    }

    suspend fun getReminder(reminderId: String): Reminder? {
        return dao.getReminder(reminderId)?.toDomain()
    }

    suspend fun getPendingFutureReminders(nowMillis: Long = System.currentTimeMillis()): List<Reminder> {
        return dao.getPendingFutureReminders(nowMillis).map { it.toDomain() }
    }
}

fun ReminderEntity.toDomain(): Reminder {
    return Reminder(
        reminderId = reminderId,
        title = title,
        scheduledAtMillis = scheduledAtMillis,
        completed = completed,
        acknowledgedAtMillis = acknowledgedAtMillis,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )
}

fun Reminder.toEntity(): ReminderEntity {
    return ReminderEntity(
        reminderId = reminderId,
        title = title,
        scheduledAtMillis = scheduledAtMillis,
        completed = completed,
        acknowledgedAtMillis = acknowledgedAtMillis,
        createdAtMillis = createdAtMillis,
        updatedAtMillis = updatedAtMillis,
    )
}
