package com.daykit.feature.focus.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FocusAppLimitRepository(
    private val dao: FocusAppLimitDao,
    private val cache: FocusAppLimitCache,
    private val context: Context,
) {
    fun observeAppLimits(): Flow<List<FocusAppLimit>> {
        return dao.observeAppLimits().map { entities ->
            entities.map { it.toModel() }
        }
    }

    suspend fun getAppLimits(): List<FocusAppLimit> {
        return dao.getAppLimits().map { it.toModel() }
    }

    suspend fun getAppLimit(packageName: String): FocusAppLimit? {
        return dao.getAppLimit(packageName)?.toModel()
    }

    suspend fun saveAppLimit(
        packageName: String,
        dailyLimitMinutes: Int,
        enabled: Boolean = true,
    ) {
        val existing = dao.getAppLimit(packageName)
        if (existing != null && existing.enabled) {
            val used = getTodayUsageMillis(packageName)
            if (used >= existing.dailyLimitMinutes * 60_000L) {
                throw IllegalStateException("Limit reached for today. Locked until midnight.")
            }
        }
        val now = System.currentTimeMillis()
        val entity = FocusAppLimitEntity(
            id = existing?.id ?: 0L,
            packageName = packageName,
            dailyLimitMinutes = dailyLimitMinutes,
            enabled = enabled,
            createdAtMillis = existing?.createdAtMillis ?: now,
            updatedAtMillis = now,
        )
        dao.upsertAppLimit(entity)
        syncCache()
    }

    suspend fun setEnabled(packageName: String, enabled: Boolean) {
        val limit = dao.getAppLimit(packageName) ?: return
        if (!enabled && limit.enabled) {
            val used = getTodayUsageMillis(packageName)
            if (used >= limit.dailyLimitMinutes * 60_000L) {
                throw IllegalStateException("Limit reached for today. Locked until midnight.")
            }
        }
        val now = System.currentTimeMillis()
        dao.setEnabled(packageName, enabled, now)
        syncCache()
    }

    suspend fun deleteAppLimit(packageName: String) {
        val limit = dao.getAppLimit(packageName) ?: return
        if (limit.enabled) {
            val used = getTodayUsageMillis(packageName)
            if (used >= limit.dailyLimitMinutes * 60_000L) {
                throw IllegalStateException("Limit reached for today. Locked until midnight.")
            }
        }
        dao.deleteAppLimit(packageName)
        syncCache()
    }

    suspend fun syncCache() {
        val enabled = dao.getEnabledAppLimits()
        val limitMap = enabled.associate { it.packageName to it.dailyLimitMinutes }
        cache.putEnabledLimits(limitMap)
    }

    fun getTodayUsageMap(): Map<String, Long> {
        return FocusUsageTracker.queryTodayUsageStats(context)
    }

    fun getTodayUsageMillis(packageName: String): Long {
        return getTodayUsageMap()[packageName] ?: 0L
    }
}
