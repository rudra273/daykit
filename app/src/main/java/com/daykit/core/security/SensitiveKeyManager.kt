package com.daykit.core.security

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
 *    where the wrapping key `WK` is derived from the PIN via Argon2id and then
 *    bound to this device by [PinHardwareBinding] (wrap version 2).
 *  - Unlock derives WK from the entered PIN and unwraps the MSK into memory.
 *    The GCM auth tag means a wrong PIN fails to unwrap (it never yields a wrong
 *    key that silently corrupts data).
 *  - Changing the PIN re-derives WK and re-wraps the SAME MSK — the data on disk
 *    is untouched.
 *
 * Wrap version 1 (Argon2id only) is still read, and upgraded to version 2 on the
 * first successful unlock, since that is the only moment the PIN is available.
 *
 * The unwrapped MSK lives only in memory while unlocked and is wiped on lock.
 * The primary wrapped copy is PIN-wrapped; the hardware binding only hardens the
 * PIN derivation. When the user opts in, BiometricUnlockManager stores a second
 * copy behind an auth-per-use Keystore key; it can only be opened after a fresh
 * strong-biometric check. The PIN copy remains available as the recovery path.
 */
class SensitiveKeyManager(
    context: Context,
    private val passwordHasher: PasswordHasher,
    private val hardwareBinding: PinHardwareBinding,
    // Overridable only so instrumented tests never touch the real key material.
    prefsName: String = PREFS_NAME,
) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private val keyCache = SessionKeyCache()

    /** Set while DayKit expects a result from an external activity. */
    @Volatile
    var expectingActivityResult: Boolean = false

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val backgroundDeadline = BackgroundLockDeadline()
    private val lockAfterGrace = Runnable { lock() }

    private val pendingActionLock = Any()
    private val pendingUnlockActions = mutableListOf<() -> Unit>()

    /** True once a PIN has been set and the MSK has been created & wrapped. */
    fun isInitialized(): Boolean =
        prefs.contains(KEY_SALT) && prefs.contains(KEY_WRAPPED_MSK) && prefs.contains(KEY_WRAPPED_IV)

    /** True while the MSK is held in memory (user is unlocked). */
    fun isUnlocked(): Boolean = keyCache.isUnlocked()

    /**
     * Creates the MSK and wraps it with a key derived from [pin]. Called once,
     * when the master PIN is first set. Leaves the MSK unlocked in memory so the
     * user is not immediately re-prompted after onboarding. Does not wipe [pin]
     * (the caller owns it).
     */
    fun initialize(pin: CharArray) {
        val generation = keyCache.generation()
        val msk = ByteArray(MSK_BYTES).also(secureRandom::nextBytes)
        try {
            persistWrapped(pin, msk)
            keyCache.install(msk, generation)
        } finally {
            msk.fill(0)
        }
    }

    /**
     * Derives the wrapping key from [pin], unwraps the MSK, and caches it in
     * memory. Returns true on success; false if the PIN is wrong (unwrap auth
     * fails) or the manager is not initialized. A version-1 wrap is upgraded to
     * the hardware-bound version here. Does not wipe [pin].
     */
    fun unlock(pin: CharArray): Boolean {
        val generation = keyCache.generation()
        val msk = unwrap(pin) ?: return false
        try {
            if (wrapVersion() < WRAP_VERSION_HARDWARE) {
                // Best effort: a failed upgrade leaves the working v1 wrap in place.
                runCatching { persistWrapped(pin, msk) }
            }
            return keyCache.install(msk, generation)
        } finally {
            msk.fill(0)
        }
    }

    /** Token that prevents a biometric operation from undoing a later lifecycle lock. */
    fun unlockGeneration(): Long = keyCache.generation()

    /** Installs an MSK recovered by a successful biometric CryptoObject operation. */
    fun unlockWithMasterKey(msk: ByteArray, expectedGeneration: Long): Boolean {
        if (msk.size != MSK_BYTES) return false
        return keyCache.install(msk, expectedGeneration)
    }

    /** Defers activity-result work until a fresh unlock has restored the MSK. */
    fun runWhenUnlocked(action: () -> Unit) {
        val runNow = synchronized(pendingActionLock) {
            if (keyCache.isUnlocked()) {
                true
            } else {
                pendingUnlockActions += action
                false
            }
        }
        if (runNow) action()
    }

    /** Runs result work only after successful PIN or biometric authentication. */
    fun resumePendingUnlockActions() {
        val actions = synchronized(pendingActionLock) {
            pendingUnlockActions.toList().also { pendingUnlockActions.clear() }
        }
        actions.forEach { action -> runCatching(action) }
    }

    fun discardPendingUnlockActions() {
        synchronized(pendingActionLock) { pendingUnlockActions.clear() }
    }

    /**
     * Re-wraps the existing MSK under a new PIN. Call during a PIN change AFTER
     * the old PIN has been verified. Returns false if the old PIN cannot unwrap
     * the MSK (in which case nothing is changed). The MSK — and therefore all
     * encrypted data — is preserved.
     */
    fun rewrap(oldPin: CharArray, newPin: CharArray): Boolean {
        val generation = keyCache.generation()
        val msk = unwrap(oldPin) ?: return false
        try {
            persistWrapped(newPin, msk)
            keyCache.install(msk, generation)
            return true
        } finally {
            msk.fill(0)
        }
    }

    /**
     * A defensive copy of the in-memory MSK, or null if locked. The caller owns
     * the returned array and must zero it after use. Returning a copy means a
     * concurrent [lock] (which zeroes the cached array) cannot corrupt a key that
     * an in-flight cipher operation is still reading.
     */
    fun key(): ByteArray? = keyCache.copy()

    /**
     * A defensive copy of the in-memory MSK; throws [SensitiveDataLockedException]
     * if locked. The caller owns the returned array and must zero it after use.
     */
    fun requireKey(): ByteArray =
        keyCache.copy() ?: throw SensitiveDataLockedException()

    /**
     * Called when DayKit leaves the foreground. Keeps the MSK for [graceSeconds]
     * (see [LockGracePeriod]) and then wipes it; 0 wipes immediately. The delayed
     * wipe may not run while the process is frozen, so [onForegrounded] also
     * checks the deadline against the elapsed-realtime clock.
     */
    fun onBackgrounded(graceSeconds: Int) {
        if (graceSeconds <= 0 || !isUnlocked()) {
            lock()
            return
        }
        val graceMillis = graceSeconds * 1000L
        mainHandler.removeCallbacks(lockAfterGrace)
        backgroundDeadline.start(SystemClock.elapsedRealtime(), graceMillis)
        mainHandler.postDelayed(lockAfterGrace, graceMillis)
    }

    /**
     * Called when DayKit returns to the foreground, before any activity result is
     * delivered. Wipes the MSK if the grace window elapsed while backgrounded.
     */
    fun onForegrounded() {
        mainHandler.removeCallbacks(lockAfterGrace)
        if (backgroundDeadline.expiredOnReturn(SystemClock.elapsedRealtime())) lock()
    }

    /** Wipes the MSK from memory immediately, cancelling any grace window. */
    fun lock() {
        mainHandler.removeCallbacks(lockAfterGrace)
        backgroundDeadline.clear()
        keyCache.clear()
    }

    /**
     * Destroys the wrapped MSK on disk as well as the in-memory copy. Everything
     * encrypted with it (vault, key store, secure notes) becomes unrecoverable, so
     * this is only for an explicit full reset.
     */
    fun clearAll() {
        lock()
        discardPendingUnlockActions()
        prefs.edit { clear() }
    }

    /** Unwraps the stored MSK with [pin], or null when the PIN is wrong. Caller zeroes it. */
    private fun unwrap(pin: CharArray): ByteArray? {
        val salt = prefs.getString(KEY_SALT, null)?.b64d() ?: return null
        val ct = prefs.getString(KEY_WRAPPED_MSK, null)?.b64d() ?: return null
        val iv = prefs.getString(KEY_WRAPPED_IV, null)?.b64d() ?: return null
        val wrappingKey = runCatching {
            deriveWrappingKey(pin, salt, wrapVersion(), createBindingKey = false)
        }.getOrNull() ?: return null
        return try {
            aesGcmDecrypt(wrappingKey, ct, iv)
        } catch (error: Exception) {
            // AEADBadTagException (wrong PIN) or any other failure -> stay locked.
            null
        } finally {
            wrappingKey.fill(0)
        }
    }

    /**
     * Wraps [msk] under [pin] with a fresh salt at the hardware-bound version,
     * falling back to Argon2id-only if this device cannot make the binding key, so
     * setup never fails outright. One commit replaces salt, wrap and version together.
     */
    private fun persistWrapped(pin: CharArray, msk: ByteArray) {
        val salt = passwordHasher.newSalt()
        val (version, wrappingKey) = runCatching {
            WRAP_VERSION_HARDWARE to deriveWrappingKey(pin, salt, WRAP_VERSION_HARDWARE, createBindingKey = true)
        }.getOrElse {
            WRAP_VERSION_ARGON_ONLY to deriveWrappingKey(pin, salt, WRAP_VERSION_ARGON_ONLY, createBindingKey = false)
        }
        try {
            val wrapped = aesGcmEncrypt(wrappingKey, msk)
            prefs.edit(commit = true) {
                putString(KEY_SALT, salt.b64())
                putString(KEY_WRAPPED_MSK, wrapped.ciphertext.b64())
                putString(KEY_WRAPPED_IV, wrapped.iv.b64())
                putInt(KEY_WRAP_VERSION, version)
            }
        } finally {
            wrappingKey.fill(0)
        }
    }

    private fun deriveWrappingKey(
        pin: CharArray,
        salt: ByteArray,
        version: Int,
        createBindingKey: Boolean,
    ): ByteArray {
        val argonKey = passwordHasher.deriveKey(pin, salt, KDF_CONTEXT)
        if (version < WRAP_VERSION_HARDWARE) return argonKey
        return try {
            hardwareBinding.bind(argonKey, createIfMissing = createBindingKey)
        } finally {
            argonKey.fill(0)
        }
    }

    private fun wrapVersion(): Int = prefs.getInt(KEY_WRAP_VERSION, WRAP_VERSION_ARGON_ONLY)

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
        const val KEY_WRAP_VERSION = "wrap_version"
        const val WRAP_VERSION_ARGON_ONLY = 1
        const val WRAP_VERSION_HARDWARE = 2
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
