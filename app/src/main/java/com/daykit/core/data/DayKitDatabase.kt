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
        FocusGroupEntity::class,
        FocusScheduleEntity::class,
    ],
    version = 1,
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
                // No migrations and deliberately no destructive fallback: the app is
                // pre-release, so the schema starts fresh at version 1. Once there are
                // real installs, every schema change needs a hand-written Migration
                // added here — a missing one must crash rather than silently wipe data.
                .build()
        }
    }
}
