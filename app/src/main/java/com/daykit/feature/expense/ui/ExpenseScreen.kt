@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.daykit.feature.expense.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import com.daykit.core.util.WeekDays
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import com.daykit.core.designsystem.MinTouchTarget
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daykit.AppContainer
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.asAccentContainer
import com.daykit.core.designsystem.components.AccentIconTile
import com.daykit.core.designsystem.components.AppAlertDialog
import com.daykit.core.designsystem.components.AppBackButton
import com.daykit.core.designsystem.components.AppBottomSheet
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppFab
import com.daykit.core.designsystem.components.AppTextButton
import com.daykit.core.designsystem.components.AppTextField
import com.daykit.core.designsystem.components.EmptyState
import com.daykit.core.designsystem.components.FilterChipButton
import com.daykit.core.designsystem.components.LoadingIndicator
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.components.rememberErrorReporter
import com.daykit.core.designsystem.components.SecondaryButton
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.SectionHeader
import com.daykit.core.designsystem.extendedColors
import com.daykit.core.util.Money
import com.daykit.feature.expense.data.ExpenseEntry
import com.daykit.feature.expense.data.ExpenseEntryKind
import com.daykit.feature.expense.data.ExpenseMonthSummary
import com.daykit.feature.expense.data.MonthlyBill
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

private enum class ExpenseChartMode {
    Daily,
    Weekly,
    Monthly,
}

private const val DEFAULT_CATEGORY = "General"
private val EXPENSE_CATEGORIES = listOf(
    DEFAULT_CATEGORY,
    "Food",
    "Transport",
    "Shopping",
    "Bills",
    "Health",
)

