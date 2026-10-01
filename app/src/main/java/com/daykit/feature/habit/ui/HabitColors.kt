package com.daykit.feature.habit.ui

import androidx.compose.ui.graphics.Color

/** The swatches a habit can pick from; shared with the Today screen. */
internal val habitPalette = listOf(
    Color(0xFF22C55E),
    Color(0xFF38BDF8),
    Color(0xFFF59E0B),
    Color(0xFFEC4899),
    Color(0xFFA78BFA),
    Color(0xFF14B8A6),
    Color(0xFFFB7185),
    Color(0xFF818CF8),
    Color(0xFFEAB308),
    Color(0xFF2DD4BF),
)

internal fun habitColor(index: Int): Color = habitPalette[index.mod(habitPalette.size)]
