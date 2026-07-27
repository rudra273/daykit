package com.daykit

import android.app.Application
import android.util.Log
import com.daykit.core.data.SecureSettingRepository
import com.daykit.core.session.AppLockSessionManager
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
        warmUp()
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
