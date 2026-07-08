package com.rudra.daykit.feature.habit.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rudra.daykit.AppContainer
import com.rudra.daykit.core.ui.AppBackButton
import com.rudra.daykit.core.ui.Cyan
import com.rudra.daykit.core.ui.DangerRed
import com.rudra.daykit.core.ui.GlassBackground
import com.rudra.daykit.core.ui.GlassFilterButton
import com.rudra.daykit.core.ui.GlassLoadingIndicator
import com.rudra.daykit.core.ui.MutedText
import com.rudra.daykit.core.ui.PrimaryButton
import com.rudra.daykit.core.ui.SecondaryButton
import com.rudra.daykit.core.ui.SoftText
import com.rudra.daykit.core.ui.Stroke
import com.rudra.daykit.core.ui.glassSurface
import com.rudra.daykit.feature.habit.data.Habit
import com.rudra.daykit.feature.habit.data.HabitDashboard
import com.rudra.daykit.feature.habit.data.HabitGoalType
import com.rudra.daykit.feature.habit.data.HabitKind
import com.rudra.daykit.feature.habit.data.HabitLog
import com.rudra.daykit.feature.habit.reminder.HabitReminderScheduler
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private enum class HabitTab {
    CheckIn,
    Habits,
    Progress,
    Quit,
}

private val quotes = listOf(
    "What are you capable of?",
    "The problem is, you think you have time.",
    "You do not always need a logical reason to begin.",
    "Everything is a win when the goal is to experience.",
    "Everything may be against you, but you have the madness to continue.",
    "Remember who you are.",
    "It is never too late to rebuild yourself.",
    "Do the next honest minute.",
    "Your future is listening to what you repeat today.",
    "Discipline is just self-respect with a schedule.",
    "Become undeniable, quietly.",
    "Tiny proof beats perfect intention.",
)

@Composable
fun HabitScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scheduler = remember(context) { HabitReminderScheduler(context) }
    val dashboard by container.habitRepository
        .observeDashboard()
        .collectAsStateWithLifecycle(initialValue = null)
    var selectedTab by remember { mutableStateOf(HabitTab.CheckIn) }
    var addOpen by remember { mutableStateOf(false) }
    var addKind by remember { mutableStateOf(HabitKind.Build) }
    var editHabit by remember { mutableStateOf<Habit?>(null) }
    var deleteHabit by remember { mutableStateOf<Habit?>(null) }
    var logHabit by remember { mutableStateOf<Habit?>(null) }
    var relapseHabit by remember { mutableStateOf<Habit?>(null) }
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val nestedUiOpen = addOpen || editHabit != null || deleteHabit != null || logHabit != null || relapseHabit != null

    BackHandler(enabled = !nestedUiOpen, onBack = onBack)

    GlassBackground {
        when {
            addOpen -> HabitEditorPage(
                habit = null,
                initialKind = addKind,
                onDismiss = { addOpen = false },
                onSave = { draft ->
                    scope.launch {
                        val habit = container.habitRepository.addHabit(
                            name = draft.name,
                            kind = draft.kind,
                            goalType = draft.goalType,
                            targetMinutes = draft.targetMinutes,
                            targetCount = draft.targetCount,
                            unitLabel = draft.unitLabel,
                            colorIndex = draft.colorIndex,
                            reminderEnabled = draft.reminderEnabled,
                            reminderHour = draft.reminderHour,
                            reminderMinute = draft.reminderMinute,
                        )
                        scheduler.schedule(habit)
                        requestNotificationPermissionIfNeeded(context as? Activity)
                        addOpen = false
                    }
                },
            )

            editHabit != null -> {
                val habit = editHabit
                if (habit != null) {
                    HabitEditorPage(
                        habit = habit,
                        initialKind = habit.kind,
                        onDismiss = { editHabit = null },
                        onSave = { draft ->
                            scope.launch {
                                container.habitRepository.updateHabit(
                                    habitId = habit.habitId,
                                    name = draft.name,
                                    goalType = draft.goalType,
                                    targetMinutes = draft.targetMinutes,
                                    targetCount = draft.targetCount,
                                    unitLabel = draft.unitLabel,
                                    colorIndex = draft.colorIndex,
                                    reminderEnabled = draft.reminderEnabled,
                                    reminderHour = draft.reminderHour,
                                    reminderMinute = draft.reminderMinute,
                                    active = draft.active,
                                )
                                scheduler.schedule(
                                    habit.copy(
                                        name = draft.name,
                                        goalType = draft.goalType,
                                        targetMinutes = draft.targetMinutes,
                                        targetCount = draft.targetCount,
                                        unitLabel = draft.unitLabel,
                                        colorIndex = draft.colorIndex,
                                        reminderEnabled = draft.reminderEnabled,
                                        reminderHour = draft.reminderHour,
                                        reminderMinute = draft.reminderMinute,
                                        active = draft.active,
                                    ),
                                )
                                requestNotificationPermissionIfNeeded(context as? Activity)
                                editHabit = null
                            }
                        },
                    )
                }
            }

            else -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            HabitTopBar(
                onBack = onBack,
                onAdd = {
                    addKind = if (selectedTab == HabitTab.Quit) HabitKind.Quit else HabitKind.Build
                    addOpen = true
                },
            )
            HabitTabs(selectedTab = selectedTab, onTabChange = { selectedTab = it })

            when (val current = dashboard) {
                null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GlassLoadingIndicator()
                }

                else -> when (selectedTab) {
                    HabitTab.CheckIn -> CheckInTab(
                        dashboard = current,
                        selectedDate = selectedDate,
                        onPreviousDate = { selectedDate = selectedDate.minusDays(1) },
                        onNextDate = { selectedDate = selectedDate.plusDays(1) },
                        onLog = { logHabit = it },
                        onAdd = {
                            addKind = HabitKind.Build
                            addOpen = true
                        },
                    )
                    HabitTab.Habits -> HabitsTab(
                        dashboard = current,
                        onAdd = {
                            addKind = HabitKind.Build
                            addOpen = true
                        },
                        onEdit = { editHabit = it },
                        onDelete = { deleteHabit = it },
                    )
                    HabitTab.Progress -> ProgressTab(
                        dashboard = current,
                        selectedMonth = selectedMonth,
                        onPrevious = { selectedMonth = selectedMonth.minusMonths(1) },
                        onNext = { selectedMonth = selectedMonth.plusMonths(1) },
                    )
                    HabitTab.Quit -> QuitTab(
                        dashboard = current,
                        onAdd = {
                            addKind = HabitKind.Quit
                            addOpen = true
                        },
                        onRelapse = { relapseHabit = it },
                        onEdit = { editHabit = it },
                    )
                }
            }
        }
    }
    }

    logHabit?.let { habit ->
        ProgressDialog(
            habit = habit,
            selectedDate = selectedDate,
            existing = dashboard?.logFor(habit.habitId, selectedDate),
            onDismiss = { logHabit = null },
            onSave = { minutes, count, note ->
                val completed = isGoalComplete(habit, minutes, count, checked = true)
                scope.launch {
                    container.habitRepository.saveDailyProgress(
                        habitId = habit.habitId,
                        date = selectedDate,
                        minutes = minutes,
                        progressCount = count,
                        completed = completed,
                        note = note,
                    )
                    logHabit = null
                }
            },
        )
    }

    relapseHabit?.let { habit ->
        RelapseDialog(
            habit = habit,
            onDismiss = { relapseHabit = null },
            onSave = { note ->
                scope.launch {
                    container.habitRepository.addRelapse(habit.habitId, LocalDate.now(), note)
                    relapseHabit = null
                }
            },
        )
    }

    deleteHabit?.let { habit ->
        DeleteHabitDialog(
            habit = habit,
            onDismiss = { deleteHabit = null },
            onConfirm = {
                scope.launch {
                    scheduler.cancel(habit.habitId)
                    container.habitRepository.deleteHabit(habit.habitId)
                    deleteHabit = null
                }
            },
        )
    }
}

