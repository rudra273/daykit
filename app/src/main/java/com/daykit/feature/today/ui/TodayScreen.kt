package com.daykit.feature.today.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daykit.AppContainer
import com.daykit.core.designsystem.MinTouchTarget
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.asAccentContainer
import com.daykit.core.designsystem.components.AccentIconTile
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.AppTopBarHeight
import com.daykit.core.designsystem.extendedColors
import com.daykit.core.util.Money
import com.daykit.core.util.TimeFormat
import com.daykit.feature.dayflow.data.focusedMinutesOn
import com.daykit.feature.habit.data.Habit
import com.daykit.feature.habit.data.HabitDashboard
import com.daykit.feature.habit.data.HabitGoalType
import com.daykit.feature.habit.ui.habitColor
import com.daykit.feature.reminder.data.Reminder
import com.daykit.navigation.Routes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Four Pomodoro work sessions: the focus ring's "full" mark. */
private const val FOCUS_GOAL_MINUTES = 100L
private const val MAX_REMINDERS = 5

@Composable
fun TodayScreen(
    container: AppContainer,
    bottomBarPadding: PaddingValues,
    onOpenTool: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }

    // Ticks once a minute so relative times, rings and the date roll over while open.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000 - now % 60_000)
            now = System.currentTimeMillis()
        }
    }
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val month = YearMonth.from(today)
    val monthKey = month.toString()

    LaunchedEffect(monthKey) { container.expenseRepository.ensureMonth(monthKey) }

    // Flows are remembered: a fresh Room flow per recomposition would restart collection.
    val habitDashboard by remember { container.habitRepository.observeDashboard() }
        .collectAsStateWithLifecycle(initialValue = null)
    val reminders by remember { container.reminderRepository.observeReminders() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val monthSummary by remember(monthKey) { container.expenseRepository.observeMonth(monthKey) }
        .collectAsStateWithLifecycle(initialValue = null)
    val sessions by remember { container.dayflowRepository.observeSessions() }
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val moodHistory by remember { container.dayflowRepository.observeMoodHistory() }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val week = TodayStats.lastSevenDays(today)
    val weekLabels = week.map { it.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()) }

    // ── Habits ──
    val dashboard = habitDashboard
    val buildHabits = dashboard?.buildHabits.orEmpty()
    val habitsDone = buildHabits.count { dashboard?.logFor(it.habitId, today)?.completed == true }

    // ── Focus ──
    val focusWeek = week.map { focusedMinutesOn(sessions, it, now, zone) }
    val focusToday = focusWeek.last()

    // ── Reminders ──
    val endOfToday = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    val pending = reminders.filter { !it.completed && !it.paused }.sortedBy { it.dueAt() }
    val dueToday = pending.count { it.dueAt() < endOfToday }
    val clearedToday = reminders.count { r ->
        r.acknowledgedAtMillis?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() == today } == true
    }

    val accents = MaterialTheme.extendedColors.accents
    val habitRing = if (buildHabits.isEmpty()) 0f else habitsDone.toFloat() / buildHabits.size
    val focusRing = focusToday.toFloat() / FOCUS_GOAL_MINUTES
    val reminderTotal = clearedToday + dueToday
    val reminderRing = if (reminderTotal == 0) 0f else clearedToday.toFloat() / reminderTotal
    val scored = listOfNotNull(
        habitRing.takeIf { buildHabits.isNotEmpty() },
        focusRing.coerceAtMost(1f),
        reminderRing.takeIf { reminderTotal > 0 },
    )
    val dayScore = (scored.average().toFloat() * 100).toInt()

    val headerHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + AppTopBarHeight
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg, end = Spacing.lg, top = headerHeight + Spacing.sm,
                bottom = bottomBarPadding.calculateBottomPadding() + Spacing.xl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item(key = "hero") {
                HeroCard(
                    greeting = greetingFor(LocalTime.now(zone)),
                    summary = daySummary(buildHabits.size - habitsDone, dueToday),
                    score = dayScore,
                    rings = listOf(
                        Ring(habitRing, accents.green),
                        Ring(focusRing, accents.indigo),
                        Ring(reminderRing, accents.orange),
                    ),
                    legend = listOf(
                        Triple(accents.green, "Habits", if (buildHabits.isEmpty()) "—" else "$habitsDone/${buildHabits.size}"),
                        Triple(accents.indigo, "Focus", "$focusToday/$FOCUS_GOAL_MINUTES min"),
                        Triple(accents.orange, "Reminders", if (reminderTotal == 0) "—" else "$clearedToday/$reminderTotal"),
                    ),
                )
            }

            item(key = "actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    QuickAction("Journal", Icons.Rounded.AutoAwesome, accents.indigo, Modifier.weight(1f)) { onOpenTool(Routes.TOOL_DAYFLOW) }
                    QuickAction("Focus", Icons.Rounded.Timer, accents.red, Modifier.weight(1f)) { onOpenTool(Routes.TOOL_FOCUS) }
                    QuickAction("Expense", Icons.Rounded.Payments, accents.pink, Modifier.weight(1f)) { onOpenTool(Routes.TOOL_EXPENSES) }
                    QuickAction("Remind", Icons.Rounded.NotificationsActive, accents.orange, Modifier.weight(1f)) { onOpenTool(Routes.TOOL_REMINDERS) }
                }
            }

            item(key = "habits") {
                HabitsCard(
                    dashboard = dashboard,
                    habits = buildHabits,
                    done = habitsDone,
                    today = today,
                    week = week,
                    weekLabels = weekLabels,
                    accent = accents.green,
                    onOpen = { onOpenTool(Routes.TOOL_HABITS) },
                    onToggle = { habit, completed ->
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

            item(key = "reminders") {
                RemindersCard(
                    pending = pending,
                    now = now,
                    endOfToday = endOfToday,
                    zone = zone,
                    accent = accents.orange,
                    onOpen = { onOpenTool(Routes.TOOL_REMINDERS) },
                    onComplete = { reminder ->
                        scope.launch { container.reminderRepository.markComplete(reminder.reminderId) }
                    },
                )
            }

            item(key = "focus") {
                FocusCard(
                    minutes = focusWeek,
                    labels = weekLabels,
                    accent = accents.indigo,
                    onOpen = { onOpenTool(Routes.TOOL_DAYFLOW) },
                )
            }

            item(key = "mood") {
                val moods = remember(moodHistory) { moodHistory.associate { it.date to it.mood } }
                MoodCard(
                    week = week,
                    labels = weekLabels,
                    moods = moods,
                    accent = accents.purple,
                    onOpen = { onOpenTool(Routes.TOOL_DAYFLOW) },
                )
            }

            monthSummary?.let { summary ->
                item(key = "spending") {
                    val cumulative = remember(summary, today) { TodayStats.cumulativeSpend(summary.entries, month, today) }
                    val categories = remember(summary) { TodayStats.topCategories(summary.entries) }
                    val spentToday = summary.entries.filter { it.expenseDate == today.toString() }.sumOf { it.amountMinor }
                    SpendingCard(
                        total = summary.totalMinor,
                        limit = summary.limitMinor,
                        spentToday = spentToday,
                        cumulative = cumulative,
                        daysInMonth = month.lengthOfMonth(),
                        categories = categories,
                        onOpen = { onOpenTool(Routes.TOOL_EXPENSES) },
                    )
                }
            }
        }
        AppTopBar(
            title = "Today",
            subtitle = today.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())),
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

// ─────────────────────────────── Hero ───────────────────────────────

@Composable
private fun HeroCard(
    greeting: String,
    summary: String,
    score: Int,
    rings: List<Ring>,
    legend: List<Triple<Color, String, String>>,
) {
    val glow = MaterialTheme.colorScheme.primary
    AppCard(contentPadding = PaddingValues(0.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRect(
                        Brush.radialGradient(
                            listOf(glow.copy(alpha = 0.16f), Color.Transparent),
                            center = Offset(size.width, 0f),
                            radius = size.width * 0.9f,
                        ),
                    )
                }
                .padding(Spacing.lg),
        ) {
            Text(greeting, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.extendedColors.textMuted)
            Spacer(Modifier.height(Spacing.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                ActivityRings(
                    rings = rings,
                    modifier = Modifier
                        .size(132.dp)
                        .semantics { contentDescription = "Day progress $score percent" },
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$score%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("of today", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.extendedColors.textMuted)
                    }
                }
                Spacer(Modifier.width(Spacing.xl))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    legend.forEach { (color, label, value) -> LegendRow(color, label, value) }
                }
            }
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, accent: Color, modifier: Modifier, onClick: () -> Unit) {
    AppCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(vertical = Spacing.md, horizontal = Spacing.xs)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            AccentIconTile(icon = icon, accent = accent, size = 36.dp, iconSize = 20.dp)
            Spacer(Modifier.height(Spacing.xs))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** Icon tile + title/subtitle + chevron; the whole row opens the tool. */
@Composable
private fun CardHeader(icon: ImageVector, accent: Color, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClickLabel = "Open $title", onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AccentIconTile(icon = icon, accent = accent, size = 34.dp, iconSize = 19.dp)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.textMuted)
            }
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.extendedColors.textMuted)
    }
}

