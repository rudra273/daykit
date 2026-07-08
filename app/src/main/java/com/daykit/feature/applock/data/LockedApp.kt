package com.rudra.daykit.feature.applock.data

data class LockedApp(
    val id: Long,
    val packageName: String,
    val label: String,
    val enabled: Boolean,
)