@Composable
private fun HabitTopBar(onBack: () -> Unit, onAdd: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AppBackButton(onClick = onBack)
        Column(modifier = Modifier.weight(1f)) {
            Text("Habit", color = SoftText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Build good days. Leave bad loops.", color = MutedText, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onAdd, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.Add, contentDescription = "Add habit", tint = Cyan)
        }
    }
}

@Composable
private fun HabitTabs(selectedTab: HabitTab, onTabChange: (HabitTab) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        HabitTab.values().forEach { tab ->
            GlassFilterButton(
                text = tab.title,
                selected = selectedTab == tab,
                modifier = Modifier.weight(1f),
                onClick = { onTabChange(tab) },
            )
        }
    }
}

private val HabitTab.title: String
    get() = when (this) {
        HabitTab.CheckIn -> "Check In"
        HabitTab.Habits -> "Habits"
        HabitTab.Progress -> "Progress"
        HabitTab.Quit -> "Quit"
    }

@Composable
private fun CheckInTab(
    dashboard: HabitDashboard,
    selectedDate: LocalDate,
    onPreviousDate: () -> Unit,
    onNextDate: () -> Unit,
    onLog: (Habit) -> Unit,
    onAdd: () -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            QuoteCard(dashboard = dashboard)
        }
        item {
            DateSelector(
                selectedDate = selectedDate,
                today = dashboard.today,
                onPrevious = onPreviousDate,
                onNext = onNextDate,
            )
        }
        if (dashboard.buildHabits.isEmpty()) {
            item {
                EmptyHabitCard(
                    title = "No habits yet",
                    subtitle = "Add coding, gym, reading, math, building, or anything you want to repeat.",
                    onAdd = onAdd,
                )
            }
        } else {
            items(dashboard.buildHabits, key = { it.habitId }) { habit ->
                DailyHabitRow(
                    habit = habit,
                    log = dashboard.logFor(habit.habitId, selectedDate),
                    streak = buildStreak(habit, dashboard.logs, selectedDate),
                    onClick = { onLog(habit) },
                )
            }
        }
    }
}

