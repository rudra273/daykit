package com.daykit.feature.focus.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusGroupDao {
    @Query("SELECT * FROM focus_groups ORDER BY name COLLATE NOCASE ASC")
    fun observeGroups(): Flow<List<FocusGroupEntity>>

    @Query("SELECT * FROM focus_groups ORDER BY name COLLATE NOCASE ASC")
    suspend fun getGroups(): List<FocusGroupEntity>

    @Query("SELECT * FROM focus_groups WHERE groupId = :groupId LIMIT 1")
    suspend fun getGroup(groupId: String): FocusGroupEntity?

    @Upsert
    suspend fun upsertGroup(entity: FocusGroupEntity)

    @Query("DELETE FROM focus_groups WHERE groupId = :groupId")
    suspend fun deleteGroup(groupId: String)
}

@Dao
interface FocusScheduleDao {
    @Query("SELECT * FROM focus_schedules ORDER BY startHour ASC, startMinute ASC")
    fun observeSchedules(): Flow<List<FocusScheduleEntity>>

    @Query("SELECT * FROM focus_schedules WHERE enabled = 1")
    suspend fun getEnabledSchedules(): List<FocusScheduleEntity>

    /** Every schedule regardless of enabled state — used by backup export. */
    @Query("SELECT * FROM focus_schedules ORDER BY startHour ASC, startMinute ASC")
    suspend fun getSchedules(): List<FocusScheduleEntity>

    @Query("SELECT * FROM focus_schedules WHERE scheduleId = :scheduleId LIMIT 1")
    suspend fun getSchedule(scheduleId: String): FocusScheduleEntity?

    @Upsert
    suspend fun upsertSchedule(entity: FocusScheduleEntity)

    @Query("UPDATE focus_schedules SET enabled = :enabled, updatedAtMillis = :updatedAtMillis WHERE scheduleId = :scheduleId")
    suspend fun setEnabled(scheduleId: String, enabled: Boolean, updatedAtMillis: Long)

    @Query("DELETE FROM focus_schedules WHERE scheduleId = :scheduleId")
    suspend fun deleteSchedule(scheduleId: String)

    /** Deleting a group must not leave schedules pointing at nothing. */
    @Query("DELETE FROM focus_schedules WHERE groupId = :groupId")
    suspend fun deleteSchedulesForGroup(groupId: String)
}
