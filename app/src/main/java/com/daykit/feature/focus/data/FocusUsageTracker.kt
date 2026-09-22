package com.daykit.feature.focus.data

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
     * Queries foreground usage in milliseconds for each package since midnight of the current day.
     */
    fun queryTodayUsageStats(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
    ): Map<String, Long> {
        if (!AppLockPermissionChecker.hasUsageAccess(context)) return emptyMap()
        val startOfDay = getStartOfDayMillis(nowMillis)
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()

        val aggregated = usageStatsManager.queryAndAggregateUsageStats(startOfDay, nowMillis)
        return aggregated.mapValues { it.value.totalTimeInForeground }
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