@Composable
private fun QuoteCard(dashboard: HabitDashboard) {
    val quote = remember(dashboard.today) { quotes[dashboard.today.dayOfYear % quotes.size] }
    StatCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconCircle(Icons.Rounded.SelfImprovement, Cyan)
            Text(
                text = quote,
                color = SoftText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DateSelector(
    selectedDate: LocalDate,
    today: LocalDate,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val label = when (selectedDate) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        today.plusDays(1) -> "Tomorrow"
        else -> selectedDate.format(DateTimeFormatter.ofPattern("EEE, dd MMM"))
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        SecondaryButton(
            text = "<",
            modifier = Modifier.width(52.dp),
            textStyle = MaterialTheme.typography.titleSmall,
            onClick = onPrevious,
        )
        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = SoftText, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(selectedDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")), color = MutedText, style = MaterialTheme.typography.labelSmall)
        }
        SecondaryButton(
            text = ">",
            modifier = Modifier.width(52.dp),
            textStyle = MaterialTheme.typography.titleSmall,
            onClick = onNext,
        )
    }
}

@Composable
private fun DailyHabitRow(
    habit: Habit,
    log: HabitLog?,
    streak: Int,
    onClick: () -> Unit,
) {
    val completed = isLogComplete(habit, log)
    val progress = habitProgress(habit, log)
    StatCard(modifier = Modifier.clickable(onClick = onClick), compact = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (completed) habitColor(habit.colorIndex) else Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (completed) Icons.Rounded.Check else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (completed) Color.White else MutedText,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(habit.name, color = SoftText, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${(progress * 100).roundToInt()}%", color = MutedText, style = MaterialTheme.typography.labelMedium)
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(99.dp)),
                    color = habitColor(habit.colorIndex),
                    trackColor = Color.White.copy(alpha = 0.10f),
                )
                Text(
                    "${progressText(habit, log)} • $streak day streak",
                    color = MutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun HabitsTab(
    dashboard: HabitDashboard,
    onAdd: () -> Unit,
    onEdit: (Habit) -> Unit,
    onDelete: (Habit) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            PrimaryButton(
                text = "Add Habit",
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                },
                onClick = onAdd,
            )
        }
        items(dashboard.buildHabits, key = { it.habitId }) { habit ->
            HabitManageCard(
                habit = habit,
                logs = dashboard.logs,
                today = dashboard.today,
                onEdit = { onEdit(habit) },
                onDelete = { onDelete(habit) },
            )
        }
    }
}

@Composable
private fun HabitManageCard(
    habit: Habit,
    logs: List<HabitLog>,
    today: LocalDate,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val streak = buildStreak(habit, logs, today)
    StatCard(compact = true) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            IconCircle(Icons.Rounded.Flag, habitColor(habit.colorIndex), size = 36.dp, iconSize = 19.dp)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        habit.name,
                        color = SoftText,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (habit.reminderEnabled) {
                        Text(
                            timeText(habit.reminderHour, habit.reminderMinute),
                            color = MutedText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Text(
                    "${habitGoalText(habit)} • $streak day streak",
                    color = MutedText,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Edit, contentDescription = "Edit", tint = SoftText)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = DangerRed)
            }
        }
    }
}

@Composable
private fun ProgressTab(
    dashboard: HabitDashboard,
    selectedMonth: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            StatCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onPrevious) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Previous", tint = SoftText)
                    }
                    Text(
                        selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                        color = SoftText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onNext) {
                        Icon(Icons.Rounded.Event, contentDescription = "Next", tint = Cyan)
                    }
                }
                Spacer(Modifier.height(8.dp))
                MonthHeatmap(dashboard = dashboard, month = selectedMonth)
            }
        }
        item {
            WeeklyConsistencyCard(dashboard = dashboard)
        }
    }
}

