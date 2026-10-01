package com.daykit

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import com.daykit.core.data.SecureSettingRepository
import com.daykit.core.session.AppLockSessionManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class DayKitApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AppLockSessionManager.clearAll()
        registerScreenOffLock()
        warmUp()
        applicationScope.launch {
            runCatching {
                container.reminderRepository.observeReminders().collect {
                    com.daykit.feature.widget.updateReminderWidgets(this@DayKitApplication)
                }
            }.onFailure { Log.w(TAG, "Could not refresh reminder widgets", it) }
        }
        applicationScope.launch {
            runCatching {
                container.habitRepository.observeDashboard().collect {
                    com.daykit.feature.widget.updateHabitWidgets(this@DayKitApplication)
                }
            }.onFailure { Log.w(TAG, "Could not refresh habit widgets", it) }
        }
    }

    /**
     * The lock grace window is for switching apps, not for a phone put down:
     * turning the screen off always wipes the MSK immediately. SCREEN_OFF is
     * only delivered to runtime-registered receivers, so it lives here for the
     * whole process lifetime.
     */
    private fun registerScreenOffLock() {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_SCREEN_OFF) {
                    container.sensitiveKeyManager.lock()
                }
            }
        }
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(Intent.ACTION_SCREEN_OFF),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /**
     * Opens the SQLCipher database and Keystore key ahead of time so the first
     * screen never blocks on native library load + passphrase decrypt + key
     * derivation, and pre-loads the launchable-app list used by App Lock.
     */
    private fun warmUp() {
        applicationScope.launch {
            // Don't swallow a storage failure here: AppContainer records it in
            // `storageFailure` so the UI can show a recovery screen. Silently
            // discarding it would just move the crash to the first composable that
            // touches the DB, with a stack trace pointing at the wrong place.
            runCatching {
                container.secureSettingRepository.getBoolean(SecureSettingRepository.KEY_BIOMETRIC_ENABLED)
                // Refreshes the plain-prefs mirror MainActivity reads on ON_STOP.
                container.secureSettingRepository.getInt(SecureSettingRepository.KEY_LOCK_GRACE_SECONDS)
                container.reminderRepository.restoreAlarms { reminder ->
                    com.daykit.feature.reminder.notification.ReminderNotifier.show(
                        this@DayKitApplication, reminder.reminderId, reminder.title, reminder.scheduledAtMillis, alert = false)
                }
            }.onFailure { error ->
                Log.w(TAG, "Warm-up failed to open secure storage", error)
            }
        }
        applicationScope.launch {
            runCatching { container.installedAppProvider.loadLaunchableApps() }
        }
    }

    private companion object {
        const val TAG = "DayKitApplication"
    }
}
