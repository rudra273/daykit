package com.daykit.core.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

/**
 * Round-trips through the always-available Keystore cipher used for settings.
 * Uses its own Keystore alias, so running it on a phone never touches the real
 * DayKit key or any stored setting.
 */
@RunWith(AndroidJUnit4::class)
class SensitiveValueCipherTest {
    private val cipher = SensitiveValueCipher(AndroidKeyStoreCrypto(keyAlias = TEST_ALIAS))

    @Before
    @After
    fun wipe() {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(TEST_ALIAS)
    }

    @Test
    fun stringsAndBytesRoundTrip() {
        val text = "setting value · ünïcödé"
        assertEquals(text, cipher.decryptString(cipher.encryptString(text, "key_a"), "key_a"))

        val bytes = ByteArray(2048) { (it * 7).toByte() }
        assertArrayEquals(bytes, cipher.decryptBytes(cipher.encryptBytes(bytes, "blob"), "blob"))
    }

    @Test
    fun ivIsFreshPerCall() {
        val a = cipher.encryptString("same", "aad")
        val b = cipher.encryptString("same", "aad")
        assertFalse(a.iv.contentEquals(b.iv))
        assertFalse(a.ciphertext.contentEquals(b.ciphertext))
    }

    /** A value stored under one setting key must not decrypt under another. */
    @Test
    fun aadMismatchFails() {
        val payload = cipher.encryptString("secret", "key_a")
        assertThrows(KeyUnavailableException::class.java) { cipher.decryptString(payload, "key_b") }
    }

    @Test
    fun tamperedCiphertextFails() {
        val payload = cipher.encryptString("secret", "aad")
        payload.ciphertext[0] = (payload.ciphertext[0].toInt() xor 1).toByte()
        assertThrows(KeyUnavailableException::class.java) { cipher.decryptString(payload, "aad") }
    }

    @Test
    fun aMissingKeyIsReportedRatherThanSilentlyRecreated() {
        val payload = cipher.encryptString("secret", "aad")
        wipe()
        assertThrows(KeyUnavailableException::class.java) { cipher.decryptString(payload, "aad") }
    }

    private companion object {
        const val TEST_ALIAS = "daykit.test.sensitive.cipher"
    }
}
