package com.daykit.feature.focus.data

import com.daykit.core.backup.BackupContributor
import com.daykit.core.backup.BackupToolKeys
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backs up focus groups, schedules, and active blocks. Without this a device
 * restore silently dropped everything — including running blocks, handing back
 * the apps the user had deliberately locked themselves out of.
 *
 * Block expiry is an absolute timestamp, so a restored block resumes with
 * whatever time was left rather than restarting; blocks that expired while the
 * backup sat around are dropped on import (see [FocusBlockStore.mergeBlocks]).
 * Groups and schedules are definitions, so they restore unconditionally.
 *
 * Two caveats worth knowing: a restored block only actually blocks if the new
 * device has granted Usage Access, and restored schedules need re-arming — the
 * caller does that by re-projecting after import.
 *
 * `schemaVersion` is 2: import silently skips a section whose version doesn't
 * match, so adding groups/schedules to the payload had to bump it.
 */
class FocusBackupContributor(
    private val focusBlockStore: FocusBlockStore,
    private val groupDao: FocusGroupDao,
    private val scheduleDao: FocusScheduleDao,
    /** Re-project and re-arm after import; see [importJson]. */
    private val onImported: (suspend () -> Unit)? = null,
) : BackupContributor {
    override val toolKey: String = BackupToolKeys.FOCUS
    override val schemaVersion: Int = 2

    override suspend fun exportJson(): JSONObject {
        val blocks = JSONArray().also { rows ->
            focusBlockStore.getActiveBlocks().forEach { block ->
                rows.put(
                    JSONObject()
                        .put("packageName", block.packageName)
                        .put("label", block.label)
                        .put("lockUntilMillis", block.lockUntilMillis),
                )
            }
        }
        val groups = JSONArray().also { rows ->
            groupDao.getGroups().forEach { group ->
                rows.put(
                    JSONObject()
                        .put("groupId", group.groupId)
                        .put("name", group.name)
                        .put("colorIndex", group.colorIndex)
                        .put("packageNames", group.packageNames)
                        .put("createdAtMillis", group.createdAtMillis)
                        .put("updatedAtMillis", group.updatedAtMillis),
                )
            }
        }
        val schedules = JSONArray().also { rows ->
            scheduleDao.getSchedules().forEach { schedule ->
                rows.put(
                    JSONObject()
                        .put("scheduleId", schedule.scheduleId)
                        .put("groupId", schedule.groupId)
                        .put("label", schedule.label)
                        .put("startHour", schedule.startHour)
                        .put("startMinute", schedule.startMinute)
                        .put("endHour", schedule.endHour)
                        .put("endMinute", schedule.endMinute)
                        .put("daysMask", schedule.daysMask)
                        .put("strict", schedule.strict)
                        .put("enabled", schedule.enabled)
                        .put("createdAtMillis", schedule.createdAtMillis)
                        .put("updatedAtMillis", schedule.updatedAtMillis),
                )
            }
        }
        return JSONObject()
            .put("blocks", blocks)
            .put("groups", groups)
            .put("schedules", schedules)
    }

    override suspend fun importJson(payload: JSONObject) {
        payload.optJSONArray("groups")?.let { rows ->
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                val groupId = row.optString("groupId")
                if (groupId.isEmpty()) continue
                val now = System.currentTimeMillis()
                groupDao.upsertGroup(
                    FocusGroupEntity(
                        id = groupDao.getGroup(groupId)?.id ?: 0,
                        groupId = groupId,
                        name = row.optString("name", "Group"),
                        colorIndex = row.optInt("colorIndex", 0),
                        packageNames = row.optString("packageNames", ""),
                        createdAtMillis = row.optLong("createdAtMillis", now),
                        updatedAtMillis = row.optLong("updatedAtMillis", now),
                    ),
                )
            }
        }

        payload.optJSONArray("schedules")?.let { rows ->
            for (index in 0 until rows.length()) {
                val row = rows.getJSONObject(index)
                val scheduleId = row.optString("scheduleId")
                val groupId = row.optString("groupId")
                if (scheduleId.isEmpty() || groupId.isEmpty()) continue
                val now = System.currentTimeMillis()
                scheduleDao.upsertSchedule(
                    FocusScheduleEntity(
                        id = scheduleDao.getSchedule(scheduleId)?.id ?: 0,
                        scheduleId = scheduleId,
                        groupId = groupId,
                        label = row.optString("label", ""),
                        startHour = row.optInt("startHour", 9),
                        startMinute = row.optInt("startMinute", 0),
                        endHour = row.optInt("endHour", 11),
                        endMinute = row.optInt("endMinute", 0),
                        daysMask = row.optInt("daysMask", 0),
                        strict = row.optBoolean("strict", false),
                        enabled = row.optBoolean("enabled", true),
                        createdAtMillis = row.optLong("createdAtMillis", now),
                        updatedAtMillis = row.optLong("updatedAtMillis", now),
                    ),
                )
            }
        }

        payload.optJSONArray("blocks")?.let { rows ->
            val blocks = buildList {
                for (index in 0 until rows.length()) {
                    val row = rows.getJSONObject(index)
                    val packageName = row.optString("packageName")
                    if (packageName.isEmpty()) continue
                    add(
                        FocusBlock(
                            packageName = packageName,
                            label = row.optString("label", packageName),
                            lockUntilMillis = row.optLong("lockUntilMillis", 0L),
                        ),
                    )
                }
            }
            focusBlockStore.mergeBlocks(blocks)
        }

        // Restored schedules are inert until their next occurrence is projected
        // and armed. Doing it here rather than in the restore UI keeps the
        // contributor self-contained — a restore path that forgot to call this
        // would leave schedules that look active but never fire.
        onImported?.invoke()
    }
}
