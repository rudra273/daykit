package com.rudra.daykit

import android.app.Application
import com.rudra.daykit.core.session.AppLockSessionManager

class DayKitApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        AppLockSessionManager.clearAll()
    }
}
