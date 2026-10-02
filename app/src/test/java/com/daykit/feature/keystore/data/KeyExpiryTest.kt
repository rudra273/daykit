package com.daykit.feature.keystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class KeyExpiryTest {
    private val today = LocalDate.of(2026, 10, 2)

    private fun entry(type: KeyType, vararg values: Pair<String, String>, deleted: Boolean = false) = KeyStoreEntry(
        id = 1, entryId = "e", name = "n", label = "",
        type = type,
        fields = KeyEntryDocument.normalize(
            type,
            values.map { (key, value) -> KeyField(key, "", KeyFieldKind.Text, value) },
        ),
        version = 1, createdAtMillis = 0, updatedAtMillis = 0,
        deletedAtMillis = if (deleted) 1L else null,
    )

    @Test
    fun cardIsValidThroughTheEndOfItsMonth() {
        assertEquals(LocalDate.of(2028, 8, 31), KeyExpiry.cardExpiry("08/28"))
        assertEquals(LocalDate.of(2028, 2, 29), KeyExpiry.cardExpiry("02/28"))
        assertNull(KeyExpiry.cardExpiry("13/28"))
        assertNull(KeyExpiry.cardExpiry(""))
    }

    @Test
    fun cardRemindsOnTheFirstOfItsExpiryMonth() {
        val reminder = KeyExpiry.reminderFor(entry(KeyType.Card, "expiry" to "08/28"), today)!!
        assertEquals(KeyExpiry.Kind.Card, reminder.kind)
        assertEquals(LocalDate.of(2028, 8, 1), reminder.remindOn)
        assertEquals(LocalDate.of(2028, 8, 31), reminder.expiresOn)
    }

    @Test
    fun documentGetsAMonthPassportSixMonths() {
        val licence = entry(KeyType.Identity, "doc_type" to "Driving licence", "expires" to "2030-05-20")
        assertEquals(LocalDate.of(2030, 4, 20), KeyExpiry.reminderFor(licence, today)!!.remindOn)

        val passport = entry(KeyType.Identity, "doc_type" to "Passport", "expires" to "2030-05-20")
        assertEquals(LocalDate.of(2029, 11, 20), KeyExpiry.reminderFor(passport, today)!!.remindOn)
    }

    @Test
    fun noReminderOncePastOrWithoutExpiry() {
        // Expires this month: the reminder day (Oct 1) has passed, so saving must not notify.
        assertNull(KeyExpiry.reminderFor(entry(KeyType.Card, "expiry" to "10/26"), today))
        assertNull(KeyExpiry.reminderFor(entry(KeyType.Card), today))
        assertNull(KeyExpiry.reminderFor(entry(KeyType.Login, "username" to "me"), today))
        assertNull(KeyExpiry.reminderFor(entry(KeyType.Card, "expiry" to "08/28", deleted = true), today))
    }

    @Test
    fun expired() {
        assertTrue(KeyExpiry.isExpired(entry(KeyType.Card, "expiry" to "09/26"), today))
        assertFalse(KeyExpiry.isExpired(entry(KeyType.Card, "expiry" to "10/26"), today))
        assertTrue(KeyExpiry.isExpired(entry(KeyType.Identity, "expires" to "2026-10-01"), today))
        assertFalse(KeyExpiry.isExpired(entry(KeyType.Identity, "expires" to "2026-10-02"), today))
    }
}
