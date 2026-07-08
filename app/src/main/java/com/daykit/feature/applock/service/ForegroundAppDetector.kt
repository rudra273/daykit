package com.rudra.daykit.feature.applock.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import kotlin.math.max

class ForegroundAppDetector(
    context: Context,
) {
    private val usageStatsManager =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun currentForegroundApp(): ForegroundApp? {
        val end = System.currentTimeMillis()
        val begin = end - LOOKBACK_MILLIS
        val events = usageStatsManager.queryEvents(begin, end)
        val event = UsageEvents.Event()
        var foregroundApp: ForegroundApp? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND ||
                event.eventType == UsageEvents.Event.ACTIVITY_RESUMED
            ) {
                foregroundApp = ForegroundApp(
                    packageName = event.packageName,
                    className = event.className,
                )
            }
        }

        return foregroundApp
    }

    fun wasBackgroundedSince(packageName: String, sinceMillis: Long): Boolean {
        val events = usageStatsManager.queryEvents(max(0L, sinceMillis - EVENT_CLOCK_SKEW_MILLIS), System.currentTimeMillis())
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.packageName == packageName &&
                (
                    event.eventType == UsageEvents.Event.MOVE_TO_BACKGROUND ||
                        event.eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
                        event.eventType == UsageEvents.Event.ACTIVITY_STOPPED
                    )
            ) {
                return true
            }
        }

        return false
    }

    fun currentResumedApp(): ForegroundApp? {
        val end = System.currentTimeMillis()
        val begin = end - RESUMED_LOOKBACK_MILLIS
        val events = usageStatsManager.queryEvents(begin, end)
        val event = UsageEvents.Event()
        var foregroundApp: ForegroundApp? = null

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND,
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    foregroundApp = ForegroundApp(
                        packageName = event.packageName,
                        className = event.className,
                    )
                }

                UsageEvents.Event.MOVE_TO_BACKGROUND,
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    if (foregroundApp?.packageName == event.packageName) {
                        foregroundApp = null
                    }
                }
            }
        }

        return foregroundApp
    }

    private companion object {
        const val LOOKBACK_MILLIS = 5_000L
        const val RESUMED_LOOKBACK_MILLIS = 20_000L
        const val EVENT_CLOCK_SKEW_MILLIS = 500L
    }
}

data class ForegroundApp(
    val packageName: String,
    val className: String?,
)