@Composable
private fun MonthHeatmap(dashboard: HabitDashboard, month: YearMonth) {
    val firstDay = month.atDay(1)
    val offset = (firstDay.dayOfWeek.value % 7)
    val days = month.lengthOfMonth()
    val cells = offset + days
    val rows = (cells + 6) / 7
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                Text(it, color = MutedText, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            }
        }
        repeat(rows) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val dayNumber = row * 7 + column - offset + 1
                    val date = if (dayNumber in 1..days) month.atDay(dayNumber) else null
                    val segments = if (date == null) emptyList() else daySegments(dashboard, date)
                    val progress = if (date == null) 0f else dayProgress(dashboard, date)
                    val relapse = date != null && dashboard.logs.any { it.date == date.toString() && it.relapse }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                when {
                                    date == null -> Color.Transparent
                                    relapse -> DangerRed.copy(alpha = 0.45f)
                                    progress > 0f -> Color.White.copy(alpha = 0.08f)
                                    else -> Color.White.copy(alpha = 0.06f)
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (date != null) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(dayNumber.toString(), color = SoftText, style = MaterialTheme.typography.labelSmall)
                                if (segments.isNotEmpty()) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        segments.take(3).forEach { segment ->
                                            Box(
                                                modifier = Modifier
                                                    .size(width = 8.dp, height = 3.dp)
                                                    .clip(RoundedCornerShape(99.dp))
                                                    .background(segment.color.copy(alpha = 0.45f + segment.progress * 0.55f)),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (dashboard.buildHabits.isNotEmpty()) {
            HabitLegend(dashboard.buildHabits)
        }
    }
}

@Composable
private fun QuitTab(
    dashboard: HabitDashboard,
    onAdd: () -> Unit,
    onRelapse: (Habit) -> Unit,
    onEdit: (Habit) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        if (dashboard.quitHabits.isEmpty()) {
            item {
                EmptyHabitCard(
                    title = "Leave a bad habit",
                    subtitle = "Track clean days for smoking, porn, junk food, alcohol, or any loop you want to break.",
                    buttonText = "Quit Habit",
                    onAdd = onAdd,
                )
            }
        } else {
            items(dashboard.quitHabits, key = { it.habitId }) { habit ->
                QuitHabitCard(
                    habit = habit,
                    cleanDays = quitCleanDays(habit, dashboard.logs, dashboard.today),
                    best = bestQuitStreak(habit, dashboard.logs, dashboard.today),
                    relapses = dashboard.relapsesFor(habit.habitId).size,
                    onRelapse = { onRelapse(habit) },
                    onEdit = { onEdit(habit) },
                )
            }
        }
    }
}

@Composable
private fun QuitHabitCard(
    habit: Habit,
    cleanDays: Int,
    best: Int,
    relapses: Int,
    onRelapse: () -> Unit,
    onEdit: () -> Unit,
) {
    StatCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconCircle(Icons.Rounded.LocalFireDepartment, habitColor(habit.colorIndex))
            Column(modifier = Modifier.weight(1f)) {
                Text(habit.name, color = SoftText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("$cleanDays days clean • Best $best • Relapses $relapses", color = MutedText, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, contentDescription = "Edit", tint = SoftText)
            }
        }
        Spacer(Modifier.height(12.dp))
        SecondaryButton(text = "I relapsed", modifier = Modifier.fillMaxWidth(), onClick = onRelapse)
    }
}

