package com.daykit.core.designsystem.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import com.daykit.core.designsystem.extendedColors

/** A translucent page-colored scrim for content that scrolls beneath app chrome. */
@Composable
fun glassChromeBrush(bottomBar: Boolean = false): Brush {
    val base = MaterialTheme.colorScheme.background
    val dark = MaterialTheme.extendedColors.isDark
    val nearContent = base.copy(alpha = if (dark) 0.90f else 0.88f)
    val outerEdge = base.copy(alpha = if (dark) 0.97f else 0.96f)
    return Brush.verticalGradient(
        if (bottomBar) listOf(nearContent, outerEdge) else listOf(outerEdge, nearContent),
    )
}
