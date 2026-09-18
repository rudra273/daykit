package com.daykit.core.designsystem.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import com.daykit.core.designsystem.extendedColors

/** An even translucent scrim; a gradient can show banding over the system bars. */
@Composable
fun glassChromeBrush(): Brush {
    val base = MaterialTheme.colorScheme.background
    val dark = MaterialTheme.extendedColors.isDark
    return SolidColor(base.copy(alpha = if (dark) 0.94f else 0.92f))
}
