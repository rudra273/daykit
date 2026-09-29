package com.daykit.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * Binds PIN-derived secrets to this device's secure hardware.
 *
 * Argon2id alone leaves a 6-digit PIN crackable offline: anyone who copies the
 * salt and wrapped MSK can try all 10^6 PINs on a GPU rig in hours. Running the
 * Argon2id output through an HMAC keyed by a non-exportable Keystore key (StrongBox
 * when present) means every guess needs this device's TEE, so guessing can't be
 * moved off the phone or parallelised.
 *
 * This is an extra step inside the PIN derivation, not a wrapping of the MSK: the
 * HMAC input is useless without the PIN, so an always-available key here does not
 * weaken the "MSK is only ever PIN-wrapped" invariant.
 *
 * Losing this key makes PIN-bound data unrecoverable, which matches the existing
 * dependency: the SQLCipher passphrase is Keystore-wrapped too, so a Keystore wipe
 * already makes the database unreadable.
 */
class PinHardwareBinding(
    // Overridable only so instrumented tests never touch the real key.
    private val keyAlias: String = KEY_ALIAS,
) {
    /** True when the binding key exists. Never creates it. */
    fun hasKey(): Boolean = runCatching { existingKey() != null }.getOrDefault(false)

    /**
     * HMAC-SHA256 of [input] under the hardware key. With [createIfMissing] false
     * a missing key throws [KeyUnavailableException] rather than silently minting a
     * new key that would derive different bytes forever after.
     */
    fun bind(input: ByteArray, createIfMissing: Boolean): ByteArray {
        val key = existingKey()
            ?: if (createIfMissing) createKey() else throw KeyUnavailableException(
                "The hardware key protecting your PIN is missing.",
                null,
            )
        val mac = Mac.getInstance(MAC_ALGORITHM)
        mac.init(key)
        return mac.doFinal(input)
    }

    fun clear() {
        runCatching { keyStore().deleteEntry(keyAlias) }
    }

    private fun existingKey(): SecretKey? =
        (keyStore().getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey

    private fun createKey(): SecretKey =
        try {
            generate(strongBox = true)
        } catch (_: StrongBoxUnavailableException) {
            generate(strongBox = false)
        }

    private fun generate(strongBox: Boolean): SecretKey {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(keyAlias, KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setIsStrongBoxBacked(strongBox)
                .build(),
        )
        return generator.generateKey()
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "daykit.pin.binding.v1"
        const val MAC_ALGORITHM = "HmacSHA256"
    }
}