@Composable
private fun WeeklyConsistencyCard(dashboard: HabitDashboard) {
    val weekDays = remember(dashboard.today) {
        val start = dashboard.today.with(DayOfWeek.MONDAY)
        (0..6).map { start.plusDays(it.toLong()) }
    }
    val keptCount = dashboard.buildHabits.sumOf { habit ->
        weekDays.count { date -> isLogComplete(habit, dashboard.logFor(habit.habitId, date)) }
    }
    val pastSlots = dashboard.buildHabits.size * weekDays.count { !it.isAfter(dashboard.today) }
    val missedCount = dashboard.buildHabits.sumOf { habit ->
        weekDays.count { date ->
            !date.isAfter(dashboard.today) && dashboard.logFor(habit.habitId, date) == null
        }
    }
    StatCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Weekly Consistency", color = SoftText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "$keptCount kept • $missedCount skipped",
                    color = MutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                if (pastSlots == 0) "0%" else "${((keptCount.toFloat() / pastSlots) * 100).roundToInt()}%",
                color = Cyan,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1.8f))
            weekDays.forEach { date ->
                Text(
                    date.dayOfWeek.name.take(1),
                    color = if (date == dashboard.today) Cyan else MutedText,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        dashboard.buildHabits.forEach { habit ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1.8f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(habitColor(habit.colorIndex)),
                    )
                    Text(
                        habit.name,
                        color = SoftText,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                weekDays.forEach { date ->
                    WeeklyStatusCell(
                        habit = habit,
                        log = dashboard.logFor(habit.habitId, date),
                        date = date,
                        today = dashboard.today,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            StatusLegendItem(status = HabitWeekStatus.Kept, text = "Kept")
            StatusLegendItem(status = HabitWeekStatus.Partial, text = "Partial")
            StatusLegendItem(status = HabitWeekStatus.Skipped, text = "Skipped")
        }
    }
}

private enum class HabitWeekStatus {
    Kept,
    Partial,
    Skipped,
}

@Composable
private fun WeeklyStatusCell(
    habit: Habit,
    log: HabitLog?,
    date: LocalDate,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val progress = habitProgress(habit, log)
    val kept = isLogComplete(habit, log)
    val future = date.isAfter(today)
    val color = habitColor(habit.colorIndex)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    when {
                        future -> Color.White.copy(alpha = 0.04f)
                        kept -> color
                        progress > 0f -> color.copy(alpha = 0.30f)
                        else -> Color.White.copy(alpha = 0.08f)
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                kept -> Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                progress > 0f -> Box(
                    modifier = Modifier
                        .size(width = 10.dp, height = 3.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(color),
                )
                !future -> Icon(Icons.Rounded.Close, contentDescription = null, tint = MutedText, modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun StatusLegendItem(status: HabitWeekStatus, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            when (status) {
                HabitWeekStatus.Kept -> Icon(Icons.Rounded.Check, contentDescription = null, tint = MutedText, modifier = Modifier.size(8.dp))
                HabitWeekStatus.Partial -> Box(
                    modifier = Modifier
                        .size(width = 7.dp, height = 2.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(MutedText),
                )
                HabitWeekStatus.Skipped -> Icon(Icons.Rounded.Close, contentDescription = null, tint = MutedText, modifier = Modifier.size(7.dp))
            }
        }
        Text(text, color = MutedText, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun HabitLegend(habits: List<Habit>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        habits.chunked(2).forEach { rowHabits ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                rowHabits.forEach { habit ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(habitColor(habit.colorIndex)),
                        )
                        Text(
                            habit.name,
                            color = MutedText,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (rowHabits.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EmptyHabitCard(
    title: String,
    subtitle: String,
    buttonText: String = "Add Habit",
    onAdd: () -> Unit,
) {
    StatCard {
        Text(title, color = SoftText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = MutedText, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        PrimaryButton(text = buttonText, modifier = Modifier.fillMaxWidth(), onClick = onAdd)
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(18.dp), tintStrength = 0.12f)
            .padding(if (compact) 10.dp else 14.dp),
    ) {
        content()
    }
}

@Composable
private fun IconCircle(
    icon: ImageVector,
    color: Color,
    size: androidx.compose.ui.unit.Dp = 42.dp,
    iconSize: androidx.compose.ui.unit.Dp = 22.dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.20f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(iconSize))
    }
}

private data class HabitDraft(
    val name: String,
    val kind: HabitKind,
    val goalType: HabitGoalType,
    val targetMinutes: Int,
    val targetCount: Int,
    val unitLabel: String,
    val colorIndex: Int,
    val reminderEnabled: Boolean,
    val reminderHour: Int,
    val reminderMinute: Int,
    val active: Boolean,
)

private enum class DayPeriod {
    AM,
    PM,
}

@Composable
private fun HabitKindSegmentedControl(
    selected: HabitKind,
    onSelected: (HabitKind) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(14.dp), tintStrength = 0.08f)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HabitKindOption(
            text = "Build",
            selected = selected == HabitKind.Build,
            modifier = Modifier.weight(1f),
            onClick = { onSelected(HabitKind.Build) },
        )
        HabitKindOption(
            text = "Quit",
            selected = selected == HabitKind.Quit,
            modifier = Modifier.weight(1f),
            onClick = { onSelected(HabitKind.Quit) },
        )
    }
}

@Composable
private fun HabitKindOption(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Cyan.copy(alpha = 0.90f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MutedText,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DayPeriodSegmentedControl(
    selected: DayPeriod,
    onSelected: (DayPeriod) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(56.dp)
            .glassSurface(RoundedCornerShape(14.dp), tintStrength = 0.08f)
            .padding(3.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        DayPeriodOption(
            text = "AM",
            selected = selected == DayPeriod.AM,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onSelected(DayPeriod.AM) },
        )
        DayPeriodOption(
            text = "PM",
            selected = selected == DayPeriod.PM,
            modifier = Modifier.fillMaxWidth(),
            onClick = { onSelected(DayPeriod.PM) },
        )
    }
}

@Composable
private fun DayPeriodOption(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Cyan.copy(alpha = 0.90f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MutedText,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun HabitEditorPage(
    habit: Habit?,
    initialKind: HabitKind,
    onDismiss: () -> Unit,
    onSave: (HabitDraft) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var name by remember { mutableStateOf(habit?.name ?: "") }
    var kind by remember { mutableStateOf(habit?.kind ?: initialKind) }
    var goalType by remember { mutableStateOf(habit?.goalType ?: HabitGoalType.Time) }
    var targetMinutes by remember { mutableStateOf((habit?.targetMinutes ?: 60).takeIf { it > 0 }?.toString() ?: "") }
    var targetCount by remember { mutableStateOf((habit?.targetCount ?: 1).takeIf { it > 0 }?.toString() ?: "") }
    var unitLabel by remember { mutableStateOf(habit?.unitLabel ?: "times") }
    var colorIndex by remember { mutableStateOf(habit?.colorIndex ?: 0) }
    var reminderEnabled by remember { mutableStateOf(habit?.reminderEnabled ?: false) }
    val initialReminderHour = habit?.reminderHour ?: 20
    var reminderHour by remember { mutableStateOf(hour12(initialReminderHour).toString()) }
    var reminderMinute by remember { mutableStateOf((habit?.reminderMinute ?: 0).toString().padStart(2, '0')) }
    var reminderPeriod by remember { mutableStateOf(if (initialReminderHour < 12) DayPeriod.AM else DayPeriod.PM) }
    var active by remember { mutableStateOf(habit?.active ?: true) }
    var inputFocused by remember { mutableStateOf(false) }
    val inputModifier = Modifier.onFocusChanged { inputFocused = it.isFocused }
    val canSave = name.trim().isNotBlank()
    fun saveDraft() {
        if (!canSave) return
        onSave(
            HabitDraft(
                name = name,
                kind = kind,
                goalType = if (kind == HabitKind.Quit) HabitGoalType.Check else goalType,
                targetMinutes = targetMinutes.toIntOrNull() ?: 0,
                targetCount = targetCount.toIntOrNull() ?: 0,
                unitLabel = unitLabel,
                colorIndex = colorIndex,
                reminderEnabled = reminderEnabled,
                reminderHour = hour24(
                    hour = reminderHour.toIntOrNull() ?: 8,
                    period = reminderPeriod,
                ),
                reminderMinute = reminderMinute.toIntOrNull() ?: 0,
                active = active,
            ),
        )
    }

    BackHandler {
        if (inputFocused) {
            keyboardController?.hide()
            focusManager.clearFocus()
        } else {
            onDismiss()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(WindowInsets.statusBars.asPaddingValues())
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            AppBackButton(onClick = onDismiss)
            Text(
                if (habit == null) "Add Habit" else "Edit Habit",
                color = SoftText,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(40) },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = inputModifier,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        colors = habitFieldColors(),
                    )
                }
                if (habit == null) {
                    item {
                        HabitKindSegmentedControl(
                            selected = kind,
                            onSelected = { kind = it },
                        )
                    }
                }
                if (kind == HabitKind.Build) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            HabitGoalType.values().forEach { type ->
                                GlassFilterButton(type.name, selected = goalType == type, modifier = Modifier.weight(1f), onClick = { goalType = type })
                            }
                        }
                    }
                    if (goalType == HabitGoalType.Time) {
                        item {
                            OutlinedTextField(
                                value = targetMinutes,
                                onValueChange = { targetMinutes = it.filter(Char::isDigit).take(4) },
                                label = { Text("Daily minutes") },
                                singleLine = true,
                                modifier = inputModifier,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = habitFieldColors(),
                            )
                        }
                    }
                    if (goalType == HabitGoalType.Count) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = targetCount,
                                    onValueChange = { targetCount = it.filter(Char::isDigit).take(4) },
                                    label = { Text("Daily target") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).onFocusChanged { inputFocused = it.isFocused },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = habitFieldColors(),
                                )
                                OutlinedTextField(
                                    value = unitLabel,
                                    onValueChange = { unitLabel = it.take(16) },
                                    label = { Text("Unit") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).onFocusChanged { inputFocused = it.isFocused },
                                    colors = habitFieldColors(),
                                )
                            }
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Color", color = SoftText, modifier = Modifier.weight(1f))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            habitPalette.indices.chunked(5).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { index ->
                                        Box(
                                            modifier = Modifier
                                                .size(if (colorIndex == index) 30.dp else 24.dp)
                                                .clip(CircleShape)
                                                .background(habitColor(index))
                                                .clickable { colorIndex = index },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Notifications, contentDescription = null, tint = MutedText, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Reminder", color = SoftText, modifier = Modifier.weight(1f))
                        Switch(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
                    }
                }
                if (reminderEnabled) {
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = reminderHour,
                                onValueChange = { value ->
                                    reminderHour = value.filter(Char::isDigit).take(2)
                                },
                                label = { Text("Hour") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).onFocusChanged { inputFocused = it.isFocused },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = habitFieldColors(),
                            )
                            OutlinedTextField(
                                value = reminderMinute,
                                onValueChange = { reminderMinute = it.filter(Char::isDigit).take(2) },
                                label = { Text("Minute") },
                                singleLine = true,
                                modifier = Modifier.weight(1f).onFocusChanged { inputFocused = it.isFocused },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = habitFieldColors(),
                            )
                            DayPeriodSegmentedControl(
                                selected = reminderPeriod,
                                onSelected = { reminderPeriod = it },
                                modifier = Modifier.width(58.dp),
                            )
                        }
                    }
                }
                if (habit != null) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Active", color = SoftText, modifier = Modifier.weight(1f))
                            Checkbox(checked = active, onCheckedChange = { active = it })
                        }
                    }
                }
            }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton(text = "Cancel", modifier = Modifier.weight(1f), onClick = onDismiss)
            PrimaryButton(text = "Save", modifier = Modifier.weight(1f), enabled = canSave, onClick = ::saveDraft)
        }
    }
}

