package com.daykit.feature.reminder.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ReminderRepositoryTest {
    private class FakeDao : ReminderDao {
        val rows = linkedMapOf<String, ReminderEntity>()
        override fun observeReminders(): Flow<List<ReminderEntity>> = MutableStateFlow(rows.values.toList())
        override suspend fun getReminder(reminderId: String) = rows[reminderId]
        override suspend fun getAllReminders() = rows.values.toList()
        override suspend fun upsertReminders(entities: List<ReminderEntity>) { entities.forEach { upsertReminder(it) } }
        override suspend fun getPendingReminders() = rows.values.filter { !it.completed && !it.paused }
        override suspend fun upsertReminder(entity: ReminderEntity) { rows[entity.reminderId] = entity }
        override suspend fun deleteReminder(reminderId: String) { rows.remove(reminderId) }
        val history = mutableListOf<ReminderOccurrenceEntity>()
        override suspend fun getOccurrenceHistory(reminderId: String) = history.filter { it.reminderId == reminderId }
        override suspend fun upsertOccurrence(entity: ReminderOccurrenceEntity) {
            history.removeAll { it.reminderId == entity.reminderId && it.occurrenceMillis == entity.occurrenceMillis }
            history += entity
        }
        override suspend fun deleteOccurrences(reminderId: String) { history.removeAll { it.reminderId == reminderId } }
    }
    private val day = 86_400_000L

    @Test fun backupRoundTripPreservesRecurrenceAndIsIdempotent() = runBlocking {
        val original = ReminderRepository(FakeDao(), clock = { 1L })
        val r = original.addReminder("Daily", day, ReminderRecurrence(ReminderFrequency.DAILY, untilEpochDay = 20, zoneId = "UTC", anchorMillis = day))
        val payload = ReminderBackupContributor(original).exportJson()
        val restored = ReminderRepository(FakeDao(), clock = { 1L })
        var rearmed = 0
        val contributor = ReminderBackupContributor(restored) { rearmed++ }
        contributor.importJson(payload)
        assertEquals(r, restored.getReminder(r.reminderId))
        restored.markComplete(r.reminderId)
        val changed = restored.getReminder(r.reminderId)
        contributor.importJson(payload)
        assertEquals(changed, restored.getReminder(r.reminderId))
        assertEquals(1, restored.exportForBackup().size)
        assertEquals(2, rearmed)
    }

    @Test fun invalidBackupIsRejectedBeforeAnyReminderIsWritten() = runBlocking {
        val source = ReminderRepository(FakeDao(), clock = { 1L })
        val valid = source.addReminder("Valid", day)
        val target = ReminderRepository(FakeDao(), clock = { 1L })
        val result = runCatching { target.importFromBackup(listOf(valid, valid.copy(reminderId = "bad-id"))) }
        assertTrue(result.isFailure)
        assertTrue(target.exportForBackup().isEmpty())
    }

    @Test fun firingArmsNextEvenWithoutAcknowledgementAndIgnoresDuplicates() = runBlocking {
        val dao = FakeDao()
        var now = 1L
        var scheduled: Reminder? = null
        val repo = ReminderRepository(dao, { _, r -> scheduled = r }, { now })
        val r = repo.addReminder("Daily", day, ReminderRecurrence(ReminderFrequency.DAILY, zoneId = "UTC", anchorMillis = day))
        now = day
        var fires = 0
        repo.fireDue(r.reminderId) { fires++ }
        assertEquals(2 * day, scheduled!!.scheduledAtMillis)
        repo.fireDue(r.reminderId) { fires++ }
        assertEquals(1, fires)
        now = 2 * day
        repo.fireDue(r.reminderId) { fires++ }
        assertEquals(2, fires)
        assertEquals(3 * day, scheduled!!.scheduledAtMillis)
        repo.markComplete(r.reminderId, day) // stale first-day notification
        assertEquals(2 * day, repo.getReminder(r.reminderId)!!.pendingOccurrenceMillis)
        repo.markComplete(r.reminderId, 2 * day)
        assertFalse(repo.getReminder(r.reminderId)!!.completed)
        assertNull(repo.getReminder(r.reminderId)!!.pendingOccurrenceMillis)
        assertEquals(3 * day, scheduled!!.scheduledAtMillis)
        val history = repo.getOccurrenceHistory(r.reminderId)
        assertEquals(ReminderOccurrenceAction.COMPLETED, history.single().action)
    }

    @Test fun finalOccurrenceCompletesAndOneTimeReminderStillWorks() = runBlocking {
        val dao = FakeDao()
        var now = 1L
        val repo = ReminderRepository(dao, clock = { now })
        val r = repo.addReminder("Last", day, ReminderRecurrence(ReminderFrequency.DAILY, untilEpochDay = 1, zoneId = "UTC", anchorMillis = day))
        now = day
        repo.fireDue(r.reminderId) {}
        repo.markComplete(r.reminderId, day)
        assertTrue(repo.getReminder(r.reminderId)!!.completed)
        val once = repo.addReminder("Once", 2 * day)
        now = 2 * day
        repo.fireDue(once.reminderId) {}
        repo.markComplete(once.reminderId, 2 * day)
        assertTrue(repo.getReminder(once.reminderId)!!.completed)
    }

    @Test fun rebootRestoresOverdueAndOutstandingNotifications() = runBlocking {
        val dao = FakeDao()
        var now = 1L
        val armed = mutableListOf<Reminder>()
        val repo = ReminderRepository(dao, { _, r -> r?.let(armed::add); Unit }, { now })
        val r = repo.addReminder("Missed", day)
        now = 2 * day
        armed.clear()
        repo.restoreAlarms {}
        assertEquals(listOf(r.reminderId), armed.map { it.reminderId })
        repo.fireDue(r.reminderId) {}
        val shown = mutableListOf<Reminder>()
        repo.restoreAlarms { shown += it }
        assertEquals(day, shown.single().scheduledAtMillis)
    }

    @Test fun editedAndDeletedRemindersIgnoreOldActions() = runBlocking {
        val dao = FakeDao()
        var now = 1L
        val repo = ReminderRepository(dao, clock = { now })
        val r = repo.addReminder("Original", day)
        now = day
        repo.fireDue(r.reminderId) {}
        repo.updateReminder(r.reminderId, "Edited", 2 * day)
        repo.markComplete(r.reminderId, day)
        assertFalse(repo.getReminder(r.reminderId)!!.completed)
        repo.fireDue(r.reminderId) { fail("Edited reminder fired early") }
        repo.deleteReminder(r.reminderId)
        repo.fireDue(r.reminderId) { fail("Deleted reminder fired") }
    }

    @Test fun snoozeKeepsNextRecurrenceAndRejectsStaleOccurrence() = runBlocking {
        val dao = FakeDao()
        var now = day
        val repo = ReminderRepository(dao, clock = { now })
        val reminder = repo.addReminder("Daily", 2 * day,
            ReminderRecurrence(ReminderFrequency.DAILY, zoneId = "UTC", anchorMillis = 2 * day))
        now = 2 * day
        repo.fireDue(reminder.reminderId) {}
        repo.snooze(reminder.reminderId, 2 * day, 10 * 60_000L)
        val snoozed = repo.getReminder(reminder.reminderId)!!
        assertEquals(3 * day, snoozed.scheduledAtMillis)
        assertEquals(now + 10 * 60_000L, snoozed.snoozedUntilMillis)
        repo.snooze(reminder.reminderId, day, 5 * 60_000L)
        assertEquals(snoozed, repo.getReminder(reminder.reminderId))
    }

    @Test fun pauseAndResumeMovesSeriesToNextFutureOccurrence() = runBlocking {
        val dao = FakeDao()
        var now = 1L
        val repo = ReminderRepository(dao, clock = { now })
        val reminder = repo.addReminder("Daily", day,
            ReminderRecurrence(ReminderFrequency.DAILY, zoneId = "UTC", anchorMillis = day))
        repo.setPaused(reminder.reminderId, true)
        assertTrue(repo.getReminder(reminder.reminderId)!!.paused)
        now = 3 * day + 1
        repo.setPaused(reminder.reminderId, false)
        val resumed = repo.getReminder(reminder.reminderId)!!
        assertFalse(resumed.paused)
        assertEquals(4 * day, resumed.scheduledAtMillis)
    }
}
