package com.daykit.feature.focus.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.asAccentContainer
import com.daykit.core.designsystem.components.AppBottomSheet
import com.daykit.core.designsystem.components.AppCheckbox
import com.daykit.core.designsystem.components.AppIconOrMonogram
import com.daykit.core.designsystem.components.AppTextButton
import com.daykit.core.designsystem.components.AppTextField
import com.daykit.core.designsystem.components.EmptyState
import com.daykit.core.designsystem.components.LoadingIndicator
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.extendedColors
import com.daykit.feature.applock.domain.InstalledApp
import com.daykit.feature.focus.data.FocusGroup

/**
 * Create/edit sheet for an app set. A set is a name, a color, and some apps, so
 * a sheet is the right size — the routine editor, which has many more fields,
 * is a full page instead (matching how habits are edited).
 *
 * Apps are ordered by [weeklyUsage]. An app may belong to several sets; any
 * other set it is already in shows as a colored tag on its row ([otherSets]),
 * so overlap is visible rather than prevented.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FocusGroupEditorSheet(
    existing: FocusGroup?,
    apps: List<InstalledApp>?,
    otherSets: List<FocusGroup>,
    weeklyUsage: Map<String, Long>,
    onSave: (name: String, colorIndex: Int, packageNames: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var colorIndex by remember { mutableStateOf(existing?.colorIndex ?: 0) }
    val selected = remember {
        mutableStateListOf<String>().apply { addAll(existing?.packageNames.orEmpty()) }
    }
    var query by remember { mutableStateOf("") }

    val palette = focusSetPalette()

    val setsByPackage = remember(otherSets) {
        otherSets.flatMap { set -> set.packageNames.map { it to set } }
            .groupBy({ it.first }, { it.second })
    }

    // Not keyed on the selection, so rows don't jump while ticking boxes.
    val visible = remember(apps, query, weeklyUsage) {
        apps.orEmpty()
            .filter {
                query.isBlank() ||
                    it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
            // Already-picked apps float to the top so an edit stays reviewable,
            // then the most used — the likely next pick.
            .sortedWith(
                compareByDescending<InstalledApp> { it.packageName in selected }
                    .then(mostUsedFirst(weeklyUsage)),
            )
    }

    val canSave = name.isNotBlank() && selected.isNotEmpty()

    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = if (existing == null) "New app set" else "Edit app set",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            AppTextField(
                value = name,
                onValueChange = { name = it },
                label = "Set name",
                placeholder = "Social, Games, News…",
            )

            Column {
                Text(
                    text = "Color",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.extendedColors.textMuted,
                )
                Spacer(Modifier.padding(top = Spacing.xs))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    palette.forEachIndexed { index, color ->
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (index == colorIndex) {
                                        Modifier.border(
                                            width = 3.dp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            shape = CircleShape,
                                        )
                                    } else {
                                        Modifier
                                    },
                                )
                                .clickable { colorIndex = index },
                        )
                    }
                }
            }

            Text(
                text = if (selected.isEmpty()) {
                    "Pick apps to block"
                } else {
                    "${selected.size} app${if (selected.size == 1) "" else "s"} selected"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.extendedColors.textMuted,
            )

            AppTextField(
                value = query,
                onValueChange = { query = it },
                label = "Search apps",
            )

            when {
                apps == null -> Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
                    contentAlignment = Alignment.Center,
                ) { LoadingIndicator() }

                visible.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = "No apps found",
                )

                else -> LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    contentPadding = PaddingValues(bottom = Spacing.sm),
                ) {
                    items(visible, key = { it.packageName }) { app ->
                        SetAppRow(
                            app = app,
                            checked = app.packageName in selected,
                            alsoIn = setsByPackage[app.packageName].orEmpty(),
                            dailyAverage = formatDailyAverage(weeklyUsage[app.packageName] ?: 0L),
                            onToggle = { toggle(selected, app.packageName) },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppTextButton(text = "Cancel", onClick = onDismiss)
                Spacer(Modifier.width(Spacing.sm))
                PrimaryButton(
                    text = "Save set",
                    enabled = canSave,
                    onClick = { onSave(name.trim(), colorIndex, selected.toList()) },
                )
            }
        }
    }
}

/**
 * One pickable app: name, then a tag per other set it's already in (dot + name
 * in that set's color) and its daily average. Tags inform; they never block.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetAppRow(
    app: InstalledApp,
    checked: Boolean,
    alsoIn: List<FocusGroup>,
    dailyAverage: String?,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp)
            .clickable(onClick = onToggle)
            .padding(horizontal = Spacing.lg, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIconOrMonogram(icon = app.icon, label = app.label, packageName = app.packageName)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (alsoIn.isNotEmpty() || dailyAverage != null) {
                Spacer(Modifier.height(2.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    alsoIn.forEach { set -> SetTag(set) }
                    if (dailyAverage != null) {
                        Text(
                            text = dailyAverage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.extendedColors.textMuted,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(Spacing.md))
        AppCheckbox(checked = checked, onCheckedChange = { onToggle() })
    }
}

/** Small pill naming another set, tinted in that set's color. */
@Composable
private fun SetTag(set: FocusGroup) {
    val color = focusSetColor(set.colorIndex)
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.asAccentContainer())
            .padding(horizontal = 6.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(4.dp))
        Text(
            text = set.name,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun toggle(selected: MutableList<String>, packageName: String) {
    if (!selected.remove(packageName)) selected.add(packageName)
}
