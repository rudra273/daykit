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
import android.os.PowerManager
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
    // Package -> epoch-millis at which its strict timed lock ("focus block")
    // expires. While now < expiry the app is blocked regardless of PIN grants.
    @Volatile
    private var focusBlockedPackages = emptyMap<String, Long>()
    private var biometricEnabled = false
    private var lastForegroundPackage: String? = null
    private var activeActivityLockPackage: String? = null
    // Adaptive polling: we only need the fast 250ms cadence for a short burst
    // right after a foreground change (to cover a locked app before the user
    // sees it). Once the foreground app is stable we back off to a ~1s cadence,
    // which cuts steady-state UsageStats queries ~4x with no switch latency.
    private var fastPollUntilMillis = 0L
    // True when the current activity lock is a focus-block countdown (vs a PIN
    // challenge). Lets us re-arm a PIN gate once a block expires on the same app.
    private var activeActivityLockIsFocus = false

    @Volatile
    private var screenInteractive = true
    private val sessionResetReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenInteractive = false
                    resetLockSession()
                }

                Intent.ACTION_SCREEN_ON -> screenInteractive = true

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
            isFocusBlocked = { packageName ->
                focusBlockedPackages[packageName]?.let { it > System.currentTimeMillis() } == true
            },
        )
        // Seed synchronously from the prefs caches so there is no window where the
        // monitor loop runs with an empty locked set or stale biometric flag.
        lockedPackages = container.appLockRepository.getLockedPackages()
        focusBlockedPackages = container.appLockRepository.activeFocusPackages()
            .mapNotNull { pkg ->
                container.appLockRepository.focusBlockUntil(pkg)?.let { pkg to it }
            }
            .toMap()
        biometricEnabled = container.settingFlagCache
            .get(SecureSettingRepository.KEY_BIOMETRIC_ENABLED) == true
        screenInteractive = getSystemService(PowerManager::class.java)?.isInteractive != false
        registerSessionResetReceiver()
        observeLockedApps()
        observeFocusBlocks()
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
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(sessionResetReceiver, filter)
    }

    private fun resetLockSession() {
        AppLockSessionManager.clearAll()
        activeActivityLockPackage = null
        activeActivityLockIsFocus = false
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

    private fun observeFocusBlocks() {
        val repository = (application as DayKitApplication).container.appLockRepository
        scope.launch {
            repository.observeFocusBlocks()
                .catch { focusBlockedPackages = emptyMap() }
                .collect { blocks ->
                    focusBlockedPackages = blocks.associate { it.packageName to it.lockUntilMillis }
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
                // No app can come to the foreground while the screen is off, so skip
                // the usage-stats query entirely; the saved budget funds the faster
                // interactive poll below.
                if (!screenInteractive) {
                    delay(SCREEN_OFF_POLL_INTERVAL_MILLIS)
                    continue
                }

                val foregroundApp = runCatching { detector.currentResumedApp() }.getOrNull()
                val foregroundPackage = foregroundApp?.packageName

                // A null reading means usage-stats has no recent foreground event to
                // report — NOT that the current app went to the background. Apps that
                // sit idle (e.g. watching a reel) stop emitting events, so treating
                // null as "backgrounded" and clearing the session would re-lock the
                // app on its next in-app navigation. Skip the tick and keep the last
                // known foreground package and its unlock grant intact instead.
                if (foregroundPackage == null) {
                    // A null can also occur in the brief window right after a switch
                    // before the resume event lands, so honor an active burst here
                    // too rather than always idling (which could delay a lock).
                    val fastPoll = System.currentTimeMillis() < fastPollUntilMillis
                    delay(if (fastPoll) FAST_POLL_INTERVAL_MILLIS else IDLE_POLL_INTERVAL_MILLIS)
                    continue
                }

                if (foregroundPackage != lastForegroundPackage) {
                    lastForegroundPackage = foregroundPackage
                    // A switch just happened — poll fast for a short burst so the
                    // lock covers the app immediately, then let the loop settle
                    // back to the idle cadence below.
                    fastPollUntilMillis = System.currentTimeMillis() + FAST_POLL_DURATION_MILLIS
                    if (foregroundPackage == packageName && activeActivityLockPackage != null) {
                        // Keep the active challenge in front without clearing the target session.
                    } else {
                        if (foregroundPackage != packageName) {
                            activeActivityLockPackage = null
                            activeActivityLockIsFocus = false
                        }
                        // Evicting every other package's grant is safe here: a real app
                        // switch always surfaces the new package as a resume event.
                        AppLockSessionManager.keepOnly(foregroundPackage)
                    }
                }

                // Strict timed lock: while now < expiry the app is blocked no
                // matter what — a PIN grant must NOT satisfy it. This is OR'd in
                // ahead of the normal grant check and applies even to apps that
                // are not in the PIN-locked set.
                val focusBlockUntil = focusBlockedPackages[foregroundPackage]
                    ?.takeIf { it > System.currentTimeMillis() }
                val focusBlocked = focusBlockUntil != null

                // When a focus block expires, its countdown LockActivity finishes
                // itself. Clear the activity dedup so that if the app is ALSO
                // PIN-locked, the PIN challenge can be re-shown for it (otherwise
                // launchActivityLockScreen would early-return on the stale package).
                // Guarded by activeActivityLockIsFocus so we never reset a live PIN
                // challenge's dedup and relaunch it in a loop.
                if (!focusBlocked &&
                    activeActivityLockIsFocus &&
                    activeActivityLockPackage == foregroundPackage
                ) {
                    activeActivityLockPackage = null
                    activeActivityLockIsFocus = false
                    // Prune the now-expired block from the store/flow so the manage
                    // screen and this service's map drop it instead of holding a
                    // stale entry indefinitely (the flow only re-emits on demand).
                    (application as DayKitApplication).container.appLockRepository.refreshFocusBlocks()
                }

                val notBypassed = foregroundPackage != packageName &&
                    !SamsungSecureFolderSupport.shouldBypassLock(
                        packageName = foregroundPackage,
                        className = foregroundApp.className,
                        settingsPackage = settingsPackage,
                    )

                val shouldLock = notBypassed && (
                    focusBlocked ||
                        (
                            foregroundPackage in lockedPackages &&
                                !AppLockSessionManager.isAllowed(foregroundPackage)
                            )
                    )

                if (shouldLock) {
                    launchLockScreen(foregroundPackage, focusBlockUntil)
                } else if (foregroundPackage !in lockedPackages && !focusBlocked) {
                    mainHandler.post { overlayController.dismiss() }
                }

                // Stay on the fast cadence during the post-switch burst, and while
                // a lock is still pending so the challenge lands promptly; otherwise
                // idle-poll to save battery.
                val fastPoll = shouldLock || System.currentTimeMillis() < fastPollUntilMillis
                delay(if (fastPoll) FAST_POLL_INTERVAL_MILLIS else IDLE_POLL_INTERVAL_MILLIS)
            }
        }
    }

    private fun launchLockScreen(packageName: String, focusBlockUntil: Long? = null) {
        // A focus block shows a countdown, not a PIN/biometric challenge, so it
        // must go through the full-screen activity — the overlay is a PIN pad and
        // biometric cannot end a timer early.
        if (focusBlockUntil != null) {
            mainHandler.post { launchActivityLockScreen(packageName, focusBlockUntil) }
            return
        }
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

    private fun launchActivityLockScreen(packageName: String, focusBlockUntil: Long? = null) {
        if (activeActivityLockPackage == packageName) return
        activeActivityLockPackage = packageName
        activeActivityLockIsFocus = focusBlockUntil != null
        val intent = LockActivity.intent(this, packageName, focusBlockUntil)
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
        // Fast cadence used only in a burst right after a foreground change (and
        // while a lock is pending); idle cadence used once the foreground app is
        // stable. Backing off from a flat 250ms to 1s at rest is the main battery
        // win, since each tick is a UsageStats query (binder IPC).
        private const val FAST_POLL_INTERVAL_MILLIS = 250L
        private const val IDLE_POLL_INTERVAL_MILLIS = 1_000L
        private const val FAST_POLL_DURATION_MILLIS = 2_000L
        private const val SCREEN_OFF_POLL_INTERVAL_MILLIS = 1_500L
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
