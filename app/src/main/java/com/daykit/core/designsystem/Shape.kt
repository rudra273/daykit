package com.daykit.core.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Apple HIG concentric corner radius scale (`outerRadius - padding = innerRadius`).
 * extraSmall 8 (badges) · small 10 (fields/chips) · medium 12 (icon tiles/buttons) ·
 * large 18 (cards, dialogs, FAB) · extraLarge 28 (bottom-sheet top corners & floating bars).
 */
val DayKitShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
