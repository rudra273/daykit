package com.daykit.core.util

import com.daykit.core.data.AppPreferences
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Week math that honours the user's first-day-of-week choice (Settings → General). */
object WeekDays {
    fun firstDay(): DayOfWeek = AppPreferences.firstDayOfWeek()

    fun startOf(date: LocalDate, firstDay: DayOfWeek = firstDay()): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(firstDay))

    /** Blank cells before the 1st in a calendar grid that starts on [firstDay]. */
    fun leadingBlanks(firstOfMonth: LocalDate, firstDay: DayOfWeek = firstDay()): Int =
        (firstOfMonth.dayOfWeek.value - firstDay.value + 7) % 7

    /** Single-letter column headers in grid order, e.g. M T W T F S S. */
    fun narrowLabels(firstDay: DayOfWeek = firstDay()): List<String> =
        (0L..6L).map { firstDay.plus(it).getDisplayName(TextStyle.NARROW, Locale.getDefault()) }
}