@Composable
private fun ProgressDialog(
    habit: Habit,
    selectedDate: LocalDate,
    existing: HabitLog?,
    onDismiss: () -> Unit,
    onSave: (Int, Int, String) -> Unit,
) {
    var minutes by remember { mutableStateOf(existing?.minutes?.takeIf { it > 0 }?.toString() ?: "") }
    var count by remember { mutableStateOf(existing?.progressCount?.takeIf { it > 0 }?.toString() ?: "") }
    var checked by remember { mutableStateOf(existing?.completed ?: true) }
    var note by remember { mutableStateOf(existing?.note ?: "") }
    val parsedMinutes = minutes.toIntOrNull() ?: 0
    val parsedCount = count.toIntOrNull() ?: 0
    val derivedCompleted = isGoalComplete(habit, parsedMinutes, parsedCount, checked)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(parsedMinutes, parsedCount, note) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = {
            Column {
                Text(habit.name)
                Text(selectedDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")), color = MutedText, style = MaterialTheme.typography.bodySmall)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (habit.goalType == HabitGoalType.Time) {
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter(Char::isDigit).take(4) },
                        label = { Text("Time spent in minutes") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = habitFieldColors(),
                    )
                    Text(
                        "${(habitProgress(habit, temporaryLog(habit, parsedMinutes, parsedCount, derivedCompleted)) * 100).roundToInt()}% complete",
                        color = MutedText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (habit.goalType == HabitGoalType.Count) {
                    OutlinedTextField(
                        value = count,
                        onValueChange = { count = it.filter(Char::isDigit).take(4) },
                        label = { Text("Progress (${habit.unitLabel})") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = habitFieldColors(),
                    )
                    Text(
                        "${(habitProgress(habit, temporaryLog(habit, parsedMinutes, parsedCount, derivedCompleted)) * 100).roundToInt()}% complete",
                        color = MutedText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (habit.goalType == HabitGoalType.Check) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Mark complete", color = SoftText, modifier = Modifier.weight(1f))
                        Checkbox(checked = checked, onCheckedChange = { checked = it })
                    }
                } else {
                    Text(
                        if (derivedCompleted) "This will check today's habit." else "Saved as partial progress.",
                        color = if (derivedCompleted) habitColor(habit.colorIndex) else MutedText,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(160) },
                    label = { Text("Note optional") },
                    colors = habitFieldColors(),
                )
            }
        },
        containerColor = Color(0xFF18181B),
        titleContentColor = SoftText,
        textContentColor = SoftText,
    )
}

@Composable
private fun RelapseDialog(
    habit: Habit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onSave(note) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Reset ${habit.name}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The counter resets, but the history stays. Add a reason if it helps you notice the pattern.", color = MutedText)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(160) },
                    label = { Text("Reason optional") },
                    colors = habitFieldColors(),
                )
            }
        },
        containerColor = Color(0xFF18181B),
        titleContentColor = SoftText,
        textContentColor = SoftText,
    )
}

