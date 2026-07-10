package com.daykit.core.security

import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * [ValueCipher] backed by the PIN-derived session key ([SensitiveKeyManager]).
 *
 * The key is read fresh from the manager on every call, so once the user locks
 * (MSK wiped) every encrypt/decrypt throws [SensitiveDataLockedException] rather
 * than silently using a stale key. This is what makes the vault / key store /
 * secure notes undecryptable without the PIN.
 */
class SessionValueCipher(
    private val keyManager: SensitiveKeyManager,
) : ValueCipher {

    override fun encryptString(value: String, aad: String): CipherPayload =
        encrypt(value.toByteArray(StandardCharsets.UTF_8), aad)

    override fun decryptString(payload: CipherPayload, aad: String): String =
        decrypt(payload, aad).toString(StandardCharsets.UTF_8)

    override fun encryptBytes(value: ByteArray, aad: String): CipherPayload =
        encrypt(value, aad)

    override fun decryptBytes(payload: CipherPayload, aad: String): ByteArray =
        decrypt(payload, aad)

    private fun encrypt(plaintext: ByteArray, aad: String): CipherPayload {
        // requireKey() returns a copy we own; zero it once the cipher is keyed.
        val key = keyManager.requireKey()
        try {
            val iv = ByteArray(IV_BYTES).also(secureRandom::nextBytes)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, KEY_ALGORITHM), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(aad.toByteArray(StandardCharsets.UTF_8))
            return CipherPayload(ciphertext = cipher.doFinal(plaintext), iv = iv)
        } finally {
            key.fill(0)
        }
    }

    private fun decrypt(payload: CipherPayload, aad: String): ByteArray {
        val key = keyManager.requireKey()
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, KEY_ALGORITHM), GCMParameterSpec(GCM_TAG_BITS, payload.iv))
            cipher.updateAAD(aad.toByteArray(StandardCharsets.UTF_8))
            return cipher.doFinal(payload.ciphertext)
        } finally {
            key.fill(0)
        }
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_ALGORITHM = "AES"
        const val GCM_TAG_BITS = 128
        const val IV_BYTES = 12
        val secureRandom = SecureRandom()
    }
}
