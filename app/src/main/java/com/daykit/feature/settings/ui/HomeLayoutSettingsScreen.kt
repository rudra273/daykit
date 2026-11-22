package com.daykit.feature.settings.ui

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.daykit.core.data.AppPreferences
import com.daykit.core.data.HomeCardSize
import com.daykit.core.data.HomeColumns
import com.daykit.core.designsystem.MinTouchTarget
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppListRow
import com.daykit.core.designsystem.components.AppSwitch
import com.daykit.core.designsystem.components.AppTextButton
import com.daykit.core.designsystem.components.FilterChipButton
import com.daykit.core.designsystem.components.RowDivider
import com.daykit.core.designsystem.components.SectionHeader
import com.daykit.core.designsystem.extendedColors
import com.daykit.feature.home.ui.ToolSection
import com.daykit.feature.home.ui.homeToolCatalog
import com.daykit.feature.home.ui.ordered

@Composable
fun HomeLayoutSettingsScreen(onBack: () -> Unit) {
    val accents = MaterialTheme.extendedColors.accents
    val hidden by AppPreferences.rememberPreference(AppPreferences.KEY_HOME_HIDDEN_TOOLS) {
        AppPreferences.homeHiddenTools
    }
    val order by AppPreferences.rememberPreference(AppPreferences.KEY_HOME_TOOL_ORDER) {
        AppPreferences.homeToolOrder
    }
    val cardSize by AppPreferences.rememberPreference(AppPreferences.KEY_HOME_CARD_SIZE) {
        AppPreferences.homeCardSize
    }
    val columns by AppPreferences.rememberPreference(AppPreferences.KEY_HOME_COLUMNS) {
        AppPreferences.homeColumns
    }
    val sections = homeToolCatalog(accents).ordered(order)

    SettingsSubPage(title = "Home Screen", onBack = onBack) {
        item(key = "header-view-mode") {
            SectionHeader("Columns", topPadding = 0.dp)
        }
        item(key = "view-mode-selector") {
            AppCard(contentPadding = PaddingValues(Spacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    HomeColumns.OPTIONS.forEach { count ->
                        FilterChipButton(
                            text = "$count",
                            selected = columns == count,
                            modifier = Modifier.weight(1f),
                            onClick = { AppPreferences.homeColumns = count },
                        )
                    }
                }
            }
        }
        item(key = "header-card-size") {
            SectionHeader("Card size", topPadding = Spacing.sm)
        }
        item(key = "card-size-selector") {
            AppCard(contentPadding = PaddingValues(Spacing.md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    HomeCardSize.entries.forEach { size ->
                        FilterChipButton(
                            text = size.label,
                            selected = cardSize == size,
                            modifier = Modifier.weight(1f),
                            onClick = { AppPreferences.homeCardSize = size },
                        )
                    }
                }
            }
        }
        item {
            SettingsFootnote(
                "Hidden tools stay available through search on Home. Drag the handle to reorder tools within a group.",
            )
        }
        sections.forEach { section ->
            item(key = "header-${section.title}") {
                SectionHeader(section.title, topPadding = Spacing.sm)
            }
            item(key = "card-${section.title}") {
                DraggableToolSectionCard(
                    sections = sections,
                    section = section,
                    hidden = hidden,
                )
            }
        }
        if (hidden.isNotEmpty() || order.isNotEmpty() || cardSize != HomeCardSize.Default || columns != HomeColumns.DEFAULT) {
            item(key = "reset") {
                AppTextButton(
                    text = "Reset to default layout",
                    onClick = AppPreferences::resetHomeLayout,
                )
            }
        }
    }
}

@Composable
private fun DraggableToolSectionCard(
    sections: List<ToolSection>,
    section: ToolSection,
    hidden: Set<String>,
) {
    val defaultRowHeightPx = with(LocalDensity.current) { 58.dp.toPx() }
    var rowHeightPx by remember { mutableFloatStateOf(defaultRowHeightPx) }
    var draggingRoute by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    val currentSections by rememberUpdatedState(sections)
    val currentSection by rememberUpdatedState(section)

    AppCard(contentPadding = PaddingValues(0.dp)) {
        section.tools.forEachIndexed { index, tool ->
            androidx.compose.runtime.key(tool.route) {
                val visible = tool.route !in hidden
                val isDragging = draggingRoute == tool.route

                AppListRow(
                    headline = tool.name,
                    supporting = if (visible) null else "Hidden",
                    leadingIcon = tool.icon,
                    leadingAccent = tool.accent(),
                    onClick = { setVisible(tool.route, !visible) },
                    modifier = Modifier
                        .zIndex(if (isDragging) 1f else 0f)
                        .onSizeChanged { size ->
                            if (size.height > 0) rowHeightPx = size.height.toFloat()
                        }
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffsetY else 0f
                            scaleX = if (isDragging) 1.02f else 1f
                            scaleY = if (isDragging) 1.02f else 1f
                            shadowElevation = if (isDragging) 12f else 0f
                        },
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AppSwitch(
                                checked = visible,
                                onCheckedChange = { setVisible(tool.route, it) },
                            )
                            DragHandleButton(
                                route = tool.route,
                                onDragStart = {
                                    draggingRoute = tool.route
                                    dragOffsetY = 0f
                                },
                                onDrag = { deltaY ->
                                    dragOffsetY += deltaY
                                    val step = rowHeightPx.coerceAtLeast(1f)
                                    val currentIdx = currentSection.tools.indexOfFirst { it.route == tool.route }
                                    if (currentIdx >= 0) {
                                        if (dragOffsetY > step * 0.6f && currentIdx < currentSection.tools.lastIndex) {
                                            move(currentSections, currentSection, currentIdx, currentIdx + 1)
                                            dragOffsetY -= step
                                        } else if (dragOffsetY < -step * 0.6f && currentIdx > 0) {
                                            move(currentSections, currentSection, currentIdx, currentIdx - 1)
                                            dragOffsetY += step
                                        }
                                    }
                                },
                                onDragEnd = {
                                    draggingRoute = null
                                    dragOffsetY = 0f
                                },
                            )
                        }
                    },
                )
                if (index < section.tools.lastIndex) RowDivider()
            }
        }
    }
}

@Composable
private fun DragHandleButton(
    route: String,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
) {
    val latestOnDragStart by rememberUpdatedState(onDragStart)
    val latestOnDrag by rememberUpdatedState(onDrag)
    val latestOnDragEnd by rememberUpdatedState(onDragEnd)

    Box(
        modifier = Modifier
            .size(MinTouchTarget)
            .pointerInput(route) {
                detectVerticalDragGestures(
                    onDragStart = { latestOnDragStart() },
                    onDragEnd = { latestOnDragEnd() },
                    onDragCancel = { latestOnDragEnd() },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        latestOnDrag(dragAmount)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.DragHandle,
            contentDescription = "Drag to reorder",
            tint = MaterialTheme.extendedColors.textMuted,
        )
    }
}

private fun setVisible(route: String, visible: Boolean) {
    val hidden = AppPreferences.homeHiddenTools
    AppPreferences.homeHiddenTools = if (visible) hidden - route else hidden + route
}

/** Swaps two tools within [section] and saves the full order of every tool. */
private fun move(sections: List<ToolSection>, section: ToolSection, from: Int, to: Int) {
    val reordered = section.tools.toMutableList().apply { add(to, removeAt(from)) }
    AppPreferences.homeToolOrder = sections.flatMap { current ->
        if (current.title == section.title) reordered else current.tools
    }.map { it.route }
}