@Composable
private fun EmptyHint(text: String, action: String, accent: Color, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.extendedColors.inputField)
            .clickable(onClick = onClick)
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.extendedColors.textMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Spacing.xs))
        Text(action, style = MaterialTheme.typography.labelLarge, color = accent)
    }
}

// ─────────────────────────────── Habits ───────────────────────────────

@Composable
private fun HabitsCard(
    dashboard: HabitDashboard?,
    habits: List<Habit>,
    done: Int,
    today: LocalDate,
    week: List<LocalDate>,
    weekLabels: List<String>,
    accent: Color,
    onOpen: () -> Unit,
    onToggle: (Habit, Boolean) -> Unit,
) {
    AppCard {
        CardHeader(
            Icons.Rounded.TrackChanges, accent, "Habits",
            if (habits.isEmpty()) "Build a daily routine" else "$done of ${habits.size} done today",
            onOpen,
        )
        if (dashboard == null || habits.isEmpty()) {
            Spacer(Modifier.height(Spacing.md))
            EmptyHint("No habits yet — build one to start tracking.", "Add habit", accent, onOpen)
            return@AppCard
        }
        val completion = remember(dashboard, week) { TodayStats.habitCompletion(dashboard, week) }
        Spacer(Modifier.height(Spacing.lg))
        WeekBarChart(
            values = completion,
            labels = weekLabels,
            color = accent,
            maxValue = 1f,
            chartHeight = 64.dp,
            valueLabels = completion.map { if (it > 0f) "${(it * 100).toInt()}%" else "" },
        )
        Spacer(Modifier.height(Spacing.md))
        HorizontalDivider(color = MaterialTheme.extendedColors.divider)
        habits.forEach { habit ->
            val completed = dashboard.logFor(habit.habitId, today)?.completed == true
            HabitRow(
                habit = habit,
                completed = completed,
                streak = TodayStats.streak(dashboard, habit.habitId, today),
                weekDone = week.map { dashboard.logFor(habit.habitId, it)?.completed == true },
                onToggle = { onToggle(habit, completed) },
            )
        }
    }
}

