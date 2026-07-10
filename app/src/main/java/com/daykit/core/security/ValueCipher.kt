package com.daykit.core.security

/**
 * App-layer authenticated encryption for sensitive values, on top of the
 * SQLCipher-encrypted database.
 *
 * Two implementations exist:
 *  - [SensitiveValueCipher]: backed by the always-available Android Keystore key.
 *    Used for data that must be readable in the background (settings).
 *  - [SessionValueCipher]: backed by the PIN-derived session key, only usable
 *    while the user is unlocked. Used for the sensitive tools (vault, key store,
 *    secure notes) so their data is not decryptable without the PIN.
 *
 * Both produce/consume the same [CipherPayload] shape, so a repository can be
 * pointed at either without changing its logic.
 */
interface ValueCipher {
    fun encryptString(value: String, aad: String): CipherPayload
    fun decryptString(payload: CipherPayload, aad: String): String
    fun encryptBytes(value: ByteArray, aad: String): CipherPayload
    fun decryptBytes(payload: CipherPayload, aad: String): ByteArray
}
