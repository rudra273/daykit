package com.daykit.core.backup

import com.daykit.core.data.SecureSettingDao
import com.daykit.core.data.SecureSettingEntity
import com.daykit.core.data.SecureSettingRepository
import com.daykit.core.security.CipherPayload
import com.daykit.core.security.ValueCipher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/**
 * The backup password, encrypted with the PIN-derived MSK ([sessionCipher]).
 *
 * Backups hold Key Store and Secure Notes in plaintext under this password, so it
 * is as sensitive as the MSK itself. It used to live in [SecureSettingRepository]
 * under the always-available Keystore key, which let anyone with root on the
 * device read it and open a backup without ever knowing the PIN.
 *
 * Reading needs the vault unlocked; that costs nothing, because every backup
 * already needs the MSK to export the sensitive tools. The legacy copy is moved
 * over and deleted the first time it is read while unlocked.
 */
class BackupPasswordStore(
    private val dao: SecureSettingDao,
    private val sessionCipher: ValueCipher,
    private val legacySettings: SecureSettingRepository,
) {
    /** Whether a password exists. Needs no key, so it works while locked. */
    fun observeIsSet(): Flow<Boolean> =
        combine(
            dao.observe(SecureSettingRepository.KEY_BACKUP_PASSWORD_SESSION),
            dao.observe(SecureSettingRepository.KEY_BACKUP_PASSWORD),
        ) { current, legacy -> current != null || legacy != null }

    /** The password, or null if none is set. Throws SensitiveDataLockedException while locked. */
    suspend fun get(): String? = withContext(Dispatchers.Default) {
        dao.get(KEY)?.let { entity ->
            return@withContext sessionCipher.decryptString(CipherPayload(entity.valueCiphertext, entity.valueIv), KEY)
        }
        val legacy = legacySettings.getString(SecureSettingRepository.KEY_BACKUP_PASSWORD)
            ?: return@withContext null
        put(legacy)
        legacy
    }

    /** Constant-time check of [candidate] against the stored password. */
    suspend fun matches(candidate: String): Boolean {
        val stored = get() ?: return false
        return MessageDigest.isEqual(stored.toByteArray(), candidate.toByteArray())
    }

    suspend fun set(password: String) = withContext(Dispatchers.Default) { put(password) }

    suspend fun clear() {
        dao.delete(KEY)
        dao.delete(SecureSettingRepository.KEY_BACKUP_PASSWORD)
    }

    /** Moves a legacy Keystore-only copy under the MSK. Call only while unlocked. */
    suspend fun migrateLegacy() {
        if (dao.get(SecureSettingRepository.KEY_BACKUP_PASSWORD) != null) get()
    }

    private suspend fun put(password: String) {
        val payload = sessionCipher.encryptString(password, aad = KEY)
        dao.upsert(
            SecureSettingEntity(
                key = KEY,
                valueCiphertext = payload.ciphertext,
                valueIv = payload.iv,
                updatedAtMillis = System.currentTimeMillis(),
            ),
        )
        // Only after the MSK copy is committed, so a failure never loses the password.
        dao.delete(SecureSettingRepository.KEY_BACKUP_PASSWORD)
    }

    private companion object {
        const val KEY = SecureSettingRepository.KEY_BACKUP_PASSWORD_SESSION
    }
}