@Composable
private fun HabitRow(habit: Habit, completed: Boolean, streak: Int, weekDone: List<Boolean>, onToggle: () -> Unit) {
    val color = habitColor(habit.colorIndex)
    val fill by animateColorAsState(if (completed) color else Color.Transparent, label = "habitFill")
    Row(Modifier.fillMaxWidth().padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(MinTouchTarget)
                .clip(CircleShape)
                .clickable(role = Role.Checkbox, onClickLabel = if (completed) "Mark not done" else "Mark done", onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(fill)
                    .border(2.dp, color, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (completed) Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.width(Spacing.xs))
        Column(Modifier.weight(1f)) {
            Text(habit.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(goalText(habit), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.textMuted)
                if (streak > 1) {
                    Spacer(Modifier.width(Spacing.sm))
                    Icon(Icons.Rounded.LocalFireDepartment, contentDescription = null, tint = MaterialTheme.extendedColors.accents.orange, modifier = Modifier.size(14.dp))
                    Text("$streak days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.extendedColors.accents.orange)
                }
            }
        }
        // Last 7 days, today on the right.
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.semantics {
            contentDescription = "${weekDone.count { it }} of 7 days this week"
        }) {
            weekDone.forEach { day ->
                Box(
                    Modifier
                        .size(width = 6.dp, height = 18.dp)
                        .clip(CircleShape)
                        .background(if (day) color else MaterialTheme.extendedColors.inputField),
                )
            }
        }
    }
}

// ─────────────────────────────── Reminders ───────────────────────────────

@Composable
private fun RemindersCard(
    pending: List<Reminder>,
    now: Long,
    endOfToday: Long,
    zone: ZoneId,
    accent: Color,
    onOpen: () -> Unit,
    onComplete: (Reminder) -> Unit,
) {
    val overdue = pending.count { it.dueAt() < now }
    AppCard {
        CardHeader(
            Icons.Rounded.NotificationsActive, accent, "Up next",
            when {
                pending.isEmpty() -> "Nothing scheduled"
                overdue > 0 -> "$overdue overdue · ${pending.size} pending"
                else -> "${pending.size} pending"
            },
            onOpen,
        )
        Spacer(Modifier.height(Spacing.md))
        if (pending.isEmpty()) {
            EmptyHint("You're all caught up.", "Add reminder", accent, onOpen)
            return@AppCard
        }
        val shown = pending.take(MAX_REMINDERS)
        shown.forEachIndexed { index, reminder ->
            ReminderTimelineRow(
                reminder = reminder,
                now = now,
                isToday = reminder.dueAt() < endOfToday,
                zone = zone,
                accent = accent,
                isLast = index == shown.lastIndex,
                onComplete = { onComplete(reminder) },
            )
        }
        if (pending.size > shown.size) {
            Text(
                "+${pending.size - shown.size} more",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.extendedColors.textMuted,
                modifier = Modifier.padding(start = 64.dp, top = Spacing.xs),
            )
        }
    }
}

@Composable
private fun ReminderTimelineRow(
    reminder: Reminder,
    now: Long,
    isToday: Boolean,
    zone: ZoneId,
    accent: Color,
    isLast: Boolean,
    onComplete: () -> Unit,
) {
    val due = reminder.dueAt()
    val late = due < now
    val tone = if (late) MaterialTheme.colorScheme.error else accent
    val dueTime = Instant.ofEpochMilli(due).atZone(zone)
    val divider = MaterialTheme.extendedColors.divider
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(52.dp), horizontalAlignment = Alignment.End) {
            Text(
                TimeFormat.format(dueTime.hour, dueTime.minute).replace(" ", "\n"),
                style = MaterialTheme.typography.labelMedium,
                color = if (late) tone else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
                lineHeight = MaterialTheme.typography.labelSmall.lineHeight,
            )
            if (!isToday) {
                Text(
                    dueTime.toLocalDate().format(java.time.format.DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.extendedColors.textMuted,
                )
            }
        }
        // Timeline spine: a dot with a connector to the next row.
        Box(
            Modifier
                .width(24.dp)
                .height(56.dp)
                .drawBehind {
                    val cx = size.width / 2
                    if (!isLast) drawLine(divider, Offset(cx, size.height / 2), Offset(cx, size.height + 8.dp.toPx()), 2.dp.toPx())
                    drawCircle(tone.copy(alpha = 0.22f), 8.dp.toPx(), Offset(cx, size.height / 2))
                    drawCircle(tone, 4.dp.toPx(), Offset(cx, size.height / 2))
                },
        )
        Column(Modifier.weight(1f)) {
            Text(reminder.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (late) "Overdue · ${relativeTime(due, now)}" else relativeTime(due, now),
                style = MaterialTheme.typography.bodySmall,
                color = if (late) tone else MaterialTheme.extendedColors.textMuted,
            )
        }
        Box(
            Modifier
                .size(MinTouchTarget)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = "Complete ${reminder.title}", onClick = onComplete),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).background(tone.asAccentContainer()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = tone, modifier = Modifier.size(16.dp))
            }
        }
    }
}

