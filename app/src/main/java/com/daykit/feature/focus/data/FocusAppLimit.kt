package com.daykit.feature.focus.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A daily usage quota configured for a specific app.
 *
 * Once [packageName] has accumulated [dailyLimitMinutes] of foreground use
 * in the current calendar day (from 12:00 AM to 12:00 AM), DayKit automatically
 * blocks it until midnight.
 */
data class FocusAppLimit(
    val packageName: String,
    val dailyLimitMinutes: Int,
    val enabled: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

@Entity(
    tableName = "focus_app_limits",
    indices = [Index(value = ["packageName"], unique = true)],
)
data class FocusAppLimitEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val dailyLimitMinutes: Int,
    val enabled: Boolean,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)

internal fun FocusAppLimitEntity.toModel(): FocusAppLimit = FocusAppLimit(
    packageName = packageName,
    dailyLimitMinutes = dailyLimitMinutes,
    enabled = enabled,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)
