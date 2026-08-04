package com.daykit.feature.focus.data

import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Owns focus schedules. Room holds the definitions; every write re-projects the
 * next occurrence of each enabled schedule into [FocusScheduleCache] so
 * enforcement keeps working while the encrypted DB is locked.
 *
 * Arming the actual alarms is the caller's job (`FocusScheduleScheduler`) — this
 * class stays free of Android alarm APIs so the projection logic remains
 * testable and the repository has no service dependency.
 */
class FocusScheduleRepository(
    private val dao: FocusScheduleDao,
    private val groupDao: FocusGroupDao,
    private val cache: FocusScheduleCache,
) {
    fun observeSchedules(): Flow<List<FocusSchedule>> =
        dao.observeSchedules().map { rows -> rows.map { it.toSchedule() } }

    suspend fun getEnabledSchedules(): List<FocusSchedule> =
        dao.getEnabledSchedules().map { it.toSchedule() }

    suspend fun getSchedule(scheduleId: String): FocusSchedule? =
        dao.getSchedule(scheduleId)?.toSchedule()

    suspend fun saveSchedule(
        scheduleId: String? = null,
        groupId: String,
        label: String,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        daysMask: Int,
        strict: Boolean,
        enabled: Boolean = true,
    ): String {
        val now = System.currentTimeMillis()
        val id = scheduleId ?: UUID.randomUUID().toString()
        val existing = scheduleId?.let { dao.getSchedule(it) }
        dao.upsertSchedule(
            FocusScheduleEntity(
                id = existing?.id ?: 0,
                scheduleId = id,
                groupId = groupId,
                label = label.trim(),
                startHour = startHour.coerceIn(0, 23),
                startMinute = startMinute.coerceIn(0, 59),
                endHour = endHour.coerceIn(0, 23),
                endMinute = endMinute.coerceIn(0, 59),
                daysMask = daysMask and FocusRecurrence.EVERY_DAY_MASK,
                strict = strict,
                enabled = enabled,
                createdAtMillis = existing?.createdAtMillis ?: now,
                updatedAtMillis = now,
            ),
        )
        return id
    }

    suspend fun setEnabled(scheduleId: String, enabled: Boolean) {
        dao.setEnabled(scheduleId, enabled, System.currentTimeMillis())
    }

    suspend fun deleteSchedule(scheduleId: String) = dao.deleteSchedule(scheduleId)

    suspend fun deleteSchedulesForGroup(groupId: String) = dao.deleteSchedulesForGroup(groupId)

    /**
     * Recomputes the prefs projection from the current Room state and returns
     * the armed occurrences, newest arming first.
     *
     * A currently-running window is preserved rather than recomputed: if a
     * session is live, re-projecting to its *next* occurrence would un-block the
     * apps mid-session. Ended-early windows are likewise kept as-is until they
     * fall out of the current time range.
     */
    suspend fun reproject(now: LocalDateTime = LocalDateTime.now()): List<ArmedSchedule> {
        val nowMillis = FocusRecurrence.toEpochMillis(now)
        val live = cache.getArmed().filter { nowMillis in it.startMillis until it.endMillis }
        val liveIds = live.map { it.scheduleId }.toSet()

        val groups = groupDao.getGroups().associateBy { it.groupId }
        val upcoming = dao.getEnabledSchedules().mapNotNull { entity ->
            if (entity.scheduleId in liveIds) return@mapNotNull null
            val group = groups[entity.groupId] ?: return@mapNotNull null
            val packages = group.toGroup().packageNames
            if (packages.isEmpty()) return@mapNotNull null
            val start = FocusRecurrence.nextStart(
                now = now,
                daysMask = entity.daysMask,
                startHour = entity.startHour,
                startMinute = entity.startMinute,
            ) ?: return@mapNotNull null
            ArmedSchedule(
                scheduleId = entity.scheduleId,
                groupId = entity.groupId,
                label = entity.label.ifBlank { group.name },
                packageNames = packages,
                startMillis = FocusRecurrence.toEpochMillis(start),
                endMillis = FocusRecurrence.toEpochMillis(
                    FocusRecurrence.endFor(start, entity.endHour, entity.endMinute),
                ),
                strict = entity.strict,
            )
        }

        val armed = live + upcoming
        cache.putArmed(armed)
        return armed
    }

    /** Packages blocked right now by a scheduled session → the session blocking them. */
    fun activeWindows(nowMillis: Long = System.currentTimeMillis()): Map<String, ArmedSchedule> =
        cache.activeWindows(nowMillis)

    fun armedSchedules(): List<ArmedSchedule> = cache.getArmed()

    /**
     * Ends a live Normal session early. Strict sessions are rejected — that is
     * the whole point of the mode, and this is the guard that enforces it at the
     * data layer rather than trusting the UI to hide the button.
     */
    fun endSessionEarly(scheduleId: String, startMillis: Long): Boolean {
        val target = cache.getArmed().firstOrNull {
            it.scheduleId == scheduleId && it.startMillis == startMillis
        } ?: return false
        if (target.strict) return false
        cache.markEndedEarly(scheduleId, startMillis)
        return true
    }
}