@Composable
private fun DeleteHabitDialog(
    habit: Habit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = DangerRed)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Delete ${habit.name}?") },
        text = {
            Text(
                "This removes the habit and all of its check-in history.",
                color = MutedText,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        containerColor = Color(0xFF18181B),
        titleContentColor = SoftText,
        textContentColor = SoftText,
    )
}

@Composable
private fun habitFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Cyan,
    unfocusedBorderColor = Stroke,
    focusedTextColor = SoftText,
    unfocusedTextColor = SoftText,
    focusedLabelColor = Cyan,
    unfocusedLabelColor = MutedText,
    cursorColor = Cyan,
)

private fun habitProgress(habit: Habit, log: HabitLog?): Float {
    if (log == null) return 0f
    return when (habit.goalType) {
        HabitGoalType.Time -> if (habit.targetMinutes <= 0) {
            if (log.completed) 1f else 0f
        } else {
            log.minutes.toFloat() / habit.targetMinutes
        }
        HabitGoalType.Count -> if (habit.targetCount <= 0) {
            if (log.completed) 1f else 0f
        } else {
            log.progressCount.toFloat() / habit.targetCount
        }
        HabitGoalType.Check -> if (log.completed) 1f else 0f
    }.coerceIn(0f, 1f)
}

private fun isGoalComplete(habit: Habit, minutes: Int, count: Int, checked: Boolean): Boolean {
    return when (habit.goalType) {
        HabitGoalType.Time -> if (habit.targetMinutes <= 0) minutes > 0 else minutes >= habit.targetMinutes
        HabitGoalType.Count -> if (habit.targetCount <= 0) count > 0 else count >= habit.targetCount
        HabitGoalType.Check -> checked
    }
}

private fun isLogComplete(habit: Habit, log: HabitLog?): Boolean {
    if (log == null) return false
    return isGoalComplete(
        habit = habit,
        minutes = log.minutes,
        count = log.progressCount,
        checked = log.completed,
    )
}

private fun temporaryLog(habit: Habit, minutes: Int, count: Int, completed: Boolean): HabitLog {
    return HabitLog(
        logId = "preview",
        habitId = habit.habitId,
        date = LocalDate.now().toString(),
        minutes = minutes,
        progressCount = count,
        completed = completed,
        relapse = false,
        note = "",
        createdAtMillis = 0L,
        updatedAtMillis = 0L,
    )
}

