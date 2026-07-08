package com.daykit.core.data

import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import com.daykit.core.security.AndroidKeyStoreCrypto
import com.daykit.core.security.CipherPayload
import java.security.SecureRandom

class DatabasePassphraseProvider(
    context: Context,
    private val keyStoreCrypto: AndroidKeyStoreCrypto,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getOrCreatePassphrase(): ByteArray {
        val encrypted = prefs.getString(KEY_CIPHERTEXT, null)
        val iv = prefs.getString(KEY_IV, null)
        if (encrypted != null && iv != null) {
            return keyStoreCrypto.decrypt(
                payload = CipherPayload(
                    ciphertext = encrypted.decodeBase64(),
                    iv = iv.decodeBase64(),
                ),
                aad = AAD,
            )
        }

        val passphrase = ByteArray(PASSPHRASE_BYTES).also(SecureRandom()::nextBytes)
        val payload = keyStoreCrypto.encrypt(passphrase, AAD)
        prefs.edit {
            putString(KEY_CIPHERTEXT, payload.ciphertext.encodeBase64())
            putString(KEY_IV, payload.iv.encodeBase64())
        }
        return passphrase
    }

    private fun ByteArray.encodeBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.decodeBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val PREFS_NAME = "daykit_sqlcipher_passphrase"
        const val KEY_CIPHERTEXT = "ciphertext"
        const val KEY_IV = "iv"
        const val PASSPHRASE_BYTES = 32
        val AAD = "daykit.secure.database.passphrase".toByteArray()
    }
}
