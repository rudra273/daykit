package com.daykit.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CredentialRulesTest {
    @Test
    fun `pin input keeps digits only up to the maximum`() {
        assertEquals("123456789012", CredentialRepository.sanitize("12a34-5678 9012345", CredentialKind.Pin))
    }

    @Test
    fun `password input keeps symbols and spaces but drops control characters`() {
        assertEquals("Ab 1!", CredentialRepository.sanitize("Ab 1!\n", CredentialKind.Password))
        assertEquals(64, CredentialRepository.sanitize("x".repeat(100), CredentialKind.Password).length)
    }

    @Test
    fun `new pin needs six digits`() {
        assertNotNull(CredentialRepository.newCredentialError("12345", CredentialKind.Pin))
        assertNull(CredentialRepository.newCredentialError("123456", CredentialKind.Pin))
    }

    @Test
    fun `new password needs length and a mix of letters and non-letters`() {
        assertNotNull(CredentialRepository.newCredentialError("Ab1!", CredentialKind.Password))
        assertNotNull(CredentialRepository.newCredentialError("onlyletters", CredentialKind.Password))
        assertNotNull(CredentialRepository.newCredentialError("12345678", CredentialKind.Password))
        assertNull(CredentialRepository.newCredentialError("letters42", CredentialKind.Password))
    }
}
