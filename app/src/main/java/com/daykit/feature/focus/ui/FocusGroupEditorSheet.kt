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
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppBottomSheet
import com.daykit.core.designsystem.components.AppCheckbox
import com.daykit.core.designsystem.components.AppIconOrMonogram
import com.daykit.core.designsystem.components.AppListRow
import com.daykit.core.designsystem.components.AppTextButton
import com.daykit.core.designsystem.components.AppTextField
import com.daykit.core.designsystem.components.EmptyState
import com.daykit.core.designsystem.components.LoadingIndicator
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.extendedColors
import com.daykit.feature.applock.domain.InstalledApp
import com.daykit.feature.focus.data.FocusGroup

/**
 * Create/edit sheet for an app group. A group is a name, a color, and a set of
 * apps, so a sheet is the right size — the schedule editor, which has many more
 * fields, is a full page instead (matching how habits are edited).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FocusGroupEditorSheet(
    existing: FocusGroup?,
    apps: List<InstalledApp>?,
    onSave: (name: String, colorIndex: Int, packageNames: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var colorIndex by remember { mutableStateOf(existing?.colorIndex ?: 0) }
    val selected = remember {
        mutableStateListOf<String>().apply { addAll(existing?.packageNames.orEmpty()) }
    }
    var query by remember { mutableStateOf("") }

    val accents = MaterialTheme.extendedColors.accents
    val palette = listOf(
        accents.blue, accents.teal, accents.green, accents.red,
        accents.orange, accents.yellow, accents.purple, accents.pink, accents.indigo,
    )

    val visible = remember(apps, query) {
        apps.orEmpty()
            .filter {
                query.isBlank() ||
                    it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
            // Selected apps float to the top so a long list stays reviewable.
            .sortedWith(
                compareByDescending<InstalledApp> { it.packageName in selected }
                    .thenBy { it.label.lowercase() },
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
                text = if (existing == null) "New group" else "Edit group",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            AppTextField(
                value = name,
                onValueChange = { name = it },
                label = "Group name",
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
                        val checked = app.packageName in selected
                        AppListRow(
                            headline = app.label,
                            leading = {
                                AppIconOrMonogram(
                                    icon = app.icon,
                                    label = app.label,
                                    packageName = app.packageName,
                                )
                            },
                            trailing = {
                                AppCheckbox(
                                    checked = checked,
                                    onCheckedChange = { toggle(selected, app.packageName) },
                                )
                            },
                            onClick = { toggle(selected, app.packageName) },
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
                    text = "Save group",
                    enabled = canSave,
                    onClick = { onSave(name.trim(), colorIndex, selected.toList()) },
                )
            }
        }
    }
}

private fun toggle(selected: MutableList<String>, packageName: String) {
    if (!selected.remove(packageName)) selected.add(packageName)
}
