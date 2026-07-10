package com.daykit.feature.today.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daykit.AppContainer
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.EmptyState
import com.daykit.core.designsystem.components.SectionHeader
import com.daykit.core.designsystem.extendedColors
import com.daykit.core.util.Money
import com.daykit.feature.habit.data.HabitGoalType
import com.daykit.feature.reminder.data.Reminder
import com.daykit.feature.reminder.notification.ReminderNotifier
import com.daykit.feature.reminder.notification.ReminderScheduler
import com.daykit.navigation.Routes
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun TodayScreen(
    container: AppContainer,
    bottomBarPadding: PaddingValues,
    onOpenTool: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scheduler = remember(context) { ReminderScheduler(context) }
    val monthKey = remember { YearMonth.now().toString() }

    androidx.compose.runtime.LaunchedEffect(monthKey) {
        container.expenseRepository.ensureMonth(monthKey)
    }

    val habitDashboard by container.habitRepository.observeDashboard()
        .collectAsStateWithLifecycle(initialValue = null)
    val reminders by container.reminderRepository.observeReminders()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val monthSummary by container.expenseRepository.observeMonth(monthKey)
        .collectAsStateWithLifecycle(initialValue = null)

    val listState = rememberLazyListState()

    val today = LocalDate.now()
    val dateLabel = "${today.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())}, " +
        "${today.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${today.dayOfMonth}"

    val buildHabits = habitDashboard?.buildHabits.orEmpty()
    val now = System.currentTimeMillis()
    val pending = reminders.filter { !it.completed }.sortedBy { it.scheduledAtMillis }
    val allEmpty = buildHabits.isEmpty() && pending.isEmpty() && monthSummary == null

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "Today · $dateLabel")
        if (allEmpty && habitDashboard != null) {
            EmptyState(
                icon = Icons.Rounded.TrackChanges,
                title = "Set up your day",
                description = "Add habits, reminders, and expenses to see them here.",
                actionText = "Open Habits",
                onAction = { onOpenTool(Routes.TOOL_HABITS) },
                modifier = Modifier.padding(top = Spacing.xxl),
            )
            return@Column
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg, end = Spacing.lg, top = Spacing.sm,
                bottom = bottomBarPadding.calculateBottomPadding() + Spacing.lg,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            // ── Habits ──
            item {
                val done = buildHabits.count { h -> habitDashboard?.logFor(h.habitId)?.completed == true }
                SectionRow(
                    title = "Habits",
                    trailing = if (buildHabits.isNotEmpty()) "$done of ${buildHabits.size} done" else null,
                    onSeeAll = { onOpenTool(Routes.TOOL_HABITS) },
                )
            }
            if (buildHabits.isEmpty()) {
                item { HintCard("No habits yet — build one to start tracking.") }
            } else {
                items(buildHabits, key = { it.habitId }) { habit ->
                    val completed = habitDashboard?.logFor(habit.habitId)?.completed == true
                    HabitTodayRow(
                        name = habit.name,
                        goal = goalText(habit.goalType, habit.targetMinutes, habit.targetCount),
                        completed = completed,
                        onToggle = {
                            scope.launch {
                                val newCompleted = !completed
                                container.habitRepository.saveDailyProgress(
                                    habitId = habit.habitId,
                                    date = today,
                                    minutes = if (newCompleted && habit.goalType == HabitGoalType.Time) habit.targetMinutes.coerceAtLeast(1) else 0,
                                    progressCount = if (newCompleted && habit.goalType == HabitGoalType.Count) habit.targetCount.coerceAtLeast(1) else 0,
                                    completed = newCompleted,
                                    note = "",
                                )
                            }
                        },
                    )
                }
            }

            // ── Reminders ──
            item {
                SectionRow(
                    title = "Reminders",
                    trailing = null,
                    onSeeAll = { onOpenTool(Routes.TOOL_REMINDERS) },
                )
            }
            val shownReminders = pending.take(4)
            if (shownReminders.isEmpty()) {
                item { HintCard("Nothing scheduled. You're all caught up.") }
            } else {
                items(shownReminders, key = { it.reminderId }) { reminder ->
                    ReminderTodayRow(
                        reminder = reminder,
                        overdue = reminder.scheduledAtMillis < now,
                        onComplete = {
                            scope.launch {
                                container.reminderRepository.markComplete(reminder.reminderId)
                                scheduler.cancel(reminder.reminderId)
                                NotificationManagerCompat.from(context)
                                    .cancel(ReminderNotifier.notificationId(reminder.reminderId))
                            }
                        },
                    )
                }
            }

            // ── Spending ──
            monthSummary?.let { summary ->
                item {
                    SectionRow(
                        title = "Spending this month",
                        trailing = null,
                        onSeeAll = { onOpenTool(Routes.TOOL_EXPENSES) },
                    )
                }
                item {
                    AppCard(onClick = { onOpenTool(Routes.TOOL_EXPENSES) }) {
                        Text(
                            Money.format(summary.totalMinor),
                            style = MaterialTheme.typography.displaySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(Spacing.xs))
                        if (summary.limitMinor > 0L) {
                            val over = summary.limitProgress > 1f
                            LinearProgressIndicator(
                                progress = { summary.limitProgress.coerceAtMost(1f) },
                                modifier = Modifier.fillMaxWidth().height(8.dp),
                                color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.extendedColors.inputField,
                            )
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                if (summary.remainingMinor >= 0)
                                    "${Money.format(summary.remainingMinor)} left of ${Money.format(summary.limitMinor)}"
                                else "${Money.format(-summary.remainingMinor)} over limit",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.extendedColors.textMuted,
                            )
                        } else {
                            Text(
                                "No monthly limit set",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.extendedColors.textMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionRow(title: String, trailing: String?, onSeeAll: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        SectionHeader(title, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extendedColors.textMuted,
                modifier = Modifier.padding(end = Spacing.sm),
            )
        }
        Text(
            "See all",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(end = Spacing.lg),
        )
    }
}

@Composable
private fun HintCard(text: String) {
    AppCard {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.extendedColors.textMuted)
    }
}

@Composable
private fun HabitTodayRow(name: String, goal: String, completed: Boolean, onToggle: () -> Unit) {
    AppCard(contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(goal, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.textMuted)
            }
            IconButton(onClick = onToggle) {
                Icon(
                    if (completed) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = if (completed) "Done" else "Mark done",
                    tint = if (completed) MaterialTheme.extendedColors.success else MaterialTheme.extendedColors.textMuted,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun ReminderTodayRow(reminder: Reminder, overdue: Boolean, onComplete: () -> Unit) {
    AppCard(contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onComplete) {
                Icon(
                    Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = "Complete",
                    tint = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.extendedColors.textMuted,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.size(Spacing.sm))
            Column(Modifier.weight(1f)) {
                Text(reminder.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    relativeTime(reminder.scheduledAtMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.extendedColors.textMuted,
                )
            }
        }
    }
}

private fun goalText(goalType: HabitGoalType, targetMinutes: Int, targetCount: Int): String = when (goalType) {
    HabitGoalType.Time -> "$targetMinutes min"
    HabitGoalType.Count -> "$targetCount times"
    HabitGoalType.Check -> "Check-in"
}

private fun relativeTime(millis: Long): String {
    val diff = millis - System.currentTimeMillis()
    val absMin = kotlin.math.abs(diff) / 60000
    val label = when {
        absMin < 60 -> "$absMin min"
        absMin < 1440 -> "${absMin / 60} h"
        else -> "${absMin / 1440} d"
    }
    return if (diff >= 0) "in $label" else "$label ago"
}
