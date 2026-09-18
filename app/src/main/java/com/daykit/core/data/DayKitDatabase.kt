package com.daykit.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.daykit.feature.applock.data.LockedAppDao
import com.daykit.feature.applock.data.LockedAppEntity
import com.daykit.feature.expense.data.ExpenseDao
import com.daykit.feature.filelocker.data.VaultFileDao
import com.daykit.feature.filelocker.data.VaultFileEntity
import com.daykit.feature.focus.data.FocusGroupDao
import com.daykit.feature.focus.data.FocusGroupEntity
import com.daykit.feature.focus.data.FocusScheduleDao
import com.daykit.feature.focus.data.FocusScheduleEntity
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
import com.daykit.feature.reminder.data.ReminderOccurrenceEntity
import com.daykit.feature.dayflow.data.DayflowDao
import com.daykit.feature.dayflow.data.DayflowDayEntity
import com.daykit.feature.dayflow.data.PomodoroSessionEntity
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
        ReminderOccurrenceEntity::class,
        VaultFileEntity::class,
        FocusGroupEntity::class,
        FocusScheduleEntity::class,
        DayflowDayEntity::class,
        PomodoroSessionEntity::class,
    ],
    version = 5,
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
    abstract fun focusGroupDao(): FocusGroupDao
    abstract fun focusScheduleDao(): FocusScheduleDao
    abstract fun dayflowDao(): DayflowDao

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
                .addMigrations(object : androidx.room.migration.Migration(1, 2) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE reminders ADD COLUMN recurrenceRule TEXT")
                        db.execSQL("ALTER TABLE reminders ADD COLUMN pendingOccurrenceMillis INTEGER")
                    }
                })
                .addMigrations(object : androidx.room.migration.Migration(2, 3) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE reminders ADD COLUMN paused INTEGER NOT NULL DEFAULT 0")
                        db.execSQL("ALTER TABLE reminders ADD COLUMN snoozedUntilMillis INTEGER")
                        db.execSQL("CREATE TABLE IF NOT EXISTS reminder_occurrences (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `reminderId` TEXT NOT NULL, `occurrenceMillis` INTEGER NOT NULL, `action` TEXT NOT NULL, `actionAtMillis` INTEGER NOT NULL)")
                        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_reminder_occurrences_reminderId_occurrenceMillis ON reminder_occurrences (`reminderId`, `occurrenceMillis`)")
                    }
                })
                .addMigrations(object : androidx.room.migration.Migration(3, 4) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS dayflow_days (`date` TEXT NOT NULL, `journal` TEXT NOT NULL, `mood` TEXT NOT NULL, `updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`date`))")
                        db.execSQL("CREATE TABLE IF NOT EXISTS dayflow_sessions (`id` TEXT NOT NULL, `kind` TEXT NOT NULL, `startedAtMillis` INTEGER NOT NULL, `endAtMillis` INTEGER NOT NULL, `remainingMillis` INTEGER NOT NULL, `state` TEXT NOT NULL, `finishedAtMillis` INTEGER, PRIMARY KEY(`id`))")
                    }
                })
                .addMigrations(object : androidx.room.migration.Migration(4, 5) {
                    override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("ALTER TABLE dayflow_days ADD COLUMN journalTitle TEXT NOT NULL DEFAULT ''")
                    }
                })
                .build()
        }
    }
}
