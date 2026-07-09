package com.daykit

import android.app.Application
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
            runCatching {
                container.secureSettingRepository.getBoolean(SecureSettingRepository.KEY_BIOMETRIC_ENABLED)
            }
        }
        applicationScope.launch {
            runCatching { container.installedAppProvider.loadLaunchableApps() }
        }
    }
}
