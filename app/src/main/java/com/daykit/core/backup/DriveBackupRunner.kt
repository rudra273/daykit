package com.daykit.core.backup

import android.accounts.Account
import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.daykit.core.data.SecureSettingRepository
import com.daykit.core.security.SensitiveKeyManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The automatic backup. Runs on unlock — deliberately never in the background.
 *
 * The sensitive tools are encrypted with the PIN-derived MSK, which
 * [SensitiveKeyManager] holds only in memory and only while the user is unlocked.
 * A background worker has no MSK, so it could only ever produce a backup missing
 * Key Store and Secure Notes; handing the MSK to the background (e.g. wrapping it
 * with an always-available Keystore key) is the exact threat the envelope design
 * closes. So the backup comes to the key rather than the reverse: "automatic"
 * means "checked every time you open the app", not "checked while you're away".
 */
class DriveBackupRunner(
    private val context: Context,
    private val settings: SecureSettingRepository,
    private val backupService: DayKitBackupService,
    private val sensitiveKeyManager: SensitiveKeyManager,
    private val driveClient: GoogleDriveBackupClient,
    /**
     * Process-lifetime scope, deliberately NOT a composable's scope. An upload
     * cancelled part-way leaves a truncated file in Drive that still looks like a
     * valid backup to [GoogleDriveBackupClient.listBackups], so it must survive the
     * unlock gate recomposing and the activity going away.
     */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    /** Guards against a second run while one is in flight (unlock can re-fire). */
    private val running = AtomicBoolean(false)

    /**
     * Fire-and-forget entry point for the unlock gate. Returns immediately; the work
     * continues on [scope] even if the caller's composition is torn down.
     */
    fun launchIfDue() {
        scope.launch {
            runCatching { runIfDue() }
                .onFailure { error -> Log.w(TAG, "Scheduled backup check failed", error) }
        }
    }

    /**
     * Backs up now if the configured schedule is due and everything needed is in
     * place. Safe (and cheap) to call on every unlock — it returns [Outcome.NotDue]
     * without touching the network when the interval has not elapsed.
     */
    suspend fun runIfDue(nowMillis: Long = System.currentTimeMillis()): Outcome = withContext(Dispatchers.IO) {
        if (!running.compareAndSet(false, true)) return@withContext Outcome.AlreadyRunning
        try {
            runIfDueInternal(nowMillis)
        } finally {
            running.set(false)
        }
    }

    private suspend fun runIfDueInternal(nowMillis: Long): Outcome {
        val schedule = DriveBackupSchedule.fromValue(
            settings.getString(SecureSettingRepository.KEY_DRIVE_BACKUP_SCHEDULE),
        )
        val intervalMillis = schedule.intervalMillis() ?: return Outcome.NotScheduled

        // Defensive: the caller only invokes this after the unlock gate, but the key
        // is wiped on background, so never assume it. A backup silently missing Key
        // Store and Secure Notes is worse than no backup at all.
        if (!sensitiveKeyManager.isUnlocked()) return Outcome.Locked

        val password = settings.getString(SecureSettingRepository.KEY_BACKUP_PASSWORD)
        if (password.isNullOrBlank()) return Outcome.NoPassword

        // "Has it been a day / a week since the last backup?" A manual backup writes
        // this same key, so tapping "Back up now" also resets the schedule clock.
        val lastBackupAt = settings
            .getString(SecureSettingRepository.KEY_DRIVE_LAST_BACKUP_AT)
            ?.toLongOrNull()
        if (lastBackupAt != null) {
            if (lastBackupAt > nowMillis) {
                // The timestamp is in the future (NTP/timezone correction, or a
                // Drive-reported createdTime ahead of this device). Neither naive
                // branch is acceptable: treating it as due backs up and prunes on
                // every unlock, rotating the whole history away in three opens, while
                // treating it as not-due stalls backups until the date catches up. So
                // clamp it to now — one backup is skipped, the clock self-heals, and
                // the interval runs normally from here.
                settings.putString(
                    SecureSettingRepository.KEY_DRIVE_LAST_BACKUP_AT,
                    nowMillis.toString(),
                )
                Log.w(TAG, "Last-backup timestamp was in the future; clamped to now")
                return Outcome.NotDue
            }
            if (nowMillis - lastBackupAt < intervalMillis) return Outcome.NotDue
        }

        val includedToolKeys = includedBackupToolKeys(
            includeExpenses = settings.getBoolean(SecureSettingRepository.KEY_BACKUP_INCLUDE_EXPENSES) == true,
            includeHabits = settings.getBoolean(SecureSettingRepository.KEY_BACKUP_INCLUDE_HABITS) == true,
            // Vault files can be large, so they are opt-in — same toggle the manual
            // backup uses. Off by default.
            includeVault = settings.getBoolean(SecureSettingRepository.KEY_BACKUP_INCLUDE_VAULT) == true,
        )

        val accessToken = authorize() ?: return Outcome.NeedsAuthorization

        val passwordChars = password.toCharArray()
        return runCatching {
            val encryptedBackup = backupService.exportEncrypted(passwordChars, includedToolKeys)
            val upload = driveClient.uploadBackup(
                accessToken = accessToken,
                encryptedBackup = encryptedBackup,
                source = DriveBackupSource.Automatic,
            )
            upload to encryptedBackup.toByteArray(Charsets.UTF_8).size.toLong()
        }.also {
            // exportEncrypted zeroes the array once it reaches the cipher, but it can
            // throw before that — clear it on every path, and with NULs, not spaces.
            passwordChars.fill('\u0000')
        }.fold(
            onSuccess = { (upload, fallbackSizeBytes) ->
                // The bytes are already in Drive. If these writes were skipped the
                // schedule clock would not advance and the next unlock would upload
                // again, so they must not be lost to cancellation.
                withContext(NonCancellable) {
                    settings.putBoolean(SecureSettingRepository.KEY_DRIVE_NEEDS_AUTHORIZATION, false)
                    settings.putString(
                        SecureSettingRepository.KEY_DRIVE_LAST_BACKUP_AT,
                        upload.file.createdAtMillis.toString(),
                    )
                    settings.putString(
                        SecureSettingRepository.KEY_DRIVE_LAST_UPLOAD_AT,
                        upload.file.createdAtMillis.toString(),
                    )
                    settings.putString(
                        SecureSettingRepository.KEY_DRIVE_LAST_BACKUP_SIZE_BYTES,
                        (upload.file.sizeBytes ?: fallbackSizeBytes).toString(),
                    )
                    settings.delete(SecureSettingRepository.KEY_DRIVE_LAST_ERROR)
                }
                Outcome.Completed
            },
            onFailure = { error ->
                Log.w(TAG, "Scheduled backup failed", error)
                settings.putString(
                    SecureSettingRepository.KEY_DRIVE_LAST_ERROR,
                    userFacingError(error, fallback = "Automatic backup failed"),
                )
                Outcome.Failed(error)
            },
        )
    }

    /**
     * Silent authorization only. The `drive.file` grant persists once given, so the
     * common case needs no UI; if Google wants a consent screen we record that and
     * let the Backup & Restore screen surface it rather than throwing a dialog at
     * the user right after unlock.
     */
    private suspend fun authorize(): String? {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            .apply {
                settings.getString(SecureSettingRepository.KEY_DRIVE_ACCOUNT_EMAIL)
                    ?.takeIf(String::isNotBlank)
                    ?.let { email -> setAccount(Account(email, GOOGLE_ACCOUNT_TYPE)) }
            }
            .build()

        val result = runCatching {
            Tasks.await(Identity.getAuthorizationClient(context).authorize(request))
        }.getOrElse { error ->
            Log.w(TAG, "Drive authorization failed", error)
            settings.putBoolean(SecureSettingRepository.KEY_DRIVE_NEEDS_AUTHORIZATION, true)
            settings.putString(
                SecureSettingRepository.KEY_DRIVE_LAST_ERROR,
                "Reconnect Google Drive",
            )
            return null
        }

        val accessToken = result.accessToken
        if (result.hasResolution() || accessToken.isNullOrBlank()) {
            settings.putBoolean(SecureSettingRepository.KEY_DRIVE_NEEDS_AUTHORIZATION, true)
            settings.putString(SecureSettingRepository.KEY_DRIVE_LAST_ERROR, "Reconnect Google Drive")
            return null
        }
        return accessToken
    }

    /**
     * The message shown on the Backup & Restore screen and persisted until the next
     * success. Deliberately does NOT include [Throwable.message]: for a Drive failure
     * that is the raw API response body, which would put server-echoed request
     * context into durable storage and onto a screen users screenshot for support.
     * The full error still goes to logcat for debugging.
     */
    private fun userFacingError(error: Throwable, fallback: String): String = when (error) {
        is java.net.UnknownHostException,
        is java.net.SocketTimeoutException,
        is java.io.InterruptedIOException,
        -> "No internet connection"
        is java.io.IOException -> "Could not reach Google Drive"
        else -> fallback
    }

    sealed interface Outcome {
        /** Schedule is Off or Manual — nothing to do. */
        data object NotScheduled : Outcome
        /** The interval has not elapsed since the last backup. */
        data object NotDue : Outcome
        /** A backup is already in flight; this call did nothing. */
        data object AlreadyRunning : Outcome
        data object Locked : Outcome
        data object NoPassword : Outcome
        data object NeedsAuthorization : Outcome
        data object Completed : Outcome
        data class Failed(val error: Throwable) : Outcome
    }

    private companion object {
        const val TAG = "DriveBackupRunner"
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}

internal fun DriveBackupSchedule.intervalMillis(): Long? = when (this) {
    DriveBackupSchedule.Daily -> 24L * 60 * 60 * 1000
    DriveBackupSchedule.Weekly -> 7L * 24 * 60 * 60 * 1000
    DriveBackupSchedule.Off,
    DriveBackupSchedule.Manual -> null
}
