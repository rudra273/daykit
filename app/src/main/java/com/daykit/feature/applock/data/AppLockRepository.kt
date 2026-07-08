package com.daykit.feature.applock.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class AppLockRepository(
    private val lockedPackageCache: LockedPackageCache,
) {
    private val lockedRecords = MutableStateFlow(lockedPackageCache.getRecords().orEmpty())

    fun observeLockedApps(): Flow<List<LockedApp>> {
        return lockedRecords.map { records -> records.toLockedApps() }
    }

    fun observeAllSelections(): Flow<List<LockedApp>> {
        return observeLockedApps()
    }

    suspend fun getLockedApps(): List<LockedApp> {
        return lockedRecords.value.toLockedApps()
    }

    fun getLockedPackages(): Set<String> {
        return lockedPackageCache.getPackages().orEmpty()
    }

    suspend fun setLocked(packageName: String, label: String, locked: Boolean) {
        lockedRecords.value = lockedPackageCache.updatePackage(
            packageName = packageName,
            label = label,
            locked = locked,
        )
    }

    private fun List<LockedPackageRecord>.toLockedApps(): List<LockedApp> {
        return sortedBy { it.label.lowercase() }
            .mapIndexed { index, record ->
                LockedApp(
                    id = index.toLong() + 1L,
                    packageName = record.packageName,
                    label = record.label,
                    enabled = true,
                )
            }
    }
}
