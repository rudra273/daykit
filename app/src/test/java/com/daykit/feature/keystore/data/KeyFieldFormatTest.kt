package com.daykit.feature.keystore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyFieldFormatTest {
    @Test
    fun secretsAreMaskedUntilRevealed() {
        assertEquals("•••• 1111", KeyFieldFormat.display(KeyFieldKind.CardNumber, "4111111111111111", revealed = false))
        assertEquals("4111 1111 1111 1111", KeyFieldFormat.display(KeyFieldKind.CardNumber, "4111111111111111", revealed = true))
        assertEquals("•••• 6789", KeyFieldFormat.display(KeyFieldKind.MaskedId, "123456789", revealed = false))
        assertEquals("•••", KeyFieldFormat.display(KeyFieldKind.Pin, "123", revealed = false))
        assertEquals(KeyFieldFormat.MASK, KeyFieldFormat.display(KeyFieldKind.Secret, "hunter2", revealed = false))
        assertEquals("hunter2", KeyFieldFormat.display(KeyFieldKind.Secret, "hunter2", revealed = true))
    }

    @Test
    fun plainKindsAreNeverMasked() {
        assertEquals("me@example.com", KeyFieldFormat.display(KeyFieldKind.Text, "me@example.com", revealed = false))
        assertEquals("08/28", KeyFieldFormat.display(KeyFieldKind.Expiry, "08/28", revealed = false))
    }

    @Test
    fun cardNumberCopiesWithoutSpaces() {
        assertEquals("4111111111111111", KeyFieldFormat.copyText(KeyFieldKind.CardNumber, "4111 1111 1111 1111"))
        assertEquals("a b", KeyFieldFormat.copyText(KeyFieldKind.Secret, "a b"))
    }

    @Test
    fun sanitizesInput() {
        assertEquals("4111", KeyFieldFormat.sanitizeInput(KeyFieldKind.CardNumber, "41-1 1"))
        assertEquals("1234", KeyFieldFormat.sanitizeInput(KeyFieldKind.Pin, "12a34"))
        assertEquals("ABCDE1234F", KeyFieldFormat.sanitizeInput(KeyFieldKind.MaskedId, "abcde 1234f", uppercase = true))
    }

    @Test
    fun expiryRoundTrips() {
        assertEquals("08/28", KeyFieldFormat.expiryToStored("0828"))
        assertEquals("08", KeyFieldFormat.expiryToStored("08"))
        assertEquals("0828", KeyFieldFormat.expiryToInput("08/28"))
        assertTrue(KeyFieldFormat.isValidExpiry("12/30"))
        assertFalse(KeyFieldFormat.isValidExpiry("13/30"))
        assertFalse(KeyFieldFormat.isValidExpiry("00/30"))
        assertFalse(KeyFieldFormat.isValidExpiry("08/2"))
    }
}
