package com.daykit.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Concrete radii for the clean, elevated-card aesthetic.
 * extraSmall 6 (badges) · small 8 (fields/chips) · medium 12 (buttons, icon tiles) ·
 * large 16 (cards, dialogs, FAB) · extraLarge 24 (bottom-sheet top corners).
 */
val DayKitShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
