package com.daykit.feature.dayflow.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daykit.AppContainer
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppTextField
import com.daykit.core.designsystem.components.AppCard
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

private val moods = listOf("😄", "🙂", "😐", "😟", "😢")

@Composable
fun DayflowScreen(container: AppContainer, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }
    val day by container.dayflowRepository.observeDay(today)
        .collectAsStateWithLifecycle(initialValue = null)
    val sessions by container.dayflowRepository.observeSessions()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var titleDraft by rememberSaveable { mutableStateOf("") }
    var draft by rememberSaveable { mutableStateOf("") }
    var draftEdited by rememberSaveable { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var journalSaved by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var error by remember { mutableStateOf<String?>(null) }
    var showHistory by rememberSaveable { mutableStateOf(false) }

    BackHandler(showHistory) { showHistory = false }

    LaunchedEffect(day, loaded) {
        if (!loaded && day != null) {
            if (!draftEdited) {
                titleDraft = day?.journalTitle.orEmpty()
                draft = day?.journal.orEmpty()
            }
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

    if (showHistory) {
        DayflowHistoryScreen(container = container, onBack = { showHistory = false })
        return
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "Dayflow", onBack = onBack, actions = {
            IconButton(onClick = { showHistory = true }) {
                Icon(Icons.Rounded.History, contentDescription = "View Dayflow history")
            }
        })
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                SectionHeader("Mood")
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    moods.forEach { mood ->
                        val selected = day?.mood == mood
                        androidx.compose.material3.TextButton(onClick = {
                            runAction { container.dayflowRepository.saveMood(today, mood) }
                        }, border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                            shape = RoundedCornerShape(12.dp),
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            )) {
                            Text(mood, style = MaterialTheme.typography.headlineMedium)
                        }
                    }
                }
            }
            item {
                SectionHeader("Pomodoro")
                AppCard(Modifier.fillMaxWidth()) {
                    PomodoroClock(active, now)
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
                    AppTextField(titleDraft, { titleDraft = it; draftEdited = true; journalSaved = false },
                        placeholder = "Title")
                    AppTextField(draft, { draft = it; draftEdited = true; journalSaved = false },
                        modifier = Modifier.padding(top = Spacing.sm).height(144.dp),
                        placeholder = "Write your thoughts...", singleLine = false)
                    Row(Modifier.fillMaxWidth().padding(top = Spacing.sm),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        PrimaryButton("Save entry", enabled = titleDraft.isNotBlank() || draft.isNotBlank()) {
                            runAction {
                                container.dayflowRepository.saveJournal(today, titleDraft, draft)
                                focusManager.clearFocus()
                                keyboardController?.hide()
                                journalSaved = true
                            }
                        }
                        if (journalSaved) Text("Saved", color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
        }
    }
}

@Composable
private fun PomodoroClock(session: PomodoroSessionEntity?, now: Long) {
    val duration = if (session?.kind == "break") 5 * 60_000L else 25 * 60_000L
    val remainingMillis = when (session?.state) {
        "paused" -> session.remainingMillis
        "running" -> (session.endAtMillis - now).coerceAtLeast(0)
        else -> duration
    }
    val progress = (remainingMillis.toFloat() / duration).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(progress, tween(1_000), label = "Pomodoro progress")
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant

    Box(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), contentAlignment = Alignment.Center) {
        Box(Modifier.size(188.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 12.dp.toPx()
                val inset = stroke / 2
                val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                val arcTopLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                drawArc(track, -90f, 360f, false, arcTopLeft, arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round))
                if (animatedProgress > 0f) drawArc(accent, -90f, animatedProgress * 360f, false,
                    arcTopLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(Modifier.wrapContentSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(when (session?.state) {
                    "paused" -> "PAUSED"
                    "running" -> if (session.kind == "break") "BREAK" else "FOCUS"
                    else -> "READY"
                }, style = MaterialTheme.typography.labelMedium, color = accent)
                Text(session?.let { remaining(it, now) } ?: "25:00",
                    style = MaterialTheme.typography.displayMedium)
                Text(if (session == null) "Time to focus" else if (session.kind == "break")
                    "Take a breath" else "Stay with it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun remaining(session: PomodoroSessionEntity, now: Long): String {
    val millis = if (session.state == "paused") session.remainingMillis
        else (session.endAtMillis - now).coerceAtLeast(0)
    val seconds = (millis + 999) / 1000
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}
