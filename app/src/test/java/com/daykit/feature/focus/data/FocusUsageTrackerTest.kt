package com.daykit.feature.focus.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FocusUsageTrackerTest {

    @Test
    fun `start of day is 00 00 00`() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 22, 14, 30, 45)
            set(Calendar.MILLISECOND, 500)
        }
        val now = calendar.timeInMillis
        val startOfDay = FocusUsageTracker.getStartOfDayMillis(now)

        val startCal = Calendar.getInstance().apply { timeInMillis = startOfDay }
        assertEquals(2026, startCal.get(Calendar.YEAR))
        assertEquals(Calendar.SEPTEMBER, startCal.get(Calendar.MONTH))
        assertEquals(22, startCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, startCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, startCal.get(Calendar.MINUTE))
        assertEquals(0, startCal.get(Calendar.SECOND))
        assertEquals(0, startCal.get(Calendar.MILLISECOND))
    }

    @Test
    fun `end of day is next day 00 00 00`() {
        val calendar = Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 22, 23, 59, 59)
        }
        val now = calendar.timeInMillis
        val endOfDay = FocusUsageTracker.getEndOfDayMillis(now)

        val endCal = Calendar.getInstance().apply { timeInMillis = endOfDay }
        assertEquals(2026, endCal.get(Calendar.YEAR))
        assertEquals(Calendar.SEPTEMBER, endCal.get(Calendar.MONTH))
        assertEquals(23, endCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(0, endCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, endCal.get(Calendar.MINUTE))
        assertEquals(0, endCal.get(Calendar.SECOND))
        assertEquals(0, endCal.get(Calendar.MILLISECOND))

        val startOfDay = FocusUsageTracker.getStartOfDayMillis(now)
        assertTrue(endOfDay > startOfDay)
        assertTrue(now in startOfDay until endOfDay)
    }

    @Test
    fun `formatUsage produces friendly strings`() {
        assertEquals("0m", FocusUsageTracker.formatUsage(0L))
        assertEquals("0m", FocusUsageTracker.formatUsage(-100L))
        assertEquals("< 1m", FocusUsageTracker.formatUsage(30_000L))
        assertEquals("15m", FocusUsageTracker.formatUsage(15 * 60_000L))
        assertEquals("1h", FocusUsageTracker.formatUsage(60 * 60_000L))
        assertEquals("1h 30m", FocusUsageTracker.formatUsage(90 * 60_000L))
        assertEquals("2h 15m", FocusUsageTracker.formatUsage(135 * 60_000L))
    }
}
