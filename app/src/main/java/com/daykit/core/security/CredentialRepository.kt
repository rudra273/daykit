package com.daykit.core.security

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import java.security.MessageDigest

/**
 * Result of a PIN verification attempt.
 *
 * [LockedOut] is returned when too many wrong attempts have accumulated; the
 * caller must wait [remainingMillis] before the PIN will be checked again. No
 * data is ever deleted on failure — the lockout only throttles guessing.
 */
sealed interface PinVerifyResult {
    data object Success : PinVerifyResult
    data object Wrong : PinVerifyResult
    data class LockedOut(val remainingMillis: Long) : PinVerifyResult
}

/** Shape of the master credential: digits on a PIN pad, or a typed password. */
enum class CredentialKind {
    Pin,
    Password,
}

class CredentialRepository(
    context: Context,
    private val passwordHasher: PasswordHasher,
    private val hardwareBinding: PinHardwareBinding,
    private val clock: () -> Long = System::currentTimeMillis,
    // Overridable only so instrumented tests never touch the real credential.
    prefsName: String = PREFS_NAME,
) {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    fun hasCredential(): Boolean {
        return prefs.contains(KEY_SALT) && prefs.contains(KEY_HASH)
    }

    /** Kind of the stored credential. Credentials saved before kinds existed are PINs. */
    fun credentialKind(): CredentialKind =
        prefs.getString(KEY_KIND, null)
            ?.let { stored -> CredentialKind.entries.firstOrNull { it.name == stored } }
            ?: CredentialKind.Pin

    fun saveCredential(secret: CharArray, kind: CredentialKind = credentialKind()) {
        try {
            val salt = passwordHasher.newSalt()
            val (version, hash) = hashForStorage(secret, salt)
            prefs.edit(commit = true) {
                putString(KEY_SALT, salt.encodeBase64())
                putString(KEY_HASH, hash.encodeBase64())
                putInt(KEY_HASH_VERSION, version)
                putInt(KEY_PIN_LENGTH, secret.size)
                putString(KEY_KIND, kind.name)
                remove(KEY_FAILED_ATTEMPTS)
                remove(KEY_LOCKED_UNTIL)
            }
        } finally {
            secret.fill(' ')
        }
    }

    /**
     * Verifies [secret] against the stored PIN, enforcing an escalating lockout.
     *
     * The lockout is checked and updated here (not in callers) so every unlock
     * surface is protected automatically and no screen can bypass it — including
     * ones that auto-submit on each keystroke.
     */
    fun verify(secret: CharArray): PinVerifyResult {
        return try {
            val now = clock()
            val lockedUntil = prefs.getLong(KEY_LOCKED_UNTIL, 0L)
            if (now < lockedUntil) {
                return PinVerifyResult.LockedOut(lockedUntil - now)
            }

            val salt = prefs.getString(KEY_SALT, null)?.decodeBase64()
            val expectedHash = prefs.getString(KEY_HASH, null)?.decodeBase64()
            val matches = salt != null && expectedHash != null && matches(secret, salt, expectedHash)

            if (matches) {
                if (hashVersion() < HASH_VERSION_HARDWARE) {
                    // The PIN is only in hand at verify time, so upgrade here.
                    // saveCredential wipes its argument, so hand it a copy.
                    runCatching { saveCredential(secret.copyOf(), credentialKind()) }
                }
                prefs.edit {
                    remove(KEY_FAILED_ATTEMPTS)
                    remove(KEY_LOCKED_UNTIL)
                }
                PinVerifyResult.Success
            } else {
                registerFailure(now)
            }
        } finally {
            secret.fill(' ')
        }
    }

    /**
     * Digit count of the stored PIN — sizes the unlock dots and the auto-submit
     * threshold. Falls back to [MIN_PIN_LENGTH] for credentials saved before the
     * length was recorded. Meaningless for [CredentialKind.Password].
     */
    fun pinLength(): Int = prefs.getInt(KEY_PIN_LENGTH, MIN_PIN_LENGTH)

    /** Remaining lockout in millis, or 0 if not currently locked out. */
    fun lockoutRemainingMillis(): Long {
        val remaining = prefs.getLong(KEY_LOCKED_UNTIL, 0L) - clock()
        return remaining.coerceAtLeast(0L)
    }

    /**
     * The verifier is Argon2id bound to this device by [PinHardwareBinding], so
     * like the wrapped MSK it cannot be brute-forced off the phone. Falls back to
     * the plain Argon2id encoding if the binding key cannot be created.
     */
    private fun hashForStorage(secret: CharArray, salt: ByteArray): Pair<Int, ByteArray> {
        val argonHash = passwordHasher.hash(secret, salt)
        return runCatching {
            HASH_VERSION_HARDWARE to hardwareBinding.bind(argonHash, createIfMissing = true)
        }.getOrElse { HASH_VERSION_ARGON_ONLY to argonHash }
    }

    private fun matches(secret: CharArray, salt: ByteArray, expectedHash: ByteArray): Boolean {
        if (hashVersion() < HASH_VERSION_HARDWARE) {
            return passwordHasher.matches(secret, salt, expectedHash)
        }
        val candidate = runCatching {
            hardwareBinding.bind(passwordHasher.hash(secret, salt), createIfMissing = false)
        }.getOrNull() ?: return false
        return MessageDigest.isEqual(candidate, expectedHash)
    }

    private fun hashVersion(): Int = prefs.getInt(KEY_HASH_VERSION, HASH_VERSION_ARGON_ONLY)

    /**
     * Times of recent wrong PIN/password entries, newest first, across every
     * unlock surface. Unlike the lockout counter this survives a correct entry,
     * so the user can see that someone tried while they were away.
     */
    fun failedAttemptLog(): List<Long> =
        prefs.getString(KEY_FAILED_LOG, null)
            ?.split(',')
            ?.mapNotNull(String::toLongOrNull)
            .orEmpty()

    fun clearFailedAttemptLog() {
        prefs.edit { remove(KEY_FAILED_LOG) }
    }

    private fun registerFailure(now: Long): PinVerifyResult {
        val attempts = prefs.getInt(KEY_FAILED_ATTEMPTS, 0) + 1
        val lockMillis = lockoutForAttempts(attempts)
        val log = (listOf(now) + failedAttemptLog()).take(MAX_FAILED_LOG_ENTRIES)
        prefs.edit {
            putString(KEY_FAILED_LOG, log.joinToString(","))
            putInt(KEY_FAILED_ATTEMPTS, attempts)
            if (lockMillis > 0L) {
                putLong(KEY_LOCKED_UNTIL, now + lockMillis)
            }
        }
        return if (lockMillis > 0L) {
            PinVerifyResult.LockedOut(lockMillis)
        } else {
            PinVerifyResult.Wrong
        }
    }

    /**
     * Escalating backoff: the first [FREE_ATTEMPTS] wrong tries cost nothing;
     * after that every additional block of [ATTEMPTS_PER_STEP] wrong tries adds
     * [STEP_MILLIS] of lockout, capped at [MAX_LOCKOUT_MILLIS].
     *
     * e.g. attempts 1–4: free; 5: 30s; 10: 60s; 15: 90s; ... up to 30 min.
     */
    private fun lockoutForAttempts(attempts: Int): Long {
        if (attempts <= FREE_ATTEMPTS) return 0L
        val steps = (attempts - FREE_ATTEMPTS + ATTEMPTS_PER_STEP - 1) / ATTEMPTS_PER_STEP
        return (steps * STEP_MILLIS).coerceAtMost(MAX_LOCKOUT_MILLIS)
    }

    /** Erases the stored PIN and its lockout state. Part of a full data reset. */
    fun clear() {
        prefs.edit { clear() }
    }

    private fun ByteArray.encodeBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.decodeBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    companion object {
        /** Minimum digits required for a new master PIN. */
        const val MIN_PIN_LENGTH = 6
        const val MAX_PIN_LENGTH = 12

        /** Bounds for a new alphanumeric master password. */
        const val MIN_PASSWORD_LENGTH = 8
        const val MAX_PASSWORD_LENGTH = 64

        /** Keeps typed input within what [kind] allows; digits only for a PIN. */
        fun sanitize(input: String, kind: CredentialKind): String = when (kind) {
            CredentialKind.Pin -> input.filter(Char::isDigit).take(MAX_PIN_LENGTH)
            CredentialKind.Password -> input.filterNot(Char::isISOControl).take(MAX_PASSWORD_LENGTH)
        }

        /** Why [secret] cannot be a new credential of [kind], or null when it can. */
        fun newCredentialError(secret: String, kind: CredentialKind): String? = when (kind) {
            CredentialKind.Pin ->
                if (secret.length < MIN_PIN_LENGTH) "Use at least $MIN_PIN_LENGTH digits" else null
            CredentialKind.Password -> when {
                secret.length < MIN_PASSWORD_LENGTH -> "Use at least $MIN_PASSWORD_LENGTH characters"
                secret.none(Char::isLetter) || secret.none { !it.isLetter() } ->
                    "Mix letters with numbers or symbols"
                else -> null
            }
        }

        private const val PREFS_NAME = "daykit_credential_store"
        private const val KEY_SALT = "pin_salt"
        private const val KEY_HASH = "pin_hash"
        private const val KEY_HASH_VERSION = "pin_hash_version"
        private const val KEY_PIN_LENGTH = "pin_length"
        private const val KEY_KIND = "credential_kind"
        private const val KEY_FAILED_ATTEMPTS = "failed_attempts"
        private const val KEY_LOCKED_UNTIL = "locked_until_millis"
        private const val KEY_FAILED_LOG = "failed_attempt_log"
        private const val MAX_FAILED_LOG_ENTRIES = 20

        private const val HASH_VERSION_ARGON_ONLY = 1
        private const val HASH_VERSION_HARDWARE = 2

        private const val FREE_ATTEMPTS = 4
        private const val ATTEMPTS_PER_STEP = 5
        private const val STEP_MILLIS = 30_000L
        private const val MAX_LOCKOUT_MILLIS = 30 * 60_000L
    }
}
