package com.daykit.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.daykit.feature.applock.data.LockedAppDao
import com.daykit.feature.applock.data.LockedAppEntity
import com.daykit.feature.expense.data.ExpenseDao
import com.daykit.feature.filelocker.data.VaultFileDao
import com.daykit.feature.filelocker.data.VaultFileEntity
import com.daykit.feature.expense.data.ExpenseEntryEntity
import com.daykit.feature.expense.data.ExpenseMonthEntity
import com.daykit.feature.expense.data.MonthlyBillAmountEntity
import com.daykit.feature.expense.data.MonthlyBillEntity
import com.daykit.feature.habit.data.HabitDao
import com.daykit.feature.habit.data.HabitEntity
import com.daykit.feature.habit.data.HabitLogEntity
import com.daykit.feature.keystore.data.KeyStoreEntryDao
import com.daykit.feature.keystore.data.KeyStoreEntryEntity
import com.daykit.feature.notes.data.SecureNoteDao
import com.daykit.feature.notes.data.SecureNoteEntity
import com.daykit.feature.notes.data.SecureNoteImageEntity
import com.daykit.feature.reminder.data.ReminderDao
import com.daykit.feature.reminder.data.ReminderEntity
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        LockedAppEntity::class,
        KeyStoreEntryEntity::class,
        SecureSettingEntity::class,
        ExpenseEntryEntity::class,
        MonthlyBillEntity::class,
        MonthlyBillAmountEntity::class,
        ExpenseMonthEntity::class,
        SecureNoteEntity::class,
        SecureNoteImageEntity::class,
        HabitEntity::class,
        HabitLogEntity::class,
        ReminderEntity::class,
        VaultFileEntity::class,
    ],
    version = 11,
    exportSchema = false,
)
abstract class DayKitDatabase : RoomDatabase() {
    abstract fun lockedAppDao(): LockedAppDao
    abstract fun keyStoreEntryDao(): KeyStoreEntryDao
    abstract fun secureSettingDao(): SecureSettingDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun secureNoteDao(): SecureNoteDao
    abstract fun habitDao(): HabitDao
    abstract fun reminderDao(): ReminderDao
    abstract fun vaultFileDao(): VaultFileDao

