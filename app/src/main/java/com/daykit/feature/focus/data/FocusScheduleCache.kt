package com.daykit.feature.focus.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * One armed occurrence of a schedule: the concrete window it will block, already
 * resolved to absolute timestamps so no recurrence math is needed to enforce it.
 */
data class ArmedSchedule(
    val scheduleId: String,
    val groupId: String,
    val label: String,
    val packageNames: List<String>,
    val startMillis: Long,
    val endMillis: Long,
    val strict: Boolean,
)

/**
 * Plain-SharedPreferences projection of the *next* occurrence of every enabled
 * schedule, written on every change to the Room tables.
 *
 * Room is the source of truth; this is a cache. It exists because
 * `AppMonitorService` seeds its blocked-package map synchronously in `onCreate`
 * so there is never a window where the poll loop runs with empty state — and on
 * a cold start (boot, or an OS restart after a kill) SQLCipher may not be
 * unlocked yet. Reading Room there would either block or fail, so enforcement
 * reads this instead. Same reasoning as `LockedPackageCache`.
 *
 * Written with `commit = true` for the same reason: the service and the alarm
 * receiver read it directly, and an async write could lose the race.
 */
class FocusScheduleCache(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getArmed(): List<ArmedSchedule> {
        val raw = prefs.getString(KEY_ARMED, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { index ->
                val item = array.getJSONObject(index)
                val packages = item.optJSONArray(KEY_PACKAGES)
                ArmedSchedule(
                    scheduleId = item.getString(KEY_SCHEDULE_ID),
                    groupId = item.optString(KEY_GROUP_ID),
                    label = item.optString(KEY_LABEL),
                    packageNames = buildList {
                        if (packages != null) {
                            for (i in 0 until packages.length()) add(packages.getString(i))
                        }
                    },
                    startMillis = item.optLong(KEY_START, 0L),
                    endMillis = item.optLong(KEY_END, 0L),
                    strict = item.optBoolean(KEY_STRICT, false),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun putArmed(schedules: List<ArmedSchedule>) {
        val array = JSONArray()
        schedules.forEach { armed ->
            array.put(
                JSONObject()
                    .put(KEY_SCHEDULE_ID, armed.scheduleId)
                    .put(KEY_GROUP_ID, armed.groupId)
                    .put(KEY_LABEL, armed.label)
                    .put(KEY_PACKAGES, JSONArray().also { pkgs -> armed.packageNames.forEach(pkgs::put) })
                    .put(KEY_START, armed.startMillis)
                    .put(KEY_END, armed.endMillis)
                    .put(KEY_STRICT, armed.strict),
            )
        }
        prefs.edit(commit = true) { putString(KEY_ARMED, array.toString()) }
    }

    /**
     * Packages blocked right now by a scheduled session, mapped to when that
     * session ends. Derived from the armed windows rather than a separate
     * "currently running" record, so a missed end alarm cannot leave an app
     * blocked forever — the window simply stops matching.
     */
    fun activeWindows(nowMillis: Long = System.currentTimeMillis()): Map<String, ArmedSchedule> {
        val active = getArmed().filter { nowMillis in it.startMillis until it.endMillis }
        return buildMap {
            active.forEach { armed ->
                armed.packageNames.forEach { pkg ->
                    // Longest-running window wins if two sessions overlap an app.
                    val existing = get(pkg)
                    if (existing == null || armed.endMillis > existing.endMillis) put(pkg, armed)
                }
            }
        }
    }

    /**
     * Marks a Normal session as ended early so its window stops matching before
     * [ArmedSchedule.endMillis]. Recorded per occurrence (`scheduleId` + start)
     * so the next occurrence of the same schedule is unaffected.
     */
    fun markEndedEarly(scheduleId: String, startMillis: Long) {
        val updated = getArmed().map { armed ->
            if (armed.scheduleId == scheduleId && armed.startMillis == startMillis) {
                armed.copy(endMillis = System.currentTimeMillis())
            } else {
                armed
            }
        }
        putArmed(updated)
    }

    fun clear() {
        prefs.edit(commit = true) { clear() }
    }

    private companion object {
        const val PREFS_NAME = "focus_schedule_cache"
        const val KEY_ARMED = "armed"
        const val KEY_SCHEDULE_ID = "scheduleId"
        const val KEY_GROUP_ID = "groupId"
        const val KEY_LABEL = "label"
        const val KEY_PACKAGES = "packageNames"
        const val KEY_START = "startMillis"
        const val KEY_END = "endMillis"
        const val KEY_STRICT = "strict"
    }
}
