package com.daykit.feature.focus.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import com.daykit.core.permissions.AppLockPermissionChecker
import java.util.Calendar

object FocusUsageTracker {

    /**
     * Start of the 24-hour cycle: local midnight (00:00:00.000).
     */
    fun getStartOfDayMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        return Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /**
     * End of the 24-hour cycle: next local midnight (00:00:00.000 of tomorrow).
     */
    fun getEndOfDayMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        return Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
    }

    /**
     * Queries foreground usage in milliseconds for each package since midnight of the
     * current day, including a session still open at [nowMillis].
     *
     * Built from raw resume/pause events rather than queryAndAggregateUsageStats: the
     * aggregate returns whole stats buckets that may start before midnight, so it
     * counted yesterday's use and tripped daily limits early.
     */
    fun queryTodayUsageStats(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ): Map<String, Long> = queryUsageStats(context, getStartOfDayMillis(nowMillis), nowMillis)

    /**
     * Foreground usage per package over roughly the last [WEEK_DAYS] days, for
     * the on-demand "Suggest apps to block" scan. Call it off the main thread.
     *
     * Uses the system's aggregated daily stats, not the event replay the daily
     * limits use. Replaying a week of raw events is fragile: one lost resume
     * turns a stray pause into days of "use" credited from the window start,
     * which put rarely-opened apps at the top. The aggregate's bucket edges can
     * include part of the day before the window — harmless for a ranking,
     * which is why daily limits (where it isn't) keep the event replay.
     */
    fun queryWeekUsageStats(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ): Map<String, Long> {
        if (!AppLockPermissionChecker.hasUsageAccess(context)) return emptyMap()
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()
        return usageStatsManager
            .queryAndAggregateUsageStats(getWeekStartMillis(nowMillis), nowMillis)
            .mapValues { (_, stats) -> stats.totalTimeInForeground }
            .filterValues { it > 0L }
    }

    /** Local midnight [WEEK_DAYS] - 1 days before today. */
    fun getWeekStartMillis(nowMillis: Long = System.currentTimeMillis()): Long =
        Calendar.getInstance().apply {
            timeInMillis = getStartOfDayMillis(nowMillis)
            add(Calendar.DAY_OF_YEAR, -(WEEK_DAYS - 1))
        }.timeInMillis

    const val WEEK_DAYS = 7

    private fun queryUsageStats(
        context: Context,
        startMillis: Long,
        endMillis: Long,
    ): Map<String, Long> {
        if (!AppLockPermissionChecker.hasUsageAccess(context)) return emptyMap()
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()

        val events = usageStatsManager.queryEvents(startMillis, endMillis)
        val event = UsageEvents.Event()
        val transitions = mutableListOf<ForegroundTransition>()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val kind = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> ForegroundTransition.Kind.Resumed
                UsageEvents.Event.ACTIVITY_PAUSED -> ForegroundTransition.Kind.Paused
                UsageEvents.Event.DEVICE_SHUTDOWN -> ForegroundTransition.Kind.Shutdown
                else -> null
            } ?: continue
            transitions += ForegroundTransition(
                packageName = event.packageName.orEmpty(),
                className = event.className,
                timestampMillis = event.timeStamp,
                kind = kind,
            )
        }
        return foregroundMillisByPackage(transitions, startMillis, endMillis)
    }

    /**
     * Sums foreground time per package over [[startMillis], [endMillis]].
     *
     * A package counts as foreground while any of its activities is resumed, so
     * moving between its own screens is one continuous session. An app already open
     * at [startMillis] surfaces only as a pause for an activity never seen resuming;
     * that span is counted from [startMillis]. Sessions still open are closed at
     * [endMillis].
     */
    internal fun foregroundMillisByPackage(
        transitions: List<ForegroundTransition>,
        startMillis: Long,
        endMillis: Long,
    ): Map<String, Long> {
        val totals = mutableMapOf<String, Long>()
        val resumedActivities = mutableMapOf<String, MutableSet<String?>>()
        val sessionStart = mutableMapOf<String, Long>()
        val seen = mutableSetOf<String>()

        fun add(packageName: String, from: Long, to: Long) {
            val span = to.coerceAtMost(endMillis) - from.coerceAtLeast(startMillis)
            if (span > 0) totals[packageName] = (totals[packageName] ?: 0L) + span
        }

        fun closeSession(packageName: String, at: Long) {
            sessionStart.remove(packageName)?.let { add(packageName, it, at) }
            resumedActivities.remove(packageName)
        }

        transitions.sortedBy { it.timestampMillis }.forEach { transition ->
            val pkg = transition.packageName
            when (transition.kind) {
                ForegroundTransition.Kind.Resumed -> {
                    seen += pkg
                    val active = resumedActivities.getOrPut(pkg) { mutableSetOf() }
                    if (active.isEmpty()) sessionStart[pkg] = transition.timestampMillis
                    active += transition.className
                }

                ForegroundTransition.Kind.Paused -> {
                    val active = resumedActivities[pkg]
                    if (active != null && active.remove(transition.className)) {
                        if (active.isEmpty()) closeSession(pkg, transition.timestampMillis)
                    } else if (pkg !in seen) {
                        // Open since before the window began.
                        add(pkg, startMillis, transition.timestampMillis)
                    }
                    seen += pkg
                }

                ForegroundTransition.Kind.Shutdown ->
                    sessionStart.keys.toList().forEach { closeSession(it, transition.timestampMillis) }
            }
        }
        sessionStart.keys.toList().forEach { closeSession(it, endMillis) }
        return totals
    }

    /**
     * Formats duration in milliseconds to human-readable string (e.g., "1h 20m", "45m", "0m").
     */
    fun formatUsage(millis: Long): String {
        if (millis <= 0L) return "0m"
        val totalMinutes = millis / 60_000L
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        return when {
            hours > 0L && minutes > 0L -> "${hours}h ${minutes}m"
            hours > 0L -> "${hours}h"
            minutes > 0L -> "${minutes}m"
            else -> "< 1m"
        }
    }
}

/** One resume/pause edge from UsageEvents, decoupled from Android for unit tests. */
internal data class ForegroundTransition(
    val packageName: String,
    val className: String?,
    val timestampMillis: Long,
    val kind: Kind,
) {
    enum class Kind { Resumed, Paused, Shutdown }
}