private fun progressText(habit: Habit, log: HabitLog?): String {
    return when (habit.goalType) {
        HabitGoalType.Time -> "${formatMinutes(log?.minutes ?: 0)} / ${formatMinutes(habit.targetMinutes)}"
        HabitGoalType.Count -> "${log?.progressCount ?: 0} / ${habit.targetCount} ${habit.unitLabel}"
        HabitGoalType.Check -> if (log?.completed == true) "Done today" else "Not checked"
    }
}

private fun habitGoalText(habit: Habit): String {
    return when (habit.goalType) {
        HabitGoalType.Time -> "${formatMinutes(habit.targetMinutes)} daily"
        HabitGoalType.Count -> "${habit.targetCount} ${habit.unitLabel} daily"
        HabitGoalType.Check -> "Daily check"
    }
}

private fun buildStreak(habit: Habit, logs: List<HabitLog>, today: LocalDate): Int {
    var streak = 0
    var date = today
    while (true) {
        val log = logs.lastOrNull { it.habitId == habit.habitId && it.date == date.toString() && !it.relapse }
        if (isLogComplete(habit, log)) {
            streak += 1
            date = date.minusDays(1)
        } else {
            return streak
        }
    }
}

private fun quitCleanDays(habit: Habit, logs: List<HabitLog>, today: LocalDate): Int {
    val lastRelapse = logs
        .filter { it.habitId == habit.habitId && it.relapse }
        .maxByOrNull { it.date }
        ?.date
        ?.let(LocalDate::parse)
    val startDate = lastRelapse ?: dateFromMillis(habit.createdAtMillis)
    return java.time.temporal.ChronoUnit.DAYS.between(startDate, today).toInt().coerceAtLeast(0)
}

private fun bestQuitStreak(habit: Habit, logs: List<HabitLog>, today: LocalDate): Int {
    val relapses = logs
        .filter { it.habitId == habit.habitId && it.relapse }
        .map { LocalDate.parse(it.date) }
        .sorted()
    var best = 0
    var start = dateFromMillis(habit.createdAtMillis)
    relapses.forEach { relapse ->
        best = maxOf(best, java.time.temporal.ChronoUnit.DAYS.between(start, relapse).toInt().coerceAtLeast(0))
        start = relapse
    }
    return maxOf(best, java.time.temporal.ChronoUnit.DAYS.between(start, today).toInt().coerceAtLeast(0))
}

private fun dayProgress(dashboard: HabitDashboard, date: LocalDate): Float {
    val buildHabits = dashboard.buildHabits
    if (buildHabits.isEmpty()) return 0f
    val total = buildHabits.sumOf { habit ->
        (habitProgress(habit, dashboard.logFor(habit.habitId, date)) * 100).roundToInt()
    }
    return (total.toFloat() / (buildHabits.size * 100)).coerceIn(0f, 1f)
}

private data class HabitDaySegment(
    val color: Color,
    val progress: Float,
)

private data class HabitBarSegment(
    val habitId: String,
    val color: Color,
    val minutes: Int,
)

private fun daySegments(dashboard: HabitDashboard, date: LocalDate): List<HabitDaySegment> {
    return dashboard.buildHabits.mapNotNull { habit ->
        val progress = habitProgress(habit, dashboard.logFor(habit.habitId, date))
        if (progress > 0f) HabitDaySegment(habitColor(habit.colorIndex), progress) else null
    }
}

private fun formatMinutes(minutes: Int): String {
    if (minutes <= 0) return "0m"
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours > 0 && mins > 0 -> "${hours}h ${mins}m"
        hours > 0 -> "${hours}h"
        else -> "${mins}m"
    }
}

private fun timeText(hour: Int, minute: Int): String {
    val suffix = if (hour < 12) "AM" else "PM"
    val displayHour = when (val value = hour % 12) {
        0 -> 12
        else -> value
    }
    return "$displayHour:${minute.toString().padStart(2, '0')} $suffix"
}

private fun hour12(hour: Int): Int {
    return when (val value = hour.coerceIn(0, 23) % 12) {
        0 -> 12
        else -> value
    }
}

private fun hour24(hour: Int, period: DayPeriod): Int {
    val normalized = hour.coerceIn(1, 12)
    return when (period) {
        DayPeriod.AM -> if (normalized == 12) 0 else normalized
        DayPeriod.PM -> if (normalized == 12) 12 else normalized + 12
    }
}

private val habitPalette = listOf(
    Color(0xFF22C55E),
    Color(0xFF38BDF8),
    Color(0xFFF59E0B),
    Color(0xFFEC4899),
    Color(0xFFA78BFA),
    Color(0xFF14B8A6),
    Color(0xFFFB7185),
    Color(0xFF818CF8),
    Color(0xFFEAB308),
    Color(0xFF2DD4BF),
)

private fun habitColor(index: Int): Color = habitPalette[index.mod(habitPalette.size)]

private fun dateFromMillis(millis: Long): LocalDate {
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
}

private fun requestNotificationPermissionIfNeeded(activity: Activity?) {
    if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    if (ContextCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
    ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 49)
}
