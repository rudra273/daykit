package com.daykit.core.backup

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.daykit.core.data.DayKitDatabase
import com.daykit.core.security.PasswordHasher
import com.daykit.core.security.SessionValueCipher
import com.daykit.feature.dayflow.data.DayflowBackupContributor
import com.daykit.feature.dayflow.data.DayflowRepository
import com.daykit.feature.expense.data.ExpenseBackupContributor
import com.daykit.feature.expense.data.ExpenseRepository
import com.daykit.feature.habit.data.HabitBackupContributor
import com.daykit.feature.habit.data.HabitRepository
import com.daykit.feature.keystore.data.KeyStoreBackupContributor
import com.daykit.feature.keystore.data.KeyStoreRepository
import com.daykit.feature.notes.data.SecureNoteBackupContributor
import com.daykit.feature.notes.data.SecureNoteRepository
import com.daykit.feature.reminder.data.ReminderBackupContributor
import com.daykit.feature.reminder.data.ReminderRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom

/**
 * Export → encrypt → decrypt → import through the real [DayKitBackupService],
 * [BackupCrypto] and repositories, into a second empty database, and checks that
 * every section comes back identical. This is the test that catches a field
 * dropped from one side of a contributor, or a section silently skipped.
 *
 * Both databases are in-memory and use their own random session key, so running
 * this on a phone never touches the real DayKit data. Contributors that write to
 * shared prefs or the vault directory (App Lock, Focus, Event Light, App
 * Preferences, Vault) are deliberately left out for the same reason.
 */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val crypto = BackupCrypto(PasswordHasher())
    private lateinit var source: Install
    private lateinit var target: Install

    @Before
    fun setUp() {
        source = Install()
        target = Install()
    }

    @After
    fun tearDown() {
        source.db.close()
        target.db.close()
    }

    @Test
    fun everySectionSurvivesAFullEncryptedRoundTrip() = runBlocking {
        source.seed()
        val before = source.exportSections()
        before.forEach { (key, section) ->
            assertTrue("fixture for $key exported nothing", section.toString().length > 40)
        }

        val backup = source.service.exportEncrypted(PASSWORD.toCharArray(), source.toolKeys)
        assertFalse("backup leaked plaintext", backup.contains("correct horse"))

        val report = target.service.importEncrypted(backup, PASSWORD.toCharArray())
        assertEquals(emptyList<DayKitBackupService.SkippedSection>(), report.skipped)
        assertEquals(source.toolKeys, report.restored.toSet())

        val after = target.exportSections()
        before.forEach { (key, section) ->
            assertEquals("$key changed in the round trip", canonical(section), canonical(after[key]))
        }
    }

    @Test
    fun restoringTwiceDoesNotDuplicateRecords() = runBlocking {
        source.seed()
        val backup = source.service.exportEncrypted(PASSWORD.toCharArray(), source.toolKeys)
        target.service.importEncrypted(backup, PASSWORD.toCharArray())
        val once = target.exportSections()
        target.service.importEncrypted(backup, PASSWORD.toCharArray())
        val twice = target.exportSections()
        once.forEach { (key, section) -> assertEquals("$key duplicated on re-import", canonical(section), canonical(twice[key])) }
    }

    /** A schemaVersion bump on one side must be reported, never silently dropped. */
    @Test
    fun aSchemaVersionMismatchIsReportedAndTheRestStillRestores() = runBlocking {
        source.seed()
        val backup = source.service.exportEncrypted(PASSWORD.toCharArray(), source.toolKeys)

        val bumped = target.contributors.map { contributor ->
            if (contributor.toolKey != BackupToolKeys.NOTES) contributor
            else object : BackupContributor by contributor {
                override val schemaVersion = contributor.schemaVersion + 1
            }
        }
        val report = DayKitBackupService(crypto, bumped).importEncrypted(backup, PASSWORD.toCharArray())

        assertFalse(report.isCompleteRestore)
        assertEquals(listOf(BackupToolKeys.NOTES), report.skipped.map { it.toolKey })
        assertEquals(source.toolKeys - BackupToolKeys.NOTES, report.restored.toSet())
    }

    @Test
    fun unknownSectionsAreReportedAsSkipped() = runBlocking {
        source.seed()
        val backup = source.service.exportEncrypted(PASSWORD.toCharArray(), source.toolKeys)
        val older = DayKitBackupService(crypto, target.contributors.filter { it.toolKey != BackupToolKeys.HABITS })
        val report = older.importEncrypted(backup, PASSWORD.toCharArray())
        assertEquals(listOf(BackupToolKeys.HABITS), report.skipped.map { it.toolKey })
    }

    @Test
    fun excludedToolsAreNotExported() = runBlocking {
        source.seed()
        val backup = source.service.exportEncrypted(PASSWORD.toCharArray(), setOf(BackupToolKeys.KEY_STORE))
        val report = target.service.importEncrypted(backup, PASSWORD.toCharArray())
        assertEquals(listOf(BackupToolKeys.KEY_STORE), report.restored)
        assertEquals(0, target.exportSections().getValue(BackupToolKeys.NOTES).getJSONArray("records").length())
    }

    @Test
    fun trashedItemsAreLeftOutOfTheBackup() = runBlocking {
        source.seed()
        source.notes.deleteNote("7d0a4f0e-2b8b-4d5e-8a4c-3f1e2d6c7b02")
        val backup = source.service.exportEncrypted(PASSWORD.toCharArray(), source.toolKeys)
        target.service.importEncrypted(backup, PASSWORD.toCharArray())
        assertEquals(0, target.exportSections().getValue(BackupToolKeys.NOTES).getJSONArray("records").length())
        assertEquals(1, target.exportSections().getValue(BackupToolKeys.KEY_STORE).getJSONArray("records").length())
    }

    @Test
    fun aWrongPasswordRestoresNothing() = runBlocking {
        source.seed()
        val backup = source.service.exportEncrypted(PASSWORD.toCharArray(), source.toolKeys)
        try {
            target.service.importEncrypted(backup, "wrong password".toCharArray())
            fail("import with the wrong password succeeded")
        } catch (_: Exception) {
        }
        assertEquals(0, target.exportSections().getValue(BackupToolKeys.KEY_STORE).getJSONArray("records").length())
    }

    /** One independent "phone": its own in-memory DB, session key and contributors. */
    private inner class Install {
        val db: DayKitDatabase = Room.inMemoryDatabaseBuilder(context, DayKitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        private val key = ByteArray(32).also(SecureRandom()::nextBytes)
        private val cipher = SessionValueCipher { key.copyOf() }
        val notes = SecureNoteRepository(db.secureNoteDao(), cipher)

        val contributors: List<BackupContributor> = listOf(
            KeyStoreBackupContributor(KeyStoreRepository(db.keyStoreEntryDao(), cipher)),
            SecureNoteBackupContributor(notes),
            ExpenseBackupContributor(ExpenseRepository(db.expenseDao())),
            HabitBackupContributor(HabitRepository(db.habitDao())),
            DayflowBackupContributor(DayflowRepository(db.dayflowDao())),
            ReminderBackupContributor(ReminderRepository(db.reminderDao())),
        )
        val toolKeys = contributors.map { it.toolKey }.toSet()
        val service = DayKitBackupService(crypto, contributors)

        suspend fun seed() = contributors.forEach { it.importJson(fixtures.getValue(it.toolKey)) }

        suspend fun exportSections(): Map<String, JSONObject> = contributors.associate { it.toolKey to it.exportJson() }
    }

    /** Android's org.json has no `similar()`; compare with object keys sorted. */
    private fun canonical(value: Any?): String = when (value) {
        is JSONObject -> value.keys().asSequence().sorted()
            .joinToString(",", "{", "}") { "\"$it\":${canonical(value.opt(it))}" }
        is JSONArray -> (0 until value.length()).joinToString(",", "[", "]") { canonical(value.opt(it)) }
        null, JSONObject.NULL -> "null"
        is String -> JSONObject.quote(value)
        else -> value.toString()
    }

    private companion object {
        const val PASSWORD = "backup-test-password"
        const val T1 = 1_759_000_000_000L
        const val T2 = 1_759_100_000_000L

        val fixtures: Map<String, JSONObject> = mapOf(
            BackupToolKeys.KEY_STORE to JSONObject().put("records", JSONArray().put(
                JSONObject()
                    .put("entryId", "2f1c7c1e-4a63-4c1f-9f2b-0f7f2a1b5c01")
                    .put("name", "Email")
                    .put("label", "personal")
                    .put("value", "correct horse battery staple")
                    .put("version", 1)
                    .put("createdAtMillis", T1)
                    .put("updatedAtMillis", T2),
            )),
            BackupToolKeys.NOTES to JSONObject().put("records", JSONArray().put(
                JSONObject()
                    .put("noteId", "7d0a4f0e-2b8b-4d5e-8a4c-3f1e2d6c7b02")
                    .put("title", "Groceries · ünïcödé")
                    .put("content", "milk\neggs\n🔑 correct horse")
                    .put("labels", "home")
                    .put("version", 2)
                    .put("createdAtMillis", T1)
                    .put("updatedAtMillis", T2),
            )),
            BackupToolKeys.EXPENSES to JSONObject()
                .put("entries", JSONArray().put(
                    JSONObject()
                        .put("entryId", "c3b2a190-1d2e-4f3a-9b8c-7d6e5f4a3b03")
                        .put("monthKey", "2026-09")
                        .put("title", "Coffee")
                        .put("category", "Food")
                        .put("amountMinor", 450L)
                        .put("kind", "Daily")
                        .put("sourceBillId", JSONObject.NULL)
                        .put("expenseDate", "2026-09-15")
                        .put("note", "with a friend")
                        .put("createdAtMillis", T1)
                        .put("updatedAtMillis", T2),
                ))
                .put("bills", JSONArray().put(
                    JSONObject()
                        .put("billId", "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c04")
                        .put("title", "Rent")
                        .put("category", "Housing")
                        .put("amountMinor", 120_000L)
                        .put("active", true)
                        .put("startMonthKey", "2026-01")
                        .put("endMonthKey", JSONObject.NULL)
                        .put("dueDay", 5)
                        .put("createdAtMillis", T1)
                        .put("updatedAtMillis", T2),
                ))
                .put("billAmounts", JSONArray().put(
                    JSONObject()
                        .put("changeId", "b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d05")
                        .put("billId", "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c04")
                        .put("effectiveMonthKey", "2026-06")
                        .put("amountMinor", 125_000L)
                        .put("createdAtMillis", T1)
                        .put("updatedAtMillis", T2),
                ))
                .put("months", JSONArray().put(
                    JSONObject()
                        .put("monthKey", "2026-09")
                        .put("limitMinor", 500_000L)
                        .put("createdAtMillis", T1)
                        .put("updatedAtMillis", T2),
                )),
            BackupToolKeys.HABITS to JSONObject()
                .put("habits", JSONArray().put(
                    JSONObject()
                        .put("habitId", "d4e5f6a7-b8c9-4d0e-8f1a-2b3c4d5e6f06")
                        .put("name", "Read")
                        .put("kind", "Build")
                        .put("goalType", "Count")
                        .put("targetMinutes", 0)
                        .put("targetCount", 10)
                        .put("unitLabel", "pages")
                        .put("colorIndex", 3)
                        .put("reminderEnabled", true)
                        .put("reminderHour", 21)
                        .put("reminderMinute", 30)
                        .put("active", true)
                        .put("createdAtMillis", T1)
                        .put("updatedAtMillis", T2),
                ))
                .put("logs", JSONArray().put(
                    JSONObject()
                        .put("logId", "e5f6a7b8-c9d0-4e1f-9a2b-3c4d5e6f7a07")
                        .put("habitId", "d4e5f6a7-b8c9-4d0e-8f1a-2b3c4d5e6f06")
                        .put("date", "2026-09-30")
                        .put("minutes", 0)
                        .put("progressCount", 12)
                        .put("completed", true)
                        .put("relapse", false)
                        .put("note", "good chapter")
                        .put("createdAtMillis", T1)
                        .put("updatedAtMillis", T2),
                )),
            BackupToolKeys.DAYFLOW to JSONObject()
                .put("days", JSONArray().put(
                    JSONObject()
                        .put("date", "2026-09-30")
                        .put("journal", "Shipped the release")
                        .put("mood", "great")
                        .put("updatedAtMillis", T2)
                        .put("journalTitle", "Release day"),
                ))
                .put("sessions", JSONArray().put(
                    JSONObject()
                        .put("id", "f6a7b8c9-d0e1-4f2a-8b3c-4d5e6f7a8b08")
                        .put("kind", "focus")
                        .put("startedAtMillis", T1)
                        .put("endAtMillis", T1 + 25 * 60_000L)
                        .put("remainingMillis", 0L)
                        .put("state", "finished")
                        .put("finishedAtMillis", T1 + 25 * 60_000L),
                )),
            BackupToolKeys.REMINDERS to JSONObject().put("reminders", JSONArray().put(
                JSONObject()
                    .put("reminderId", "0a1b2c3d-4e5f-4a6b-8c7d-9e0f1a2b3c09")
                    .put("title", "Pay rent")
                    .put("scheduledAtMillis", T2)
                    .put("completed", false)
                    .put("acknowledgedAtMillis", JSONObject.NULL)
                    .put("createdAtMillis", T1)
                    .put("updatedAtMillis", T2)
                    .put("recurrenceRule", JSONObject.NULL)
                    .put("pendingOccurrenceMillis", JSONObject.NULL)
                    .put("paused", false)
                    .put("snoozedUntilMillis", JSONObject.NULL),
            )),
        )
    }
}
