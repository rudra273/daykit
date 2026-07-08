package com.daykit.feature.keystore.data

data class KeyStoreEntry(
    val id: Long,
    val entryId: String,
    val name: String,
    val label: String,
    val value: String,
    val version: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
