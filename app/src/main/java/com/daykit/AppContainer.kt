package com.daykit

import android.content.Context
import com.daykit.core.backup.BackupCrypto
import com.daykit.core.backup.DriveBackupScheduler
import com.daykit.core.backup.DayKitBackupService
import com.daykit.core.backup.GoogleDriveBackupClient
import com.daykit.core.data.DatabasePassphraseProvider
import com.daykit.core.data.DayKitDatabase
import com.daykit.core.data.SecureSettingRepository
import com.daykit.core.data.SettingFlagCache
import com.daykit.core.security.AndroidKeyStoreCrypto
import com.daykit.core.security.CredentialRepository
import com.daykit.core.security.PasswordHasher
import com.daykit.core.security.SensitiveValueCipher
import com.daykit.feature.applock.data.AppLockRepository
import com.daykit.feature.applock.data.LockedPackageCache
import com.daykit.feature.applock.domain.InstalledAppProvider
import com.daykit.feature.expense.data.ExpenseBackupContributor
import com.daykit.feature.expense.data.ExpenseRepository
import com.daykit.feature.habit.data.HabitBackupContributor
import com.daykit.feature.habit.data.HabitRepository
import com.daykit.feature.keystore.data.KeyStoreBackupContributor
import com.daykit.feature.keystore.data.KeyStoreRepository
import com.daykit.feature.notes.data.SecureNoteBackupContributor
import com.daykit.feature.notes.data.SecureNoteRepository
import com.daykit.feature.reminder.data.ReminderRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val keyStoreCrypto = AndroidKeyStoreCrypto()
    val sensitiveValueCipher = SensitiveValueCipher(keyStoreCrypto)
    val credentialRepository = CredentialRepository(appContext, PasswordHasher())
    val lockedPackageCache = LockedPackageCache(appContext)
    val settingFlagCache = SettingFlagCache(appContext)

    val database: DayKitDatabase by lazy {
        val passphraseProvider = DatabasePassphraseProvider(appContext, keyStoreCrypto)
        DayKitDatabase.create(appContext, passphraseProvider)
    }

    val secureSettingRepository: SecureSettingRepository by lazy {
        SecureSettingRepository(database.secureSettingDao(), sensitiveValueCipher, settingFlagCache)
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
