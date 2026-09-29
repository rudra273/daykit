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

    private fun resumed(pkg: String, at: Long, cls: String = "Main") =
        ForegroundTransition(pkg, cls, at, ForegroundTransition.Kind.Resumed)

    private fun paused(pkg: String, at: Long, cls: String = "Main") =
        ForegroundTransition(pkg, cls, at, ForegroundTransition.Kind.Paused)

    @Test
    fun `sums closed sessions per package`() {
        val totals = FocusUsageTracker.foregroundMillisByPackage(
            listOf(
                resumed("a", 100), paused("a", 400),
                resumed("b", 400), paused("b", 500),
                resumed("a", 600), paused("a", 700),
            ),
            startMillis = 0,
            endMillis = 1_000,
        )
        assertEquals(400L, totals["a"])
        assertEquals(100L, totals["b"])
    }

    @Test
    fun `app open before midnight counts only from window start`() {
        // Only the pause survives into today's window.
        val totals = FocusUsageTracker.foregroundMillisByPackage(
            listOf(paused("a", 1_300)),
            startMillis = 1_000,
            endMillis = 2_000,
        )
        assertEquals(300L, totals["a"])
    }

    @Test
    fun `open session is closed at end of window`() {
        val totals = FocusUsageTracker.foregroundMillisByPackage(
            listOf(resumed("a", 1_500)),
            startMillis = 1_000,
            endMillis = 2_000,
        )
        assertEquals(500L, totals["a"])
    }

    @Test
    fun `switching activities inside one app is one session`() {
        // Some apps resume the next activity before pausing the previous one.
        val totals = FocusUsageTracker.foregroundMillisByPackage(
            listOf(
                resumed("a", 100, "First"),
                resumed("a", 200, "Second"),
                paused("a", 210, "First"),
                paused("a", 500, "Second"),
            ),
            startMillis = 0,
            endMillis = 1_000,
        )
        assertEquals(400L, totals["a"])
    }

    @Test
    fun `stray pause after a completed session is not recounted from window start`() {
        val totals = FocusUsageTracker.foregroundMillisByPackage(
            listOf(resumed("a", 100), paused("a", 200), paused("a", 900, "Other")),
            startMillis = 0,
            endMillis = 1_000,
        )
        assertEquals(100L, totals["a"])
    }

    @Test
    fun `shutdown closes every open session`() {
        val totals = FocusUsageTracker.foregroundMillisByPackage(
            listOf(
                resumed("a", 100),
                ForegroundTransition("android", null, 300, ForegroundTransition.Kind.Shutdown),
            ),
            startMillis = 0,
            endMillis = 1_000,
        )
        assertEquals(200L, totals["a"])
    }
}
