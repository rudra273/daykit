package com.daykit.feature.settings.ui

import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.daykit.core.data.AppPreferences
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppListRow
import com.daykit.core.designsystem.components.AppSwitch
import com.daykit.core.designsystem.components.AppTextButton
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
    val sections = homeToolCatalog(accents).ordered(order)

    SettingsSubPage(title = "Home Screen", onBack = onBack) {
        item {
            SettingsFootnote(
                "Hidden tools stay available through search on Home. Use the arrows to reorder tools within a group.",
            )
        }
        sections.forEachIndexed { sectionIndex, section ->
            item(key = "header-${section.title}") {
                SectionHeader(section.title, topPadding = if (sectionIndex == 0) 0.dp else Spacing.sm)
            }
            item(key = "card-${section.title}") {
                AppCard(contentPadding = PaddingValues(0.dp)) {
                    section.tools.forEachIndexed { index, tool ->
                        val visible = tool.route !in hidden
                        AppListRow(
                            headline = tool.name,
                            supporting = if (visible) null else "Hidden",
                            leadingIcon = tool.icon,
                            leadingAccent = tool.accent(),
                            onClick = { setVisible(tool.route, !visible) },
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    MoveButton(
                                        up = true,
                                        enabled = index > 0,
                                        onClick = { move(sections, section, index, index - 1) },
                                    )
                                    MoveButton(
                                        up = false,
                                        enabled = index < section.tools.lastIndex,
                                        onClick = { move(sections, section, index, index + 1) },
                                    )
                                    AppSwitch(
                                        checked = visible,
                                        onCheckedChange = { setVisible(tool.route, it) },
                                    )
                                }
                            },
                        )
                        if (index < section.tools.lastIndex) RowDivider(startIndent = Spacing.lg)
                    }
                }
            }
        }
        if (hidden.isNotEmpty() || order.isNotEmpty()) {
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
private fun MoveButton(up: Boolean, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Icon(
            imageVector = if (up) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
            contentDescription = if (up) "Move up" else "Move down",
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.extendedColors.textMuted.copy(alpha = 0.4f),
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
