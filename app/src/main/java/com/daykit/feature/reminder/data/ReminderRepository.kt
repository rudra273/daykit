package com.daykit.feature.reminder.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class ReminderRepository(
    private val dao: ReminderDao,
    private val onChanged: (String, Reminder?) -> Unit = { _, _ -> },
    private val clock: () -> Long = System::currentTimeMillis,
) {
    // All alarm, UI, and notification mutations use the same application repository.
    private val mutex = Mutex()

    fun observeReminders(): Flow<List<Reminder>> = dao.observeReminders().map { rows -> rows.map { it.toDomain() } }

    suspend fun addReminder(title: String, scheduledAtMillis: Long, recurrence: ReminderRecurrence? = null): Reminder = mutex.withLock {
        validate(title, scheduledAtMillis, recurrence)
        val now = clock()
        val reminder = Reminder(UUID.randomUUID().toString(), title.trim(), scheduledAtMillis, false, null, now, now, recurrence)
        dao.upsertReminder(reminder.toEntity())
        onChanged(reminder.reminderId, reminder)
        reminder
    }

    suspend fun updateReminder(reminderId: String, title: String, scheduledAtMillis: Long, recurrence: ReminderRecurrence? = null): Reminder? = mutex.withLock {
        validate(title, scheduledAtMillis, recurrence)
        val entity = dao.getReminder(reminderId) ?: return@withLock null
        val updated = entity.toDomain().copy(title = title.trim(), scheduledAtMillis = scheduledAtMillis,
            completed = false, acknowledgedAtMillis = null, updatedAtMillis = clock(), recurrence = recurrence,
            pendingOccurrenceMillis = null)
        dao.upsertReminder(updated.toEntity().copy(id = entity.id))
        onChanged(reminderId, updated)
        updated
    }

    /** A notification acknowledges only the occurrence it displayed. UI completion skips the next one if none has fired. */
    suspend fun markComplete(reminderId: String, occurrenceMillis: Long? = null) = mutex.withLock {
        val entity = dao.getReminder(reminderId) ?: return@withLock
        val current = entity.toDomain()
        if (current.completed) return@withLock
        if (occurrenceMillis != null && current.pendingOccurrenceMillis != occurrenceMillis) return@withLock
        val now = clock()
        val next = if (current.pendingOccurrenceMillis != null && current.scheduledAtMillis > current.pendingOccurrenceMillis) {
            current.scheduledAtMillis
        } else current.recurrence?.nextAfter(maxOf(now, current.scheduledAtMillis))
        val updated = current.copy(scheduledAtMillis = next ?: current.scheduledAtMillis, completed = next == null,
            pendingOccurrenceMillis = null, acknowledgedAtMillis = now, updatedAtMillis = now)
        dao.upsertReminder(updated.toEntity().copy(id = entity.id))
        onChanged(reminderId, updated)
    }

    /** Persist and arm the next occurrence before displaying this one; duplicate/stale broadcasts are ignored. */
    suspend fun fireDue(reminderId: String, show: (Reminder) -> Unit) = mutex.withLock {
        val entity = dao.getReminder(reminderId) ?: return@withLock
        val current = entity.toDomain()
        val now = clock()
        if (current.completed || current.scheduledAtMillis > now || current.pendingOccurrenceMillis == current.scheduledAtMillis) return@withLock
        val next = current.recurrence?.nextAfter(now)
        val updated = current.copy(scheduledAtMillis = next ?: current.scheduledAtMillis,
            pendingOccurrenceMillis = current.scheduledAtMillis, updatedAtMillis = now)
        dao.upsertReminder(updated.toEntity().copy(id = entity.id))
        onChanged(reminderId, updated)
        show(current)
    }

    suspend fun deleteReminder(reminderId: String) = mutex.withLock {
        dao.deleteReminder(reminderId)
        onChanged(reminderId, null)
    }

    suspend fun getReminder(reminderId: String): Reminder? = dao.getReminder(reminderId)?.toDomain()

    suspend fun restoreAlarms(show: (Reminder) -> Unit) = mutex.withLock {
        dao.getPendingReminders().forEach { entity ->
            val reminder = entity.toDomain()
            onChanged(reminder.reminderId, reminder)
            reminder.pendingOccurrenceMillis?.let { show(reminder.copy(scheduledAtMillis = it)) }
        }
    }

    suspend fun exportForBackup(): List<Reminder> = mutex.withLock {
        dao.getAllReminders().map { it.toDomain() }
    }

    /** Merge missing reminders only; restoring an older backup never rolls back current edits/completions. */
    suspend fun importFromBackup(reminders: List<Reminder>) = mutex.withLock {
        reminders.forEach { reminder ->
            require(runCatching { UUID.fromString(reminder.reminderId).toString() == reminder.reminderId }.getOrDefault(false)) { "Invalid reminder ID" }
            require(reminder.title.isNotBlank() && reminder.title.length <= 80) { "Invalid reminder title" }
            require(reminder.scheduledAtMillis >= 0)
            require(reminder.pendingOccurrenceMillis == null || reminder.pendingOccurrenceMillis in 0..reminder.scheduledAtMillis)
            require(reminder.recurrence == null || reminder.recurrence.nextAfter(reminder.scheduledAtMillis - 1) == reminder.scheduledAtMillis) { "Invalid reminder recurrence" }
        }
        require(reminders.map { it.reminderId }.distinct().size == reminders.size) { "Duplicate reminder IDs" }
        val missing = reminders.filter { dao.getReminder(it.reminderId) == null }
        dao.upsertReminders(missing.map { it.toEntity() })
        missing.forEach { onChanged(it.reminderId, it) }
    }

    private fun validate(title: String, time: Long, rule: ReminderRecurrence?) {
        require(title.trim().isNotBlank()) { "Reminder title cannot be empty" }
        require(time > clock()) { "Choose a future date and time" }
        require(rule == null || rule.nextAfter(time - 1) == time) { "Start must match the repeat schedule" }
    }
}

fun ReminderEntity.toDomain() = Reminder(reminderId, title, scheduledAtMillis, completed, acknowledgedAtMillis,
    createdAtMillis, updatedAtMillis, recurrenceRule?.let(ReminderRecurrence::decode), pendingOccurrenceMillis)

fun Reminder.toEntity() = ReminderEntity(reminderId = reminderId, title = title, scheduledAtMillis = scheduledAtMillis,
    completed = completed, acknowledgedAtMillis = acknowledgedAtMillis, createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis, recurrenceRule = recurrence?.encode(), pendingOccurrenceMillis = pendingOccurrenceMillis)
