package com.daykit.feature.keystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyEntryDocumentTest {
    private fun field(key: String, value: String, kind: KeyFieldKind = KeyFieldKind.Text, label: String = "") =
        KeyField(key, label, kind, value)

    @Test
    fun plainKeyStaysSingleValue() {
        val document = KeyEntryDocument.single("s3cret")
        assertTrue(document.isSingleValue)
        assertEquals(KeyType.Simple, document.type)
        assertEquals("s3cret", document.fields.single { it.key == KeyType.VALUE }.value)
    }

    @Test
    fun keyWithAddedFieldNeedsADocument() {
        val document = KeyEntryDocument(
            KeyType.Simple,
            listOf(field(KeyType.VALUE, "a"), field("x_1", "b", label = "Recovery email")),
        )
        assertFalse(document.isSingleValue)
    }

    @Test
    fun typedEntryRoundTripsThroughJson() {
        val original = KeyEntryDocument(
            type = KeyType.Card,
            fields = KeyEntryDocument.normalize(
                KeyType.Card,
                listOf(
                    field("number", "4111111111111111"),
                    field("expiry", "08/28"),
                    field("x_ab", "1800 123", KeyFieldKind.Text, "Helpline"),
                ),
            ),
        )
        val decoded = KeyEntryDocument.fromJson(original.toJson())
        assertEquals(KeyType.Card, decoded.type)
        assertEquals(original.fields, decoded.fields)
        assertFalse(decoded.isSingleValue)
    }

    @Test
    fun normalizeOrdersTemplateFirstAndKeepsExtras() {
        val fields = KeyEntryDocument.normalize(
            KeyType.Login,
            listOf(field("x_1", "extra", label = "Pet name"), field("password", "pw")),
        )
        assertEquals(listOf("username", "password", "website", "x_1"), fields.map { it.key })
        assertEquals("Password", fields[1].label)
        assertEquals(KeyFieldKind.Secret, fields[1].kind)
        assertEquals("", fields[0].value)
    }

    @Test
    fun usedCodesSurviveRoundTrip() {
        val document = KeyEntryDocument(
            KeyType.Recovery,
            KeyEntryDocument.normalize(KeyType.Recovery, listOf(field(KeyType.CODES, "aaaa\nbbbb"))),
            usedCodes = setOf("aaaa"),
        )
        assertEquals(setOf("aaaa"), KeyEntryDocument.fromJson(document.toJson()).usedCodes)
    }

    @Test
    fun unknownTypeAndKindFallBackSafely() {
        val decoded = KeyEntryDocument.fromJson(
            """{"type":"spaceship","fields":[{"key":"x_1","label":"Warp code","kind":"hologram","value":"42"}]}""",
        )
        assertEquals(KeyType.Simple, decoded.type)
        // An unknown kind must stay masked, never become plain text.
        assertEquals(KeyFieldKind.Secret, decoded.fields.single { it.key == "x_1" }.kind)
    }

    @Test
    fun recoveryCodesSplitOnLinesOnly() {
        assertEquals(listOf("1234 5678", "abcd-efgh"), RecoveryCodes.parse(" 1234 5678 \n\nabcd-efgh\n1234 5678"))
    }

    @Test
    fun quickCopyPrefersNextUnusedCode() {
        val entry = KeyStoreEntry(
            id = 1, entryId = "e", name = "GitHub", label = "",
            type = KeyType.Recovery,
            fields = KeyEntryDocument.normalize(KeyType.Recovery, listOf(field(KeyType.CODES, "a\nb\nc"))),
            usedCodes = setOf("a"),
            version = 1, createdAtMillis = 0, updatedAtMillis = 0,
        )
        assertEquals("b", entry.quickCopyValue)
    }
}
