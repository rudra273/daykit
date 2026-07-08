package com.rudra.daykit.feature.notes.data

data class SecureNote(
    val id: Long,
    val noteId: String,
    val title: String,
    val content: String,
    val labels: String,
    val version: Int,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
)
