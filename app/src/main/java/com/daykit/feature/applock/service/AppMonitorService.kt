package com.daykit.feature.applock.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.daykit.DayKitApplication
import com.daykit.MainActivity
import com.daykit.R
import com.daykit.core.data.SecureSettingRepository
import com.daykit.core.permissions.AppLockPermissionChecker
import com.daykit.core.session.AppLockSessionManager
import com.daykit.feature.applock.domain.SamsungSecureFolderSupport
import com.daykit.feature.applock.domain.SettingsPackageResolver
import com.daykit.feature.applock.ui.LockActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class AppMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mainHandler = Handler(Looper.getMainLooper())
    private lateinit var detector: ForegroundAppDetector
    private lateinit var overlayController: LockOverlayController
    private lateinit var settingsPackage: String
    private var lockedPackages = emptySet<String>()
    private var biometricEnabled = false
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

    override fun onCreate() {
        super.onCreate()
        // Foreground promotion keeps the monitor alive while DayKit is backgrounded;
        // as a plain background service the OS killed it within minutes, leaving
        // locked apps unprotected until DayKit was reopened.
        startForeground(NOTIFICATION_ID, buildNotification())
        val container = (application as DayKitApplication).container
        detector = ForegroundAppDetector(this)
        settingsPackage = SettingsPackageResolver.resolve(this)
        overlayController = LockOverlayController(
            context = this,
            credentialRepository = container.credentialRepository,
            onBiometricRequested = { packageName -> launchActivityLockScreen(packageName) },
        )
        // Seed synchronously from the prefs caches so there is no window where the
        // monitor loop runs with an empty locked set or stale biometric flag.
        lockedPackages = container.appLockRepository.getLockedPackages()
        biometricEnabled = container.settingFlagCache
            .get(SecureSettingRepository.KEY_BIOMETRIC_ENABLED) == true
        registerSessionResetReceiver()
        observeLockedApps()
        observeBiometricSetting()
        monitorForegroundApps()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!AppLockPermissionChecker.hasUsageAccess(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { unregisterReceiver(sessionResetReceiver) }
        resetLockSession()
        mainHandler.post { overlayController.dismiss() }
        scope.cancel()
        super.onDestroy()
    }

    private fun registerSessionResetReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(sessionResetReceiver, filter)
    }

    private fun resetLockSession() {
        AppLockSessionManager.clearAll()
        activeActivityLockPackage = null
        lastForegroundPackage = null
    }

    private fun observeLockedApps() {
        val repository = (application as DayKitApplication).container.appLockRepository
        scope.launch {
            repository.observeLockedApps()
                .catch { lockedPackages = emptySet() }
                .collect { apps ->
                    lockedPackages = apps.map { it.packageName }.toSet()
                    (application as DayKitApplication).container.lockedPackageCache
                        .putPackages(lockedPackages)
                }
        }
    }

    private fun observeBiometricSetting() {
        val repository = (application as DayKitApplication).container.secureSettingRepository
        scope.launch {
            repository.observeBoolean(SecureSettingRepository.KEY_BIOMETRIC_ENABLED)
                .catch { biometricEnabled = false }
                .collect { enabled ->
                    biometricEnabled = enabled == true
                }
        }
    }

    private fun monitorForegroundApps() {
        scope.launch {
            while (true) {
                val foregroundApp = runCatching { detector.currentForegroundApp() }.getOrNull()
                val foregroundPackage = foregroundApp?.packageName
                if (foregroundPackage != lastForegroundPackage) {
                    lastForegroundPackage = foregroundPackage
                    if (foregroundPackage == packageName && activeActivityLockPackage != null) {
                        // Keep the active challenge in front without clearing the target session.
                    } else if (foregroundPackage != null) {
                        if (foregroundPackage != packageName) {
                            activeActivityLockPackage = null
                        }
                        AppLockSessionManager.keepOnly(foregroundPackage)
                    } else {
                        // App went to background and no new app came to foreground
                        activeActivityLockPackage = null
                        AppLockSessionManager.clearAll()
                    }
                }

                val shouldLock = foregroundPackage != null &&
                    foregroundPackage != packageName &&
                    !SamsungSecureFolderSupport.shouldBypassLock(
                        packageName = foregroundPackage,
                        className = foregroundApp.className,
                        settingsPackage = settingsPackage,
                    ) &&
                    foregroundPackage in lockedPackages &&
                    !AppLockSessionManager.isAllowed(foregroundPackage)

                foregroundPackage?.let { currentPackage ->
                    if (shouldLock) {
                        launchLockScreen(currentPackage)
                    } else if (currentPackage !in lockedPackages) {
                        mainHandler.post { overlayController.dismiss() }
                    }
                }

                delay(POLL_INTERVAL_MILLIS)
            }
        }
    }

    private fun launchLockScreen(packageName: String) {
        if (biometricEnabled) {
            mainHandler.post { launchActivityLockScreen(packageName) }
            return
        }
        if (packageName != settingsPackage && Settings.canDrawOverlays(this)) {
            mainHandler.post {
                overlayController.show(
                    packageName = packageName,
                    appLabel = resolveLabel(packageName),
                )
            }
            return
        }
        mainHandler.post { launchActivityLockScreen(packageName) }
    }

    private fun launchActivityLockScreen(packageName: String) {
        if (activeActivityLockPackage == packageName) return
        activeActivityLockPackage = packageName
        val intent = LockActivity.intent(this, packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
    }

    private fun resolveLabel(packageName: String): String {
        return runCatching {
            val info = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(info).toString()
        }.getOrDefault(packageName)
    }

    private fun buildNotification(): Notification {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "App Lock protection",
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = "Keeps App Lock monitoring active"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_app_lock)
            .setContentTitle("App Lock is protecting your apps")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val POLL_INTERVAL_MILLIS = 750L
        private const val NOTIFICATION_CHANNEL_ID = "app_lock_monitor"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, AppMonitorService::class.java),
                )
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AppMonitorService::class.java))
        }
    }
}
