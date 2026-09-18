package com.daykit.feature.dayflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daykit.AppContainer
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppTextField
import com.daykit.core.designsystem.components.AppTextButton
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.components.SecondaryButton
import com.daykit.core.designsystem.components.SectionHeader
import com.daykit.feature.dayflow.data.PomodoroSessionEntity
import com.daykit.feature.dayflow.data.focusedMinutesOn
import com.daykit.feature.widget.updateDayflowWidgets
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val moods = listOf("😄", "🙂", "😐", "😟", "😢")

@Composable
fun DayflowScreen(container: AppContainer, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    val day by container.dayflowRepository.observeDay(today)
        .collectAsStateWithLifecycle(initialValue = null)
    val sessions by container.dayflowRepository.observeSessions()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var draft by rememberSaveable { mutableStateOf("") }
    var loaded by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showMoodHistory by rememberSaveable { mutableStateOf(false) }

    BackHandler(showMoodHistory) { showMoodHistory = false }

    LaunchedEffect(day, loaded) {
        if (!loaded && day != null) {
            draft = day?.journal.orEmpty()
            loaded = true
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            container.dayflowRepository.currentSession()
            delay(1_000)
        }
    }

    val active = sessions.firstOrNull { it.state == "running" || it.state == "paused" }
        ?.takeUnless { it.state == "running" && now >= it.endAtMillis }
    val completed = sessions.filter { it.state == "completed" && it.kind == "work" }
    val todayCount = completed.count {
        Instant.ofEpochMilli(it.startedAtMillis).atZone(ZoneId.systemDefault()).toLocalDate() == today
    }
    val focusedMinutesToday = focusedMinutesOn(sessions, today, now)

    fun runAction(action: suspend () -> Unit) {
        scope.launch {
            try {
                action()
                updateDayflowWidgets(context)
                error = null
            } catch (e: Exception) {
                error = e.message ?: "Couldn't save Dayflow changes"
            }
        }
    }

    if (showMoodHistory) {
        MoodHistoryScreen(container = container, onBack = { showMoodHistory = false })
        return
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "Dayflow", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                SectionHeader("Pomodoro")
                AppCard(Modifier.fillMaxWidth()) {
                    Text(
                        when (active?.state) {
                            "running" -> "${active.kind.replaceFirstChar { it.uppercase() }} · ${remaining(active, now)}"
                            "paused" -> "Paused · ${remaining(active, now)}"
                            else -> "Ready for a focus session?"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text("$focusedMinutesToday min focused today · $todayCount completed",
                        style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.padding(top = Spacing.md)) {
                        if (active == null) {
                            PrimaryButton("Start 25 min") { runAction { container.dayflowRepository.start("work") } }
                            SecondaryButton("Break 5 min") { runAction { container.dayflowRepository.start("break") } }
                        } else {
                            if (active.state == "running") {
                                PrimaryButton("Pause") { runAction { container.dayflowRepository.pause() } }
                            } else {
                                PrimaryButton("Resume") { runAction { container.dayflowRepository.resume() } }
                            }
                            SecondaryButton("Stop") { runAction { container.dayflowRepository.stop() } }
                        }
                    }
                }
            }
            item {
                SectionHeader("Journal")
                AppCard(Modifier.fillMaxWidth()) {
                    Text("What do you want to do today?", style = MaterialTheme.typography.titleMedium)
                    AppTextField(draft, { draft = it }, modifier = Modifier.padding(top = Spacing.sm),
                        placeholder = "Write your thoughts...", singleLine = false)
                    PrimaryButton("Save entry", modifier = Modifier.padding(top = Spacing.sm)) {
                        runAction { container.dayflowRepository.saveJournal(today, draft) }
                    }
                }
            }
            item {
                SectionHeader("Mood")
                AppCard(Modifier.fillMaxWidth()) {
                    Text("How are you feeling today?", style = MaterialTheme.typography.titleMedium)
                    Row(Modifier.fillMaxWidth().padding(top = Spacing.sm),
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        moods.forEach { mood ->
                            androidx.compose.material3.TextButton(onClick = {
                                runAction { container.dayflowRepository.saveMood(today, mood) }
                            }) {
                                Text(mood, style = MaterialTheme.typography.headlineMedium)
                            }
                        }
                    }
                    if (!day?.mood.isNullOrEmpty()) Text("Today: ${day?.mood}")
                    AppTextButton(text = "View mood history", onClick = { showMoodHistory = true })
                }
            }
            item { SectionHeader("Pomodoro history") }
            if (sessions.none { it.state == "completed" || it.state == "stopped" }) {
                item { Text("No sessions yet.") }
            } else {
                items(sessions.filter { it.state == "completed" || it.state == "stopped" },
                    key = { it.id }) { session ->
                    AppCard(Modifier.fillMaxWidth()) {
                        val label = if (session.kind == "break") "Break" else "Pomodoro"
                        val date = Instant.ofEpochMilli(session.startedAtMillis)
                            .atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
                        Text("$label · ${session.state.replaceFirstChar { it.uppercase() }}")
                        Text(date, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        }
    }
}

private fun remaining(session: PomodoroSessionEntity, now: Long): String {
    val millis = if (session.state == "paused") session.remainingMillis
        else (session.endAtMillis - now).coerceAtLeast(0)
    val seconds = (millis + 999) / 1000
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
