package com.rudra.daykit.core.security

import java.nio.charset.StandardCharsets

class SensitiveValueCipher(
    private val keyStoreCrypto: AndroidKeyStoreCrypto,
) {
    fun encryptString(value: String, aad: String): CipherPayload {
        return keyStoreCrypto.encrypt(
            plaintext = value.toByteArray(StandardCharsets.UTF_8),
            aad = aad.toByteArray(StandardCharsets.UTF_8),
        )
    }

    fun decryptString(payload: CipherPayload, aad: String): String {
        val plaintext = keyStoreCrypto.decrypt(
            payload = payload,
            aad = aad.toByteArray(StandardCharsets.UTF_8),
        )
        return plaintext.toString(StandardCharsets.UTF_8)
    }
}
