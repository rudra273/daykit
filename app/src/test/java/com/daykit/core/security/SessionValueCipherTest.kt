package com.daykit.core.security

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.AEADBadTagException

class SessionValueCipherTest {
    private var key: ByteArray? = ByteArray(32) { it.toByte() }
    private val cipher = SessionValueCipher { key?.copyOf() ?: throw SensitiveDataLockedException() }

    @Test fun stringsAndBytesRoundTrip() {
        val text = "hunter2 · ünïcödé · 🔑"
        assertEquals(text, cipher.decryptString(cipher.encryptString(text, "password"), "password"))

        val bytes = ByteArray(4096) { (it * 31).toByte() }
        assertArrayEquals(bytes, cipher.decryptBytes(cipher.encryptBytes(bytes, "blob"), "blob"))
        assertEquals("", cipher.decryptString(cipher.encryptString("", "empty"), "empty"))
    }

    @Test fun ciphertextDoesNotContainPlaintextAndIvIsFreshPerCall() {
        val a = cipher.encryptString("same value", "aad")
        val b = cipher.encryptString("same value", "aad")
        assertEquals(12, a.iv.size)
        assertFalse(a.iv.contentEquals(b.iv))
        assertFalse(a.ciphertext.contentEquals(b.ciphertext))
        assertFalse(String(a.ciphertext, Charsets.ISO_8859_1).contains("same value"))
    }

    /** A row moved to another column (or a renamed aad) must fail, not decrypt. */
    @Test(expected = AEADBadTagException::class)
    fun aadMismatchFails() {
        cipher.decryptString(cipher.encryptString("secret", "title"), "content")
    }

    @Test(expected = AEADBadTagException::class)
    fun tamperedCiphertextFails() {
        val payload = cipher.encryptString("secret", "aad")
        payload.ciphertext[0] = (payload.ciphertext[0].toInt() xor 1).toByte()
        cipher.decryptString(payload, "aad")
    }

    @Test(expected = AEADBadTagException::class)
    fun aDifferentKeyFails() {
        val payload = cipher.encryptString("secret", "aad")
        key = ByteArray(32) { 9 }
        cipher.decryptString(payload, "aad")
    }

    @Test fun lockedCipherThrowsInsteadOfUsingAStaleKey() {
        val payload = cipher.encryptString("secret", "aad")
        key = null
        assertThrows(SensitiveDataLockedException::class.java) { cipher.decryptString(payload, "aad") }
        assertThrows(SensitiveDataLockedException::class.java) { cipher.encryptString("x", "aad") }
    }

    @Test fun theKeyCopyHandedToTheCipherIsZeroedAfterUse() {
        val handedOut = mutableListOf<ByteArray>()
        val tracking = SessionValueCipher { ByteArray(32) { 5 }.also(handedOut::add) }
        tracking.decryptString(tracking.encryptString("secret", "aad"), "aad")
        assertEquals(2, handedOut.size)
        handedOut.forEach { copy -> assertTrue(copy.all { it == 0.toByte() }) }
    }
}
