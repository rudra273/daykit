package com.daykit.feature.dayflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daykit.AppContainer
import com.daykit.core.designsystem.Spacing
import com.daykit.feature.dayflow.data.DayflowDayEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val journalDateFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d, yyyy", Locale.getDefault())

@Composable
fun JournalHistoryScreen(container: AppContainer) {
    val entries by container.dayflowRepository.observeJournalHistory()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (entries.isEmpty()) {
                item { Text("No journal entries yet.", style = MaterialTheme.typography.bodyMedium) }
            } else {
                items(entries, key = DayflowDayEntity::date) { entry ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            LocalDate.parse(entry.date).format(journalDateFormatter),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (entry.journalTitle.isNotBlank()) {
                            Text(entry.journalTitle, modifier = Modifier.padding(top = Spacing.xs),
                                style = MaterialTheme.typography.titleMedium)
                        }
                        if (entry.journal.isNotBlank()) {
                            Text(entry.journal, modifier = Modifier.padding(top = Spacing.xs),
                                style = MaterialTheme.typography.bodyMedium)
                        }
                        HorizontalDivider(Modifier.padding(top = Spacing.md))
                    }
                }
            }
        }
    }
}
