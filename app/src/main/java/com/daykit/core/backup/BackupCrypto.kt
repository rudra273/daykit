package com.rudra.daykit.core.backup

import android.util.Base64
import com.rudra.daykit.core.security.PasswordHasher
import org.json.JSONObject
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class BackupCrypto(
    private val passwordHasher: PasswordHasher,
) {
    fun encrypt(payload: JSONObject, password: CharArray): JSONObject {
        val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
        val iv = ByteArray(IV_BYTES).also(secureRandom::nextBytes)
        val key = passwordHasher.deriveKey(password, salt, KDF_CONTEXT)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, KEY_ALGORITHM), GCMParameterSpec(GCM_TAG_BITS, iv))
            val ciphertext = cipher.doFinal(payload.toString().toByteArray(Charsets.UTF_8))
            JSONObject()
                .put("formatVersion", FORMAT_VERSION)
                .put("cipher", CIPHER_NAME)
                .put("kdf", KDF_NAME)
                .put("kdfSalt", salt.base64())
                .put("iv", iv.base64())
                .put("ciphertext", ciphertext.base64())
        } finally {
            key.fill(0)
            password.fill('\u0000')
        }
    }

    fun decrypt(envelope: JSONObject, password: CharArray): JSONObject {
        require(envelope.getInt("formatVersion") == FORMAT_VERSION) { "Unsupported backup format" }
        require(envelope.getString("cipher") == CIPHER_NAME) { "Unsupported backup cipher" }
        require(envelope.getString("kdf") == KDF_NAME) { "Unsupported backup KDF" }

        val salt = envelope.getString("kdfSalt").decodeBase64()
        val iv = envelope.getString("iv").decodeBase64()
        val ciphertext = envelope.getString("ciphertext").decodeBase64()
        val key = passwordHasher.deriveKey(password, salt, KDF_CONTEXT)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, KEY_ALGORITHM), GCMParameterSpec(GCM_TAG_BITS, iv))
            JSONObject(cipher.doFinal(ciphertext).toString(Charsets.UTF_8))
        } finally {
            key.fill(0)
            password.fill('\u0000')
        }
    }

    private fun ByteArray.base64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.decodeBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val FORMAT_VERSION = 1
        const val CIPHER_NAME = "AES-256-GCM"
        const val KDF_NAME = "Argon2id"
        const val KDF_CONTEXT = "daykit.app.backup.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_ALGORITHM = "AES"
        const val GCM_TAG_BITS = 128
        const val SALT_BYTES = 32
        const val IV_BYTES = 12
        val secureRandom = SecureRandom()
    }
}
