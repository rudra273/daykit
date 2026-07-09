package com.daykit.feature.applock.domain

import android.graphics.drawable.Drawable

data class InstalledApp(
    val packageName: String,
    val label: String,
    val icon: Drawable? = null,
)
