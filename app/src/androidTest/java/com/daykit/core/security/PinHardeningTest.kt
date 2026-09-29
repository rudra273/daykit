package com.daykit.core.security

import android.content.Context
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Covers the hardware-bound PIN derivation and its upgrade from Argon2id-only
 * installs. Uses its own prefs files and Keystore alias, so running it on a phone
 * never touches the real DayKit credential or master key.
 */
@RunWith(AndroidJUnit4::class)
class PinHardeningTest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val hasher = PasswordHasher()
    private val binding = PinHardwareBinding(keyAlias = "daykit.test.pin.binding")

    private fun keyManager() = SensitiveKeyManager(context, hasher, binding, prefsName = KEY_PREFS)
    private fun credentials() = CredentialRepository(context, hasher, binding, prefsName = CREDENTIAL_PREFS)

    @Before
    @After
    fun wipe() {
        context.getSharedPreferences(KEY_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(CREDENTIAL_PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        binding.clear()
    }

    @Test
    fun freshSetupIsHardwareBoundAndUnlocks() {
        val manager = keyManager()
        manager.initialize(PIN.toCharArray())
        val msk = manager.requireKey()
        manager.lock()

        assertEquals(2, keyPrefs().getInt("wrap_version", 1))
        assertTrue(binding.hasKey())
        assertFalse(manager.unlock("000000".toCharArray()))
        assertTrue(manager.unlock(PIN.toCharArray()))
        assertArrayEquals(msk, manager.requireKey())
    }

    @Test
    fun legacyWrapUpgradesOnUnlockAndKeepsTheSameMasterKey() {
        val msk = ByteArray(32).also(SecureRandom()::nextBytes)
        writeLegacyWrap(PIN, msk)
        val manager = keyManager()

        assertTrue(manager.unlock(PIN.toCharArray()))
        assertArrayEquals(msk, manager.requireKey())
        assertEquals(2, keyPrefs().getInt("wrap_version", 1))

        // The upgraded wrap must still open with the same PIN, from a cold start.
        manager.lock()
        val restarted = keyManager()
        assertTrue(restarted.unlock(PIN.toCharArray()))
        assertArrayEquals(msk, restarted.requireKey())
    }

    @Test
    fun hardwareBoundWrapFailsWithoutTheBindingKey() {
        val manager = keyManager()
        manager.initialize(PIN.toCharArray())
        manager.lock()
        binding.clear()

        // Must fail closed, never mint a new key that derives different bytes.
        assertFalse(manager.unlock(PIN.toCharArray()))
        assertFalse(binding.hasKey())
    }

    @Test
    fun rewrapMovesToTheNewPinOnly() {
        val manager = keyManager()
        manager.initialize(PIN.toCharArray())
        val msk = manager.requireKey()
        assertTrue(manager.rewrap(PIN.toCharArray(), NEW_PASSWORD.toCharArray()))
        manager.lock()

        assertFalse(manager.unlock(PIN.toCharArray()))
        assertTrue(manager.unlock(NEW_PASSWORD.toCharArray()))
        assertArrayEquals(msk, manager.requireKey())
    }

    @Test
    fun legacyVerifierUpgradesOnSuccessfulVerify() {
        val salt = hasher.newSalt()
        credentialPrefs().edit()
            .putString("pin_salt", salt.b64())
            .putString("pin_hash", hasher.hash(PIN.toCharArray(), salt).b64())
            .putInt("pin_length", PIN.length)
            .commit()
        val repository = credentials()

        assertEquals(PinVerifyResult.Wrong, repository.verify("000000".toCharArray()))
        assertEquals(1, credentialPrefs().getInt("pin_hash_version", 1))
        assertEquals(PinVerifyResult.Success, repository.verify(PIN.toCharArray()))
        assertEquals(2, credentialPrefs().getInt("pin_hash_version", 1))
        assertEquals(PinVerifyResult.Success, repository.verify(PIN.toCharArray()))
        assertEquals(CredentialKind.Pin, repository.credentialKind())
    }

    @Test
    fun passwordCredentialRoundTrips() {
        val repository = credentials()
        repository.saveCredential(NEW_PASSWORD.toCharArray(), CredentialKind.Password)

        assertEquals(CredentialKind.Password, repository.credentialKind())
        assertEquals(PinVerifyResult.Wrong, repository.verify("correct horse".toCharArray()))
        assertEquals(PinVerifyResult.Success, repository.verify(NEW_PASSWORD.toCharArray()))
    }

    @Test
    fun staleVerifierAfterInterruptedPinChangeHeals() {
        val repository = credentials()
        val manager = keyManager()
        repository.saveCredential(PIN.toCharArray(), CredentialKind.Pin)
        manager.initialize(PIN.toCharArray())
        // Simulate dying between rewrap and saveCredential.
        assertTrue(manager.rewrap(PIN.toCharArray(), NEW_PIN.toCharArray()))
        manager.lock()

        assertEquals(PinVerifyResult.Success, unlockWithMasterPin(repository, manager, NEW_PIN))
        assertTrue(manager.isUnlocked())
        // The verifier now matches the new PIN on its own.
        assertEquals(PinVerifyResult.Success, repository.verify(NEW_PIN.toCharArray()))
    }

    private fun writeLegacyWrap(pin: String, msk: ByteArray) {
        val salt = hasher.newSalt()
        val wrappingKey = hasher.deriveKey(pin.toCharArray(), salt, "daykit.sensitive.msk.v1")
        val iv = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(wrappingKey, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD("daykit.sensitive.msk.wrap".toByteArray())
        keyPrefs().edit()
            .putString("msk_salt", salt.b64())
            .putString("wrapped_msk", cipher.doFinal(msk).b64())
            .putString("wrapped_msk_iv", iv.b64())
            .commit()
    }

    private fun keyPrefs() = context.getSharedPreferences(KEY_PREFS, Context.MODE_PRIVATE)
    private fun credentialPrefs() = context.getSharedPreferences(CREDENTIAL_PREFS, Context.MODE_PRIVATE)
    private fun ByteArray.b64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private companion object {
        const val KEY_PREFS = "test_pin_hardening_key"
        const val CREDENTIAL_PREFS = "test_pin_hardening_credential"
        const val PIN = "482915"
        const val NEW_PIN = "730164"
        const val NEW_PASSWORD = "Tr1cky-passphrase"
    }
}
