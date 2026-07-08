package com.daykit.core.security

data class CipherPayload(
    val ciphertext: ByteArray,
    val iv: ByteArray,
)
