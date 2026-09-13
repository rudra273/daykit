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
            pendingOccurrenceMillis = null, paused = false, snoozedUntilMillis = null)
        dao.upsertReminder(updated.toEntity().copy(id = entity.id))
        onChanged(reminderId, updated)
        updated
    }

    /** A notification acknowledges only the occurrence it displayed. UI completion skips the next one if none has fired. */
    suspend fun markComplete(reminderId: String, occurrenceMillis: Long? = null) = mutex.withLock {
        val entity = dao.getReminder(reminderId) ?: return@withLock
        val current = entity.toDomain()
        if (current.completed || current.paused) return@withLock
        if (occurrenceMillis != null && current.pendingOccurrenceMillis != occurrenceMillis) return@withLock
        val now = clock()
        val occurrence = current.pendingOccurrenceMillis ?: current.scheduledAtMillis
        val next = if (current.pendingOccurrenceMillis != null && current.scheduledAtMillis > current.pendingOccurrenceMillis) {
            current.scheduledAtMillis
        } else current.recurrence?.nextAfter(maxOf(now, current.scheduledAtMillis))
        val updated = current.copy(scheduledAtMillis = next ?: current.scheduledAtMillis, completed = next == null,
            pendingOccurrenceMillis = null, snoozedUntilMillis = null, acknowledgedAtMillis = now, updatedAtMillis = now)
        dao.upsertReminderAndOccurrence(updated.toEntity().copy(id = entity.id), ReminderOccurrenceEntity(
            reminderId = reminderId,
            occurrenceMillis = occurrence,
            action = if (current.pendingOccurrenceMillis != null) ReminderOccurrenceAction.COMPLETED.name else ReminderOccurrenceAction.SKIPPED.name,
            actionAtMillis = now,
        ))
        onChanged(reminderId, updated)
    }

    suspend fun skipNext(reminderId: String) = markComplete(reminderId)

    suspend fun setPaused(reminderId: String, paused: Boolean) = mutex.withLock {
        val entity = dao.getReminder(reminderId) ?: return@withLock
        val current = entity.toDomain()
        if (current.recurrence == null || current.completed || current.paused == paused) return@withLock
        val now = clock()
        val next = if (!paused) current.recurrence.nextAfter(now) else current.scheduledAtMillis
        val updated = current.copy(
            paused = paused,
            scheduledAtMillis = next ?: current.scheduledAtMillis,
            completed = !paused && next == null,
            pendingOccurrenceMillis = null,
            snoozedUntilMillis = null,
            updatedAtMillis = now,
        )
        dao.upsertReminder(updated.toEntity().copy(id = entity.id))
        onChanged(reminderId, updated)
    }

    suspend fun snooze(reminderId: String, occurrenceMillis: Long, durationMillis: Long) = mutex.withLock {
        require(durationMillis in 60_000L..86_400_000L) { "Invalid snooze duration" }
        val entity = dao.getReminder(reminderId) ?: return@withLock
        val current = entity.toDomain()
        if (current.completed || current.paused || current.pendingOccurrenceMillis != occurrenceMillis) return@withLock
        val updated = current.copy(snoozedUntilMillis = clock() + durationMillis, updatedAtMillis = clock())
        dao.upsertReminder(updated.toEntity().copy(id = entity.id))
        onChanged(reminderId, updated)
    }

    suspend fun fireSnoozed(reminderId: String, snoozedUntilMillis: Long, show: (Reminder) -> Unit) = mutex.withLock {
        val entity = dao.getReminder(reminderId) ?: return@withLock
        val current = entity.toDomain()
        if (current.completed || current.paused || current.snoozedUntilMillis != snoozedUntilMillis ||
            current.pendingOccurrenceMillis == null || snoozedUntilMillis > clock()) return@withLock
        val updated = current.copy(snoozedUntilMillis = null, updatedAtMillis = clock())
        dao.upsertReminder(updated.toEntity().copy(id = entity.id))
        onChanged(reminderId, updated)
        show(updated.copy(scheduledAtMillis = current.pendingOccurrenceMillis))
    }

    suspend fun getOccurrenceHistory(reminderId: String): List<ReminderOccurrence> =
        dao.getOccurrenceHistory(reminderId).map {
            ReminderOccurrence(it.occurrenceMillis, ReminderOccurrenceAction.valueOf(it.action), it.actionAtMillis)
        }

    /** Persist and arm the next occurrence before displaying this one; duplicate/stale broadcasts are ignored. */
    suspend fun fireDue(reminderId: String, show: (Reminder) -> Unit) = mutex.withLock {
        val entity = dao.getReminder(reminderId) ?: return@withLock
        val current = entity.toDomain()
        val now = clock()
        if (current.completed || current.paused || current.scheduledAtMillis > now || current.pendingOccurrenceMillis == current.scheduledAtMillis) return@withLock
        val next = current.recurrence?.nextAfter(now)
        val updated = current.copy(scheduledAtMillis = next ?: current.scheduledAtMillis,
            pendingOccurrenceMillis = current.scheduledAtMillis, snoozedUntilMillis = null, updatedAtMillis = now)
        dao.upsertReminder(updated.toEntity().copy(id = entity.id))
        onChanged(reminderId, updated)
        show(current)
    }

    suspend fun deleteReminder(reminderId: String) = mutex.withLock {
        dao.deleteReminderAndOccurrences(reminderId)
        onChanged(reminderId, null)
    }

    suspend fun getReminder(reminderId: String): Reminder? = dao.getReminder(reminderId)?.toDomain()

    suspend fun restoreAlarms(show: (Reminder) -> Unit) = mutex.withLock {
        dao.getPendingReminders().forEach { entity ->
            val reminder = entity.toDomain()
            onChanged(reminder.reminderId, reminder)
            if (reminder.snoozedUntilMillis == null) {
                reminder.pendingOccurrenceMillis?.let { show(reminder.copy(scheduledAtMillis = it)) }
            }
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
            require(reminder.snoozedUntilMillis == null || reminder.pendingOccurrenceMillis != null)
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
    createdAtMillis, updatedAtMillis, recurrenceRule?.let(ReminderRecurrence::decode), pendingOccurrenceMillis,
    paused, snoozedUntilMillis)

fun Reminder.toEntity() = ReminderEntity(reminderId = reminderId, title = title, scheduledAtMillis = scheduledAtMillis,
    completed = completed, acknowledgedAtMillis = acknowledgedAtMillis, createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis, recurrenceRule = recurrence?.encode(), pendingOccurrenceMillis = pendingOccurrenceMillis,
    paused = paused, snoozedUntilMillis = snoozedUntilMillis)
