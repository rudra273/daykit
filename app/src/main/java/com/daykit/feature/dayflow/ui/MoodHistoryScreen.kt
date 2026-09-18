package com.daykit.feature.dayflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daykit.AppContainer
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppCard
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val monthFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

@Composable
fun MoodHistoryScreen(container: AppContainer) {
    val history by container.dayflowRepository.observeMoodHistory()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var monthKey by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthKey)
    val today = LocalDate.now()
    val entries = history.filter { entry ->
        runCatching { YearMonth.from(LocalDate.parse(entry.date)) == month }.getOrDefault(false)
    }
    val byDate = entries.associateBy { it.date }
    val firstDayOffset = month.atDay(1).dayOfWeek.value - 1 // Monday first
    val days: List<LocalDate?> = List(firstDayOffset) { null } +
        (1..month.lengthOfMonth()).map(month::atDay)

    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = { monthKey = month.minusMonths(1).toString() }) {
                        Icon(Icons.Rounded.ChevronLeft, contentDescription = "Previous month")
                    }
                    Text(month.format(monthFormatter), style = MaterialTheme.typography.titleLarge)
                    IconButton(onClick = { monthKey = month.plusMonths(1).toString() },
                        enabled = month.isBefore(YearMonth.from(today))) {
                        Icon(Icons.Rounded.ChevronRight, contentDescription = "Next month")
                    }
                }
            }
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth()) {
                        listOf("M", "T", "W", "T", "F", "S", "S").forEach { label ->
                            Text(label, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    days.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                            week.forEach { date ->
                                if (date == null) {
                                    Spacer(Modifier.weight(1f))
                                } else {
                                    Column(Modifier.weight(1f),
                                        horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(date.dayOfMonth.toString(),
                                            color = if (date == today) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal,
                                            style = MaterialTheme.typography.bodySmall)
                                        Text(byDate[date.toString()]?.mood ?: " ",
                                            style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                            repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
        }
    }
}