@Composable
fun ExpenseScreen(
    container: AppContainer,
    onBack: () -> Unit,
) {
    val errors = rememberErrorReporter()
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    val monthKey = remember(selectedMonth) { selectedMonth.toString() }
    val summary by container.expenseRepository
        .observeMonth(monthKey)
        .collectAsStateWithLifecycle(initialValue = null)
    val allBills by container.expenseRepository
        .observeBills()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val allEntries by container.expenseRepository
        .observeAllEntries()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var addDailyOpen by remember { mutableStateOf(false) }
    var addBillOpen by remember { mutableStateOf(false) }
    var manageBillsOpen by remember { mutableStateOf(false) }
    var chartMode by remember { mutableStateOf(ExpenseChartMode.Daily) }
    var limitOpen by remember { mutableStateOf(false) }
    var editEntry by remember { mutableStateOf<ExpenseEntry?>(null) }
    var deleteEntry by remember { mutableStateOf<ExpenseEntry?>(null) }
    var stopBill by remember { mutableStateOf<MonthlyBill?>(null) }
    var editBill by remember { mutableStateOf<MonthlyBill?>(null) }

    BackHandler {
        if (manageBillsOpen) {
            manageBillsOpen = false
        } else {
            onBack()
        }
    }

    LaunchedEffect(monthKey) {
        try {
            container.expenseRepository.ensureMonth(monthKey)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.e("DayKit", "ensureMonth failed for $monthKey", e)
            errors.show("Couldn't load this month.")
        }
    }

    val listState = rememberLazyListState()
    val manageBillsListState = rememberLazyListState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(errors.host) },
        topBar = {
            if (manageBillsOpen) {
                AppTopBar(
                    title = "Monthly bills",
                    subtitle = "${allBills.count { it.active }} active",
                    onBack = { manageBillsOpen = false },
                )
            } else {
                AppTopBar(
                    title = "Expenses",
                    subtitle = monthLabel(selectedMonth),
                    onBack = onBack,
                    actions = {
                        IconButton(onClick = { selectedMonth = selectedMonth.minusMonths(1) }) {
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                contentDescription = "Previous month",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        IconButton(onClick = { selectedMonth = selectedMonth.plusMonths(1) }) {
                            Icon(
                                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = "Next month",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    },
                )
            }
        },
        floatingActionButton = {
            if (manageBillsOpen) {
                AppFab(
                    icon = Icons.Rounded.Add,
                    contentDescription = "Add bill",
                    onClick = { addBillOpen = true },
                )
            } else {
                AppFab(
                    icon = Icons.Rounded.Add,
                    contentDescription = "Add expense",
                    onClick = { addDailyOpen = true },
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (manageBillsOpen) {
                ManageBillsContent(
                    bills = allBills,
                    listState = manageBillsListState,
                    onAddBill = { addBillOpen = true },
                    onEditBill = { editBill = it },
                )
            } else {
                when (val currentSummary = summary) {
                    null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }

                    else -> ExpenseMainList(
                        summary = currentSummary,
                        allEntries = allEntries,
                        selectedMonth = selectedMonth,
                        chartMode = chartMode,
                        listState = listState,
                        onChartModeChange = { chartMode = it },
                        onSetLimit = { limitOpen = true },
                        onManageBills = { manageBillsOpen = true },
                        onEditEntry = { editEntry = it },
                        onDeleteEntry = { deleteEntry = it },
                    )
                }
            }
        }
    }

    if (addDailyOpen) {
        ExpenseFormSheet(
            entry = null,
            onDismiss = { addDailyOpen = false },
            onSave = { title, category, amount, note, expenseDate ->
                errors.launchGuarded(
                    failureMessage = "Couldn't save that expense.",
                    onFailure = { addDailyOpen = false },
                ) {
                    container.expenseRepository.addDailyExpense(expenseDate, title, category, amount, note)
                    selectedMonth = YearMonth.from(LocalDate.parse(expenseDate))
                    addDailyOpen = false
                }
            },
        )
    }

    editEntry?.let { entry ->
        ExpenseFormSheet(
            entry = entry,
            onDismiss = { editEntry = null },
            onSave = { title, category, amount, note, expenseDate ->
                errors.launchGuarded(
                    failureMessage = "Couldn't save your changes.",
                    onFailure = { editEntry = null },
                ) {
                    container.expenseRepository.updateEntry(entry.entryId, title, category, amount, note, expenseDate)
                    selectedMonth = YearMonth.from(LocalDate.parse(expenseDate))
                    editEntry = null
                }
            },
            onDelete = {
                editEntry = null
                deleteEntry = entry
            },
        )
    }

    if (limitOpen) {
        LimitSheet(
            currentLimit = summary?.limitMinor ?: 0L,
            month = selectedMonth,
            onDismiss = { limitOpen = false },
            onSave = { amount ->
                errors.launchGuarded(
                    failureMessage = "Couldn't update the monthly limit.",
                    onFailure = { limitOpen = false },
                ) {
                    container.expenseRepository.setMonthlyLimit(monthKey, amount)
                    limitOpen = false
                }
            },
        )
    }

    if (addBillOpen) {
        BillFormSheet(
            bill = null,
            defaultMonth = selectedMonth,
            onDismiss = { addBillOpen = false },
            onSave = { draft ->
                errors.launchGuarded(
                    failureMessage = "Couldn't add that bill.",
                    onFailure = { addBillOpen = false },
                ) {
                    container.expenseRepository.addMonthlyBill(
                        draft.title,
                        draft.category,
                        draft.amountMinor,
                        draft.startMonth.toString(),
                        draft.endMonth?.toString(),
                        draft.dueDay,
                    )
                    container.expenseRepository.ensureMonth(monthKey)
                    addBillOpen = false
                }
            },
        )
    }

    editBill?.let { bill ->
        BillFormSheet(
            bill = bill,
            defaultMonth = selectedMonth,
            onDismiss = { editBill = null },
            onSave = { draft ->
                errors.launchGuarded(
                    failureMessage = "Couldn't save that bill.",
                    onFailure = { editBill = null },
                ) {
                    container.expenseRepository.updateMonthlyBill(
                        bill.billId,
                        draft.title,
                        draft.category,
                        draft.startMonth.toString(),
                        draft.endMonth?.toString(),
                        draft.dueDay,
                    )
                    if (draft.amountMinor != bill.amountMinor) {
                        container.expenseRepository.updateMonthlyBillAmount(
                            bill.billId,
                            draft.amountFrom.toString(),
                            draft.amountMinor,
                        )
                    }
                    container.expenseRepository.ensureMonth(monthKey)
                    editBill = null
                }
            },
            onStop = {
                editBill = null
                stopBill = bill
            },
        )
    }

    deleteEntry?.let { entry ->
        AppAlertDialog(
            onDismissRequest = { deleteEntry = null },
            title = "Delete expense?",
            text = "Remove ${entry.title} (${Money.format(entry.amountMinor)}) from ${monthLabel(selectedMonth)}?",
            confirmText = "Delete",
            destructiveConfirm = true,
            onConfirm = {
                deleteEntry = null
                errors.launchGuarded("Couldn't delete that expense.") {
                    container.expenseRepository.deleteEntry(entry.entryId)
                }
            },
        )
    }

    stopBill?.let { bill ->
        AppAlertDialog(
            onDismissRequest = { stopBill = null },
            title = "Stop ${bill.title}?",
            text = "It won't be added to future months. Past months keep it.",
            confirmText = "Stop",
            destructiveConfirm = true,
            onConfirm = {
                stopBill = null
                errors.launchGuarded("Couldn't stop that bill.") {
                    container.expenseRepository.stopMonthlyBill(bill.billId)
                    container.expenseRepository.ensureMonth(monthKey)
                }
            },
        )
    }
}

@Composable
private fun ExpenseMainList(
    summary: ExpenseMonthSummary,
    allEntries: List<ExpenseEntry>,
    selectedMonth: YearMonth,
    chartMode: ExpenseChartMode,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onChartModeChange: (ExpenseChartMode) -> Unit,
    onSetLimit: () -> Unit,
    onManageBills: () -> Unit,
    onEditEntry: (ExpenseEntry) -> Unit,
    onDeleteEntry: (ExpenseEntry) -> Unit,
) {
    val todayKey = LocalDate.now().toString()
    val todaysBills = summary.entries.filter {
        it.kind == ExpenseEntryKind.MonthlyBill && it.expenseDate == todayKey
    }
    val activeBillCount = summary.monthlyBills.size
    val billsPerMonth = summary.monthlyBills.sumOf { it.amountMinor }
    val dayGroups = remember(summary.entries) {
        summary.entries
            .groupBy { it.expenseDate }
            .toList()
            .sortedByDescending { it.first }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.lg, end = Spacing.lg,
            top = Spacing.md, bottom = Spacing.xxl + 72.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        item {
            HeroSummaryCard(summary = summary, onSetLimit = onSetLimit)
        }
        item {
            AppCard(onClick = onManageBills) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AccentIconTile(
                        icon = Icons.Rounded.Subscriptions,
                        accent = MaterialTheme.extendedColors.accents.indigo,
                    )
                    Spacer(Modifier.width(Spacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Monthly bills",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            if (activeBillCount == 0) "Rent, subscriptions, internet…" else "$activeBillCount active · ${Money.format(billsPerMonth)} a month",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.extendedColors.textMuted,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.extendedColors.textMuted,
                    )
                }
            }
        }
        if (summary.entries.isNotEmpty()) {
            item {
                SpendChartCard(
                    mode = chartMode,
                    onModeChange = onChartModeChange,
                    selectedMonth = selectedMonth,
                    monthEntries = summary.entries,
                    allEntries = allEntries,
                )
            }
        }

        if (todaysBills.isNotEmpty()) {
            item { SectionHeader("Bills due today") }
            items(todaysBills, key = { "today-bill-${it.entryId}" }) { entry ->
                ExpenseEntryRow(
                    entry = entry,
                    onEdit = { onEditEntry(entry) },
                    onDelete = { onDeleteEntry(entry) },
                )
            }
        }

        item { SectionHeader("This month") }
        if (dayGroups.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.ReceiptLong,
                    title = "No expenses this month",
                    description = "Add an expense to see your monthly totals and spending chart.",
                )
            }
        } else {
            dayGroups.forEach { (date, entries) ->
                item(key = "day-header-$date") {
                    DayHeader(date = date, subtotal = entries.sumOf { it.amountMinor })
                }
                items(entries, key = { "month-entry-${it.entryId}" }) { entry ->
                    ExpenseEntryRow(
                        entry = entry,
                        onEdit = { onEditEntry(entry) },
                        onDelete = { onDeleteEntry(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroSummaryCard(
    summary: ExpenseMonthSummary,
    onSetLimit: () -> Unit,
) {
    val isOverLimit = summary.limitMinor > 0L && summary.totalMinor > summary.limitMinor
    AppCard {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Spent this month",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extendedColors.textMuted,
                modifier = Modifier.weight(1f),
            )
            AppTextButton(
                text = if (summary.limitMinor > 0L) "Edit limit" else "Set limit",
                onClick = onSetLimit,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            Money.format(summary.totalMinor),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        if (summary.limitMinor > 0L) {
            Spacer(Modifier.height(Spacing.md))
            LinearProgressIndicator(
                progress = { summary.limitProgress.coerceAtMost(1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(8.dp)),
                color = if (isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.extendedColors.inputField,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = if (isOverLimit) {
                    "${Money.format(-summary.remainingMinor)} over ${Money.format(summary.limitMinor)} limit"
                } else {
                    "${Money.format(summary.remainingMinor)} left from ${Money.format(summary.limitMinor)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (isOverLimit) MaterialTheme.colorScheme.error else MaterialTheme.extendedColors.textMuted,
            )
        }
        Spacer(Modifier.height(Spacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            com.daykit.core.designsystem.components.StatTile(
                label = "Bills",
                value = Money.format(summary.billTotalMinor),
                icon = Icons.Rounded.Subscriptions,
                accent = MaterialTheme.extendedColors.accents.indigo,
                modifier = Modifier.weight(1f),
            )
            com.daykit.core.designsystem.components.StatTile(
                label = "Other expenses",
                value = Money.format(summary.dailyTotalMinor),
                icon = Icons.Rounded.ReceiptLong,
                accent = MaterialTheme.extendedColors.accents.teal,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DayHeader(date: String, subtotal: Long) {
    val label = date.toLocalDateOrNull()?.format(DateTimeFormatter.ofPattern("EEE, dd MMM")) ?: date
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.extendedColors.textMuted,
            modifier = Modifier.weight(1f),
        )
        Text(
            Money.format(subtotal),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ExpenseEntryRow(
    entry: ExpenseEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val isBill = entry.kind == ExpenseEntryKind.MonthlyBill
    val (accent, icon) = categoryStyle(entry.category)
    val displayIcon = if (isBill) Icons.Rounded.CalendarMonth else icon
    val supporting = if (isBill) "Monthly bill · ${entry.category}" else entry.category
    val note = entry.note.takeIf { entry.kind == ExpenseEntryKind.Daily && it.isNotBlank() }

    AppCard(
        onClick = onEdit,
        contentPadding = PaddingValues(start = Spacing.md, top = Spacing.md, bottom = Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentIconTile(icon = displayIcon, accent = if (isBill) MaterialTheme.extendedColors.accents.indigo else accent)
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                note?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.extendedColors.textMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(Spacing.md))
            Text(
                Money.format(entry.amountMinor),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
            EntryOverflowMenu(onEdit = onEdit, onDelete = onDelete)
        }
    }
}

@Composable
private fun SpendChartCard(
    mode: ExpenseChartMode,
    onModeChange: (ExpenseChartMode) -> Unit,
    selectedMonth: YearMonth,
    monthEntries: List<ExpenseEntry>,
    allEntries: List<ExpenseEntry>,
) {
    val bars = remember(mode, selectedMonth, monthEntries, allEntries) {
        chartBars(mode, selectedMonth, monthEntries, allEntries)
    }
    var selectedBar by remember(mode, selectedMonth, bars) { mutableStateOf<ChartBar?>(null) }
    val maxAmount = bars.maxOfOrNull { it.amountMinor }?.coerceAtLeast(1L) ?: 1L
    val barColor = MaterialTheme.colorScheme.primary
    val selectedColor = MaterialTheme.extendedColors.accents.teal
    val zeroColor = MaterialTheme.extendedColors.inputField

    AppCard {
        Text(
            "Spending graph",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
            ExpenseChartMode.values().forEach { item ->
                FilterChipButton(
                    text = item.name.lowercase().replaceFirstChar(Char::titlecase),
                    selected = mode == item,
                    onClick = { onModeChange(item) },
                )
            }
        }
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = selectedBar?.let { "${it.label}: ${Money.format(it.amountMinor)}" } ?: "Tap a bar to see exact spend",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.extendedColors.textMuted,
        )
        Spacer(Modifier.height(Spacing.sm))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(
                modifier = Modifier
                    .width(42.dp)
                    .height(126.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                Text(Money.compact(maxAmount), color = MaterialTheme.extendedColors.textMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Text(Money.compact(maxAmount / 2), color = MaterialTheme.extendedColors.textMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Text(Money.compact(0), color = MaterialTheme.extendedColors.textMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            Spacer(Modifier.width(Spacing.sm))
            Box(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    bars.forEach { bar ->
                        val ratio = (bar.amountMinor.toFloat() / maxAmount).coerceIn(0.04f, 1f)
                        val isSelected = selectedBar == bar
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(104.dp),
                                contentAlignment = Alignment.BottomCenter,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height((104 * ratio).dp)
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        .background(
                                            when {
                                                bar.amountMinor <= 0L -> zeroColor
                                                isSelected -> selectedColor
                                                else -> barColor
                                            },
                                        )
                                        .clickable { selectedBar = bar },
                                )
                            }
                            Spacer(Modifier.height(Spacing.xs))
                            Text(
                                bar.label,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManageBillsContent(
    bills: List<MonthlyBill>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onAddBill: () -> Unit,
    onEditBill: (MonthlyBill) -> Unit,
) {
    val (active, stopped) = bills.partition { it.active }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.lg, end = Spacing.lg,
            top = Spacing.sm, bottom = Spacing.xxl + 72.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        if (bills.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.Subscriptions,
                    title = "No bills yet",
                    description = "Rent, subscriptions, internet: add them once and they show up every month.",
                    actionText = "Add bill",
                    onAction = onAddBill,
                )
            }
        } else {
            item {
                Text(
                    "${Money.format(active.sumOf { it.amountMinor })} a month",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(active, key = { it.billId }) { bill ->
                ManageBillCard(bill = bill, onClick = { onEditBill(bill) })
            }
            if (stopped.isNotEmpty()) {
                item { SectionHeader("Stopped") }
                items(stopped, key = { it.billId }) { bill ->
                    ManageBillCard(bill = bill, onClick = { onEditBill(bill) })
                }
            }
        }
    }
}

@Composable
private fun ManageBillCard(
    bill: MonthlyBill,
    onClick: () -> Unit,
) {
    val muted = MaterialTheme.extendedColors.textMuted
    AppCard(onClick = onClick, contentPadding = PaddingValues(Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentIconTile(
                icon = Icons.Rounded.Subscriptions,
                accent = if (bill.active) MaterialTheme.extendedColors.accents.indigo else muted,
            )
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    bill.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (bill.active) MaterialTheme.colorScheme.onSurface else muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Due on the ${ordinal(bill.dueDay)} · ${billRangeLabel(bill)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Text(
                Money.format(bill.amountMinor),
                style = MaterialTheme.typography.titleMedium,
                color = if (bill.active) MaterialTheme.colorScheme.onSurface else muted,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun EntryOverflowMenu(onEdit: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.size(MinTouchTarget)) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = "More options",
                tint = MaterialTheme.extendedColors.textMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = MaterialTheme.extendedColors.card,
        ) {
            DropdownMenuItem(
                text = { Text("Edit") },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(20.dp)) },
                onClick = {
                    expanded = false
                    onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.extendedColors.danger) },
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.extendedColors.danger,
                        modifier = Modifier.size(20.dp),
                    )
                },
                onClick = {
                    expanded = false
                    onDelete()
                },
            )
        }
    }
}

/** Large amount entry with the currency symbol in front; no label, the size says what it is. */
@Composable
private fun AmountInput(
    value: String,
    onValueChange: (String) -> Unit,
    autoFocus: Boolean,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(autoFocus) {
        if (autoFocus) runCatching { focusRequester.requestFocus() }
    }
    val textStyle = MaterialTheme.typography.headlineLarge.copy(
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold,
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.extendedColors.inputField)
            .clickable { runCatching { focusRequester.requestFocus() } }
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
    ) {
        Text(
            Money.symbol(),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.extendedColors.textMuted,
        )
        Spacer(Modifier.width(Spacing.sm))
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(it.cleanAmountInput()) },
            textStyle = textStyle,
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text("0", style = textStyle.copy(color = MaterialTheme.extendedColors.textMuted))
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun SheetTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun ExpenseFormSheet(
    entry: ExpenseEntry?,
    onDismiss: () -> Unit,
    onSave: (String, String, Long, String, String) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val today = LocalDate.now()
    var amount by remember { mutableStateOf(entry?.let { minorToInput(it.amountMinor) } ?: "") }
    var category by remember { mutableStateOf(entry?.category ?: DEFAULT_CATEGORY) }
    var name by remember { mutableStateOf(entry?.title ?: "") }
    var note by remember { mutableStateOf(entry?.note ?: "") }
    var noteOpen by remember { mutableStateOf(note.isNotBlank()) }
    var date by remember { mutableStateOf(entry?.expenseDate?.toLocalDateOrNull() ?: today) }
    var datePickerOpen by remember { mutableStateOf(false) }
    val amountMinor = amount.toMinorOrNull()
    val canSave = amountMinor != null && amountMinor > 0L

    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SheetTitle(if (entry == null) "Add expense" else "Edit expense")
            AmountInput(value = amount, onValueChange = { amount = it }, autoFocus = entry == null)
            CategoryChips(category = category, onCategoryChange = { category = it })
            AppTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = "What for? (optional)",
                placeholder = category,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                FilterChipButton(text = "Today", selected = date == today, onClick = { date = today })
                FilterChipButton(
                    text = "Yesterday",
                    selected = date == today.minusDays(1),
                    onClick = { date = today.minusDays(1) },
                )
                val otherDate = date != today && date != today.minusDays(1)
                FilterChipButton(
                    text = if (otherDate) date.format(DateTimeFormatter.ofPattern("d MMM")) else "Pick date",
                    selected = otherDate,
                    onClick = { datePickerOpen = true },
                )
            }
            if (noteOpen) {
                AppTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = "Note",
                    singleLine = false,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
            } else {
                AppTextButton(text = "Add a note", onClick = { noteOpen = true })
            }
            PrimaryButton(
                text = if (entry == null) "Add expense" else "Save",
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    onSave(name.trim().ifBlank { category }, category, amountMinor ?: 0L, note, date.toString())
                },
            )
            if (onDelete != null) {
                AppTextButton(
                    text = "Delete expense",
                    color = MaterialTheme.extendedColors.danger,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    onClick = onDelete,
                )
            }
        }
    }

    if (datePickerOpen) {
        ExpenseDatePickerDialog(
            date = date,
            onDismiss = { datePickerOpen = false },
            onConfirm = {
                date = it
                datePickerOpen = false
            },
        )
    }
}

@Composable
private fun LimitSheet(
    currentLimit: Long,
    month: YearMonth,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
) {
    var amount by remember { mutableStateOf(if (currentLimit > 0L) minorToInput(currentLimit) else "") }
    val amountMinor = amount.toMinorOrNull() ?: 0L

    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SheetTitle("Spending limit")
            Text(
                "For ${monthLabel(month)}. The bar on top turns red when you go over.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extendedColors.textMuted,
            )
            AmountInput(value = amount, onValueChange = { amount = it }, autoFocus = currentLimit <= 0L)
            PrimaryButton(
                text = "Save",
                enabled = amountMinor > 0L,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onSave(amountMinor) },
            )
            if (currentLimit > 0L) {
                AppTextButton(
                    text = "Remove limit",
                    color = MaterialTheme.extendedColors.danger,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    onClick = { onSave(0L) },
                )
            }
        }
    }
}

private data class BillDraft(
    val title: String,
    val category: String,
    val amountMinor: Long,
    val amountFrom: YearMonth,
    val startMonth: YearMonth,
    val endMonth: YearMonth?,
    val dueDay: Int,
)

@Composable
private fun BillFormSheet(
    bill: MonthlyBill?,
    defaultMonth: YearMonth,
    onDismiss: () -> Unit,
    onSave: (BillDraft) -> Unit,
    onStop: (() -> Unit)? = null,
) {
    var amount by remember { mutableStateOf(bill?.let { minorToInput(it.amountMinor) } ?: "") }
    var name by remember { mutableStateOf(bill?.title ?: "") }
    var category by remember { mutableStateOf(bill?.category ?: "Bills") }
    var dueDay by remember { mutableStateOf(bill?.dueDay ?: LocalDate.now().dayOfMonth) }
    var startMonth by remember { mutableStateOf(bill?.startMonthKey?.let(YearMonth::parse) ?: defaultMonth) }
    var endMonth by remember { mutableStateOf(bill?.endMonthKey?.let(YearMonth::parse)) }
    var amountFrom by remember { mutableStateOf(defaultMonth) }
    var dueDayOpen by remember { mutableStateOf(false) }
    val amountMinor = amount.toMinorOrNull()
    val amountChanged = bill != null && amountMinor != null && amountMinor != bill.amountMinor
    val rangeValid = endMonth?.let { !it.isBefore(startMonth) } ?: true
    val canSave = name.isNotBlank() && amountMinor != null && amountMinor > 0L && rangeValid

    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            SheetTitle(if (bill == null) "New monthly bill" else "Edit bill")
            AmountInput(value = amount, onValueChange = { amount = it }, autoFocus = bill == null)
            if (amountChanged) {
                MonthPickerField(
                    label = "New amount applies from",
                    month = amountFrom,
                    onMonthChange = { it?.let { month -> amountFrom = month } },
                )
            }
            AppTextField(
                value = name,
                onValueChange = { name = it.take(60) },
                label = "Bill name",
                placeholder = "Rent, Netflix, Internet…",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            CategoryChips(category = category, onCategoryChange = { category = it })
            PickerField(
                label = "Due on",
                value = "The ${ordinal(dueDay)} of every month",
                icon = Icons.Rounded.Event,
                onClick = { dueDayOpen = true },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.weight(1f)) {
                    MonthPickerField(
                        label = "Starts",
                        month = startMonth,
                        onMonthChange = { it?.let { month -> startMonth = month } },
                    )
                }
                Box(Modifier.weight(1f)) {
                    MonthPickerField(
                        label = "Ends",
                        month = endMonth,
                        allowNever = true,
                        onMonthChange = { endMonth = it },
                    )
                }
            }
            if (!rangeValid) {
                Text(
                    "The end month is before the start month.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            PrimaryButton(
                text = if (bill == null) "Add bill" else "Save",
                enabled = canSave,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    onSave(
                        BillDraft(
                            title = name.trim(),
                            category = category,
                            amountMinor = amountMinor ?: 0L,
                            amountFrom = amountFrom,
                            startMonth = startMonth,
                            endMonth = endMonth,
                            dueDay = dueDay,
                        ),
                    )
                },
            )
            if (onStop != null && bill?.active == true) {
                AppTextButton(
                    text = "Stop this bill",
                    color = MaterialTheme.extendedColors.danger,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    onClick = onStop,
                )
            }
        }
    }

    if (dueDayOpen) {
        DueDaySheet(
            selected = dueDay,
            onDismiss = { dueDayOpen = false },
            onSelect = {
                dueDay = it
                dueDayOpen = false
            },
        )
    }
}

@Composable
private fun DueDaySheet(
    selected: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = Spacing.lg)
                .padding(bottom = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            SheetTitle("Due on")
            (1..31).chunked(7).forEach { week ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), modifier = Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        val isSelected = day == selected
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .height(MinTouchTarget)
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .selectable(selected = isSelected, onClick = { onSelect(day) }, role = Role.RadioButton),
                        ) {
                            Text(
                                day.toString(),
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    }
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Text(
                "In shorter months it falls on the last day.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extendedColors.textMuted,
            )
        }
    }
}

@Composable
private fun CategoryChips(
    category: String,
    onCategoryChange: (String) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        items(EXPENSE_CATEGORIES, key = { it }) { option ->
            FilterChipButton(
                text = option,
                selected = option == category,
                onClick = { onCategoryChange(option) },
            )
        }
    }
}

@Composable
private fun ExpenseDatePickerDialog(
    date: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = date.toMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onConfirm(it.toLocalDate()) } ?: onDismiss() }) {
                Text("Select", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.extendedColors.textMuted)
            }
        },
        colors = DatePickerDefaults.colors(containerColor = MaterialTheme.extendedColors.card),
    ) {
        DatePicker(state = state)
    }
}

/** [allowNever] adds a "Never" choice, reported as a null month. */
@Composable
private fun MonthPickerField(
    label: String,
    month: YearMonth?,
    onMonthChange: (YearMonth?) -> Unit,
    allowNever: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    var draftMonth by remember(month) { mutableStateOf(month ?: YearMonth.now()) }
    PickerField(
        label = label,
        value = month?.let(::monthLabel) ?: "Never",
        icon = Icons.Rounded.CalendarMonth,
        onClick = {
            draftMonth = month ?: YearMonth.now()
            open = true
        },
    )
    if (open) {
        AppBottomSheet(onDismissRequest = { open = false }) {
            Column(
                modifier = Modifier
                    .padding(horizontal = Spacing.lg)
                    .padding(bottom = Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                SheetTitle(label)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = { draftMonth = draftMonth.minusMonths(1) }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                            contentDescription = "Previous month",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Text(
                        monthLabel(draftMonth),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    IconButton(onClick = { draftMonth = draftMonth.plusMonths(1) }) {
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = "Next month",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                    if (allowNever) {
                        SecondaryButton(text = "Never", modifier = Modifier.weight(1f), onClick = {
                            onMonthChange(null)
                            open = false
                        })
                    }
                    PrimaryButton(text = "Select", modifier = Modifier.weight(1f), onClick = {
                        onMonthChange(draftMonth)
                        open = false
                    })
                }
            }
        }
    }
}

@Composable
private fun PickerField(
    label: String,
    value: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.extendedColors.inputField)
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.md, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(label, color = MaterialTheme.extendedColors.textMuted, style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text(
                value,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun ordinal(day: Int): String {
    val suffix = if (day in 11..13) "th" else when (day % 10) {
        1 -> "st"
        2 -> "nd"
        3 -> "rd"
        else -> "th"
    }
    return "$day$suffix"
}

private fun monthLabel(month: YearMonth): String {
    return month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
}

@Composable
private fun categoryStyle(category: String): Pair<Color, ImageVector> {
    val accents = MaterialTheme.extendedColors.accents
    return when (category) {
        "Food" -> accents.orange to Icons.Rounded.Restaurant
        "Transport" -> accents.blue to Icons.Rounded.DirectionsCar
        "Shopping" -> accents.pink to Icons.Rounded.ShoppingBag
        "Bills" -> accents.indigo to Icons.Rounded.Subscriptions
        "Health" -> accents.green to Icons.Rounded.FavoriteBorder
        else -> accents.teal to Icons.Rounded.ReceiptLong
    }
}

private fun String.cleanAmountInput(): String {
    val filtered = filter { it.isDigit() || it == '.' }
    val firstDot = filtered.indexOf('.')
    return if (firstDot == -1) {
        filtered.take(9)
    } else {
        filtered.take(firstDot + 1) + filtered.drop(firstDot + 1).filter(Char::isDigit).take(2)
    }
}

private fun String.toMinorOrNull(): Long? {
    val value = toDoubleOrNull() ?: return null
    return (value * 100.0).roundToLong()
}

private fun String.toLocalDateOrNull(): LocalDate? {
    return runCatching { LocalDate.parse(this) }.getOrNull()
}

private fun minorToInput(amountMinor: Long): String {
    val whole = amountMinor / 100
    val fraction = amountMinor % 100
    return if (fraction == 0L) whole.toString() else "$whole.${fraction.toString().padStart(2, '0')}"
}

private data class ChartBar(
    val label: String,
    val amountMinor: Long,
)

private fun chartBars(
    mode: ExpenseChartMode,
    selectedMonth: YearMonth,
    monthEntries: List<ExpenseEntry>,
    allEntries: List<ExpenseEntry>,
): List<ChartBar> {
    return when (mode) {
        ExpenseChartMode.Daily -> {
            val today = LocalDate.now()
            val weekStart = WeekDays.startOf(today)
            (0..6).map { offset ->
                val date = weekStart.plusDays(offset.toLong())
                val amount = allEntries
                    .filter { it.expenseDate == date.toString() }
                    .sumOf { it.amountMinor }
                ChartBar(date.format(DateTimeFormatter.ofPattern("EEE")), amount)
            }
        }

        ExpenseChartMode.Weekly -> {
            (1..5).map { week ->
                val startDay = ((week - 1) * 7) + 1
                val endDay = (week * 7).coerceAtMost(selectedMonth.lengthOfMonth())
                val amount = monthEntries
                    .filter {
                        val day = it.expenseDate.toLocalDateOrNull()?.dayOfMonth ?: 0
                        day in startDay..endDay
                    }
                    .sumOf { it.amountMinor }
                ChartBar("W$week", amount)
            }
        }

        ExpenseChartMode.Monthly -> {
            (5 downTo 0).map { offset ->
                val month = selectedMonth.minusMonths(offset.toLong())
                val amount = allEntries
                    .filter { it.monthKey == month.toString() }
                    .sumOf { it.amountMinor }
                ChartBar(month.format(DateTimeFormatter.ofPattern("MMM")), amount)
            }
        }
    }
}

private fun billRangeLabel(bill: MonthlyBill): String {
    val start = YearMonth.parse(bill.startMonthKey).format(DateTimeFormatter.ofPattern("MMM yyyy"))
    val end = bill.endMonthKey?.let { YearMonth.parse(it).format(DateTimeFormatter.ofPattern("MMM yyyy")) }
    return if (end == null) "$start onward" else "$start - $end"
}

private fun LocalDate.toMillis(): Long {
    return atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

private fun Long.toLocalDate(): LocalDate {
    return Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
}