// ─────────────────────────────── Focus & mood ───────────────────────────────

@Composable
private fun FocusCard(minutes: List<Long>, labels: List<String>, accent: Color, onOpen: () -> Unit) {
    val total = minutes.sum()
    val activeDays = minutes.count { it > 0 }
    AppCard {
        CardHeader(Icons.Rounded.Timer, accent, "Focus time", "Pomodoro sessions, last 7 days", onOpen)
        Spacer(Modifier.height(Spacing.lg))
        Row(Modifier.fillMaxWidth()) {
            Metric(formatMinutes(minutes.last()), "Today", Modifier.weight(1f), accent)
            Metric(formatMinutes(total), "This week", Modifier.weight(1f))
            Metric(if (activeDays == 0) "—" else formatMinutes(total / activeDays), "Daily avg", Modifier.weight(1f))
        }
        Spacer(Modifier.height(Spacing.lg))
        if (total == 0L) {
            EmptyHint("No focus sessions this week.", "Start a Pomodoro", accent, onOpen)
        } else {
            WeekBarChart(
                values = minutes.map { it.toFloat() },
                labels = labels,
                color = accent,
                maxValue = maxOf(minutes.max(), FOCUS_GOAL_MINUTES).toFloat(),
            )
        }
    }
}

@Composable
private fun Metric(value: String, label: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    Column(modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.textMuted)
    }
}

