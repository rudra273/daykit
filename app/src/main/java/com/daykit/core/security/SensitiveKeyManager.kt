package com.daykit.core.security

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Manages the Master Sensitive Key (MSK) that protects the vault, key store, and
 * secure notes — the data we want undecryptable without the user's PIN, even on
 * a rooted or forensically-imaged device.
 *
 * Envelope design (so changing the PIN does NOT re-encrypt all data):
 *  - The MSK is a random 256-bit key that actually encrypts the sensitive data.
 *  - The MSK is stored only in wrapped form: `wrappedMsk = AES-GCM(WK, MSK)`,
 *    where the wrapping key `WK` is derived from the PIN via Argon2id.
 *  - Unlock derives WK from the entered PIN and unwraps the MSK into memory.
 *    The GCM auth tag means a wrong PIN fails to unwrap (it never yields a wrong
 *    key that silently corrupts data).
 *  - Changing the PIN re-derives WK and re-wraps the SAME MSK — the data on disk
 *    is untouched.
 *
 * The unwrapped MSK lives only in memory while unlocked and is wiped on lock.
 * Nothing here touches the Android Keystore: that is deliberate — a key wrapped
 * by the always-available Keystore would be recoverable by root without the PIN,
 * which is exactly the threat this closes.
 */
class SensitiveKeyManager(
    context: Context,
    private val passwordHasher: PasswordHasher,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var cachedKey: ByteArray? = null

    /**
     * Set while the app has intentionally launched another activity (a file
     * picker, the account chooser, a permission dialog) and expects to return to
     * the foreground. The lifecycle ON_STOP that such a launch triggers must NOT
     * wipe the key — otherwise the picker's result callback would run with the
     * vault locked and every import/export/backup would fail. Cleared on the
     * next foreground resume.
     */
    @Volatile
    var expectingActivityResult: Boolean = false

    /** True once a PIN has been set and the MSK has been created & wrapped. */
    fun isInitialized(): Boolean =
        prefs.contains(KEY_SALT) && prefs.contains(KEY_WRAPPED_MSK) && prefs.contains(KEY_WRAPPED_IV)

    /** True while the MSK is held in memory (user is unlocked). */
    fun isUnlocked(): Boolean = cachedKey != null

    /**
     * Creates the MSK and wraps it with a key derived from [pin]. Called once,
     * when the master PIN is first set. Leaves the MSK unlocked in memory so the
     * user is not immediately re-prompted after onboarding. Does not wipe [pin]
     * (the caller owns it).
     */
    fun initialize(pin: CharArray) {
        val salt = passwordHasher.newSalt()
        val msk = ByteArray(MSK_BYTES).also(secureRandom::nextBytes)
        val wrappingKey = passwordHasher.deriveKey(pin, salt, KDF_CONTEXT)
        try {
            val wrapped = aesGcmEncrypt(wrappingKey, msk)
            prefs.edit {
                putString(KEY_SALT, salt.b64())
                putString(KEY_WRAPPED_MSK, wrapped.ciphertext.b64())
                putString(KEY_WRAPPED_IV, wrapped.iv.b64())
            }
            cachedKey = msk.copyOf()
        } finally {
            wrappingKey.fill(0)
            msk.fill(0)
        }
    }

    /**
     * Derives the wrapping key from [pin], unwraps the MSK, and caches it in
     * memory. Returns true on success; false if the PIN is wrong (unwrap auth
     * fails) or the manager is not initialized. Does not wipe [pin].
     */
    fun unlock(pin: CharArray): Boolean {
        val salt = prefs.getString(KEY_SALT, null)?.b64d() ?: return false
        val ct = prefs.getString(KEY_WRAPPED_MSK, null)?.b64d() ?: return false
        val iv = prefs.getString(KEY_WRAPPED_IV, null)?.b64d() ?: return false
        val wrappingKey = passwordHasher.deriveKey(pin, salt, KDF_CONTEXT)
        return try {
            val msk = aesGcmDecrypt(wrappingKey, ct, iv)
            cachedKey = msk
            true
        } catch (error: Exception) {
            // AEADBadTagException (wrong PIN) or any other failure -> stay locked.
            false
        } finally {
            wrappingKey.fill(0)
        }
    }

    /**
     * Re-wraps the existing MSK under a new PIN. Call during a PIN change AFTER
     * the old PIN has been verified. Returns false if the old PIN cannot unwrap
     * the MSK (in which case nothing is changed). The MSK — and therefore all
     * encrypted data — is preserved.
     */
    fun rewrap(oldPin: CharArray, newPin: CharArray): Boolean {
        val salt = prefs.getString(KEY_SALT, null)?.b64d() ?: return false
        val ct = prefs.getString(KEY_WRAPPED_MSK, null)?.b64d() ?: return false
        val iv = prefs.getString(KEY_WRAPPED_IV, null)?.b64d() ?: return false
        val oldKey = passwordHasher.deriveKey(oldPin, salt, KDF_CONTEXT)
        val msk = try {
            aesGcmDecrypt(oldKey, ct, iv)
        } catch (error: Exception) {
            return false
        } finally {
            oldKey.fill(0)
        }
        val newSalt = passwordHasher.newSalt()
        val newKey = passwordHasher.deriveKey(newPin, newSalt, KDF_CONTEXT)
        try {
            val wrapped = aesGcmEncrypt(newKey, msk)
            prefs.edit {
                putString(KEY_SALT, newSalt.b64())
                putString(KEY_WRAPPED_MSK, wrapped.ciphertext.b64())
                putString(KEY_WRAPPED_IV, wrapped.iv.b64())
            }
            cachedKey = msk.copyOf()
            return true
        } finally {
            newKey.fill(0)
            msk.fill(0)
        }
    }

    /**
     * A defensive copy of the in-memory MSK, or null if locked. The caller owns
     * the returned array and must zero it after use. Returning a copy means a
     * concurrent [lock] (which zeroes the cached array) cannot corrupt a key that
     * an in-flight cipher operation is still reading.
     */
    fun key(): ByteArray? = cachedKey?.copyOf()

    /**
     * A defensive copy of the in-memory MSK; throws [SensitiveDataLockedException]
     * if locked. The caller owns the returned array and must zero it after use.
     */
    fun requireKey(): ByteArray =
        cachedKey?.copyOf() ?: throw SensitiveDataLockedException()

    /** Wipes the MSK from memory. Called on lock / background. */
    fun lock() {
        cachedKey?.fill(0)
        cachedKey = null
    }

    private data class Wrapped(val ciphertext: ByteArray, val iv: ByteArray)

    private fun aesGcmEncrypt(key: ByteArray, plaintext: ByteArray): Wrapped {
        val iv = ByteArray(IV_BYTES).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, KEY_ALGORITHM), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(WRAP_AAD)
        return Wrapped(ciphertext = cipher.doFinal(plaintext), iv = iv)
    }

    private fun aesGcmDecrypt(key: ByteArray, ciphertext: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, KEY_ALGORITHM), GCMParameterSpec(GCM_TAG_BITS, iv))
        cipher.updateAAD(WRAP_AAD)
        return cipher.doFinal(ciphertext)
    }

    private fun ByteArray.b64(): String = Base64.encodeToString(this, Base64.NO_WRAP)
    private fun String.b64d(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val PREFS_NAME = "daykit_sensitive_key"
        const val KEY_SALT = "msk_salt"
        const val KEY_WRAPPED_MSK = "wrapped_msk"
        const val KEY_WRAPPED_IV = "wrapped_msk_iv"
        const val KDF_CONTEXT = "daykit.sensitive.msk.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_ALGORITHM = "AES"
        const val GCM_TAG_BITS = 128
        const val IV_BYTES = 12
        const val MSK_BYTES = 32
        val WRAP_AAD = "daykit.sensitive.msk.wrap".toByteArray()
        val secureRandom = SecureRandom()
    }
}

/** Thrown when sensitive data is accessed while the vault is locked (no PIN this session). */
class SensitiveDataLockedException :
    IllegalStateException("Sensitive data is locked. Unlock with your PIN to continue.")
