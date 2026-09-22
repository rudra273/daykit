package com.daykit.feature.focus.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusAppLimitDao {
    @Query("SELECT * FROM focus_app_limits ORDER BY createdAtMillis ASC")
    fun observeAppLimits(): Flow<List<FocusAppLimitEntity>>

    @Query("SELECT * FROM focus_app_limits ORDER BY createdAtMillis ASC")
    suspend fun getAppLimits(): List<FocusAppLimitEntity>

    @Query("SELECT * FROM focus_app_limits WHERE enabled = 1")
    suspend fun getEnabledAppLimits(): List<FocusAppLimitEntity>

    @Query("SELECT * FROM focus_app_limits WHERE packageName = :packageName LIMIT 1")
    suspend fun getAppLimit(packageName: String): FocusAppLimitEntity?

    @Upsert
    suspend fun upsertAppLimit(entity: FocusAppLimitEntity)

    @Query("UPDATE focus_app_limits SET enabled = :enabled, updatedAtMillis = :updatedAtMillis WHERE packageName = :packageName")
    suspend fun setEnabled(packageName: String, enabled: Boolean, updatedAtMillis: Long)

    @Query("DELETE FROM focus_app_limits WHERE packageName = :packageName")
    suspend fun deleteAppLimit(packageName: String)
}
