package com.daykit.feature.reminder.notification

import com.daykit.feature.reminder.data.Reminder
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TodayReminderPreviewTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = time("2026-10-04T04:30:00Z") // 10 AM local.

    private fun time(value: String) = Instant.parse(value).toEpochMilli()
    private fun reminder(id: String, at: String) = Reminder(id, id, time(at), false, null, 0, 0)

    @Test fun showsOnlyTheEarliestUpcomingReminderToday() {
        val later = reminder("later", "2026-10-04T06:30:00Z")
        val next = reminder("next", "2026-10-04T05:30:00Z")
        assertEquals("next", nextReminderToday(listOf(later, next), now, zone)?.reminderId)
    }

    @Test fun hidesTomorrowEvenWhenItIsTheSameUtcDate() {
        val tomorrow = reminder("tomorrow", "2026-10-04T18:40:00Z") // 12:10 AM local tomorrow.
        assertNull(nextReminderToday(listOf(tomorrow), now, zone))
        assertEquals("tomorrow", nextReminderToday(listOf(tomorrow), time("2026-10-04T18:31:00Z"), zone)?.reminderId)
    }

    @Test fun excludesCompletedPausedAndPastReminders() {
        val done = reminder("done", "2026-10-04T05:00:00Z").copy(completed = true)
        val paused = reminder("paused", "2026-10-04T05:10:00Z").copy(paused = true)
        val past = reminder("past", "2026-10-04T04:00:00Z")
        assertNull(nextReminderToday(listOf(done, paused, past), now, zone))
        assertNull(nextReminderToday(emptyList(), now, zone))
    }

    @Test fun snoozeReplacesTheOriginalOccurrenceTime() {
        val original = time("2026-10-04T05:00:00Z")
        val snoozed = reminder("snoozed", "2026-10-04T05:00:00Z").copy(
            pendingOccurrenceMillis = original,
            snoozedUntilMillis = time("2026-10-04T06:00:00Z"),
        )
        assertEquals(snoozed.snoozedUntilMillis, nextReminderToday(listOf(snoozed), now, zone)?.dueAtMillis)
        val sooner = reminder("sooner", "2026-10-04T05:30:00Z")
        assertEquals("sooner", nextReminderToday(listOf(snoozed, sooner), now, zone)?.reminderId)
        assertNull(nextReminderToday(listOf(snoozed.copy(snoozedUntilMillis = time("2026-10-04T18:40:00Z"))), now, zone))
    }

    @Test fun advanceAlertDoesNotHideTodaysPendingOccurrence() {
        val occurrence = time("2026-10-04T04:35:00Z")
        val recurring = reminder("series", "2026-10-05T04:35:00Z").copy(pendingOccurrenceMillis = occurrence)
        assertEquals(occurrence, nextReminderToday(listOf(recurring), now, zone)?.dueAtMillis)
    }

    @Test fun oldPendingOccurrenceDoesNotHideTodaysNextOccurrence() {
        val recurring = reminder("series", "2026-10-04T05:00:00Z").copy(
            pendingOccurrenceMillis = time("2026-10-03T05:00:00Z"),
        )
        assertEquals(recurring.scheduledAtMillis, nextReminderToday(listOf(recurring), now, zone)?.dueAtMillis)
    }
}
