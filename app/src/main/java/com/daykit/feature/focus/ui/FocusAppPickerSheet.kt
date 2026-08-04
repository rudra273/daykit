package com.daykit.feature.focus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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

/**
 * Sheet for choosing which app to block. Shown before [FocusBlockSheet], which
 * then picks the duration for the app selected here.
 *
 * Apps that already have an active block are omitted — a block cannot be
 * replaced or extended while it runs, so offering them would be a dead end.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FocusAppPickerSheet(
    apps: List<InstalledApp>?,
    blockedPackages: Set<String>,
    onSelect: (InstalledApp) -> Unit,
    onDismiss: () -> Unit,
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
                text = "Block an app",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
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
                    title = if (query.isBlank()) "No apps available to block" else "No apps found",
                    description = if (query.isBlank()) {
                        "Every app you can block already has an active focus block."
                    } else {
                        null
                    },
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