    companion object {
        fun create(
            context: Context,
            passphraseProvider: DatabasePassphraseProvider,
        ): DayKitDatabase {
            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(passphraseProvider.getOrCreatePassphrase())
            return Room.databaseBuilder(
                context.applicationContext,
                DayKitDatabase::class.java,
                "daykit_secure.db",
            )
                .openHelperFactory(factory)
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                    MIGRATION_6_7,
                    MIGRATION_7_8,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11,
                    MIGRATION_7_6,
                )
                .build()
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `key_store_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `entryId` TEXT NOT NULL,
                        `nameCiphertext` BLOB NOT NULL,
                        `nameIv` BLOB NOT NULL,
                        `labelCiphertext` BLOB NOT NULL,
                        `labelIv` BLOB NOT NULL,
                        `valueCiphertext` BLOB NOT NULL,
                        `valueIv` BLOB NOT NULL,
                        `version` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_key_store_entries_entryId` ON `key_store_entries` (`entryId`)",
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `key_store_entries`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `key_store_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `entryId` TEXT NOT NULL,
                        `nameCiphertext` BLOB NOT NULL,
                        `nameIv` BLOB NOT NULL,
                        `labelCiphertext` BLOB NOT NULL,
                        `labelIv` BLOB NOT NULL,
                        `valueCiphertext` BLOB NOT NULL,
                        `valueIv` BLOB NOT NULL,
                        `version` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_key_store_entries_entryId` ON `key_store_entries` (`entryId`)",
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `expense_entries` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `entryId` TEXT NOT NULL,
                        `monthKey` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `amountMinor` INTEGER NOT NULL,
                        `kind` TEXT NOT NULL,
                        `sourceBillId` TEXT,
                        `note` TEXT NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_expense_entries_entryId` ON `expense_entries` (`entryId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_entries_monthKey` ON `expense_entries` (`monthKey`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_expense_entries_monthKey_sourceBillId` ON `expense_entries` (`monthKey`, `sourceBillId`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `monthly_bills` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `billId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `category` TEXT NOT NULL,
                        `amountMinor` INTEGER NOT NULL,
                        `active` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_monthly_bills_billId` ON `monthly_bills` (`billId`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `expense_months` (
                        `monthKey` TEXT NOT NULL,
                        `limitMinor` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`monthKey`)
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `secure_notes` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `noteId` TEXT NOT NULL,
                        `titleCiphertext` BLOB NOT NULL,
                        `titleIv` BLOB NOT NULL,
                        `contentCiphertext` BLOB NOT NULL,
                        `contentIv` BLOB NOT NULL,
                        `labelsCiphertext` BLOB NOT NULL,
                        `labelsIv` BLOB NOT NULL,
                        `version` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_secure_notes_noteId` ON `secure_notes` (`noteId`)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `expense_entries` ADD COLUMN `expenseDate` TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    UPDATE `expense_entries`
                    SET `expenseDate` = substr(datetime(`createdAtMillis` / 1000, 'unixepoch'), 1, 10)
                    WHERE `expenseDate` = ''
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `monthly_bills` ADD COLUMN `startMonthKey` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `monthly_bills` ADD COLUMN `endMonthKey` TEXT")
                db.execSQL("ALTER TABLE `monthly_bills` ADD COLUMN `dueDay` INTEGER NOT NULL DEFAULT 1")
                db.execSQL(
                    """
                    UPDATE `monthly_bills`
                    SET `startMonthKey` = (
                        SELECT COALESCE(MIN(`monthKey`), substr(datetime(`monthly_bills`.`createdAtMillis` / 1000, 'unixepoch'), 1, 7))
                        FROM `expense_entries`
                        WHERE `expense_entries`.`sourceBillId` = `monthly_bills`.`billId`
                    )
                    WHERE `startMonthKey` = ''
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `monthly_bill_amounts` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `changeId` TEXT NOT NULL,
                        `billId` TEXT NOT NULL,
                        `effectiveMonthKey` TEXT NOT NULL,
                        `amountMinor` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_monthly_bill_amounts_changeId` ON `monthly_bill_amounts` (`changeId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_monthly_bill_amounts_billId_effectiveMonthKey` ON `monthly_bill_amounts` (`billId`, `effectiveMonthKey`)")
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `monthly_bill_amounts` (
                        `changeId`,
                        `billId`,
                        `effectiveMonthKey`,
                        `amountMinor`,
                        `createdAtMillis`,
                        `updatedAtMillis`
                    )
                    SELECT
                        `billId` || '-initial',
                        `billId`,
                        CASE WHEN `startMonthKey` = '' THEN substr(datetime(`createdAtMillis` / 1000, 'unixepoch'), 1, 7) ELSE `startMonthKey` END,
                        `amountMinor`,
                        `createdAtMillis`,
                        `updatedAtMillis`
                    FROM `monthly_bills`
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_7_6 = object : Migration(7, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `locked_apps_v6` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `packageNameCiphertext` BLOB NOT NULL,
                        `packageNameIv` BLOB NOT NULL,
                        `labelCiphertext` BLOB NOT NULL,
                        `labelIv` BLOB NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO `locked_apps_v6` (
                        `id`,
                        `packageNameCiphertext`,
                        `packageNameIv`,
                        `labelCiphertext`,
                        `labelIv`,
                        `enabled`,
                        `updatedAtMillis`
                    )
                    SELECT
                        `id`,
                        `packageNameCiphertext`,
                        `packageNameIv`,
                        `labelCiphertext`,
                        `labelIv`,
                        `enabled`,
                        `updatedAtMillis`
                    FROM `locked_apps`
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE `locked_apps`")
                db.execSQL("ALTER TABLE `locked_apps_v6` RENAME TO `locked_apps`")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `habits` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `habitId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `goalType` TEXT NOT NULL,
                        `targetMinutes` INTEGER NOT NULL,
                        `targetCount` INTEGER NOT NULL,
                        `unitLabel` TEXT NOT NULL,
                        `colorIndex` INTEGER NOT NULL,
                        `reminderEnabled` INTEGER NOT NULL,
                        `reminderHour` INTEGER NOT NULL,
                        `reminderMinute` INTEGER NOT NULL,
                        `active` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_habits_habitId` ON `habits` (`habitId`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `habit_logs` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `logId` TEXT NOT NULL,
                        `habitId` TEXT NOT NULL,
                        `date` TEXT NOT NULL,
                        `minutes` INTEGER NOT NULL,
                        `progressCount` INTEGER NOT NULL,
                        `completed` INTEGER NOT NULL,
                        `relapse` INTEGER NOT NULL,
                        `note` TEXT NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_habit_logs_logId` ON `habit_logs` (`logId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_habit_logs_habitId_date` ON `habit_logs` (`habitId`, `date`)")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `reminders` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `reminderId` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `scheduledAtMillis` INTEGER NOT NULL,
                        `completed` INTEGER NOT NULL,
                        `acknowledgedAtMillis` INTEGER,
                        `createdAtMillis` INTEGER NOT NULL,
                        `updatedAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_reminders_reminderId` ON `reminders` (`reminderId`)")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `secure_note_images` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `imageId` TEXT NOT NULL,
                        `noteId` TEXT NOT NULL,
                        `imageCiphertext` BLOB NOT NULL,
                        `imageIv` BLOB NOT NULL,
                        `position` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_secure_note_images_imageId` ON `secure_note_images` (`imageId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_secure_note_images_noteId` ON `secure_note_images` (`noteId`)")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `vault_files` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `fileId` TEXT NOT NULL,
                        `storedFileName` TEXT NOT NULL,
                        `nameCiphertext` BLOB NOT NULL,
                        `nameIv` BLOB NOT NULL,
                        `mimeCiphertext` BLOB NOT NULL,
                        `mimeIv` BLOB NOT NULL,
                        `wrappedDekCiphertext` BLOB NOT NULL,
                        `wrappedDekIv` BLOB NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `createdAtMillis` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_vault_files_fileId` ON `vault_files` (`fileId`)")
            }
        }
    }
}