@Composable
private fun MoodCard(week: List<LocalDate>, labels: List<String>, moods: Map<String, String>, accent: Color, onOpen: () -> Unit) {
    val todayMood = moods[week.last().toString()]
    AppCard {
        CardHeader(
            Icons.Rounded.AutoAwesome, accent, "Mood",
            if (todayMood == null) "How are you feeling today?" else "Logged today",
            onOpen,
        )
        Spacer(Modifier.height(Spacing.md))
        Row(Modifier.fillMaxWidth()) {
            week.forEachIndexed { index, date ->
                val mood = moods[date.toString()]
                val isToday = index == week.lastIndex
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isToday) accent.asAccentContainer() else MaterialTheme.extendedColors.inputField)
                            .then(if (isToday && mood == null) Modifier.border(1.5.dp, accent, CircleShape) else Modifier)
                            .clickable(enabled = isToday, onClick = onOpen),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (mood != null) Text(mood, style = MaterialTheme.typography.titleMedium)
                        else if (isToday) Text("+", style = MaterialTheme.typography.titleMedium, color = accent)
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        labels[index],
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.extendedColors.textMuted,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────── Spending ───────────────────────────────

@Composable
private fun SpendingCard(
    total: Long,
    limit: Long,
    spentToday: Long,
    cumulative: List<Long>,
    daysInMonth: Int,
    categories: List<Pair<String, Long>>,
    onOpen: () -> Unit,
) {
    val accents = MaterialTheme.extendedColors.accents
    val over = limit in 1 until total
    val lineColor = if (over) MaterialTheme.colorScheme.error else accents.pink
    AppCard {
        CardHeader(
            Icons.Rounded.Payments, accents.pink, "Spending",
            when {
                limit <= 0L -> "No monthly limit set"
                over -> "${Money.format(total - limit)} over limit"
                else -> "${Money.format(limit - total)} left of ${Money.format(limit)}"
            },
            onOpen,
        )
        Spacer(Modifier.height(Spacing.lg))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(Money.format(total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("this month", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.textMuted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(spentToday), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text("today", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.textMuted)
            }
        }
        if (total == 0L) {
            Spacer(Modifier.height(Spacing.md))
            EmptyHint("Nothing spent yet this month.", "Add expense", accents.pink, onOpen)
            return@AppCard
        }
        Spacer(Modifier.height(Spacing.md))
        SpendTrendChart(
            cumulative = cumulative,
            daysInMonth = daysInMonth,
            limit = limit,
            color = lineColor,
            modifier = Modifier.fillMaxWidth().height(120.dp),
        )
        if (limit > 0) {
            Text(
                "Dashed line: even pace to your ${Money.compact(limit)} limit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.extendedColors.textMuted,
            )
        }
        if (categories.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.lg))
            HorizontalDivider(color = MaterialTheme.extendedColors.divider)
            Spacer(Modifier.height(Spacing.lg))
            val palette = listOf(accents.pink, accents.purple, accents.blue, accents.teal)
            val slices = categories.mapIndexed { i, (label, value) ->
                Slice(label, value, if (label == "Other") MaterialTheme.extendedColors.textMuted else palette[i % palette.size])
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                DonutChart(slices, Modifier.size(96.dp)) {
                    Text("${slices.size}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(Spacing.xl))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    slices.forEach { slice ->
                        LegendRow(slice.color, slice.label, "${(slice.value * 100 / total.coerceAtLeast(1))}%")
                    }
                }
            }
        }
    }
}

// ─────────────────────────────── Helpers ───────────────────────────────

/** When this reminder next needs attention: a fired-but-unhandled or snoozed occurrence first. */
private fun Reminder.dueAt(): Long = pendingOccurrenceMillis ?: snoozedUntilMillis ?: scheduledAtMillis

private fun greetingFor(time: LocalTime): String = when (time.hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Good night"
}

private fun daySummary(habitsLeft: Int, remindersLeft: Int): String {
    val parts = buildList {
        if (habitsLeft > 0) add("$habitsLeft habit${if (habitsLeft == 1) "" else "s"}")
        if (remindersLeft > 0) add("$remindersLeft reminder${if (remindersLeft == 1) "" else "s"}")
    }
    return if (parts.isEmpty()) "You're all clear for today." else "${parts.joinToString(" and ")} left today."
}

private fun goalText(habit: Habit): String = when (habit.goalType) {
    HabitGoalType.Time -> "${habit.targetMinutes} min"
    HabitGoalType.Count -> "${habit.targetCount} ${habit.unitLabel.ifBlank { "times" }}"
    HabitGoalType.Check -> "Check-in"
}

private fun formatMinutes(minutes: Long): String =
    if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${(minutes % 60).toString().padStart(2, '0')}m"

private fun relativeTime(millis: Long, now: Long): String {
    val diff = millis - now
    val absMin = kotlin.math.abs(diff) / 60000
    val label = when {
        absMin < 1 -> return "now"
        absMin < 60 -> "$absMin min"
        absMin < 1440 -> "${absMin / 60} h"
        else -> "${absMin / 1440} d"
    }
    return if (diff >= 0) "in $label" else "$label ago"
}
