package com.daykit.feature.reminder.data

/** Shared timing rules for every reminder frequency. */
object ReminderTiming {
    const val ADVANCE_NOTICE_MILLIS = 10 * 60_000L

    /** The time at which an occurrence should first be presented to the user. */
    fun alertAtMillis(occurrenceMillis: Long): Long =
        (occurrenceMillis - ADVANCE_NOTICE_MILLIS).coerceAtLeast(0L)

    fun isReadyToAlert(occurrenceMillis: Long, nowMillis: Long): Boolean =
        nowMillis >= alertAtMillis(occurrenceMillis)
}
