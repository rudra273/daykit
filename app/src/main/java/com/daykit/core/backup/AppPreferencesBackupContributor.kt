package com.daykit.core.backup

import android.content.Context
import com.daykit.core.data.AppPreferences
import com.daykit.core.data.SecureSettingRepository
import com.daykit.core.data.StartTab
import com.daykit.core.data.TimeFormatPreference
import com.daykit.core.data.WeekStart
import com.daykit.core.designsystem.HapticStore
import com.daykit.core.designsystem.ThemeMode
import com.daykit.core.designsystem.ThemeModeStore
import com.daykit.core.designsystem.background.CardStyle
import com.daykit.core.designsystem.background.CardStyleStore
import com.daykit.core.designsystem.background.PageBackgroundKind
import com.daykit.core.designsystem.background.PageBackgroundStore
import org.json.JSONArray
import org.json.JSONObject

/**
 * Allowlisted preferences only. Never export credentials, grants, account tokens or backup opt-ins,
 * and no security choices (auto-lock, clipboard, App Lock re-lock) — a restore must not relax them.
 *
 * Fields after the first four were added later and are optional on import, so
 * older backups still restore without a schemaVersion bump.
 */
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
        .put("currency", AppPreferences.currencyCode)
        .put("weekStart", AppPreferences.weekStart.name)
        .put("timeFormat", AppPreferences.timeFormat.name)
        .put("startTab", AppPreferences.startTab.name)
        .put("snoozeMinutes", AppPreferences.snoozeMinutes)
        .put("reminderFullScreen", AppPreferences.reminderFullScreen)
        .put("homeHiddenTools", JSONArray(AppPreferences.homeHiddenTools.toList()))
        .put("homeToolOrder", JSONArray(AppPreferences.homeToolOrder))
        .put("cardStyle", CardStyleStore.get(context).name)
        // The photo itself is not backed up, so a custom background restores as plain.
        .put("pageBackground", PageBackgroundStore.get(context).takeUnless { it == PageBackgroundKind.Custom }?.name ?: PageBackgroundKind.Plain.name)

    override suspend fun importJson(payload: JSONObject) {
        val theme = ThemeMode.valueOf(payload.getString("theme"))
        val haptics = payload.getBoolean("haptics")
        val expenses = payload.getBoolean("expenseWidget")
        val habits = payload.getBoolean("habitWidget")
        settings.putBoolean(SecureSettingRepository.KEY_WIDGET_EXPENSES, expenses)
        settings.putBoolean(SecureSettingRepository.KEY_WIDGET_HABITS, habits)
        ThemeModeStore.set(context, theme)
        HapticStore.set(context, haptics)

        payload.optString("currency").takeIf { it.isNotEmpty() }?.let { code ->
            if (runCatching { java.util.Currency.getInstance(code) }.isSuccess) AppPreferences.currencyCode = code
        }
        enumOrNull<WeekStart>(payload.optString("weekStart"))?.let { AppPreferences.weekStart = it }
        enumOrNull<TimeFormatPreference>(payload.optString("timeFormat"))?.let { AppPreferences.timeFormat = it }
        enumOrNull<StartTab>(payload.optString("startTab"))?.let { AppPreferences.startTab = it }
        if (payload.has("snoozeMinutes")) AppPreferences.snoozeMinutes = payload.getInt("snoozeMinutes")
        if (payload.has("reminderFullScreen")) AppPreferences.reminderFullScreen = payload.getBoolean("reminderFullScreen")
        enumOrNull<CardStyle>(payload.optString("cardStyle"))?.let { CardStyleStore.set(context, it) }
        enumOrNull<PageBackgroundKind>(payload.optString("pageBackground"))
            ?.takeUnless { it == PageBackgroundKind.Custom }
            ?.let { PageBackgroundStore.set(context, it) }
        payload.optJSONArray("homeHiddenTools")?.let { AppPreferences.homeHiddenTools = it.strings().toSet() }
        payload.optJSONArray("homeToolOrder")?.let { AppPreferences.homeToolOrder = it.strings() }
    }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String): E? =
        enumValues<E>().firstOrNull { it.name == name }

    private fun JSONArray.strings(): List<String> = (0 until length()).map(::getString)
}
