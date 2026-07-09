@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.daykit.feature.expense.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.daykit.core.designsystem.components.SecondaryButton
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.SectionHeader
import com.daykit.core.designsystem.extendedColors
import com.daykit.core.util.Money
import com.daykit.feature.expense.data.ExpenseEntry
import com.daykit.feature.expense.data.ExpenseEntryKind
import com.daykit.feature.expense.data.ExpenseMonthSummary
import com.daykit.feature.expense.data.MonthlyBill
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
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
    val scope = rememberCoroutineScope()
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
    var actionEntry by remember { mutableStateOf<ExpenseEntry?>(null) }
    var editEntry by remember { mutableStateOf<ExpenseEntry?>(null) }
    var deleteEntry by remember { mutableStateOf<ExpenseEntry?>(null) }
    var stopBill by remember { mutableStateOf<MonthlyBill?>(null) }
    var editBill by remember { mutableStateOf<MonthlyBill?>(null) }
    var updateBillAmount by remember { mutableStateOf<MonthlyBill?>(null) }

    BackHandler {
        if (manageBillsOpen) {
            manageBillsOpen = false
        } else {
            onBack()
        }
    }

    LaunchedEffect(monthKey) {
        container.expenseRepository.ensureMonth(monthKey)
    }

    val listState = rememberLazyListState()
    val scrolledUnder by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 4
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                    scrolledUnder = scrolledUnder,
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
            if (!manageBillsOpen) {
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
                    addBillOpen = addBillOpen,
                    onToggleAddBill = { addBillOpen = !addBillOpen },
                    onEditBill = { editBill = it },
                    onUpdateAmount = { updateBillAmount = it },
                    onStopBill = { stopBill = it },
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
                        onManageBills = {
                            addBillOpen = false
                            manageBillsOpen = true
                        },
                        onEntryLongPress = { actionEntry = it },
                        onStopBill = { stopBill = it },
                    )
                }
            }
        }
    }

    if (addDailyOpen) {
        ExpenseFormSheet(
            title = "Add Daily Expense",
            initialName = "",
            initialAmount = "",
            initialCategory = DEFAULT_CATEGORY,
            initialNote = "",
            initialDate = LocalDate.now().toString(),
            onDismiss = { addDailyOpen = false },
            onSave = { title, category, amount, note, expenseDate ->
                scope.launch {
                    container.expenseRepository.addDailyExpense(expenseDate, title, category, amount, note)
                    selectedMonth = YearMonth.from(LocalDate.parse(expenseDate))
                    addDailyOpen = false
                }
            },
        )
    }

    editEntry?.let { entry ->
        ExpenseFormSheet(
            title = "Update Expense",
            initialName = entry.title,
            initialAmount = minorToInput(entry.amountMinor),
            initialCategory = entry.category,
            initialNote = entry.note,
            initialDate = entry.expenseDate,
            onDismiss = { editEntry = null },
            onSave = { title, category, amount, note, expenseDate ->
                scope.launch {
                    container.expenseRepository.updateEntry(entry.entryId, title, category, amount, note, expenseDate)
                    selectedMonth = YearMonth.from(LocalDate.parse(expenseDate))
                    editEntry = null
                }
            },
        )
    }

    if (limitOpen) {
        LimitSheet(
            currentLimit = summary?.limitMinor ?: 0L,
            onDismiss = { limitOpen = false },
            onSave = { amount ->
                scope.launch {
                    container.expenseRepository.setMonthlyLimit(monthKey, amount)
                    limitOpen = false
                }
            },
        )
    }

    if (addBillOpen && manageBillsOpen) {
        BillScheduleSheet(
            title = "Add Static Monthly Bill",
            initialName = "",
            initialCategory = DEFAULT_CATEGORY,
            initialStartMonth = selectedMonth,
            initialEndMonth = selectedMonth.plusMonths(6),
            initialNoEndMonth = false,
            initialDueDay = LocalDate.now().dayOfMonth.toString(),
            showAmount = true,
            onDismiss = { addBillOpen = false },
            onSave = { title, category, amount, startMonth, endMonth, dueDay ->
                scope.launch {
                    container.expenseRepository.addMonthlyBill(title, category, amount, startMonth, endMonth, dueDay)
                    addBillOpen = false
                }
            },
        )
    }

    editBill?.let { bill ->
        BillScheduleSheet(
            title = "Update Bill",
            initialName = bill.title,
            initialCategory = bill.category,
            initialStartMonth = YearMonth.parse(bill.startMonthKey),
            initialEndMonth = bill.endMonthKey?.let(YearMonth::parse) ?: YearMonth.parse(bill.startMonthKey).plusMonths(6),
            initialNoEndMonth = bill.endMonthKey == null,
            initialDueDay = bill.dueDay.toString(),
            showAmount = false,
            onDismiss = { editBill = null },
            onSave = { title, category, _, startMonth, endMonth, dueDay ->
                scope.launch {
                    container.expenseRepository.updateMonthlyBill(bill.billId, title, category, startMonth, endMonth, dueDay)
                    container.expenseRepository.ensureMonth(monthKey)
                    editBill = null
                }
            },
        )
    }

    updateBillAmount?.let { bill ->
        BillAmountSheet(
            bill = bill,
            defaultMonth = selectedMonth.plusMonths(1),
            onDismiss = { updateBillAmount = null },
            onSave = { effectiveMonth, amount ->
                scope.launch {
                    container.expenseRepository.updateMonthlyBillAmount(bill.billId, effectiveMonth, amount)
                    container.expenseRepository.ensureMonth(monthKey)
                    updateBillAmount = null
                }
            },
        )
    }

    actionEntry?.let { entry ->
        EntryActionSheet(
            entry = entry,
            onDismiss = { actionEntry = null },
            onUpdate = {
                actionEntry = null
                editEntry = entry
            },
            onDelete = {
                actionEntry = null
                deleteEntry = entry
            },
        )
    }

    deleteEntry?.let { entry ->
        AppAlertDialog(
            onDismissRequest = { deleteEntry = null },
            title = "Delete Expense",
            text = "Remove ${entry.title} from ${monthLabel(selectedMonth)}?",
            confirmText = "Delete",
            destructiveConfirm = true,
            onConfirm = {
                scope.launch {
                    container.expenseRepository.deleteEntry(entry.entryId)
                    deleteEntry = null
                }
            },
        )
    }

    stopBill?.let { bill ->
        AppAlertDialog(
            onDismissRequest = { stopBill = null },
            title = "Stop Monthly Bill",
            text = "${bill.title} will not be added to future months.",
            confirmText = "Stop",
            destructiveConfirm = true,
            onConfirm = {
                scope.launch {
                    container.expenseRepository.stopMonthlyBill(bill.billId)
                    container.expenseRepository.ensureMonth(monthKey)
                    stopBill = null
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
    onEntryLongPress: (ExpenseEntry) -> Unit,
    onStopBill: (MonthlyBill) -> Unit,
) {
    val todayKey = LocalDate.now().toString()
    val todaysBills = summary.entries.filter {
        it.kind == ExpenseEntryKind.MonthlyBill && it.expenseDate == todayKey
    }
    val activeBillCount = summary.monthlyBills.size
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
            top = Spacing.sm, bottom = Spacing.xxl + 72.dp,
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
                            "$activeBillCount active",
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
        item {
            SpendChartCard(
                mode = chartMode,
                onModeChange = onChartModeChange,
                selectedMonth = selectedMonth,
                monthEntries = summary.entries,
                allEntries = allEntries,
            )
        }

        if (todaysBills.isNotEmpty()) {
            item { SectionHeader("Bills due today") }
            items(todaysBills, key = { "today-bill-${it.entryId}" }) { entry ->
                ExpenseEntryRow(entry = entry, onLongPress = { onEntryLongPress(entry) })
            }
        }

        item { SectionHeader("This month") }
        if (dayGroups.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Rounded.ReceiptLong,
                    title = "No expenses this month",
                    description = "Tap + to add your first expense.",
                )
            }
        } else {
            dayGroups.forEach { (date, entries) ->
                item(key = "day-header-$date") {
                    DayHeader(date = date, subtotal = entries.sumOf { it.amountMinor })
                }
                items(entries, key = { "month-entry-${it.entryId}" }) { entry ->
                    ExpenseEntryRow(entry = entry, onLongPress = { onEntryLongPress(entry) })
                }
            }
        }

        item { SectionHeader("Static monthly bills · $activeBillCount active") }
        if (summary.monthlyBills.isEmpty()) {
            item {
                Text(
                    "No active static monthly bills",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.extendedColors.textMuted,
                    modifier = Modifier.padding(horizontal = Spacing.xs, vertical = Spacing.sm),
                )
            }
        } else {
            items(summary.monthlyBills, key = { "active-bill-${it.billId}" }) { bill ->
                MonthlyBillRow(bill = bill, onLongPress = { onStopBill(bill) })
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
        Text(
            "Spent this month",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.extendedColors.textMuted,
        )
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
        AppTextButton(
            text = if (summary.limitMinor > 0L) "Edit limit" else "Set limit",
            onClick = onSetLimit,
        )
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
                label = "Daily",
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExpenseEntryRow(
    entry: ExpenseEntry,
    onLongPress: () -> Unit,
) {
    val isBill = entry.kind == ExpenseEntryKind.MonthlyBill
    val (accent, icon) = categoryStyle(entry.category)
    val displayIcon = if (isBill) Icons.Rounded.CalendarMonth else icon
    val supporting = if (isBill) {
        "${entry.category} monthly · ${entry.expenseDate}"
    } else {
        "${entry.category} · ${entry.expenseDate}"
    }
    val note = entry.note.takeIf { entry.kind == ExpenseEntryKind.Daily && it.isNotBlank() }

    AppCard(
        modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress),
        contentPadding = PaddingValues(Spacing.md),
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
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MonthlyBillRow(
    bill: MonthlyBill,
    onLongPress: () -> Unit,
) {
    AppCard(
        modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onLongPress),
        contentPadding = PaddingValues(Spacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentIconTile(
                icon = Icons.Rounded.Subscriptions,
                accent = MaterialTheme.extendedColors.accents.indigo,
            )
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    bill.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${bill.category} · due ${bill.dueDay} · ${billRangeLabel(bill)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Text(
                Money.format(bill.amountMinor),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
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
    addBillOpen: Boolean,
    onToggleAddBill: () -> Unit,
    onEditBill: (MonthlyBill) -> Unit,
    onUpdateAmount: (MonthlyBill) -> Unit,
    onStopBill: (MonthlyBill) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg, end = Spacing.lg,
                top = Spacing.sm, bottom = Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            item {
                PrimaryButton(
                    text = if (addBillOpen) "Close" else "New bill",
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    onClick = onToggleAddBill,
                )
            }
            if (bills.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Rounded.Subscriptions,
                        title = "No bills yet",
                        description = "Add a recurring bill to track it every month.",
                    )
                }
            } else {
                items(bills, key = { it.billId }) { bill ->
                    ManageBillCard(
                        bill = bill,
                        onEditBill = { onEditBill(bill) },
                        onUpdateAmount = { onUpdateAmount(bill) },
                        onStopBill = { onStopBill(bill) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ManageBillCard(
    bill: MonthlyBill,
    onEditBill: () -> Unit,
    onUpdateAmount: () -> Unit,
    onStopBill: () -> Unit,
) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentIconTile(
                icon = Icons.Rounded.Subscriptions,
                accent = if (bill.active) MaterialTheme.extendedColors.accents.indigo else MaterialTheme.extendedColors.textMuted,
            )
            Spacer(Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    bill.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${bill.category} · due ${bill.dueDay} · ${billRangeLabel(bill)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(Spacing.md))
            Text(
                Money.format(bill.amountMinor),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(Spacing.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
            SecondaryButton(text = "Edit", modifier = Modifier.weight(1f), onClick = onEditBill)
            SecondaryButton(text = "Price", modifier = Modifier.weight(1f), onClick = onUpdateAmount)
            SecondaryButton(
                text = if (bill.active) "Stop" else "Stopped",
                enabled = bill.active,
                modifier = Modifier.weight(1f),
                onClick = onStopBill,
            )
        }
    }
}

@Composable
private fun EntryActionSheet(
    entry: ExpenseEntry,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(
                entry.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                Money.format(entry.amountMinor),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            if (entry.note.isNotBlank()) {
                Text(
                    entry.note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.extendedColors.textMuted,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(text = "Edit", modifier = Modifier.weight(1f), onClick = onUpdate)
                com.daykit.core.designsystem.components.DestructiveButton(
                    text = "Delete",
                    modifier = Modifier.weight(1f),
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun ExpenseFormSheet(
    title: String,
    initialName: String,
    initialAmount: String,
    initialCategory: String,
    initialNote: String,
    initialDate: String,
    onDismiss: () -> Unit,
    onSave: (String, String, Long, String, String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var amount by remember { mutableStateOf(initialAmount) }
    var category by remember { mutableStateOf(initialCategory) }
    var note by remember { mutableStateOf(initialNote) }
    var expenseDate by remember { mutableStateOf(initialDate) }
    val amountMinor = amount.toMinorOrNull()
    val dateValid = expenseDate.toLocalDateOrNull() != null
    val canSave = name.isNotBlank() && amountMinor != null && amountMinor > 0L && dateValid

    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            AppTextField(
                value = amount,
                onValueChange = { amount = it.cleanAmountInput() },
                label = "Amount",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            AppTextField(
                value = name,
                onValueChange = { name = it },
                label = "Name",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            CategoryChips(category = category, onCategoryChange = { category = it })
            DatePickerField(
                label = "Date",
                date = expenseDate.toLocalDateOrNull() ?: LocalDate.now(),
                onDateChange = { expenseDate = it.toString() },
            )
            AppTextField(
                value = note,
                onValueChange = { note = it },
                label = "Note",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            if (expenseDate.isNotBlank() && !dateValid) {
                Text("Use YYYY-MM-DD date", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(canSave = canSave, onCancel = onDismiss, onSave = {
                onSave(name, category, amountMinor ?: 0L, note, expenseDate)
            })
        }
    }
}

@Composable
private fun LimitSheet(
    currentLimit: Long,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
) {
    var amount by remember { mutableStateOf(if (currentLimit > 0L) minorToInput(currentLimit) else "") }
    val amountMinor = amount.toMinorOrNull() ?: 0L

    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text("Monthly limit", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            AppTextField(
                value = amount,
                onValueChange = { amount = it.cleanAmountInput() },
                label = "Limit amount",
                supportingText = "Use 0 to remove the limit.",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(text = "Cancel", modifier = Modifier.weight(1f), onClick = onDismiss)
                PrimaryButton(text = "Save", modifier = Modifier.weight(1f), onClick = { onSave(amountMinor) })
            }
        }
    }
}

@Composable
private fun BillScheduleSheet(
    title: String,
    initialName: String,
    initialCategory: String,
    initialStartMonth: YearMonth,
    initialEndMonth: YearMonth,
    initialNoEndMonth: Boolean,
    initialDueDay: String,
    showAmount: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, Long, String, String?, Int) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(initialCategory) }
    var startMonth by remember { mutableStateOf(initialStartMonth) }
    var endMonth by remember { mutableStateOf(initialEndMonth) }
    var noEndMonth by remember { mutableStateOf(initialNoEndMonth) }
    var dueDay by remember { mutableStateOf(initialDueDay) }
    val amountMinor = amount.toMinorOrNull()
    val dueDayInt = dueDay.toIntOrNull()
    val rangeValid = noEndMonth || !endMonth.isBefore(startMonth)
    val canSave = name.isNotBlank() &&
        (!showAmount || (amountMinor != null && amountMinor > 0L)) &&
        dueDayInt in 1..31 &&
        rangeValid

    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            AppTextField(
                value = name,
                onValueChange = { name = it },
                label = "Bill name",
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )
            if (showAmount) {
                AppTextField(
                    value = amount,
                    onValueChange = { amount = it.cleanAmountInput() },
                    label = "Monthly amount",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            CategoryChips(category = category, onCategoryChange = { category = it })
            AppTextField(
                value = dueDay,
                onValueChange = { dueDay = it.filter(Char::isDigit).take(2) },
                label = "Due day",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            MonthPickerField(label = "Start month", month = startMonth, onMonthChange = { startMonth = it })
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = noEndMonth, onCheckedChange = { noEndMonth = it })
                Text("No end month", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
            }
            if (!noEndMonth) {
                MonthPickerField(label = "End month", month = endMonth, onMonthChange = { endMonth = it })
            }
            if (!rangeValid) {
                Text("End month must be after start month", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            FormActions(canSave = canSave, onCancel = onDismiss, onSave = {
                onSave(name, category, amountMinor ?: 0L, startMonth.toString(), if (noEndMonth) null else endMonth.toString(), dueDayInt ?: 1)
            })
        }
    }
}

@Composable
private fun BillAmountSheet(
    bill: MonthlyBill,
    defaultMonth: YearMonth,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit,
) {
    var amount by remember(bill.billId) { mutableStateOf(minorToInput(bill.amountMinor)) }
    var effectiveMonth by remember(bill.billId) { mutableStateOf(defaultMonth) }
    val amountMinor = amount.toMinorOrNull()
    val canSave = amountMinor != null && amountMinor > 0L

    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Text("Update price", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
            Text(bill.title, color = MaterialTheme.extendedColors.textMuted, style = MaterialTheme.typography.bodyMedium)
            AppTextField(
                value = amount,
                onValueChange = { amount = it.cleanAmountInput() },
                label = "New amount",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            MonthPickerField(label = "Effective from", month = effectiveMonth, onMonthChange = { effectiveMonth = it })
            FormActions(canSave = canSave, onCancel = onDismiss, onSave = {
                onSave(effectiveMonth.toString(), amountMinor ?: 0L)
            })
        }
    }
}

@Composable
private fun CategoryChips(
    category: String,
    onCategoryChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text("Category", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
            EXPENSE_CATEGORIES.take(3).forEach { option ->
                FilterChipButton(
                    text = option,
                    selected = option == category,
                    onClick = { onCategoryChange(option) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
            EXPENSE_CATEGORIES.drop(3).forEach { option ->
                FilterChipButton(
                    text = option,
                    selected = option == category,
                    onClick = { onCategoryChange(option) },
                )
            }
        }
    }
}

@Composable
private fun DatePickerField(
    label: String,
    date: LocalDate,
    onDateChange: (LocalDate) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val state = rememberDatePickerState(initialSelectedDateMillis = date.toMillis())
    PickerField(
        label = label,
        value = date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
        icon = Icons.Rounded.Event,
        onClick = { open = true },
    )
    if (open) {
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onDateChange(it.toLocalDate()) }
                    open = false
                }) {
                    Text("Select", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) {
                    Text("Cancel", color = MaterialTheme.extendedColors.textMuted)
                }
            },
            colors = DatePickerDefaults.colors(containerColor = MaterialTheme.extendedColors.card),
        ) {
            DatePicker(state = state)
        }
    }
}

@Composable
private fun MonthPickerField(
    label: String,
    month: YearMonth,
    onMonthChange: (YearMonth) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var draftMonth by remember(month) { mutableStateOf(month) }
    PickerField(
        label = label,
        value = monthLabel(month),
        icon = Icons.Rounded.CalendarMonth,
        onClick = {
            draftMonth = month
            open = true
        },
    )
    if (open) {
        AppBottomSheet(onDismissRequest = { open = false }) {
            Column(modifier = Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
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
                    SecondaryButton(text = "Cancel", modifier = Modifier.weight(1f), onClick = { open = false })
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
            Text(value, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun FormActions(
    canSave: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), modifier = Modifier.fillMaxWidth()) {
        SecondaryButton(text = "Cancel", modifier = Modifier.weight(1f), onClick = onCancel)
        PrimaryButton(text = "Save", enabled = canSave, modifier = Modifier.weight(1f), onClick = onSave)
    }
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
            val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
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
