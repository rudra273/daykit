package com.daykit.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.AppleSystemGrays
import com.daykit.core.designsystem.LocalReduceMotion
import com.daykit.core.designsystem.extendedColors

private val TrackWidth = 46.dp
private val TrackHeight = 28.dp
private val ThumbSize = 24.dp
private val ThumbInset = 2.dp

/**
 * Apple iOS-proportioned toggle switch (`46dp × 28dp` pill track with a crisp `24dp`
 * pure-white elevated circular thumb).
 *
 * Fixes two Material 3 Switch flaws:
 * 1. No `.scale(0.78f)` bitmap/vector distortion.
 * 2. In Dark Mode, the unchecked track (`systemGray3` `#48484A` with a subtle rim)
 *    and pure-white `#FFFFFF` thumb remain clearly distinct against `#2C2C2E` (`systemGray5`)
 *    cards instead of blending into the card background.
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val ext = MaterialTheme.extendedColors
    val reduceMotion = LocalReduceMotion.current

    val targetTrackColor = when {
        checked && enabled -> ext.accents.blue
        checked && !enabled -> ext.accents.blue.copy(alpha = 0.42f)
        !checked && enabled -> if (ext.isDark) AppleSystemGrays.Gray3Dark else AppleSystemGrays.Gray4Light
        else -> if (ext.isDark) AppleSystemGrays.Gray4Dark else AppleSystemGrays.Gray5Light
    }

    val trackColor by animateColorAsState(
        targetValue = targetTrackColor,
        animationSpec = if (reduceMotion) snap() else tween(180),
        label = "switch track",
    )

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) TrackWidth - ThumbSize - ThumbInset else ThumbInset,
        animationSpec = if (reduceMotion) {
            snap()
        } else {
            spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMedium)
        },
        label = "switch thumb offset",
    )

    val thumbColor = when {
        enabled -> Color.White
        ext.isDark -> Color.White.copy(alpha = 0.48f)
        else -> Color.White.copy(alpha = 0.75f)
    }

    val borderColor = when {
        checked -> Color.Transparent
        ext.isDark -> Color.White.copy(alpha = if (enabled) 0.16f else 0.08f)
        else -> Color.Black.copy(alpha = if (enabled) 0.08f else 0.04f)
    }

    val interactiveModifier = if (onCheckedChange != null) {
        val interactionSource = remember { MutableInteractionSource() }
        Modifier
            .minimumInteractiveComponentSize()
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
    } else {
        Modifier
    }

    Box(
        modifier = modifier.then(interactiveModifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = TrackWidth, height = TrackHeight)
                .clip(CircleShape)
                .background(trackColor)
                .border(1.dp, borderColor, CircleShape),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(ThumbSize)
                    .shadow(
                        elevation = if (enabled) 2.dp else 0.dp,
                        shape = CircleShape,
                        clip = false,
                    )
                    .clip(CircleShape)
                    .background(thumbColor),
            )
        }
    }
}
