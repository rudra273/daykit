package com.daykit.core.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

class WeekDaysTest {
    // 2026-10-01 is a Thursday.
    private val thursday = LocalDate.of(2026, 10, 1)

    @Test fun startOfWeekFollowsTheChosenFirstDay() {
        assertEquals(LocalDate.of(2026, 9, 28), WeekDays.startOf(thursday, DayOfWeek.MONDAY))
        assertEquals(LocalDate.of(2026, 9, 27), WeekDays.startOf(thursday, DayOfWeek.SUNDAY))
        assertEquals(thursday, WeekDays.startOf(thursday, DayOfWeek.THURSDAY))
    }

    @Test fun leadingBlanksCountCellsBeforeTheFirst() {
        assertEquals(3, WeekDays.leadingBlanks(thursday, DayOfWeek.MONDAY))
        assertEquals(4, WeekDays.leadingBlanks(thursday, DayOfWeek.SUNDAY))
        assertEquals(0, WeekDays.leadingBlanks(thursday, DayOfWeek.THURSDAY))
    }

    @Test fun labelsStartOnTheFirstDay() {
        val labels = WeekDays.narrowLabels(DayOfWeek.SUNDAY)
        assertEquals(7, labels.size)
        assertEquals(DayOfWeek.SUNDAY.getDisplayName(TextStyle.NARROW, Locale.getDefault()), labels.first())
    }

    @Test fun defaultsToMondayWithoutPreferences() {
        assertEquals(DayOfWeek.MONDAY, WeekDays.firstDay())
    }
}
