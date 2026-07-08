package com.rudra.daykit.core.security

import android.content.Context
import android.util.Base64
import androidx.core.content.edit

class CredentialRepository(
    context: Context,
    private val passwordHasher: PasswordHasher,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasCredential(): Boolean {
        return prefs.contains(KEY_SALT) && prefs.contains(KEY_HASH)
    }

    fun saveCredential(secret: CharArray) {
        try {
            val salt = passwordHasher.newSalt()
            val hash = passwordHasher.hash(secret, salt)
            prefs.edit {
                putString(KEY_SALT, salt.encodeBase64())
                putString(KEY_HASH, hash.encodeBase64())
            }
        } finally {
            secret.fill('\u0000')
        }
    }

    fun verify(secret: CharArray): Boolean {
        return try {
            val salt = prefs.getString(KEY_SALT, null)?.decodeBase64() ?: return false
            val expectedHash = prefs.getString(KEY_HASH, null)?.decodeBase64() ?: return false
            passwordHasher.matches(secret, salt, expectedHash)
        } finally {
            secret.fill('\u0000')
        }
    }

    private fun ByteArray.encodeBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.decodeBase64(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private companion object {
        const val PREFS_NAME = "daykit_credential_store"
        const val KEY_SALT = "pin_salt"
        const val KEY_HASH = "pin_hash"
    }
}
