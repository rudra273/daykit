package com.daykit.feature.dayflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daykit.AppContainer
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.feature.dayflow.data.PomodoroSessionEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val historyTabs = listOf("Mood", "Journal", "Pomodoro")
private val sessionDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a")

@Composable
fun DayflowHistoryScreen(container: AppContainer, onBack: () -> Unit) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "Dayflow history", onBack = onBack)
        TabRow(selectedTabIndex = selectedTab) {
            historyTabs.forEachIndexed { index, title ->
                Tab(selected = selectedTab == index, onClick = { selectedTab = index },
                    text = { Text(title) })
            }
        }
        when (selectedTab) {
            0 -> MoodHistoryScreen(container)
            1 -> JournalHistoryScreen(container)
            else -> PomodoroHistoryContent(container)
        }
    }
}

@Composable
private fun PomodoroHistoryContent(container: AppContainer) {
    val sessions by container.dayflowRepository.observeSessions()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val finished = sessions.filter { it.state == "completed" || it.state == "stopped" }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (finished.isEmpty()) {
            item { Text("No sessions yet.", style = MaterialTheme.typography.bodyMedium) }
        } else {
            items(finished, key = PomodoroSessionEntity::id) { session ->
                Column(Modifier.fillMaxWidth()) {
                    val label = if (session.kind == "break") "Break" else "Pomodoro"
                    val date = Instant.ofEpochMilli(session.startedAtMillis)
                        .atZone(ZoneId.systemDefault()).format(sessionDateFormatter)
                    Text("$label · ${session.state.replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.titleMedium)
                    Text(date, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = Spacing.xs))
                }
            }
        }
    }
}
