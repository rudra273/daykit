package com.daykit.core.session

import com.daykit.core.data.AppLockRelock
import java.util.concurrent.ConcurrentHashMap

object AppLockSessionManager {
    private val temporaryAccess = ConcurrentHashMap<String, AccessGrant>()

    /**
     * How long an unlock grant stays valid before the app re-challenges, in the
     * default [AppLockRelock.OnLeave] mode.
     */
    private const val GRANT_TTL_MILLIS = 5 * 60_000L

    /**
     * The user's re-lock choice, mirrored from AppPreferences by DayKitApplication
     * so the monitor service never reads prefs on its polling loop. Screen-off
     * always clears every grant (AppMonitorService.resetLockSession) whatever
     * this is set to.
     */
    @Volatile
    var relock: AppLockRelock = AppLockRelock.OnLeave

    fun allow(packageName: String) {
        temporaryAccess[packageName] = AccessGrant(
            windowId = null,
            grantedAtMillis = System.currentTimeMillis(),
        )
    }

    fun isAllowed(packageName: String): Boolean {
        val grant = temporaryAccess[packageName] ?: return false
        if (grant.isExpired(System.currentTimeMillis())) {
            temporaryAccess.remove(packageName)
            return false
        }
        return true
    }

    private fun AccessGrant.isExpired(now: Long): Boolean = when (val mode = relock) {
        // Grants auto-expire so a locked app cannot stay unlocked indefinitely
        // while the screen stays on (e.g. user unlocks, leaves, comes back later).
        AppLockRelock.OnLeave -> now - grantedAtMillis > GRANT_TTL_MILLIS
        AppLockRelock.ScreenOff -> false
        else -> leftAtMillis != null && now - leftAtMillis > mode.awayMillis
    }

    /**
     * Called by the monitor on every real app switch. In [AppLockRelock.OnLeave]
     * every other package's grant is evicted; in the relaxed modes they are only
     * stamped with when the user left, so a quick return doesn't re-challenge.
     */
    fun onForegroundChanged(foregroundPackage: String?, nowMillis: Long = System.currentTimeMillis()) {
        if (relock == AppLockRelock.OnLeave) {
            keepOnly(foregroundPackage)
            return
        }
        // A grant whose away time ran out must be dropped before the returning
        // app's stamp is cleared, or coming back would always look "in time".
        temporaryAccess.entries.removeIf { (_, grant) -> grant.isExpired(nowMillis) }
        temporaryAccess.replaceAll { packageName, grant ->
            when {
                packageName == foregroundPackage -> grant.copy(leftAtMillis = null)
                grant.leftAtMillis == null -> grant.copy(leftAtMillis = nowMillis)
                else -> grant
            }
        }
    }

    fun isAllowedForWindow(packageName: String, windowId: Int): Boolean {
        if (!isAllowed(packageName)) return false
        return temporaryAccess[packageName]?.windowId == windowId
    }

    fun bindWindow(packageName: String, windowId: Int) {
        val grant = temporaryAccess[packageName]
        if (grant != null) {
            temporaryAccess[packageName] = grant.copy(windowId = windowId)
        }
    }

    fun hasBoundWindow(packageName: String): Boolean {
        return temporaryAccess[packageName]?.windowId != null
    }

    fun revoke(packageName: String) {
        temporaryAccess.remove(packageName)
    }

    fun revokeByWindow(windowId: Int): Boolean {
        val packages = temporaryAccess
            .filterValues { grant -> grant.windowId == windowId }
            .keys

        packages.forEach(temporaryAccess::remove)
        return packages.isNotEmpty()
    }

    fun revokeUnbound() {
        temporaryAccess
            .filterValues { grant -> grant.windowId == null }
            .keys
            .forEach(temporaryAccess::remove)
    }

    fun grantedAtMillis(packageName: String): Long? {
        return temporaryAccess[packageName]?.grantedAtMillis
    }

    fun grantedPackages(): Map<String, Long> {
        return temporaryAccess.mapValues { it.value.grantedAtMillis }
    }

    fun keepOnly(packageName: String?) {
        if (packageName == null) {
            clearAll()
            return
        }
        temporaryAccess.keys
            .filterNot { it == packageName }
            .forEach(temporaryAccess::remove)
    }

    fun clearAll() {
        temporaryAccess.clear()
    }

    private data class AccessGrant(
        val windowId: Int?,
        val grantedAtMillis: Long,
        /** When the user switched away from the app; null while it is in front. */
        val leftAtMillis: Long? = null,
    )
}
