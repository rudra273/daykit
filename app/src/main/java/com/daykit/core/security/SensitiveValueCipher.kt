package com.daykit.core.security

import java.nio.charset.StandardCharsets

class SensitiveValueCipher(
    private val keyStoreCrypto: AndroidKeyStoreCrypto,
) : ValueCipher {
    override fun encryptString(value: String, aad: String): CipherPayload {
        return keyStoreCrypto.encrypt(
            plaintext = value.toByteArray(StandardCharsets.UTF_8),
            aad = aad.toByteArray(StandardCharsets.UTF_8),
        )
    }

    override fun decryptString(payload: CipherPayload, aad: String): String {
        val plaintext = keyStoreCrypto.decrypt(
            payload = payload,
            aad = aad.toByteArray(StandardCharsets.UTF_8),
        )
        return plaintext.toString(StandardCharsets.UTF_8)
    }

    override fun encryptBytes(value: ByteArray, aad: String): CipherPayload {
        return keyStoreCrypto.encrypt(
            plaintext = value,
            aad = aad.toByteArray(StandardCharsets.UTF_8),
        )
    }

    override fun decryptBytes(payload: CipherPayload, aad: String): ByteArray {
        return keyStoreCrypto.decrypt(
            payload = payload,
            aad = aad.toByteArray(StandardCharsets.UTF_8),
        )
    }
}
