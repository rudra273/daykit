package com.daykit.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Concrete radii for the clean, flat-card aesthetic.
 * extraSmall 6 (badges) · small 8 (fields/chips) · medium 10 (icon tiles) ·
 * large 14 (cards, dialogs, FAB) · extraLarge 24 (bottom-sheet top corners).
 * Card (large) stays a step above the icon tile (medium) so the nesting reads.
 */
val DayKitShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
