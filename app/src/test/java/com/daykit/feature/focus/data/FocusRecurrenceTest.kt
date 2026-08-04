package com.daykit.feature.focus.data

import java.time.DayOfWeek
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusRecurrenceTest {

    // 2026-08-05 is a Wednesday.
    private val wedNoon = LocalDateTime.of(2026, 8, 5, 12, 0)

    @Test
    fun mondayIsBitZero() {
        assertEquals(0b000_0001, FocusRecurrence.maskOf(setOf(DayOfWeek.MONDAY)))
        assertEquals(0b100_0000, FocusRecurrence.maskOf(setOf(DayOfWeek.SUNDAY)))
    }

    @Test
    fun maskRoundTripsThroughDaySet() {
        val days = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY, DayOfWeek.SUNDAY)
        assertEquals(days, FocusRecurrence.daysOf(FocusRecurrence.maskOf(days)))

        val all = DayOfWeek.entries.toSet()
        assertEquals(FocusRecurrence.EVERY_DAY_MASK, FocusRecurrence.maskOf(all))
        assertEquals(all, FocusRecurrence.daysOf(FocusRecurrence.EVERY_DAY_MASK))
    }

    @Test
    fun nextStartPicksLaterToday() {
        // Wednesday 12:00 now, schedule runs Wednesdays at 14:00 → today.
        val next = FocusRecurrence.nextStart(
            now = wedNoon,
            daysMask = FocusRecurrence.maskOf(setOf(DayOfWeek.WEDNESDAY)),
            startHour = 14,
            startMinute = 0,
        )
        assertEquals(LocalDateTime.of(2026, 8, 5, 14, 0), next)
    }

    @Test
    fun nextStartSkipsTodayOncePassedAndWrapsAWeek() {
        // Wednesday 12:00 now, Wednesdays at 09:00 already passed → next Wednesday.
        val next = FocusRecurrence.nextStart(
            now = wedNoon,
            daysMask = FocusRecurrence.maskOf(setOf(DayOfWeek.WEDNESDAY)),
            startHour = 9,
            startMinute = 0,
        )
        assertEquals(LocalDateTime.of(2026, 8, 12, 9, 0), next)
    }

    @Test
    fun nextStartFindsLaterThisWeek() {
        // Wednesday now, Mon-Fri at 09:00 → Thursday (Wednesday's 09:00 is gone).
        val weekdays = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )
        val next = FocusRecurrence.nextStart(
            now = wedNoon,
            daysMask = FocusRecurrence.maskOf(weekdays),
            startHour = 9,
            startMinute = 0,
        )
        assertEquals(LocalDateTime.of(2026, 8, 6, 9, 0), next)
    }

    @Test
    fun nextStartCrossesIntoNextMonth() {
        // Guards against naive day arithmetic at a month boundary.
        val sat = LocalDateTime.of(2026, 8, 29, 23, 0) // Saturday
        val next = FocusRecurrence.nextStart(
            now = sat,
            daysMask = FocusRecurrence.maskOf(setOf(DayOfWeek.MONDAY)),
            startHour = 8,
            startMinute = 30,
        )
        assertEquals(LocalDateTime.of(2026, 8, 31, 8, 30), next)
    }

    @Test
    fun emptyMaskNeverFires() {
        assertNull(FocusRecurrence.nextStart(wedNoon, 0, 9, 0))
    }

    @Test
    fun nextStartIsAlwaysStrictlyInTheFuture() {
        // Exactly-now must roll forward, or an alarm would be armed for the past.
        val next = FocusRecurrence.nextStart(
            now = wedNoon,
            daysMask = FocusRecurrence.EVERY_DAY_MASK,
            startHour = 12,
            startMinute = 0,
        )
        assertTrue(next!!.isAfter(wedNoon))
        assertEquals(LocalDateTime.of(2026, 8, 6, 12, 0), next)
    }

    @Test
    fun endOnSameDayWhenEndIsLater() {
        val start = LocalDateTime.of(2026, 8, 5, 9, 0)
        assertEquals(
            LocalDateTime.of(2026, 8, 5, 11, 0),
            FocusRecurrence.endFor(start, 11, 0),
        )
    }

    @Test
    fun endRollsToNextDayWhenCrossingMidnight() {
        // 22:00 -> 06:00 is the canonical "wind down" schedule.
        val start = LocalDateTime.of(2026, 8, 5, 22, 0)
        assertEquals(
            LocalDateTime.of(2026, 8, 6, 6, 0),
            FocusRecurrence.endFor(start, 6, 0),
        )
    }

    @Test
    fun equalStartAndEndIsAFullDayNotZeroLength() {
        // A zero-length session would be silently unenforceable.
        val start = LocalDateTime.of(2026, 8, 5, 9, 0)
        assertEquals(
            LocalDateTime.of(2026, 8, 6, 9, 0),
            FocusRecurrence.endFor(start, 9, 0),
        )
    }

    @Test
    fun describeUsesFriendlyNamesForCommonMasks() {
        val weekdays = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )
        assertEquals("Daily", FocusRecurrence.describe(FocusRecurrence.EVERY_DAY_MASK))
        assertEquals("Mon–Fri", FocusRecurrence.describe(FocusRecurrence.maskOf(weekdays)))
        assertEquals(
            "Weekends",
            FocusRecurrence.describe(
                FocusRecurrence.maskOf(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)),
            ),
        )
        assertEquals("Never", FocusRecurrence.describe(0))
        assertEquals(
            "Mon, Thu",
            FocusRecurrence.describe(
                FocusRecurrence.maskOf(setOf(DayOfWeek.THURSDAY, DayOfWeek.MONDAY)),
            ),
        )
    }

    @Test
    fun formatTimeHandlesNoonAndMidnight() {
        assertEquals("12:00 AM", FocusRecurrence.formatTime(0, 0))
        assertEquals("12:30 PM", FocusRecurrence.formatTime(12, 30))
        assertEquals("9:05 AM", FocusRecurrence.formatTime(9, 5))
        assertEquals("11:59 PM", FocusRecurrence.formatTime(23, 59))
    }
}
