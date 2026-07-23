package com.daykit.feature.applock.data

import com.daykit.core.session.AppLockSessionManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class AppLockRepository(
    private val lockedPackageCache: LockedPackageCache,
    private val focusBlockStore: FocusBlockStore,
) {
    private val lockedRecords = MutableStateFlow(lockedPackageCache.getRecords().orEmpty())
    private val focusBlocks = MutableStateFlow(focusBlockStore.getActiveBlocks())

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

    /** Emits the set of apps that are currently under a timed focus block. */
    fun observeFocusBlocks(): Flow<List<FocusBlock>> = focusBlocks

    /**
     * Starts a strict timed lock on [packageName] lasting [durationMillis].
     * Until it expires the app cannot be opened, even with the correct PIN.
     */
    suspend fun startFocusBlock(packageName: String, label: String, durationMillis: Long) {
        val lockUntilMillis = System.currentTimeMillis() + durationMillis
        focusBlockStore.startBlock(packageName, label, lockUntilMillis)
        // Drop any live PIN-unlock grant so that when the block expires the app
        // falls back to its true state (PIN gate if locked) instead of being
        // opened by a grant that outlived the block. Without this, unlocking an
        // app and then starting a short block would leave it openable at expiry.
        AppLockSessionManager.revoke(packageName)
        focusBlocks.value = focusBlockStore.getActiveBlocks()
    }

    /** Expiry timestamp for an active focus block on [packageName], else null. */
    fun focusBlockUntil(packageName: String): Long? = focusBlockStore.lockUntil(packageName)

    fun activeFocusPackages(): Set<String> = focusBlockStore.activePackages()

    /** Re-reads the store and drops any blocks whose timers have expired. */
    fun refreshFocusBlocks() {
        focusBlocks.value = focusBlockStore.getActiveBlocks()
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
