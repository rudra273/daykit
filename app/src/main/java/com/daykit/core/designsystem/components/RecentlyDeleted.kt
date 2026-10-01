package com.daykit.core.designsystem.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.daykit.core.data.RecentlyDeleted
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.extendedColors

/** One row in [RecentlyDeletedSheet]; [id] is whatever the caller restores or purges by. */
data class TrashItem(
    val id: String,
    val title: String,
    val deletedAtMillis: Long,
    val icon: ImageVector? = null,
)

/** Top-bar action that opens a tool's Recently deleted sheet. */
@Composable
fun RecentlyDeletedAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            Icons.Rounded.DeleteOutline,
            contentDescription = "Recently deleted",
            tint = MaterialTheme.extendedColors.textMuted,
        )
    }
}

/**
 * Shared "Recently deleted" bin for Secure Notes, Key Store and the File Vault.
 * Every permanent delete is confirmed; restoring is immediate.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentlyDeletedSheet(
    items: List<TrashItem>,
    onDismiss: () -> Unit,
    onRestore: (TrashItem) -> Unit,
    onDeleteForever: (TrashItem) -> Unit,
    onEmpty: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf<TrashItem?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }
    val now = remember(items) { System.currentTimeMillis() }

    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = Spacing.lg)) {
            Text("Recently deleted", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Items stay encrypted here for ${RecentlyDeleted.RETENTION_DAYS} days, then are deleted for good.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.extendedColors.textMuted,
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        if (items.isEmpty()) {
            EmptyState(icon = Icons.Rounded.DeleteOutline, title = "Nothing here")
        } else {
            items.forEach { item ->
                AppListRow(
                    headline = item.title.ifBlank { "Untitled" },
                    supporting = RecentlyDeleted.daysLeftLabel(item.deletedAtMillis, now),
                    leadingIcon = item.icon,
                    trailing = {
                        Row {
                            IconButton(onClick = { onRestore(item) }) {
                                Icon(
                                    Icons.Rounded.RestoreFromTrash,
                                    contentDescription = "Restore ${item.title}",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            IconButton(onClick = { confirmDelete = item }) {
                                Icon(
                                    Icons.Rounded.DeleteForever,
                                    contentDescription = "Delete ${item.title} forever",
                                    tint = MaterialTheme.extendedColors.danger,
                                )
                            }
                        }
                    },
                )
            }
            Spacer(Modifier.height(Spacing.md))
            DestructiveButton(
                text = "Delete all forever",
                onClick = { confirmEmpty = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg),
            )
        }
        Spacer(Modifier.height(Spacing.lg))
    }

    confirmDelete?.let { item ->
        AppAlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = "Delete forever?",
            text = "\"${item.title.ifBlank { "Untitled" }}\" will be permanently erased. This can't be undone.",
            confirmText = "Delete forever",
            destructiveConfirm = true,
            onConfirm = {
                confirmDelete = null
                onDeleteForever(item)
            },
        )
    }

    if (confirmEmpty) {
        AppAlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = "Delete all forever?",
            text = "${items.size} item${if (items.size == 1) "" else "s"} will be permanently erased. This can't be undone.",
            confirmText = "Delete all",
            destructiveConfirm = true,
            onConfirm = {
                confirmEmpty = false
                onEmpty()
            },
        )
    }
}

/** Shows "[message]" with an Undo action; runs [onUndo] if the user taps it. */
suspend fun SnackbarHostState.showUndo(message: String, onUndo: suspend () -> Unit) {
    if (showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) {
        onUndo()
    }
}
