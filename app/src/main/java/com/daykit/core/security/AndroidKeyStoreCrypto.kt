package com.daykit.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.InvalidKeyException
import java.security.KeyStore
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Raised when Keystore-encrypted data can no longer be decrypted because the
 * underlying hardware key is gone or invalid. This is unrecoverable for the
 * affected ciphertext — the only remedy is to reset the data it protected.
 */
class KeyUnavailableException(
    message: String,
    cause: Throwable?,
) : Exception(message, cause)

class AndroidKeyStoreCrypto {
    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    }

    fun encrypt(plaintext: ByteArray, aad: ByteArray? = null): CipherPayload {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        aad?.let(cipher::updateAAD)
        return CipherPayload(
            ciphertext = cipher.doFinal(plaintext),
            iv = cipher.iv,
        )
    }

    /**
     * Decrypts data written by [encrypt].
     *
     * Throws [KeyUnavailableException] when the Keystore entry that produced the
     * ciphertext is gone or no longer usable — a real field failure on some OEM
     * ROMs and across certain OS upgrades. Callers must handle it: decrypting with
     * a freshly generated key would silently fail the GCM tag check, so we surface
     * the cause instead of letting an opaque AEADBadTagException escape.
     */
    fun decrypt(payload: CipherPayload, aad: ByteArray? = null): ByteArray {
        val key = existingKey() ?: throw KeyUnavailableException(
            "The device keystore entry for DayKit is missing.",
            null,
        )
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, payload.iv)
            cipher.init(Cipher.DECRYPT_MODE, key, spec)
            aad?.let(cipher::updateAAD)
            cipher.doFinal(payload.ciphertext)
        } catch (e: KeyPermanentlyInvalidatedException) {
            throw KeyUnavailableException("The device keystore key was invalidated.", e)
        } catch (e: InvalidKeyException) {
            throw KeyUnavailableException("The device keystore key is unusable.", e)
        } catch (e: AEADBadTagException) {
            throw KeyUnavailableException("Stored data could not be authenticated.", e)
        }
    }

    /** True when a usable Keystore entry already exists (no side effects). */
    fun hasKey(): Boolean = runCatching { existingKey() != null }.getOrDefault(false)

    private fun existingKey(): SecretKey? {
        return runCatching {
            (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
        }.getOrNull()
    }

    private fun getOrCreateKey(): SecretKey {
        existingKey()?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "daykit.local.aes256gcm"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
