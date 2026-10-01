package com.daykit.core.data

import android.content.Context
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.daykit.core.security.SessionValueCipher
import com.daykit.feature.keystore.data.KeyStoreRepository
import com.daykit.feature.notes.data.SecureNoteRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * Recently deleted at the repository level, on an in-memory database with its
 * own session key (never touches real DayKit data).
 */
@RunWith(AndroidJUnit4::class)
class RecentlyDeletedRepositoryTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val db = Room.inMemoryDatabaseBuilder(context, DayKitDatabase::class.java).allowMainThreadQueries().build()
    private val key = ByteArray(32).also(SecureRandom()::nextBytes)
    private val cipher = SessionValueCipher { key.copyOf() }
    private val notes = SecureNoteRepository(db.secureNoteDao(), cipher)
    private val keys = KeyStoreRepository(db.keyStoreEntryDao(), cipher)

    @After
    fun close() = db.close()

    @Test
    fun deleteHidesTheItemAndRestoreBringsItBackIntact() = runBlocking {
        val id = notes.addNote("Title", "Body", "home")
        notes.deleteNote(id)

        assertTrue(notes.observeNotes().first().isEmpty())
        assertTrue(notes.exportRecords().isEmpty())
        val trashed = notes.observeTrash().first().single()
        assertNotNull(trashed.deletedAtMillis)

        notes.restoreNote(id)
        val restored = notes.observeNotes().first().single()
        assertEquals("Body", restored.content)
        assertTrue(notes.observeTrash().first().isEmpty())
    }

    @Test
    fun purgeRemovesOnlyItemsPastTheRetentionWindow() = runBlocking {
        keys.addEntry("old", "", "v1")
        keys.addEntry("recent", "", "v2")
        val all = keys.observeEntries().first()
        all.forEach { keys.deleteEntry(it.entryId) }
        val now = System.currentTimeMillis()

        // Nothing is old enough yet.
        keys.purgeExpiredTrash(now)
        assertEquals(2, keys.observeTrash().first().size)

        // 31 days later both are past the window.
        keys.purgeExpiredTrash(now + TimeUnit.DAYS.toMillis(31))
        assertTrue(keys.observeTrash().first().isEmpty())
        assertTrue(keys.observeEntries().first().isEmpty())
    }

    @Test
    fun emptyTrashLeavesLiveItemsAlone() = runBlocking {
        val keep = notes.addNote("keep", "", "")
        val drop = notes.addNote("drop", "", "")
        notes.deleteNote(drop)
        notes.emptyTrash()
        assertEquals(listOf(keep), notes.observeNotes().first().map { it.noteId })
        assertTrue(notes.observeTrash().first().isEmpty())
    }
}
