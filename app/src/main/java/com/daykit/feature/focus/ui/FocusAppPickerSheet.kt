package com.daykit.feature.focus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppBottomSheet
import com.daykit.core.designsystem.components.AppIconOrMonogram
import com.daykit.core.designsystem.components.AppListRow
import com.daykit.core.designsystem.components.AppTextField
import com.daykit.core.designsystem.components.EmptyState
import com.daykit.core.designsystem.components.LoadingIndicator
import com.daykit.feature.applock.domain.InstalledApp
import com.daykit.feature.focus.data.FocusGroup

/**
 * Sheet for choosing an app, shown before the Lock now or Daily limit sheet.
 *
 * Apps in [blockedPackages] are omitted — for Lock now, a running block can't be
 * replaced; for Daily limit, the app already has one (edit it from its row).
 *
 * When [sets] is non-empty (Lock now only), they appear as chips above the list,
 * so a whole app set can be locked in one tap without a separate entry point.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FocusAppPickerSheet(
    title: String,
    apps: List<InstalledApp>?,
    blockedPackages: Set<String>,
    onSelect: (InstalledApp) -> Unit,
    onDismiss: () -> Unit,
    sets: List<FocusGroup> = emptyList(),
    onSelectSet: (FocusGroup) -> Unit = {},
) {
    var query by remember { mutableStateOf("") }

    val selectable = remember(apps, blockedPackages, query) {
        apps.orEmpty()
            .filterNot { it.packageName in blockedPackages }
            .filter {
                query.isBlank() ||
                    it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
            .sortedBy { it.label.lowercase() }
    }

    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val usableSets = sets.filter { it.packageNames.isNotEmpty() }
            if (usableSets.isNotEmpty() && query.isBlank()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    usableSets.forEach { set ->
                        FocusSetChip(set = set, onClick = { onSelectSet(set) })
                    }
                }
            }
            AppTextField(
                value = query,
                onValueChange = { query = it },
                label = "Search apps",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            when {
                apps == null -> Box(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }

                selectable.isEmpty() -> EmptyState(
                    icon = Icons.Rounded.SearchOff,
                    title = if (query.isBlank()) "Every app is already set" else "No apps found",
                )

                else -> LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    contentPadding = PaddingValues(bottom = Spacing.sm),
                ) {
                    items(selectable, key = { it.packageName }) { app ->
                        AppListRow(
                            headline = app.label,
                            supporting = app.packageName,
                            leading = {
                                AppIconOrMonogram(
                                    icon = app.icon,
                                    label = app.label,
                                    packageName = app.packageName,
                                )
                            },
                            onClick = { onSelect(app) },
                        )
                    }
                }
            }
        }
    }
}
