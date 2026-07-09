@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.daykit.feature.habit.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daykit.AppContainer
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.asAccentContainer
import com.daykit.core.designsystem.extendedColors
import com.daykit.core.designsystem.components.AccentIconTile
import com.daykit.core.designsystem.components.AppAlertDialog
import com.daykit.core.designsystem.components.AppBottomSheet
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppFab
import com.daykit.core.designsystem.components.AppTextField
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.EmptyState
import com.daykit.core.designsystem.components.FilterChipButton
import com.daykit.core.designsystem.components.LoadingIndicator
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.components.SecondaryButton
import com.daykit.core.designsystem.components.StatTile
import com.daykit.feature.habit.data.Habit
import com.daykit.feature.habit.data.HabitDashboard
import com.daykit.feature.habit.data.HabitGoalType
import com.daykit.feature.habit.data.HabitKind
import com.daykit.feature.habit.data.HabitLog
import com.daykit.feature.habit.reminder.HabitReminderScheduler
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

        else -> HabitHome(
            dashboard = dashboard,
            selectedTab = selectedTab,
            onTabChange = { selectedTab = it },
            onBack = onBack,
            selectedDate = selectedDate,
            onPreviousDate = { selectedDate = selectedDate.minusDays(1) },
            onNextDate = { selectedDate = selectedDate.plusDays(1) },
            onSelectDate = { selectedDate = it },
            selectedMonth = selectedMonth,
            onPreviousMonth = { selectedMonth = selectedMonth.minusMonths(1) },
            onNextMonth = { selectedMonth = selectedMonth.plusMonths(1) },
            onAdd = {
                addKind = if (selectedTab == HabitTab.Quit) HabitKind.Quit else HabitKind.Build
                addOpen = true
            },
            onAddBuild = {
                addKind = HabitKind.Build
                addOpen = true
            },
            onAddQuit = {
                addKind = HabitKind.Quit
                addOpen = true
            },
            onLog = { habit ->
                if (habit.goalType == HabitGoalType.Check) {
                    val existing = dashboard?.logFor(habit.habitId, selectedDate)
                    val newChecked = !(existing?.completed ?: false)
                    val completed = isGoalComplete(habit, 0, 0, checked = newChecked)
                    scope.launch {
                        container.habitRepository.saveDailyProgress(
                            habitId = habit.habitId,
                            date = selectedDate,
                            minutes = 0,
                            progressCount = 0,
                            completed = completed,
                            note = existing?.note ?: "",
                        )
                    }
                } else {
                    logHabit = habit
                }
            },
            onEdit = { editHabit = it },
            onDelete = { deleteHabit = it },
            onRelapse = { relapseHabit = it },
        )
    }

    logHabit?.let { habit ->
        ProgressSheet(
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
        RelapseSheet(
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
        AppAlertDialog(
            onDismissRequest = { deleteHabit = null },
            title = "Delete ${habit.name}?",
            text = "This removes the habit and all of its check-in history.",
            confirmText = "Delete",
            destructiveConfirm = true,
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
private fun HabitHome(
    dashboard: HabitDashboard?,
    selectedTab: HabitTab,
    onTabChange: (HabitTab) -> Unit,
    onBack: () -> Unit,
    selectedDate: LocalDate,
    onPreviousDate: () -> Unit,
    onNextDate: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    selectedMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onAdd: () -> Unit,
    onAddBuild: () -> Unit,
    onAddQuit: () -> Unit,
    onLog: (Habit) -> Unit,
    onEdit: (Habit) -> Unit,
    onDelete: (Habit) -> Unit,
    onRelapse: (Habit) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            AppFab(
                icon = Icons.Rounded.Add,
                contentDescription = "Add habit",
                onClick = onAdd,
            )
        },
    ) { innerPadding ->
        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        Box(modifier = Modifier.fillMaxSize()) {
            when (val current = dashboard) {
                null -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = topInset + 56.dp + Spacing.sm,
                        start = Spacing.lg,
                        end = Spacing.lg,
                        bottom = innerPadding.calculateBottomPadding() + 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    item(key = "tabs") {
                        HabitTabs(selectedTab = selectedTab, onTabChange = onTabChange)
                    }
                    when (selectedTab) {
                        HabitTab.CheckIn -> checkInItems(
                            dashboard = current,
                            selectedDate = selectedDate,
                            onSelectDate = onSelectDate,
                            onLog = onLog,
                            onAdd = onAddBuild,
                        )
                        HabitTab.Habits -> habitsItems(
                            dashboard = current,
                            onAdd = onAddBuild,
                            onEdit = onEdit,
                            onDelete = onDelete,
                        )
                        HabitTab.Progress -> progressItems(
                            dashboard = current,
                            selectedMonth = selectedMonth,
                            onPrevious = onPreviousMonth,
                            onNext = onNextMonth,
                        )
                        HabitTab.Quit -> quitItems(
                            dashboard = current,
                            onAdd = onAddQuit,
                            onRelapse = onRelapse,
                            onEdit = onEdit,
                        )
                    }
                }
            }

            AppTopBar(
                title = "Habits",
                onBack = onBack,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun HabitTabs(selectedTab: HabitTab, onTabChange: (HabitTab) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxWidth(),
    ) {
        HabitTab.values().forEach { tab ->
            FilterChipButton(
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

// ---------------------------------------------------------------------------
// CHECK-IN TAB
// ---------------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.checkInItems(
    dashboard: HabitDashboard,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    onLog: (Habit) -> Unit,
    onAdd: () -> Unit,
) {
    item(key = "quote") { QuoteCard(dashboard = dashboard) }
    item(key = "datestrip") {
        DateStrip(
            dashboard = dashboard,
            selectedDate = selectedDate,
            onSelectDate = onSelectDate,
        )
    }
    if (dashboard.buildHabits.isEmpty()) {
        item(key = "empty") {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                EmptyState(
                    icon = Icons.Rounded.Flag,
                    title = "No habits yet",
                    description = "Add coding, gym, reading, math, building, or anything you want to repeat.",
                    actionText = "Add Habit",
                    onAction = onAdd,
                )
            }
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

@Composable
private fun QuoteCard(dashboard: HabitDashboard) {
    val quote = remember(dashboard.today) { quotes[dashboard.today.dayOfYear % quotes.size] }
    val accent = MaterialTheme.extendedColors.accents.teal
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            AccentIconTile(icon = Icons.Rounded.SelfImprovement, accent = accent)
            Text(
                text = quote,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DateStrip(
    dashboard: HabitDashboard,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
) {
    val today = dashboard.today
    val days = remember(today) { (6 downTo 0).map { today.minusDays(it.toLong()) } }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(days, key = { it.toString() }) { date ->
            DateStripCell(
                date = date,
                progress = dayProgress(dashboard, date),
                selected = date == selectedDate,
                isToday = date == today,
                onClick = { onSelectDate(date) },
            )
        }
    }
}

@Composable
private fun DateStripCell(
    date: LocalDate,
    progress: Float,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.extendedColors.textMuted
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(MaterialTheme.shapes.large)
            .then(
                if (selected) Modifier.border(1.dp, accent, MaterialTheme.shapes.large) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            date.format(DateTimeFormatter.ofPattern("EEE")),
            color = if (selected) accent else muted,
            style = MaterialTheme.typography.labelSmall,
        )
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(38.dp)) {
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.size(38.dp),
                strokeWidth = 3.dp,
                color = MaterialTheme.extendedColors.inputField,
            )
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(38.dp),
                strokeWidth = 3.dp,
                color = accent,
            )
            Text(
                date.dayOfMonth.toString(),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            )
        }
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
    val color = habitColor(habit.colorIndex)
    AppCard(modifier = Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (completed) color else color.asAccentContainer()),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (completed) Icons.Rounded.Check else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (completed) Color.White else color,
                    modifier = Modifier.size(18.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        habit.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${(progress * 100).roundToInt()}%",
                        color = MaterialTheme.extendedColors.textMuted,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(99.dp)),
                    color = color,
                    trackColor = MaterialTheme.extendedColors.inputField,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        progressText(habit, log),
                        color = MaterialTheme.extendedColors.textMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (streak > 0) {
                        Icon(
                            Icons.Rounded.LocalFireDepartment,
                            contentDescription = null,
                            tint = MaterialTheme.extendedColors.accents.orange,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            "$streak",
                            color = MaterialTheme.extendedColors.textMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// HABITS TAB
// ---------------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.habitsItems(
    dashboard: HabitDashboard,
    onAdd: () -> Unit,
    onEdit: (Habit) -> Unit,
    onDelete: (Habit) -> Unit,
) {
    if (dashboard.buildHabits.isEmpty()) {
        item(key = "empty") {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                EmptyState(
                    icon = Icons.Rounded.Flag,
                    title = "No habits yet",
                    description = "Add coding, gym, reading, math, building, or anything you want to repeat.",
                    actionText = "Add Habit",
                    onAction = onAdd,
                )
            }
        }
    } else {
        item(key = "add") {
            PrimaryButton(
                text = "Add Habit",
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
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
    AppCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            AccentIconTile(icon = Icons.Rounded.Flag, accent = habitColor(habit.colorIndex))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        habit.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (habit.reminderEnabled) {
                        Text(
                            timeText(habit.reminderHour, habit.reminderMinute),
                            color = MaterialTheme.extendedColors.textMuted,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                Text(
                    "${habitGoalText(habit)} • $streak day streak",
                    color = MaterialTheme.extendedColors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = MaterialTheme.extendedColors.danger)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// PROGRESS TAB
// ---------------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.progressItems(
    dashboard: HabitDashboard,
    selectedMonth: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    item(key = "stats") { ProgressStatsRow(dashboard = dashboard, month = selectedMonth) }
    item(key = "heatmap") {
        AppCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Previous", tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    selectedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onNext) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Next", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.height(Spacing.sm))
            MonthHeatmap(dashboard = dashboard, month = selectedMonth)
        }
    }
    item(key = "weekly") { WeeklyConsistencyCard(dashboard = dashboard) }
}

@Composable
private fun ProgressStatsRow(dashboard: HabitDashboard, month: YearMonth) {
    val activeHabits = dashboard.buildHabits.size
    val monthDays = (1..month.lengthOfMonth()).map { month.atDay(it) }.filter { !it.isAfter(dashboard.today) }
    val monthPct = if (monthDays.isEmpty()) 0 else {
        (monthDays.sumOf { (dayProgress(dashboard, it) * 100).roundToInt() }.toFloat() / (monthDays.size * 100) * 100).roundToInt()
    }
    val bestStreak = dashboard.buildHabits.maxOfOrNull { habit ->
        bestBuildStreak(habit, dashboard.logs, dashboard.today)
    } ?: 0
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
        StatTile(
            label = "Active",
            value = activeHabits.toString(),
            accent = MaterialTheme.extendedColors.accents.blue,
            icon = Icons.Rounded.Flag,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = "This month",
            value = "$monthPct%",
            accent = MaterialTheme.extendedColors.accents.green,
            icon = Icons.Rounded.CheckCircle,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            label = "Best streak",
            value = bestStreak.toString(),
            accent = MaterialTheme.extendedColors.accents.orange,
            icon = Icons.Rounded.LocalFireDepartment,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun MonthHeatmap(dashboard: HabitDashboard, month: YearMonth) {
    val firstDay = month.atDay(1)
    val offset = (firstDay.dayOfWeek.value % 7)
    val days = month.lengthOfMonth()
    val cells = offset + days
    val rows = (cells + 6) / 7
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach {
                Text(it, color = MaterialTheme.extendedColors.textMuted, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
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
                                    relapse -> MaterialTheme.extendedColors.dangerContainer
                                    progress > 0f -> MaterialTheme.extendedColors.elevated
                                    else -> MaterialTheme.extendedColors.inputField
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (date != null) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(dayNumber.toString(), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelSmall)
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
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Weekly Consistency", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "$keptCount kept • $missedCount skipped",
                    color = MaterialTheme.extendedColors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                if (pastSlots == 0) "0%" else "${((keptCount.toFloat() / pastSlots) * 100).roundToInt()}%",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.weight(1.8f))
            weekDays.forEach { date ->
                Text(
                    date.dayOfWeek.name.take(1),
                    color = if (date == dashboard.today) MaterialTheme.colorScheme.primary else MaterialTheme.extendedColors.textMuted,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
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
                        color = MaterialTheme.colorScheme.onSurface,
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
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.fillMaxWidth()) {
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
                        future -> MaterialTheme.extendedColors.inputField
                        kept -> color
                        progress > 0f -> color.copy(alpha = 0.30f)
                        else -> MaterialTheme.extendedColors.inputField
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
                !future -> Icon(Icons.Rounded.Close, contentDescription = null, tint = MaterialTheme.extendedColors.textMuted, modifier = Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun StatusLegendItem(status: HabitWeekStatus, text: String) {
    val muted = MaterialTheme.extendedColors.textMuted
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(MaterialTheme.extendedColors.inputField),
            contentAlignment = Alignment.Center,
        ) {
            when (status) {
                HabitWeekStatus.Kept -> Icon(Icons.Rounded.Check, contentDescription = null, tint = muted, modifier = Modifier.size(8.dp))
                HabitWeekStatus.Partial -> Box(
                    modifier = Modifier
                        .size(width = 7.dp, height = 2.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(muted),
                )
                HabitWeekStatus.Skipped -> Icon(Icons.Rounded.Close, contentDescription = null, tint = muted, modifier = Modifier.size(7.dp))
            }
        }
        Text(text, color = muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun HabitLegend(habits: List<Habit>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        habits.chunked(2).forEach { rowHabits ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.fillMaxWidth()) {
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
                            color = MaterialTheme.extendedColors.textMuted,
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

// ---------------------------------------------------------------------------
// QUIT TAB
// ---------------------------------------------------------------------------

private fun androidx.compose.foundation.lazy.LazyListScope.quitItems(
    dashboard: HabitDashboard,
    onAdd: () -> Unit,
    onRelapse: (Habit) -> Unit,
    onEdit: (Habit) -> Unit,
) {
    if (dashboard.quitHabits.isEmpty()) {
        item(key = "empty") {
            AppCard(modifier = Modifier.fillMaxWidth()) {
                EmptyState(
                    icon = Icons.Rounded.EventBusy,
                    title = "Leave a bad habit",
                    description = "Track clean days for smoking, porn, junk food, alcohol, or any loop you want to break.",
                    actionText = "Quit Habit",
                    onAction = onAdd,
                )
            }
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

@Composable
private fun QuitHabitCard(
    habit: Habit,
    cleanDays: Int,
    best: Int,
    relapses: Int,
    onRelapse: () -> Unit,
    onEdit: () -> Unit,
) {
    val color = habitColor(habit.colorIndex)
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            AccentIconTile(icon = Icons.Rounded.LocalFireDepartment, accent = color)
            Text(
                habit.name,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                cleanDays.toString(),
                color = color,
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "days clean",
                color = MaterialTheme.extendedColors.textMuted,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
            StatTile(label = "Best streak", value = best.toString(), accent = color, modifier = Modifier.weight(1f))
            StatTile(label = "Relapses", value = relapses.toString(), accent = MaterialTheme.extendedColors.accents.red, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(Spacing.md))
        SecondaryButton(text = "Log relapse", modifier = Modifier.fillMaxWidth(), onClick = onRelapse)
    }
}

// ---------------------------------------------------------------------------
// EDITOR
// ---------------------------------------------------------------------------

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
private fun SegmentedRow(
    modifier: Modifier = Modifier,
    items: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.extendedColors.inputField)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        content = items,
    )
}

@Composable
private fun SegmentOption(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.extendedColors.textMuted,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
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
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.extendedColors.inputField)
            .padding(3.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        DayPeriodOption(
            text = "AM",
            selected = selected == DayPeriod.AM,
            modifier = Modifier.fillMaxWidth().weight(1f),
            onClick = { onSelected(DayPeriod.AM) },
        )
        DayPeriodOption(
            text = "PM",
            selected = selected == DayPeriod.PM,
            modifier = Modifier.fillMaxWidth().weight(1f),
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
            .clip(MaterialTheme.shapes.small)
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.extendedColors.textMuted,
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

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(
                title = if (habit == null) "Add Habit" else "Edit Habit",
                onBack = onDismiss,
            )
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    top = Spacing.sm,
                    bottom = Spacing.lg,
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                item("basics") {
                    EditorSection(title = "Basics") {
                        AppTextField(
                            value = name,
                            onValueChange = { name = it.take(40) },
                            label = "Name",
                            modifier = inputModifier,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        )
                        if (habit == null) {
                            Spacer(Modifier.height(Spacing.sm))
                            SegmentedRow {
                                SegmentOption(
                                    text = "Build",
                                    selected = kind == HabitKind.Build,
                                    modifier = Modifier.weight(1f),
                                    onClick = { kind = HabitKind.Build },
                                )
                                SegmentOption(
                                    text = "Quit",
                                    selected = kind == HabitKind.Quit,
                                    modifier = Modifier.weight(1f),
                                    onClick = { kind = HabitKind.Quit },
                                )
                            }
                        }
                    }
                }

                if (kind == HabitKind.Build) {
                    item("goal") {
                        EditorSection(title = "Goal") {
                            SegmentedRow {
                                HabitGoalType.values().forEach { type ->
                                    SegmentOption(
                                        text = type.name,
                                        selected = goalType == type,
                                        modifier = Modifier.weight(1f),
                                        onClick = { goalType = type },
                                    )
                                }
                            }
                            if (goalType == HabitGoalType.Time) {
                                Spacer(Modifier.height(Spacing.sm))
                                AppTextField(
                                    value = targetMinutes,
                                    onValueChange = { targetMinutes = it.filter(Char::isDigit).take(4) },
                                    label = "Daily minutes",
                                    modifier = inputModifier,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )
                            }
                            if (goalType == HabitGoalType.Count) {
                                Spacer(Modifier.height(Spacing.sm))
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                    AppTextField(
                                        value = targetCount,
                                        onValueChange = { targetCount = it.filter(Char::isDigit).take(4) },
                                        label = "Daily target",
                                        modifier = Modifier.weight(1f).onFocusChanged { inputFocused = it.isFocused },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    )
                                    AppTextField(
                                        value = unitLabel,
                                        onValueChange = { unitLabel = it.take(16) },
                                        label = "Unit",
                                        modifier = Modifier.weight(1f).onFocusChanged { inputFocused = it.isFocused },
                                    )
                                }
                            }
                        }
                    }
                }

                item("color") {
                    EditorSection(title = "Color") {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            habitPalette.indices.chunked(5).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                                    row.forEach { index ->
                                        Box(
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .then(
                                                    if (colorIndex == index) Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier
                                                )
                                                .background(habitColor(index))
                                                .clickable { colorIndex = index },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (colorIndex == index) {
                                                Icon(Icons.Rounded.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item("reminder") {
                    EditorSection(title = "Reminder") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Notifications, contentDescription = null, tint = MaterialTheme.extendedColors.textMuted, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(Spacing.sm))
                            Text("Daily reminder", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            Switch(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
                        }
                        if (reminderEnabled) {
                            Spacer(Modifier.height(Spacing.sm))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppTextField(
                                    value = reminderHour,
                                    onValueChange = { value -> reminderHour = value.filter(Char::isDigit).take(2) },
                                    label = "Hour",
                                    modifier = Modifier.weight(1f).onFocusChanged { inputFocused = it.isFocused },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )
                                AppTextField(
                                    value = reminderMinute,
                                    onValueChange = { reminderMinute = it.filter(Char::isDigit).take(2) },
                                    label = "Minute",
                                    modifier = Modifier.weight(1f).onFocusChanged { inputFocused = it.isFocused },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                )
                                DayPeriodSegmentedControl(
                                    selected = reminderPeriod,
                                    onSelected = { reminderPeriod = it },
                                    modifier = Modifier.width(58.dp),
                                )
                            }
                        }
                    }
                }

                if (habit != null) {
                    item("active") {
                        EditorSection(title = "Status") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Active", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                                Checkbox(checked = active, onCheckedChange = { active = it })
                            }
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = innerPadding.calculateBottomPadding() + Spacing.md, top = Spacing.sm),
            ) {
                SecondaryButton(text = "Cancel", modifier = Modifier.weight(1f), onClick = onDismiss)
                PrimaryButton(text = "Save", modifier = Modifier.weight(1f), enabled = canSave, onClick = ::saveDraft)
            }
        }
    }
}

@Composable
private fun EditorSection(
    title: String,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            title,
            color = MaterialTheme.extendedColors.textMuted,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(Spacing.sm))
        content()
    }
}

// ---------------------------------------------------------------------------
// SHEETS
// ---------------------------------------------------------------------------

@Composable
private fun ProgressSheet(
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
    val color = habitColor(habit.colorIndex)

    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Column {
                Text(habit.name, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    selectedDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                    color = MaterialTheme.extendedColors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (habit.goalType == HabitGoalType.Time) {
                Stepper(
                    label = "Minutes",
                    value = parsedMinutes,
                    step = 5,
                    onValueChange = { minutes = it.coerceAtLeast(0).toString() },
                    onGoal = if (habit.targetMinutes > 0) {
                        { minutes = habit.targetMinutes.toString() }
                    } else null,
                    goalLabel = if (habit.targetMinutes > 0) "Goal ${formatMinutes(habit.targetMinutes)}" else null,
                )
                Text(
                    "${(habitProgress(habit, temporaryLog(habit, parsedMinutes, parsedCount, derivedCompleted)) * 100).roundToInt()}% complete",
                    color = MaterialTheme.extendedColors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (habit.goalType == HabitGoalType.Count) {
                Stepper(
                    label = habit.unitLabel.ifBlank { "Count" },
                    value = parsedCount,
                    step = 1,
                    onValueChange = { count = it.coerceAtLeast(0).toString() },
                    onGoal = if (habit.targetCount > 0) {
                        { count = habit.targetCount.toString() }
                    } else null,
                    goalLabel = if (habit.targetCount > 0) "Goal ${habit.targetCount}" else null,
                )
                Text(
                    "${(habitProgress(habit, temporaryLog(habit, parsedMinutes, parsedCount, derivedCompleted)) * 100).roundToInt()}% complete",
                    color = MaterialTheme.extendedColors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (habit.goalType == HabitGoalType.Check) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mark complete", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Checkbox(checked = checked, onCheckedChange = { checked = it })
                }
            } else {
                Text(
                    if (derivedCompleted) "This will check today's habit." else "Saved as partial progress.",
                    color = if (derivedCompleted) color else MaterialTheme.extendedColors.textMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            AppTextField(
                value = note,
                onValueChange = { note = it.take(160) },
                label = "Note (optional)",
                singleLine = false,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(text = "Cancel", modifier = Modifier.weight(1f), onClick = onDismiss)
                PrimaryButton(text = "Save", modifier = Modifier.weight(1f), onClick = { onSave(parsedMinutes, parsedCount, note) })
            }
        }
    }
}

@Composable
private fun Stepper(
    label: String,
    value: Int,
    step: Int,
    onValueChange: (Int) -> Unit,
    onGoal: (() -> Unit)?,
    goalLabel: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            modifier = Modifier.fillMaxWidth(),
        ) {
            StepperButton(icon = Icons.Rounded.Remove, onClick = { onValueChange(value - step) })
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    value.toString(),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(label, color = MaterialTheme.extendedColors.textMuted, style = MaterialTheme.typography.labelSmall)
            }
            StepperButton(icon = Icons.Rounded.Add, onClick = { onValueChange(value + step) })
        }
        if (onGoal != null && goalLabel != null) {
            SecondaryButton(text = goalLabel, modifier = Modifier.fillMaxWidth(), onClick = onGoal)
        }
    }
}

@Composable
private fun StepperButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.extendedColors.inputField)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun RelapseSheet(
    habit: Habit,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var note by remember { mutableStateOf("") }
    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text("Reset ${habit.name}?", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "The counter resets, but the history stays. Add a reason if it helps you notice the pattern.",
                color = MaterialTheme.extendedColors.textMuted,
                style = MaterialTheme.typography.bodyMedium,
            )
            AppTextField(
                value = note,
                onValueChange = { note = it.take(160) },
                label = "Reason (optional)",
                singleLine = false,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(text = "Cancel", modifier = Modifier.weight(1f), onClick = onDismiss)
                PrimaryButton(text = "Save", modifier = Modifier.weight(1f), onClick = { onSave(note) })
            }
        }
    }
}

// ---------------------------------------------------------------------------
// DOMAIN / HELPERS (preserved verbatim)
// ---------------------------------------------------------------------------

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

private fun bestBuildStreak(habit: Habit, logs: List<HabitLog>, today: LocalDate): Int {
    val completedDates = logs
        .filter { it.habitId == habit.habitId && !it.relapse }
        .filter { isLogComplete(habit, it) }
        .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
        .filter { !it.isAfter(today) }
        .distinct()
        .sorted()
    if (completedDates.isEmpty()) return 0
    var best = 1
    var run = 1
    for (i in 1 until completedDates.size) {
        if (completedDates[i] == completedDates[i - 1].plusDays(1)) {
            run += 1
            best = maxOf(best, run)
        } else {
            run = 1
        }
    }
    return best
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
