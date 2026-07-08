package com.daykit.core.security

import com.lambdapioneer.argon2kt.Argon2Kt
import com.lambdapioneer.argon2kt.Argon2Mode
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

class PasswordHasher {
    private val argon2 = Argon2Kt()

    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)

    fun hash(secret: CharArray, salt: ByteArray): ByteArray {
        val password = secret.concatToString().toByteArray(StandardCharsets.UTF_8)
        return try {
            argon2.hash(
                mode = Argon2Mode.ARGON2_ID,
                password = password,
                salt = salt,
                tCostInIterations = ARGON2_ITERATIONS,
                mCostInKibibyte = ARGON2_MEMORY_KIB,
                parallelism = ARGON2_PARALLELISM,
                hashLengthInBytes = HASH_BYTES,
            ).encodedOutputAsString().toByteArray(StandardCharsets.UTF_8)
        } finally {
            password.fill(0)
        }
    }

    fun deriveKey(secret: CharArray, salt: ByteArray, context: String, keyBytes: Int = HASH_BYTES): ByteArray {
        require(keyBytes <= HASH_BYTES) { "Argon2id key length cannot exceed $HASH_BYTES bytes" }
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(hash(secret, salt))
        digest.update(context.toByteArray(StandardCharsets.UTF_8))
        return digest.digest().copyOf(keyBytes)
    }

    fun matches(secret: CharArray, salt: ByteArray, expectedHash: ByteArray): Boolean {
        val password = secret.concatToString().toByteArray(StandardCharsets.UTF_8)
        return try {
            argon2.verify(
                mode = Argon2Mode.ARGON2_ID,
                encoded = expectedHash.toString(StandardCharsets.UTF_8),
                password = password,
            )
        } finally {
            password.fill(0)
        }
    }

    private companion object {
        const val ARGON2_ITERATIONS = 3
        const val ARGON2_MEMORY_KIB = 64 * 1024
        const val ARGON2_PARALLELISM = 2
        const val HASH_BYTES = 32
        const val SALT_BYTES = 32
        val secureRandom = SecureRandom()
    }
}
