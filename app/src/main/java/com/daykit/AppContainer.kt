package com.daykit

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import com.daykit.core.backup.BackupCrypto
import com.daykit.core.backup.DriveBackupRunner
import com.daykit.core.backup.DayKitBackupService
import com.daykit.core.backup.GoogleDriveBackupClient
import com.daykit.core.data.DatabasePassphraseProvider
import com.daykit.core.data.DayKitDatabase
import com.daykit.core.data.SecureSettingRepository
import com.daykit.core.data.SettingFlagCache
import com.daykit.core.security.AndroidKeyStoreCrypto
import com.daykit.core.security.CredentialRepository
import com.daykit.core.security.KeyUnavailableException
import com.daykit.core.security.PasswordHasher
import com.daykit.core.security.SensitiveKeyManager
import com.daykit.core.security.SensitiveValueCipher
import com.daykit.core.security.SessionValueCipher
import com.daykit.feature.applock.data.AppLockRepository
import com.daykit.feature.focus.data.FocusBlockStore
import com.daykit.feature.focus.data.FocusRepository
import com.daykit.feature.applock.data.LockedPackageCache
import com.daykit.feature.applock.domain.InstalledAppProvider
import com.daykit.feature.expense.data.ExpenseBackupContributor
import com.daykit.feature.filelocker.data.VaultBackupContributor
import com.daykit.feature.expense.data.ExpenseRepository
import com.daykit.feature.filelocker.data.VaultFileRepository
import com.daykit.feature.filelocker.data.VaultStreamingCrypto
import com.daykit.feature.habit.data.HabitBackupContributor
import com.daykit.feature.habit.data.HabitRepository
import com.daykit.feature.keystore.data.KeyStoreBackupContributor
import com.daykit.feature.keystore.data.KeyStoreRepository
import com.daykit.feature.notes.data.SecureNoteBackupContributor
import com.daykit.feature.notes.data.SecureNoteRepository
import com.daykit.feature.reminder.data.ReminderRepository
import java.io.File

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    // Media URIs shared into the app ("share to DayKit" from Gallery), waiting
    // to be imported into the file vault once the user has unlocked.
    val pendingVaultShares = MutableStateFlow<List<Uri>>(emptyList())
    val keyStoreCrypto = AndroidKeyStoreCrypto()
    val sensitiveValueCipher = SensitiveValueCipher(keyStoreCrypto)
    val credentialRepository = CredentialRepository(appContext, PasswordHasher())

    // PIN-derived key for the sensitive tools (vault, key store, secure notes).
    // The MSK is only in memory while unlocked; sessionValueCipher throws if locked.
    val sensitiveKeyManager = SensitiveKeyManager(appContext, PasswordHasher())
    val sessionValueCipher = SessionValueCipher(sensitiveKeyManager)
    val lockedPackageCache = LockedPackageCache(appContext)
    val focusBlockStore = FocusBlockStore(appContext)
    val settingFlagCache = SettingFlagCache(appContext)

    /**
     * Set when the encrypted database could not be opened at all — the Keystore key
     * that wraps the SQLCipher passphrase is gone, so no stored data is readable.
     *
     * The UI observes this to show a recovery screen instead of crash-looping: a
     * bare throw out of the [database] lazy re-throws on every access, which turns
     * one Keystore failure into an app that can never launch again.
     */
    val storageFailure = MutableStateFlow<KeyUnavailableException?>(null)

    val database: DayKitDatabase by lazy {
        val passphraseProvider = DatabasePassphraseProvider(appContext, keyStoreCrypto)
        try {
            DayKitDatabase.create(appContext, passphraseProvider)
        } catch (e: KeyUnavailableException) {
            storageFailure.value = e
            throw e
        }
    }

    /**
     * Destroys all local data and the keys protecting it, so a device whose Keystore
     * entry was lost can be used again. Irreversible — only call from an explicit,
     * user-confirmed reset.
     */
    fun resetAllLocalData() {
        runCatching { if (database.isOpen) database.close() }
        appContext.deleteDatabase("daykit_secure.db")
        runCatching { DatabasePassphraseProvider(appContext, keyStoreCrypto).clear() }
        runCatching { sensitiveKeyManager.clearAll() }
        runCatching { credentialRepository.clear() }
        runCatching { lockedPackageCache.clear() }
        runCatching { focusBlockStore.clear() }
        runCatching { settingFlagCache.clear() }
        runCatching { File(appContext.filesDir, "vault").deleteRecursively() }
    }

    val secureSettingRepository: SecureSettingRepository by lazy {
        SecureSettingRepository(database.secureSettingDao(), sensitiveValueCipher, settingFlagCache)
    }

    val appLockRepository: AppLockRepository by lazy {
        AppLockRepository(lockedPackageCache)
    }

    val focusRepository: FocusRepository by lazy {
        FocusRepository(focusBlockStore)
    }

    val keyStoreRepository: KeyStoreRepository by lazy {
        KeyStoreRepository(database.keyStoreEntryDao(), sessionValueCipher)
    }

    val expenseRepository: ExpenseRepository by lazy {
        ExpenseRepository(database.expenseDao())
    }

    val secureNoteRepository: SecureNoteRepository by lazy {
        SecureNoteRepository(database.secureNoteDao(), sessionValueCipher)
    }

    val habitRepository: HabitRepository by lazy {
        HabitRepository(database.habitDao())
    }

    val reminderRepository: ReminderRepository by lazy {
        ReminderRepository(database.reminderDao())
    }

    val vaultFileRepository: VaultFileRepository by lazy {
        VaultFileRepository(
            context = appContext,
            dao = database.vaultFileDao(),
            streamingCrypto = VaultStreamingCrypto(),
            cipher = sessionValueCipher,
        )
    }

    val backupService: DayKitBackupService by lazy {
        DayKitBackupService(
            crypto = BackupCrypto(PasswordHasher()),
            contributors = listOf(
                KeyStoreBackupContributor(keyStoreRepository),
                ExpenseBackupContributor(expenseRepository),
                SecureNoteBackupContributor(secureNoteRepository),
                HabitBackupContributor(habitRepository),
                VaultBackupContributor(vaultFileRepository),
            ),
        )
    }

    val googleDriveBackupClient: GoogleDriveBackupClient by lazy {
        GoogleDriveBackupClient()
    }

    /**
     * The automatic Drive backup. Runs on unlock, in the foreground, where the
     * PIN-derived MSK exists — the only place Key Store and Secure Notes can
     * actually be exported. There is deliberately no background worker.
     *
     * It carries its own process-lifetime scope so an in-flight upload is not
     * cancelled when the unlock gate recomposes or the activity goes away.
     */
    val driveBackupRunner: DriveBackupRunner by lazy {
        DriveBackupRunner(
            context = appContext,
            settings = secureSettingRepository,
            backupService = backupService,
            sensitiveKeyManager = sensitiveKeyManager,
            driveClient = googleDriveBackupClient,
        )
    }

    val installedAppProvider = InstalledAppProvider(appContext)
}
