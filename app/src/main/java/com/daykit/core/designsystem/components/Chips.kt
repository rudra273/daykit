package com.daykit.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daykit.core.designsystem.AppleSystemGrays
import com.daykit.core.designsystem.LocalReduceMotion
import com.daykit.core.designsystem.asAccentContainer
import com.daykit.core.designsystem.extendedColors

/**
 * Pill filter chip. Selected = primaryContainer fill + primary text.
 * Unselected = inputField fill + onSurface. Matches the old GlassFilterButton signature.
 */
@Composable
fun FilterChipButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val container = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.extendedColors.inputField
    }
    val content = when {
        !enabled -> MaterialTheme.extendedColors.textTertiary
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 38.dp)
            .glassControl(CircleShape)
            .clip(CircleShape)
            .background(container)
            .then(
                if (selected) Modifier.border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                    CircleShape,
                ) else Modifier
            )
            .selectable(selected = selected, enabled = enabled, onClick = onClick, role = Role.Tab)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = content,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Apple HIG sliding-pill segmented control (`UISegmentedControl`).
 * Renders an inset rounded track with an elevated sliding active pill thumb.
 */
@Composable
fun SegmentedControl(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val ext = MaterialTheme.extendedColors
    val reduceMotion = LocalReduceMotion.current
    val outerShape = RoundedCornerShape(11.dp)
    val thumbShape = RoundedCornerShape(9.dp)
    val trackFill = if (ext.isDark) AppleSystemGrays.Gray6Dark else AppleSystemGrays.Gray5Light
    val thumbFill = if (ext.isDark) AppleSystemGrays.Gray3Dark else Color.White

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .clip(outerShape)
            .background(trackFill)
            .padding(2.dp),
    ) {
        val segmentWidth = maxWidth / items.size
        val safeSelected = selectedIndex.coerceIn(0, items.lastIndex)
        val thumbOffset by animateDpAsState(
            targetValue = segmentWidth * safeSelected,
            animationSpec = if (reduceMotion) {
                snap()
            } else {
                spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMedium)
            },
            label = "segmented thumb",
        )

        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .width(segmentWidth)
                .fillMaxHeight()
                .shadow(elevation = 2.dp, shape = thumbShape, clip = false)
                .clip(thumbShape)
                .background(thumbFill),
        )

        Row(Modifier.fillMaxSize()) {
            items.forEachIndexed { index, label ->
                val selected = index == safeSelected
                val textColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onSurface else ext.textMuted,
                    animationSpec = if (reduceMotion) snap() else tween(150),
                    label = "segment text",
                )
                val interactionSource = remember { MutableInteractionSource() }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(thumbShape)
                        .selectable(
                            selected = selected,
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(index) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Compact Apple-style "BETA" pill badge for experimental tools (Canvas, Image Tool).
 */
@Composable
fun BetaBadge(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.extendedColors.accents.orange
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.asAccentContainer())
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "BETA",
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
            ),
            color = accent,
            maxLines = 1,
        )
    }
}
