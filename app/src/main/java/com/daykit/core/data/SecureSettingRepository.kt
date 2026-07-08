package com.daykit.core.data

import com.daykit.core.security.CipherPayload
import com.daykit.core.security.SensitiveValueCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SecureSettingRepository(
    private val dao: SecureSettingDao,
    private val cipher: SensitiveValueCipher,
) {
    suspend fun getBoolean(key: String): Boolean? {
        return dao.get(key)?.decrypt()?.toBooleanStrictOrNull()
    }

    fun observeBoolean(key: String): Flow<Boolean?> {
        return dao.observe(key).map { it?.decrypt()?.toBooleanStrictOrNull() }
    }

    suspend fun getString(key: String): String? {
        return dao.get(key)?.decrypt()
    }

    fun observeString(key: String): Flow<String?> {
        return dao.observe(key).map { it?.decrypt() }
    }

    suspend fun putBoolean(key: String, value: Boolean) {
        putString(key, value.toString())
    }

    suspend fun putString(key: String, value: String) {
        val payload = cipher.encryptString(value.toString(), aad = key)
        dao.upsert(
            SecureSettingEntity(
                key = key,
                valueCiphertext = payload.ciphertext,
                valueIv = payload.iv,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun delete(key: String) {
        dao.delete(key)
    }

    private fun SecureSettingEntity.decrypt(): String {
        return cipher.decryptString(
            payload = CipherPayload(valueCiphertext, valueIv),
            aad = key,
        )
    }

    companion object {
        const val KEY_BIOMETRIC_ENABLED = "app_lock.biometric_enabled"
        const val KEY_BACKUP_PASSWORD = "backup.password"
        const val KEY_DRIVE_BACKUP_SCHEDULE = "backup.drive.schedule"
        const val KEY_DRIVE_LAST_BACKUP_AT = "backup.drive.last_backup_at"
        const val KEY_DRIVE_LAST_BACKUP_SIZE_BYTES = "backup.drive.last_backup_size_bytes"
        const val KEY_DRIVE_LAST_UPLOAD_AT = "backup.drive.last_upload_at"
        const val KEY_DRIVE_LAST_ERROR = "backup.drive.last_error"
        const val KEY_DRIVE_NEEDS_AUTHORIZATION = "backup.drive.needs_authorization"
        const val KEY_DRIVE_ACCOUNT_EMAIL = "backup.drive.account_email"
        const val KEY_BACKUP_INCLUDE_EXPENSES = "backup.include.expenses"
        const val KEY_BACKUP_INCLUDE_HABITS = "backup.include.habits"
        const val KEY_SCREENSHOT_PROTECTION = "privacy.screenshot_protection"
        const val KEY_TOOL_LOCK_APP_LOCK = "utility_lock.app_lock"
        const val KEY_TOOL_LOCK_KEY_STORE = "utility_lock.key_store"
        const val KEY_TOOL_LOCK_NOTES = "utility_lock.notes"
        const val KEY_WIDGET_EXPENSES = "dashboard_widget.expenses"
        const val KEY_WIDGET_HABITS = "dashboard_widget.habits"
    }
}
