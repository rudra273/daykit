package com.daykit.core.designsystem

import androidx.compose.ui.unit.dp

/** Standard spacing scale — replaces the absent dimens.xml for Compose. */
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/**
 * Material's minimum touch-target size. A control may *look* smaller than this, but its
 * hit area must not be: wrap the visual in a `Box(Modifier.size(MinTouchTarget))` rather
 * than shrinking an `IconButton` with `Modifier.size(...)`, which overrides the 48dp
 * minimum `IconButton` otherwise enforces for you.
 */
val MinTouchTarget = 48.dp
