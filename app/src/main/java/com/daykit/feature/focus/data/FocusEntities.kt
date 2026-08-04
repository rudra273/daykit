package com.daykit.feature.focus.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A named set of apps the user blocks together ("Social", "Games").
 *
 * Members are stored as a newline-joined string rather than a child table: a
 * group is always loaded whole and never queried by member app, so a junction
 * table would buy a join and nothing else. Newline is safe because Android
 * package names cannot contain one.
 *
 * Holds no secrets — package names and a user-chosen label — so it uses the
 * plain DAO path, not [com.daykit.core.security.SessionValueCipher]. That is
 * deliberate: enforcement has to act on this data while the vault is locked.
 */
@Entity(
    tableName = "focus_groups",
    indices = [Index(value = ["groupId"], unique = true)],
)
data class FocusGroupEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val groupId: String,
    val name: String,
    val colorIndex: Int,
    val packageNames: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

/**
 * A recurring window during which a group is blocked.
 *
 * [daysMask] is a 7-bit weekday set with **bit 0 = Monday** (see
 * [FocusRecurrence]). An end time at or before the start time means the window
 * crosses midnight.
 *
 * [strict] is the safety valve: a Normal session can be ended early with the
 * PIN, a Strict one cannot. Scheduled sessions default to Normal because a
 * recurring irreversible block is a trap — one bad entry would lock the user out
 * every week with no escape.
 */
@Entity(
    tableName = "focus_schedules",
    indices = [
        Index(value = ["scheduleId"], unique = true),
        Index(value = ["groupId"]),
    ],
)
data class FocusScheduleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val scheduleId: String,
    val groupId: String,
    val label: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val daysMask: Int,
    val strict: Boolean,
    val enabled: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
