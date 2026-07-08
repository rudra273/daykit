package com.rudra.daykit.feature.applock.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import com.rudra.daykit.DayKitApplication
import com.rudra.daykit.core.session.AppLockSessionManager
import com.rudra.daykit.feature.applock.domain.SamsungSecureFolderSupport
import com.rudra.daykit.feature.applock.domain.SettingsPackageResolver
import com.rudra.daykit.feature.applock.ui.LockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class AppLockAccessibilityService : AccessibilityService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var settingsPackage: String
    private lateinit var detector: ForegroundAppDetector
    private var launcherPackage: String? = null
    private var lastForegroundPackage: String? = null
    private var activeActivityLockPackage: String? = null
    private val sessionResetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF,
                Intent.ACTION_USER_PRESENT -> resetLockSession()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        settingsPackage = SettingsPackageResolver.resolve(this)
        launcherPackage = resolveLauncherPackage()
        detector = ForegroundAppDetector(this)
        registerSessionResetReceiver()
        loadLockedPackages()
        guardUnlockedSessions()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val type = event?.eventType ?: return
        if (type == AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            handleWindowsChanged(event)
            return
        }
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val foregroundPackage = event.packageName?.toString() ?: return
        if (foregroundPackage == packageName) return

        val currentLockedPackages = loadLockedPackages()
        val windowId = event.windowId

        if (foregroundPackage != lastForegroundPackage) {
            lastForegroundPackage = foregroundPackage
            if (activeActivityLockPackage != foregroundPackage) {
                activeActivityLockPackage = null
            }
            AppLockSessionManager.keepOnly(foregroundPackage)
        }

        if (
            foregroundPackage in currentLockedPackages &&
            AppLockSessionManager.isAllowed(foregroundPackage)
        ) {
            val wasBackgroundedAfterUnlock = AppLockSessionManager.grantedAtMillis(foregroundPackage)
                ?.let { grantedAt ->
                    ::detector.isInitialized && detector.wasBackgroundedSince(foregroundPackage, grantedAt)
                } == true

            if (wasBackgroundedAfterUnlock) {
                AppLockSessionManager.revoke(foregroundPackage)
            } else if (!AppLockSessionManager.hasBoundWindow(foregroundPackage) && windowId >= 0) {
                if (activeActivityLockPackage == foregroundPackage) {
                    AppLockSessionManager.bindWindow(foregroundPackage, windowId)
                } else {
                    AppLockSessionManager.revoke(foregroundPackage)
                }
            } else if (windowId >= 0 && !AppLockSessionManager.isAllowedForWindow(foregroundPackage, windowId)) {
                AppLockSessionManager.revoke(foregroundPackage)
            }
        }

        val shouldLock = foregroundPackage in currentLockedPackages &&
            !AppLockSessionManager.isAllowed(foregroundPackage) &&
            !SamsungSecureFolderSupport.shouldBypassLock(
                packageName = foregroundPackage,
                className = event.className?.toString(),
                settingsPackage = settingsPackage,
            )

        if (shouldLock) {
            launchActivityLockScreen(foregroundPackage)
        }
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        runCatching { unregisterReceiver(sessionResetReceiver) }
        resetLockSession()
        scope.cancel()
        super.onDestroy()
    }

    private fun guardUnlockedSessions() {
        scope.launch {
            while (true) {
                val currentLockedPackages = loadLockedPackages()
                val currentForegroundApp = runCatching { detector.currentResumedApp() }.getOrNull()
                var revokedCurrentForeground = false

                AppLockSessionManager.grantedPackages().forEach { (packageName, grantedAtMillis) ->
                    if (detector.wasBackgroundedSince(packageName, grantedAtMillis)) {
                        AppLockSessionManager.revoke(packageName)
                        if (currentForegroundApp?.packageName == packageName) {
                            revokedCurrentForeground = true
                        }
                    }
                }

                val foregroundPackage = currentForegroundApp?.packageName
                if (
                    foregroundPackage != null &&
                    foregroundPackage != packageName &&
                    foregroundPackage in currentLockedPackages &&
                    !AppLockSessionManager.isAllowed(foregroundPackage) &&
                    !SamsungSecureFolderSupport.shouldBypassLock(
                        packageName = foregroundPackage,
                        className = currentForegroundApp.className,
                        settingsPackage = settingsPackage,
                    )
                ) {
                    if (revokedCurrentForeground || activeActivityLockPackage != foregroundPackage) {
                        launchActivityLockScreen(foregroundPackage)
                    }
                }

                delay(SESSION_GUARD_INTERVAL_MILLIS)
            }
        }
    }

    private fun loadLockedPackages(): Set<String> {
        val container = (application as DayKitApplication).container
        return container.appLockRepository.getLockedPackages()
    }

    private fun registerSessionResetReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(sessionResetReceiver, filter)
    }

    private fun handleWindowsChanged(event: AccessibilityEvent) {
        val eventPackage = event.packageName?.toString()
        if (eventPackage == packageName || eventPackage == activeActivityLockPackage) return
        if (eventPackage == launcherPackage || eventPackage == SYSTEM_UI_PACKAGE) {
            resetLockSession()
            return
        }

        if (event.windowId >= 0 &&
            event.windowChanges and AccessibilityEvent.WINDOWS_CHANGE_REMOVED != 0
        ) {
            val revoked = AppLockSessionManager.revokeByWindow(event.windowId)
            if (revoked) {
                lastForegroundPackage = null
            } else {
                AppLockSessionManager.revokeUnbound()
            }
        }
    }

    private fun resolveLauncherPackage(): String? {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName
    }

    private fun resetLockSession() {
        AppLockSessionManager.clearAll()
        lastForegroundPackage = null
        activeActivityLockPackage = null
    }

    private fun launchActivityLockScreen(packageName: String) {
        if (activeActivityLockPackage == packageName) return
        activeActivityLockPackage = packageName
        mainHandler.post {
            val intent = LockActivity.intent(this, packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
        }
    }

    private companion object {
        const val SESSION_GUARD_INTERVAL_MILLIS = 300L
        const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    }
}
