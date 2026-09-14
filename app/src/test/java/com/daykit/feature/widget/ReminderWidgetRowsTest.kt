package com.daykit.feature.widget

import com.daykit.feature.reminder.data.Reminder
import org.junit.Assert.*
import org.junit.Test

class ReminderWidgetRowsTest {
    private fun reminder(id: String, time: Long) = Reminder(id, id, time, false, null, 0, 0)

    @Test fun excludesCompletedAndPausedAndSortsByVisibleOccurrence() {
        val upcoming = reminder("upcoming", 200)
        val pending = reminder("pending", 300).copy(pendingOccurrenceMillis = 100)
        val snoozed = reminder("snoozed", 400).copy(pendingOccurrenceMillis = 50, snoozedUntilMillis = 250)
        val rows = reminderWidgetRows(listOf(upcoming, snoozed, pending,
            reminder("done", 1).copy(completed = true), reminder("paused", 2).copy(paused = true)))
        assertEquals(listOf("pending", "upcoming", "snoozed"), rows.map { it.reminderId })
        assertEquals(50L, snoozed.widgetOccurrence)
        assertEquals(250L, snoozed.widgetDisplayTime)
    }

    @Test fun emptyListIsSupported() { assertTrue(reminderWidgetRows(emptyList()).isEmpty()) }
}
