package com.daykit.feature.reminder.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

enum class ReminderFrequency { MINUTELY, HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY }

/** Calendar recurrence anchored to the original local time, including across DST changes. */
data class ReminderRecurrence(
    val frequency: ReminderFrequency,
    val interval: Int = 1,
    val weekdays: Int = 0,
    val untilEpochDay: Long? = null,
    val zoneId: String = ZoneId.systemDefault().id,
    val anchorMillis: Long,
) {
    init {
        require(interval in 1..999) { "Repeat interval must be between 1 and 999" }
        require(weekdays in 0..127)
        require(frequency != ReminderFrequency.WEEKLY || weekdays != 0) { "Choose at least one weekday" }
        val anchor = Instant.ofEpochMilli(anchorMillis).atZone(ZoneId.of(zoneId)).toLocalDate()
        require(untilEpochDay == null || untilEpochDay >= anchor.toEpochDay()) { "End date must be on or after the start date" }
    }

    /** First occurrence strictly after [afterMillis]. End date is inclusive in the saved timezone. */
    fun nextAfter(afterMillis: Long): Long? {
        val zone = ZoneId.of(zoneId)
        val fixedIntervalMillis = when (frequency) {
            ReminderFrequency.MINUTELY -> interval * 60_000L
            ReminderFrequency.HOURLY -> interval * 3_600_000L
            else -> null
        }
        if (fixedIntervalMillis != null) {
            val steps = if (afterMillis < anchorMillis) 0L else (afterMillis - anchorMillis) / fixedIntervalMillis + 1L
            val candidate = runCatching { Math.addExact(anchorMillis, Math.multiplyExact(steps, fixedIntervalMillis)) }
                .getOrNull() ?: return null
            if (untilEpochDay != null && Instant.ofEpochMilli(candidate).atZone(zone).toLocalDate().toEpochDay() > untilEpochDay) {
                return null
            }
            return candidate
        }
        val anchor = Instant.ofEpochMilli(anchorMillis).atZone(zone)
        val start = anchor.toLocalDate()
        var date = maxOf(start, Instant.ofEpochMilli(afterMillis).atZone(zone).toLocalDate())
        // At most one interval plus a leap-year cycle is needed for these supported rules.
        val limit = date.plusYears(interval.toLong() + 8)
        while (!date.isAfter(limit)) {
            if (untilEpochDay != null && date.toEpochDay() > untilEpochDay) return null
            val matches = when (frequency) {
                ReminderFrequency.MINUTELY, ReminderFrequency.HOURLY -> error("Handled above")
                ReminderFrequency.DAILY -> ChronoUnit.DAYS.between(start, date) % interval == 0L
                ReminderFrequency.WEEKLY -> {
                    val startWeek = start.minusDays((start.dayOfWeek.value - 1).toLong())
                    val week = date.minusDays((date.dayOfWeek.value - 1).toLong())
                    ChronoUnit.WEEKS.between(startWeek, week) % interval == 0L &&
                        weekdays and (1 shl (date.dayOfWeek.value - 1)) != 0
                }
                ReminderFrequency.MONTHLY -> ChronoUnit.MONTHS.between(start.withDayOfMonth(1), date.withDayOfMonth(1)) % interval == 0L &&
                    date.dayOfMonth == minOf(start.dayOfMonth, date.lengthOfMonth())
                ReminderFrequency.YEARLY -> (date.year - start.year) % interval == 0 && date.month == start.month &&
                    date.dayOfMonth == minOf(start.dayOfMonth, date.lengthOfMonth())
            }
            if (matches) {
                val candidate = date.atTime(anchor.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
                if (candidate >= anchorMillis && candidate > afterMillis) return candidate
            }
            date = date.plusDays(1)
        }
        return null
    }

    fun encode(): String = listOf(frequency.name, interval, weekdays, untilEpochDay ?: "", zoneId, anchorMillis).joinToString("|")

    fun describe(): String {
        val unit = when (frequency) {
            ReminderFrequency.MINUTELY -> "minute"
            ReminderFrequency.HOURLY -> "hour"
            ReminderFrequency.DAILY -> "day"
            ReminderFrequency.WEEKLY -> "week"
            ReminderFrequency.MONTHLY -> "month"
            ReminderFrequency.YEARLY -> "year"
        }
        val days = if (frequency == ReminderFrequency.WEEKLY) " on " + java.time.DayOfWeek.entries
            .filter { weekdays and (1 shl (it.value - 1)) != 0 }
            .joinToString(", ") { it.name.take(3).lowercase().replaceFirstChar(Char::uppercase) } else ""
        return "Every ${if (interval == 1) "" else "$interval "}$unit${if (interval == 1) "" else "s"}$days" +
            (untilEpochDay?.let { " · until ${LocalDate.ofEpochDay(it)}" } ?: "")
    }

    companion object {
        fun decode(value: String): ReminderRecurrence {
            val parts = value.split('|')
            require(parts.size == 6)
            return ReminderRecurrence(ReminderFrequency.valueOf(parts[0]), parts[1].toInt(), parts[2].toInt(),
                parts[3].takeIf { it.isNotEmpty() }?.toLong(), parts[4], parts[5].toLong())
        }
    }
}
