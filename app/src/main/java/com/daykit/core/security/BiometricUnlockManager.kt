package com.daykit.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Maintains an optional, biometric-gated wrapping of DayKit's master sensitive
 * key. The PIN-wrapped copy remains the recovery path and is never replaced.
 *
 * The Android Keystore key requires a strong biometric for every decrypt. Its
 * bytes never leave Keystore, and enrolling a new biometric invalidates it.
 */
class BiometricUnlockManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnrolled(): Boolean =
        prefs.contains(KEY_WRAPPED_MSK) &&
            prefs.contains(KEY_WRAPPED_IV) &&
            runCatching { keyStore().containsAlias(KEY_ALIAS) }.getOrDefault(false)

    /** Creates the authenticated operation used when enabling biometric unlock. */
    fun enrollmentCipher(): Cipher {
        // Re-enabling starts with a fresh key so stale enrollment state cannot
        // authorize the newly wrapped master key.
        clear()
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
                .setInvalidatedByBiometricEnrollment(true)
                .build(),
        )
        val cipher = newCipher()
        cipher.init(Cipher.ENCRYPT_MODE, generator.generateKey())
        return cipher
    }

    /** Stores a second, biometric-protected wrapping of the current MSK. */
    fun completeEnrollment(cipher: Cipher, sensitiveKeyManager: SensitiveKeyManager) {
        val msk = sensitiveKeyManager.requireKey()
        try {
            val ciphertext = cipher.doFinal(msk)
            prefs.edit(commit = true) {
                putString(KEY_WRAPPED_MSK, ciphertext.encodeBase64())
                putString(KEY_WRAPPED_IV, cipher.iv.encodeBase64())
            }
        } catch (error: Exception) {
            clear()
            throw error
        } finally {
            msk.fill(0)
        }
    }

    /** Returns a decrypt operation, or null when enrollment is missing/invalidated. */
    fun unlockCipher(): Cipher? {
        val ciphertext = prefs.getString(KEY_WRAPPED_MSK, null) ?: return null
        val iv = prefs.getString(KEY_WRAPPED_IV, null)
            ?.let { runCatching { it.decodeBase64() }.getOrNull() }
            ?: return null
        if (ciphertext.isEmpty()) return null
        return try {
            val key = keyStore().getKey(KEY_ALIAS, null) as? SecretKey ?: return null
            newCipher().apply {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            }
        } catch (_: KeyPermanentlyInvalidatedException) {
            clear()
            null
        } catch (_: Exception) {
            clear()
            null
        } finally {
            iv.fill(0)
        }
    }

    /** Completes the authenticated decrypt and installs the MSK only in memory. */
    fun completeUnlock(
        cipher: Cipher,
        sensitiveKeyManager: SensitiveKeyManager,
        expectedGeneration: Long,
    ): Boolean {
        val ciphertext = prefs.getString(KEY_WRAPPED_MSK, null)
            ?.let { runCatching { it.decodeBase64() }.getOrNull() }
            ?: return false
        return try {
            val msk = cipher.doFinal(ciphertext)
            try {
                sensitiveKeyManager.unlockWithMasterKey(msk, expectedGeneration)
            } finally {
                msk.fill(0)
            }
        } catch (_: Exception) {
            false
        } finally {
            ciphertext.fill(0)
        }
    }

    fun clear() {
        prefs.edit(commit = true) { clear() }
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private fun newCipher(): Cipher = Cipher.getInstance(TRANSFORMATION)
    private fun ByteArray.encodeBase64(): String =
        android.util.Base64.encodeToString(this, android.util.Base64.NO_WRAP)
    private fun String.decodeBase64(): ByteArray =
        android.util.Base64.decode(this, android.util.Base64.NO_WRAP)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "daykit_biometric_msk_v1"
        const val PREFS_NAME = "daykit_biometric_unlock"
        const val KEY_WRAPPED_MSK = "wrapped_msk"
        const val KEY_WRAPPED_IV = "wrapped_msk_iv"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
