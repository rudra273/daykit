package com.daykit.core.backup

import android.content.Context
import com.daykit.core.data.SecureSettingRepository
import com.daykit.core.designsystem.HapticStore
import com.daykit.core.designsystem.ThemeMode
import com.daykit.core.designsystem.ThemeModeStore
import org.json.JSONObject

/** Allowlisted preferences only. Never export credentials, grants, account tokens or backup opt-ins. */
class AppPreferencesBackupContributor(
    private val context: Context,
    private val settings: SecureSettingRepository,
) : BackupContributor {
    override val toolKey = BackupToolKeys.APP_PREFERENCES
    override val schemaVersion = 1
    override suspend fun exportJson() = JSONObject()
        .put("theme", ThemeModeStore.get(context).name)
        .put("haptics", HapticStore.get(context))
        .put("expenseWidget", settings.getBoolean(SecureSettingRepository.KEY_WIDGET_EXPENSES) ?: false)
        .put("habitWidget", settings.getBoolean(SecureSettingRepository.KEY_WIDGET_HABITS) ?: false)

    override suspend fun importJson(payload: JSONObject) {
        val theme = ThemeMode.valueOf(payload.getString("theme"))
        val haptics = payload.getBoolean("haptics")
        val expenses = payload.getBoolean("expenseWidget")
        val habits = payload.getBoolean("habitWidget")
        settings.putBoolean(SecureSettingRepository.KEY_WIDGET_EXPENSES, expenses)
        settings.putBoolean(SecureSettingRepository.KEY_WIDGET_HABITS, habits)
        ThemeModeStore.set(context, theme)
        HapticStore.set(context, haptics)
    }
}
