package com.daykit.core.util

import com.daykit.core.data.AppPreferences
import java.time.format.DateTimeFormatter

/** Clock formatting that honours the user's 12/24-hour choice (Settings → General). */
object TimeFormat {
    fun is24Hour(): Boolean = AppPreferences.is24Hour()

    /** "h:mm a" or "HH:mm", for composing with date patterns. */
    fun pattern(use24Hour: Boolean = is24Hour()): String = if (use24Hour) "HH:mm" else "h:mm a"

    fun formatter(use24Hour: Boolean = is24Hour()): DateTimeFormatter =
        DateTimeFormatter.ofPattern(pattern(use24Hour))

    /** "9:05 AM" / "09:05". */
    fun format(hour: Int, minute: Int, use24Hour: Boolean = is24Hour()): String {
        if (use24Hour) return "%02d:%02d".format(hour, minute)
        val period = if (hour < 12) "AM" else "PM"
        val display = when (val h = hour % 12) {
            0 -> 12
            else -> h
        }
        return "%d:%02d %s".format(display, minute, period)
    }
}
