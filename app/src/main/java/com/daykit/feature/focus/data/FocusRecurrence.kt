package com.daykit.feature.focus.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Weekday recurrence math for focus schedules.
 *
 * Days are stored as a 7-bit mask so a schedule is one row rather than one row
 * per weekday. **Bit 0 is Monday** (`DayOfWeek.value - 1`), matching the
 * Monday-first convention the rest of the app displays weeks in.
 *
 * All functions here are pure and take an explicit "now", so they are unit
 * testable without a device clock.
 */
object FocusRecurrence {

    const val EVERY_DAY_MASK = 0b111_1111

    fun maskOf(days: Set<DayOfWeek>): Int =
        days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

    fun daysOf(mask: Int): Set<DayOfWeek> =
        DayOfWeek.entries.filter { mask and (1 shl (it.value - 1)) != 0 }.toSet()

    fun includes(mask: Int, day: DayOfWeek): Boolean =
        mask and (1 shl (day.value - 1)) != 0

    /**
     * The next date-time at which a schedule with [daysMask] and start time
     * [startHour]:[startMinute] begins, strictly after [now]. Returns null when
     * the mask selects no days (a schedule that can never fire).
     *
     * Scans up to 8 days rather than 7: if today matches but its start time has
     * already passed, the same weekday one week out is the answer, and that is
     * day 7 from here.
     */
    fun nextStart(
        now: LocalDateTime,
        daysMask: Int,
        startHour: Int,
        startMinute: Int,
    ): LocalDateTime? {
        if (daysMask and EVERY_DAY_MASK == 0) return null
        val start = LocalTime.of(startHour, startMinute)
        for (offset in 0..7) {
            val date = now.toLocalDate().plusDays(offset.toLong())
            if (!includes(daysMask, date.dayOfWeek)) continue
            val candidate = LocalDateTime.of(date, start)
            if (candidate.isAfter(now)) return candidate
        }
        return null
    }

    /**
     * When a session that started at [start] ends, given its end time.
     *
     * An end time at or before the start time means the session crosses
     * midnight (e.g. 22:00–06:00), so the end lands on the following day. Equal
     * times are treated as a full 24 hours rather than a zero-length session,
     * which would otherwise be silently unenforceable.
     */
    fun endFor(start: LocalDateTime, endHour: Int, endMinute: Int): LocalDateTime {
        val end = LocalTime.of(endHour, endMinute)
        val sameDay = LocalDateTime.of(start.toLocalDate(), end)
        return if (end > start.toLocalTime()) sameDay else sameDay.plusDays(1)
    }

    /** Formats a mask the way the schedule list shows it: "Mon–Fri", "Daily", "Mon, Thu". */
    fun describe(mask: Int): String {
        val days = daysOf(mask)
        if (days.isEmpty()) return "Never"
        if (days.size == 7) return "Daily"
        val weekdays = setOf(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
        )
        if (days == weekdays) return "Mon–Fri"
        if (days == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) return "Weekends"
        // Sorted by DayOfWeek.value, so output is Monday-first like the pickers.
        return days.sortedBy { it.value }.joinToString(", ") { shortLabel(it) }
    }

    /** Single-day label used by the weekday picker chips and [describe]. */
    fun shortLabel(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "Mon"
        DayOfWeek.TUESDAY -> "Tue"
        DayOfWeek.WEDNESDAY -> "Wed"
        DayOfWeek.THURSDAY -> "Thu"
        DayOfWeek.FRIDAY -> "Fri"
        DayOfWeek.SATURDAY -> "Sat"
        DayOfWeek.SUNDAY -> "Sun"
    }

    /** Formats an hour+minute pair as "9:00 AM", matching the reminder screen. */
    fun formatTime(hour: Int, minute: Int): String {
        val period = if (hour < 12) "AM" else "PM"
        val display = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        return "%d:%02d %s".format(display, minute, period)
    }

    fun toEpochMillis(dateTime: LocalDateTime, zone: ZoneId = ZoneId.systemDefault()): Long =
        dateTime.atZone(zone).toInstant().toEpochMilli()

    fun today(zone: ZoneId = ZoneId.systemDefault()): LocalDate = LocalDate.now(zone)
}
