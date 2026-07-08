package com.rudra.daykit

import android.content.Context
import com.rudra.daykit.core.backup.BackupCrypto
import com.rudra.daykit.core.backup.DriveBackupScheduler
import com.rudra.daykit.core.backup.DayKitBackupService
import com.rudra.daykit.core.backup.GoogleDriveBackupClient
import com.rudra.daykit.core.data.DatabasePassphraseProvider
import com.rudra.daykit.core.data.DayKitDatabase
import com.rudra.daykit.core.data.SecureSettingRepository
import com.rudra.daykit.core.security.AndroidKeyStoreCrypto
import com.rudra.daykit.core.security.CredentialRepository
import com.rudra.daykit.core.security.PasswordHasher
import com.rudra.daykit.core.security.SensitiveValueCipher
import com.rudra.daykit.feature.applock.data.AppLockRepository
import com.rudra.daykit.feature.applock.data.LockedPackageCache
import com.rudra.daykit.feature.applock.domain.InstalledAppProvider
import com.rudra.daykit.feature.expense.data.ExpenseBackupContributor
import com.rudra.daykit.feature.expense.data.ExpenseRepository
import com.rudra.daykit.feature.habit.data.HabitBackupContributor
import com.rudra.daykit.feature.habit.data.HabitRepository
import com.rudra.daykit.feature.keystore.data.KeyStoreBackupContributor
import com.rudra.daykit.feature.keystore.data.KeyStoreRepository
import com.rudra.daykit.feature.notes.data.SecureNoteBackupContributor
import com.rudra.daykit.feature.notes.data.SecureNoteRepository
import com.rudra.daykit.feature.reminder.data.ReminderRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val keyStoreCrypto = AndroidKeyStoreCrypto()
    val sensitiveValueCipher = SensitiveValueCipher(keyStoreCrypto)
    val credentialRepository = CredentialRepository(appContext, PasswordHasher())
    val lockedPackageCache = LockedPackageCache(appContext)

    val database: DayKitDatabase by lazy {
        val passphraseProvider = DatabasePassphraseProvider(appContext, keyStoreCrypto)
        DayKitDatabase.create(appContext, passphraseProvider)
    }

    val secureSettingRepository: SecureSettingRepository by lazy {
        SecureSettingRepository(database.secureSettingDao(), sensitiveValueCipher)
    }

    val appLockRepository: AppLockRepository by lazy {
        AppLockRepository(lockedPackageCache)
    }

    val keyStoreRepository: KeyStoreRepository by lazy {
        KeyStoreRepository(database.keyStoreEntryDao(), sensitiveValueCipher)
    }

    val expenseRepository: ExpenseRepository by lazy {
        ExpenseRepository(database.expenseDao())
    }

    val secureNoteRepository: SecureNoteRepository by lazy {
        SecureNoteRepository(database.secureNoteDao(), sensitiveValueCipher)
    }

    val habitRepository: HabitRepository by lazy {
        HabitRepository(database.habitDao())
    }

    val reminderRepository: ReminderRepository by lazy {
        ReminderRepository(database.reminderDao())
    }

    val backupService: DayKitBackupService by lazy {
        DayKitBackupService(
            crypto = BackupCrypto(PasswordHasher()),
            contributors = listOf(
                KeyStoreBackupContributor(keyStoreRepository),
                ExpenseBackupContributor(expenseRepository),
                SecureNoteBackupContributor(secureNoteRepository),
                HabitBackupContributor(habitRepository),
            ),
        )
    }

    val googleDriveBackupClient: GoogleDriveBackupClient by lazy {
        GoogleDriveBackupClient()
    }

    val driveBackupScheduler: DriveBackupScheduler by lazy {
        DriveBackupScheduler(appContext)
    }

    val installedAppProvider = InstalledAppProvider(appContext)
}
