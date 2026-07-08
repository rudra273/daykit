package com.daykit.core.session

import java.util.concurrent.ConcurrentHashMap

object AppLockSessionManager {
    private val temporaryAccess = ConcurrentHashMap<String, AccessGrant>()

    fun allow(packageName: String) {
        temporaryAccess[packageName] = AccessGrant(
            windowId = null,
            grantedAtMillis = System.currentTimeMillis(),
        )
    }

    fun isAllowed(packageName: String): Boolean {
        return temporaryAccess.containsKey(packageName)
    }

    fun isAllowedForWindow(packageName: String, windowId: Int): Boolean {
        val grant = temporaryAccess[packageName] ?: return false
        return grant.windowId == windowId
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
    )
}
