package com.daykit.feature.reminder.data

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZonedDateTime

class ReminderRecurrenceTest {
    private fun millis(value: String) = ZonedDateTime.parse(value).toInstant().toEpochMilli()
    private fun rule(frequency: ReminderFrequency, start: String, interval: Int = 1, days: Int = 0, until: String? = null) =
        ReminderRecurrence(frequency, interval, days, until?.let { LocalDate.parse(it).toEpochDay() },
            ZonedDateTime.parse(start).zone.id, millis(start))

    @Test fun dailyPreservesWallTimeAcrossSpringDst() {
        val r = rule(ReminderFrequency.DAILY, "2026-03-07T09:00:00-05:00[America/New_York]")
        assertEquals(millis("2026-03-08T09:00:00-04:00[America/New_York]"), r.nextAfter(r.anchorMillis))
    }

    @Test fun customMinuteIntervalUsesExactElapsedTime() {
        val r = rule(ReminderFrequency.MINUTELY, "2026-09-14T09:00:00Z", interval = 15)
        assertEquals(millis("2026-09-14T09:15:00Z"), r.nextAfter(r.anchorMillis))
        assertEquals(millis("2026-09-14T10:15:00Z"), r.nextAfter(millis("2026-09-14T10:07:00Z")))
    }

    @Test fun customHourIntervalSkipsMissedOccurrences() {
        val r = rule(ReminderFrequency.HOURLY, "2026-09-14T09:30:00Z", interval = 2)
        assertEquals(millis("2026-09-14T11:30:00Z"), r.nextAfter(r.anchorMillis))
        assertEquals(millis("2026-09-14T17:30:00Z"), r.nextAfter(millis("2026-09-14T16:00:00Z")))
    }

    @Test fun subDayEndDateIsInclusive() {
        val r = rule(ReminderFrequency.HOURLY, "2026-09-14T23:00:00Z", until = "2026-09-14")
        assertNull(r.nextAfter(r.anchorMillis))
    }

    @Test fun nonexistentTimeDoesNotPermanentlyShiftFollowingDays() {
        val r = rule(ReminderFrequency.DAILY, "2026-03-07T02:30:00-05:00[America/New_York]")
        val gap = r.nextAfter(r.anchorMillis)!!
        assertEquals(millis("2026-03-08T03:30:00-04:00[America/New_York]"), gap)
        assertEquals(millis("2026-03-09T02:30:00-04:00[America/New_York]"), r.nextAfter(gap))
    }

    @Test fun fallOverlapFiresOnlyOnce() {
        val r = rule(ReminderFrequency.DAILY, "2026-10-31T01:30:00-04:00[America/New_York]")
        val first = r.nextAfter(r.anchorMillis)!!
        assertEquals(millis("2026-11-02T01:30:00-05:00[America/New_York]"), r.nextAfter(first))
    }

    @Test fun alternateWeeksWithMultipleDays() {
        val r = rule(ReminderFrequency.WEEKLY, "2026-09-14T09:00:00Z", interval = 2, days = 5)
        val wed = r.nextAfter(r.anchorMillis)!!
        assertEquals(millis("2026-09-16T09:00:00Z"), wed)
        assertEquals(millis("2026-09-28T09:00:00Z"), r.nextAfter(wed))
    }

    @Test fun endDateIsInclusive() {
        val r = rule(ReminderFrequency.DAILY, "2026-09-14T09:00:00Z", until = "2026-09-15")
        val last = r.nextAfter(r.anchorMillis)!!
        assertEquals(millis("2026-09-15T09:00:00Z"), last)
        assertNull(r.nextAfter(last))
    }

    @Test fun monthEndReturnsToOriginalDay() {
        val r = rule(ReminderFrequency.MONTHLY, "2026-01-31T09:00:00Z")
        val feb = r.nextAfter(r.anchorMillis)!!
        assertEquals(millis("2026-02-28T09:00:00Z"), feb)
        assertEquals(millis("2026-03-31T09:00:00Z"), r.nextAfter(feb))
    }

    @Test fun leapYearRecurrenceUsesFebruaryEnd() {
        val r = rule(ReminderFrequency.YEARLY, "2024-02-29T09:00:00Z")
        assertEquals(millis("2025-02-28T09:00:00Z"), r.nextAfter(r.anchorMillis))
        assertEquals(millis("2028-02-29T09:00:00Z"), r.nextAfter(millis("2027-02-28T09:00:00Z")))
    }

    @Test fun skipsMissedOccurrencesAndRoundTripsStorage() {
        val r = rule(ReminderFrequency.DAILY, "2020-01-01T09:00:00Z", interval = 3)
        assertEquals(r, ReminderRecurrence.decode(r.encode()))
        val now = millis("2026-09-15T12:00:00Z")
        assertTrue(r.nextAfter(now)!! > now)
    }

    @Test(expected = IllegalArgumentException::class) fun rejectsEmptyWeekdays() {
        rule(ReminderFrequency.WEEKLY, "2026-09-14T09:00:00Z")
    }
}
