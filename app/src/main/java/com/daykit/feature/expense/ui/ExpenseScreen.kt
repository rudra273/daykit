package com.rudra.daykit.feature.expense.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Subscriptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rudra.daykit.AppContainer
import com.rudra.daykit.core.ui.AppBackButton
import com.rudra.daykit.core.ui.Cyan
import com.rudra.daykit.core.ui.DangerRed
import com.rudra.daykit.core.ui.DangerRedMuted
import com.rudra.daykit.core.ui.GlassBackground
import com.rudra.daykit.core.ui.GlassFilterButton
import com.rudra.daykit.core.ui.GlassLoadingIndicator
import com.rudra.daykit.core.ui.MutedText
import com.rudra.daykit.core.ui.PanelAlt
import com.rudra.daykit.core.ui.PrimaryButton
import com.rudra.daykit.core.ui.SecondaryButton
import com.rudra.daykit.core.ui.SoftText
import com.rudra.daykit.core.ui.Stroke
import com.rudra.daykit.core.ui.glassSurface
import com.rudra.daykit.feature.expense.data.ExpenseEntry
import com.rudra.daykit.feature.expense.data.ExpenseEntryKind
import com.rudra.daykit.feature.expense.data.ExpenseMonthSummary
import com.rudra.daykit.feature.expense.data.MonthlyBill
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Currency
import java.util.Locale
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

@OptIn(ExperimentalMaterial3Api::class)
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

    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (manageBillsOpen) {
                ManageBillsPage(
                    bills = allBills,
                    selectedMonth = selectedMonth,
                    addBillOpen = addBillOpen,
                    onBack = { manageBillsOpen = false },
                    onToggleAddBill = { addBillOpen = !addBillOpen },
                    onCancelAddBill = { addBillOpen = false },
                    onCreateBill = { title, category, amount, startMonth, endMonth, dueDay ->
                        scope.launch {
                            container.expenseRepository.addMonthlyBill(title, category, amount, startMonth, endMonth, dueDay)
                            addBillOpen = false
                        }
                    },
                    onEditBill = { editBill = it },
                    onUpdateAmount = { updateBillAmount = it },
                    onStopBill = { stopBill = it },
                )
            } else {
                ExpenseTopBar(
                    selectedMonth = selectedMonth,
                    onBack = onBack,
                    onPrevious = { selectedMonth = selectedMonth.minusMonths(1) },
                    onNext = { selectedMonth = selectedMonth.plusMonths(1) },
                )

                when (val currentSummary = summary) {
            null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlassLoadingIndicator()
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val todayKey = LocalDate.now().toString()
                val todaysBills = currentSummary.entries.filter {
                    it.kind == ExpenseEntryKind.MonthlyBill && it.expenseDate == todayKey
                }
                item {
                    MonthTotalCard(
                        summary = currentSummary,
                        onSetLimit = { limitOpen = true },
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        SecondaryButton(
                            text = "Monthly Bills",
                            modifier = Modifier.weight(1f),
                            leadingIcon = {
                                Icon(Icons.Rounded.Subscriptions, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                            },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            onClick = {
                                addBillOpen = false
                                manageBillsOpen = true
                            },
                        )
                        PrimaryButton(
                            text = "Daily",
                            modifier = Modifier.weight(1f),
                            leadingIcon = {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                            },
                            onClick = { addDailyOpen = !addDailyOpen },
                        )
                    }
                }
                if (addDailyOpen) {
                    item {
                        ExpenseFormCard(
                            title = "Add Daily Expense",
                            defaultCategory = DEFAULT_CATEGORY,
                            onCancel = { addDailyOpen = false },
                            onSave = { title, category, amount, note, expenseDate ->
                                scope.launch {
                                    container.expenseRepository.addDailyExpense(expenseDate, title, category, amount, note)
                                    selectedMonth = YearMonth.from(LocalDate.parse(expenseDate))
                                    addDailyOpen = false
                                }
                            },
                        )
                    }
                }
                item {
                    SpendChartCard(
                        mode = chartMode,
                        onModeChange = { chartMode = it },
                        selectedMonth = selectedMonth,
                        monthEntries = currentSummary.entries,
                        allEntries = allEntries,
                    )
                }
                item {
                    SectionHeader(
                        title = "Today's Bills",
                        value = money(todaysBills.sumOf { it.amountMinor }),
                    )
                }
                if (todaysBills.isEmpty()) {
                    item { EmptyExpenseState("No bills due today") }
                } else {
                    items(todaysBills, key = { "today-bill-${it.entryId}" }) { entry ->
                        ExpenseEntryRow(
                            entry = entry,
                            onLongPress = { actionEntry = entry },
                        )
                    }
                }
                item {
                    SectionHeader(
                        title = "This Month",
                        value = "${currentSummary.entries.size} items",
                    )
                }
                if (currentSummary.entries.isEmpty()) {
                    item { EmptyExpenseState("No expenses for this month") }
                } else {
                    items(currentSummary.entries, key = { "month-entry-${it.entryId}" }) { entry ->
                        ExpenseEntryRow(
                            entry = entry,
                            onLongPress = { actionEntry = entry },
                        )
                    }
                }
                item {
                    SectionHeader(
                        title = "Static Monthly Bills",
                        value = "${currentSummary.monthlyBills.size} active",
                    )
                }
                if (currentSummary.monthlyBills.isEmpty()) {
                    item { EmptyExpenseState("No active static monthly bills") }
                } else {
                    items(currentSummary.monthlyBills, key = { "active-bill-${it.billId}" }) { bill ->
                        MonthlyBillRow(
                            bill = bill,
                            onStop = { stopBill = bill },
                        )
                    }
                }
            }
        }
            }
        }
    }

    actionEntry?.let { entry ->
        ExpenseActionDialog(
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

    editEntry?.let { entry ->
        UpdateExpenseDialog(
            entry = entry,
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
        LimitDialog(
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

    deleteEntry?.let { entry ->
        ConfirmDialog(
            title = "Delete Expense",
            message = "Remove ${entry.title} from ${monthLabel(selectedMonth)}?",
            confirmText = "Delete",
            onDismiss = { deleteEntry = null },
            onConfirm = {
                scope.launch {
                    container.expenseRepository.deleteEntry(entry.entryId)
                    deleteEntry = null
                }
            },
        )
    }

    stopBill?.let { bill ->
        ConfirmDialog(
            title = "Stop Monthly Bill",
            message = "${bill.title} will not be added to future months.",
            confirmText = "Stop",
            onDismiss = { stopBill = null },
            onConfirm = {
                scope.launch {
                    container.expenseRepository.stopMonthlyBill(bill.billId)
                    container.expenseRepository.ensureMonth(monthKey)
                    stopBill = null
                }
            },
        )
    }

    editBill?.let { bill ->
        BillScheduleDialog(
            bill = bill,
            onDismiss = { editBill = null },
            onSave = { title, category, startMonth, endMonth, dueDay ->
                scope.launch {
                    container.expenseRepository.updateMonthlyBill(bill.billId, title, category, startMonth, endMonth, dueDay)
                    container.expenseRepository.ensureMonth(monthKey)
                    editBill = null
                }
            },
        )
    }

    updateBillAmount?.let { bill ->
        BillAmountDialog(
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
}

@Composable
private fun ExpenseTopBar(
    selectedMonth: YearMonth,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppBackButton(onClick = onBack)
        Column(modifier = Modifier.weight(1f)) {
            Text("Expenses", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(monthLabel(selectedMonth), color = Cyan, style = MaterialTheme.typography.bodySmall)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MonthIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous month", onPrevious)
            MonthIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next month", onNext)
        }
    }
}

@Composable
private fun MonthIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.06f)),
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = SoftText,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun MonthTotalCard(
    summary: ExpenseMonthSummary,
    onSetLimit: () -> Unit,
) {
    val isOverLimit = summary.limitMinor > 0L && summary.totalMinor > summary.limitMinor
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(18.dp), selected = false),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Payments, contentDescription = null, tint = Cyan, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Monthly Total", color = MutedText, style = MaterialTheme.typography.bodySmall)
                    Text(money(summary.totalMinor), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                }
                TextButton(onClick = onSetLimit) {
                    Text(if (summary.limitMinor > 0L) "Edit limit" else "Set limit", color = Cyan)
                }
            }
            if (summary.limitMinor > 0L) {
                LinearProgressIndicator(
                    progress = { summary.limitProgress.coerceAtMost(1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    color = if (isOverLimit) DangerRed else Cyan,
                    trackColor = Color.White.copy(alpha = 0.08f),
                )
                Text(
                    text = if (isOverLimit) {
                        "${money(-summary.remainingMinor)} over ${money(summary.limitMinor)} limit"
                    } else {
                        "${money(summary.remainingMinor)} left from ${money(summary.limitMinor)}"
                    },
                    color = if (isOverLimit) DangerRed else MutedText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatPill("Bills", money(summary.billTotalMinor), Icons.Rounded.Subscriptions, Modifier.weight(1f))
                StatPill("Daily", money(summary.dailyTotalMinor), Icons.Rounded.ReceiptLong, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .glassSurface(RoundedCornerShape(14.dp), selected = false, tintStrength = 0.06f)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Cyan, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, color = MutedText, style = MaterialTheme.typography.labelSmall)
            Text(value, color = SoftText, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ExpenseFormCard(
    title: String,
    defaultCategory: String,
    onCancel: () -> Unit,
    onSave: (String, String, Long, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(defaultCategory) }
    var note by remember { mutableStateOf("") }
    var expenseDate by remember { mutableStateOf(LocalDate.now().toString()) }
    val amountMinor = amount.toMinorOrNull()
    val dateValid = expenseDate.toLocalDateOrNull() != null
    val canSave = name.isNotBlank() && amountMinor != null && amountMinor > 0L && dateValid

    FormCard(title = title, icon = Icons.Rounded.ReceiptLong) {
        ExpenseTextField(value = name, onValueChange = { name = it }, label = "Name")
        DatePickerField(
            label = "Date",
            date = LocalDate.parse(expenseDate),
            onDateChange = { expenseDate = it.toString() },
        )
        ExpenseTextField(
            value = amount,
            onValueChange = { amount = it.cleanAmountInput() },
            label = "Amount",
            keyboardType = KeyboardType.Decimal,
        )
        CategoryPicker(category = category, onCategoryChange = { category = it })
        ExpenseTextField(value = note, onValueChange = { note = it }, label = "Note")
        if (expenseDate.isNotBlank() && !dateValid) {
            Text("Use YYYY-MM-DD date", color = DangerRed, style = MaterialTheme.typography.bodySmall)
        }
        FormActions(canSave = canSave, onCancel = onCancel, onSave = { onSave(name, category, amountMinor ?: 0L, note, expenseDate) })
    }
}

@Composable
private fun BillFormCard(
    selectedMonth: YearMonth,
    onCancel: () -> Unit,
    onSave: (String, String, Long, String, String?, Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("General") }
    var startMonth by remember(selectedMonth) { mutableStateOf(selectedMonth) }
    var endMonth by remember(selectedMonth) { mutableStateOf(selectedMonth.plusMonths(6)) }
    var noEndMonth by remember { mutableStateOf(false) }
    var dueDay by remember { mutableStateOf(LocalDate.now().dayOfMonth.toString()) }
    val amountMinor = amount.toMinorOrNull()
    val dueDayInt = dueDay.toIntOrNull()
    val rangeValid = noEndMonth || !endMonth.isBefore(startMonth)
    val canSave = name.isNotBlank() && amountMinor != null && amountMinor > 0L && dueDayInt in 1..31 && rangeValid

    FormCard(title = "Add Static Monthly Bill", icon = Icons.Rounded.Subscriptions) {
        ExpenseTextField(value = name, onValueChange = { name = it }, label = "Bill name")
        ExpenseTextField(
            value = amount,
            onValueChange = { amount = it.cleanAmountInput() },
            label = "Monthly amount",
            keyboardType = KeyboardType.Decimal,
        )
        CategoryPicker(category = category, onCategoryChange = { category = it })
        ExpenseTextField(
            value = dueDay,
            onValueChange = { dueDay = it.filter(Char::isDigit).take(2) },
            label = "Due day",
            keyboardType = KeyboardType.Number,
        )
        MonthPickerField(label = "Start month", month = startMonth, onMonthChange = { startMonth = it })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = noEndMonth, onCheckedChange = { noEndMonth = it })
            Text("No end month", color = SoftText, style = MaterialTheme.typography.bodyMedium)
        }
        if (!noEndMonth) {
            MonthPickerField(label = "End month", month = endMonth, onMonthChange = { endMonth = it })
        }
        if (!rangeValid) {
            Text("End month must be after start month", color = DangerRed, style = MaterialTheme.typography.bodySmall)
        }
        FormActions(
            canSave = canSave,
            onCancel = onCancel,
            onSave = { onSave(name, category, amountMinor ?: 0L, startMonth.toString(), if (noEndMonth) null else endMonth.toString(), dueDayInt ?: 1) },
        )
    }
}

@Composable
private fun FormCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(18.dp), selected = false),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = Cyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.SemiBold, color = Cyan)
            }
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
                TextButton(
                    onClick = {
                        state.selectedDateMillis?.let { onDateChange(it.toLocalDate()) }
                        open = false
                    },
                ) {
                    Text("Select", color = Cyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) {
                    Text("Cancel", color = MutedText)
                }
            },
            colors = androidx.compose.material3.DatePickerDefaults.colors(containerColor = PanelAlt),
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
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label, fontWeight = FontWeight.Bold) },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    MonthIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, "Previous month") {
                        draftMonth = draftMonth.minusMonths(1)
                    }
                    Text(monthLabel(draftMonth), color = SoftText, fontWeight = FontWeight.SemiBold)
                    MonthIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Next month") {
                        draftMonth = draftMonth.plusMonths(1)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onMonthChange(draftMonth)
                        open = false
                    },
                ) {
                    Text("Select", color = Cyan, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) {
                    Text("Cancel", color = MutedText)
                }
            },
            containerColor = PanelAlt,
            titleContentColor = SoftText,
            textContentColor = SoftText,
            shape = RoundedCornerShape(12.dp),
        )
    }
}

@Composable
private fun PickerField(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, color = MutedText, style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Cyan, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(value, color = SoftText, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CategoryPicker(
    category: String,
    onCategoryChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        PickerField(
            label = "Category",
            value = category.ifBlank { DEFAULT_CATEGORY },
            icon = Icons.Rounded.ReceiptLong,
            onClick = { expanded = true },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(PanelAlt),
        ) {
            EXPENSE_CATEGORIES.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            option,
                            color = SoftText,
                            fontWeight = if (option == category) FontWeight.Bold else FontWeight.Normal,
                        )
                    },
                    onClick = {
                        onCategoryChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ExpenseTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = KeyboardCapitalization.Words,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Cyan,
            unfocusedBorderColor = Stroke,
            focusedLabelColor = Cyan,
            unfocusedLabelColor = MutedText,
            cursorColor = Cyan,
            focusedTextColor = SoftText,
            unfocusedTextColor = SoftText,
            focusedContainerColor = Color.White.copy(alpha = 0.08f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
        ),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FormActions(
    canSave: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        SecondaryButton(text = "Cancel", modifier = Modifier.weight(1f), onClick = onCancel)
        PrimaryButton(text = "Save", enabled = canSave, modifier = Modifier.weight(1f), onClick = onSave)
    }
}

@Composable
private fun SectionHeader(
    title: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = MutedText, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        Text(value, color = Cyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DateFilterChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    GlassFilterButton(text = text, selected = selected, onClick = onClick)
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
    FormCard(title = "Spending Graph", icon = Icons.Rounded.Payments) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            ExpenseChartMode.values().forEach { item ->
                DateFilterChip(
                    text = item.name.lowercase().replaceFirstChar(Char::titlecase),
                    selected = mode == item,
                    onClick = { onModeChange(item) },
                )
            }
        }
        Text(
            text = selectedBar?.let { "${it.label}: ${money(it.amountMinor)}" } ?: "Tap a bar to see exact spend",
            color = MutedText,
            style = MaterialTheme.typography.bodySmall,
        )
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
                Text(axisMoney(maxAmount), color = MutedText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Text(axisMoney(maxAmount / 2), color = MutedText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                Text(axisMoney(0), color = MutedText, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
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
                                                bar.amountMinor <= 0L -> Color.White.copy(alpha = 0.08f)
                                                isSelected -> Color(0xFF7AF6EA)
                                                else -> Cyan
                                            },
                                        )
                                        .clickable { selectedBar = bar },
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                bar.label,
                                color = SoftText,
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
private fun ManageBillsPage(
    bills: List<MonthlyBill>,
    selectedMonth: YearMonth,
    addBillOpen: Boolean,
    onBack: () -> Unit,
    onToggleAddBill: () -> Unit,
    onCancelAddBill: () -> Unit,
    onCreateBill: (String, String, Long, String, String?, Int) -> Unit,
    onEditBill: (MonthlyBill) -> Unit,
    onUpdateAmount: (MonthlyBill) -> Unit,
    onStopBill: (MonthlyBill) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AppBackButton(onClick = onBack)
        Column(modifier = Modifier.weight(1f)) {
            Text("Monthly Bills", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("${bills.count { it.active }} active schedules", color = Cyan, style = MaterialTheme.typography.bodySmall)
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PrimaryButton(
                text = if (addBillOpen) "Close" else "Monthly Bill",
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                },
                onClick = onToggleAddBill,
            )
        }
        if (addBillOpen) {
            item {
                BillFormCard(
                    selectedMonth = selectedMonth,
                    onCancel = onCancelAddBill,
                    onSave = onCreateBill,
                )
            }
        }
        if (bills.isEmpty()) {
            item { EmptyExpenseState("No bills yet") }
        } else {
            items(bills, key = { it.billId }) { bill ->
                ManageBillRow(
                    bill = bill,
                    onEditBill = { onEditBill(bill) },
                    onUpdateAmount = { onUpdateAmount(bill) },
                    onStopBill = { onStopBill(bill) },
                )
            }
        }
    }
}

@Composable
private fun ManageBillRow(
    bill: MonthlyBill,
    onEditBill: () -> Unit,
    onUpdateAmount: () -> Unit,
    onStopBill: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(18.dp), selected = false),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Subscriptions, contentDescription = null, tint = if (bill.active) Cyan else MutedText)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(bill.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${bill.category} · due ${bill.dueDay} · ${billRangeLabel(bill)}",
                        color = MutedText,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(money(bill.amountMinor), color = SoftText, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SecondaryButton(text = "Edit", modifier = Modifier.weight(1f), textStyle = MaterialTheme.typography.bodySmall, onClick = onEditBill)
                SecondaryButton(text = "Price", modifier = Modifier.weight(1f), textStyle = MaterialTheme.typography.bodySmall, onClick = onUpdateAmount)
                SecondaryButton(text = if (bill.active) "Stop" else "Stopped", enabled = bill.active, modifier = Modifier.weight(1f), textStyle = MaterialTheme.typography.bodySmall, onClick = onStopBill)
            }
        }
    }
}

@Composable
private fun MonthlyBillRow(
    bill: MonthlyBill,
    onStop: () -> Unit,
) {
    ExpenseBaseRow(
        icon = Icons.Rounded.Subscriptions,
        title = bill.title,
        category = bill.category,
        amount = money(bill.amountMinor),
        accent = Cyan,
        onLongPress = onStop,
    )
}

@Composable
private fun ExpenseEntryRow(
    entry: ExpenseEntry,
    onLongPress: () -> Unit,
) {
    val accent = if (entry.kind == ExpenseEntryKind.MonthlyBill) Cyan else SoftText
    ExpenseBaseRow(
        icon = if (entry.kind == ExpenseEntryKind.MonthlyBill) Icons.Rounded.CalendarMonth else Icons.Rounded.ReceiptLong,
        title = entry.title,
        category = if (entry.kind == ExpenseEntryKind.MonthlyBill) {
            "${entry.category} monthly · ${entry.expenseDate}"
        } else {
            "${entry.category} · ${entry.expenseDate}"
        },
        note = entry.note.takeIf { entry.kind == ExpenseEntryKind.Daily && it.isNotBlank() },
        amount = money(entry.amountMinor),
        accent = accent,
        onLongPress = onLongPress,
    )
}

@Composable
private fun ExpenseBaseRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    category: String,
    note: String? = null,
    amount: String,
    accent: Color,
    onLongPress: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(18.dp), selected = false)
            .combinedClickable(
                onClick = {},
                onLongClick = onLongPress,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = 0.055f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(category, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MutedText, style = MaterialTheme.typography.bodySmall)
                note?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, color = SoftText.copy(alpha = 0.78f), style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(amount, color = SoftText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun EmptyExpenseState(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .glassSurface(RoundedCornerShape(18.dp), selected = false),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = MutedText)
    }
}

@Composable
private fun ExpenseActionDialog(
    entry: ExpenseEntry,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
    onDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                entry.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(money(entry.amountMinor), color = Cyan, fontWeight = FontWeight.SemiBold)
                if (entry.note.isNotBlank()) {
                    Text(entry.note, color = MutedText, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdate) {
                Icon(Icons.Rounded.Edit, contentDescription = null, tint = Cyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Update", color = Cyan, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDelete) {
                    Text("Delete", color = DangerRed)
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = MutedText)
                }
            }
        },
        containerColor = PanelAlt,
        titleContentColor = SoftText,
        textContentColor = SoftText,
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun UpdateExpenseDialog(
    entry: ExpenseEntry,
    onDismiss: () -> Unit,
    onSave: (String, String, Long, String, String) -> Unit,
) {
    var name by remember(entry.entryId) { mutableStateOf(entry.title) }
    var amount by remember(entry.entryId) { mutableStateOf(minorToInput(entry.amountMinor)) }
    var category by remember(entry.entryId) { mutableStateOf(entry.category) }
    var note by remember(entry.entryId) { mutableStateOf(entry.note) }
    var expenseDate by remember(entry.entryId) { mutableStateOf(entry.expenseDate) }
    val amountMinor = amount.toMinorOrNull()
    val dateValid = expenseDate.toLocalDateOrNull() != null
    val canSave = name.isNotBlank() && amountMinor != null && amountMinor > 0L && dateValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Expense", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpenseTextField(value = name, onValueChange = { name = it }, label = "Name")
                DatePickerField(
                    label = "Date",
                    date = expenseDate.toLocalDateOrNull() ?: LocalDate.now(),
                    onDateChange = { expenseDate = it.toString() },
                )
                ExpenseTextField(
                    value = amount,
                    onValueChange = { amount = it.cleanAmountInput() },
                    label = "Amount",
                    keyboardType = KeyboardType.Decimal,
                )
                CategoryPicker(category = category, onCategoryChange = { category = it })
                ExpenseTextField(value = note, onValueChange = { note = it }, label = "Note")
                if (expenseDate.isNotBlank() && !dateValid) {
                    Text("Use YYYY-MM-DD date", color = DangerRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onSave(name, category, amountMinor ?: 0L, note, expenseDate) },
            ) {
                Text("Save", color = if (canSave) Cyan else MutedText, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MutedText)
            }
        },
        containerColor = PanelAlt,
        titleContentColor = SoftText,
        textContentColor = SoftText,
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun LimitDialog(
    currentLimit: Long,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
) {
    var amount by remember(currentLimit) { mutableStateOf(if (currentLimit > 0L) minorToInput(currentLimit) else "") }
    val amountMinor = amount.toMinorOrNull() ?: 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Monthly Limit", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpenseTextField(
                    value = amount,
                    onValueChange = { amount = it.cleanAmountInput() },
                    label = "Limit amount",
                    keyboardType = KeyboardType.Decimal,
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(DangerRedMuted)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Savings, contentDescription = null, tint = DangerRed, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Use 0 to remove the limit.", color = SoftText, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(amountMinor) }) {
                Text("Save", color = Cyan, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MutedText)
            }
        },
        containerColor = PanelAlt,
        titleContentColor = SoftText,
        textContentColor = SoftText,
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun BillScheduleDialog(
    bill: MonthlyBill,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String?, Int) -> Unit,
) {
    var name by remember(bill.billId) { mutableStateOf(bill.title) }
    var category by remember(bill.billId) { mutableStateOf(bill.category) }
    var startMonth by remember(bill.billId) { mutableStateOf(YearMonth.parse(bill.startMonthKey)) }
    var endMonth by remember(bill.billId) { mutableStateOf(bill.endMonthKey?.let(YearMonth::parse) ?: startMonth.plusMonths(6)) }
    var noEndMonth by remember(bill.billId) { mutableStateOf(bill.endMonthKey == null) }
    var dueDay by remember(bill.billId) { mutableStateOf(bill.dueDay.toString()) }
    val dueDayInt = dueDay.toIntOrNull()
    val rangeValid = noEndMonth || !endMonth.isBefore(startMonth)
    val canSave = name.isNotBlank() && dueDayInt in 1..31 && rangeValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Bill", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpenseTextField(value = name, onValueChange = { name = it }, label = "Bill name")
                CategoryPicker(category = category, onCategoryChange = { category = it })
                ExpenseTextField(
                    value = dueDay,
                    onValueChange = { dueDay = it.filter(Char::isDigit).take(2) },
                    label = "Due day",
                    keyboardType = KeyboardType.Number,
                )
                MonthPickerField(label = "Start month", month = startMonth, onMonthChange = { startMonth = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = noEndMonth, onCheckedChange = { noEndMonth = it })
                    Text("No end month", color = SoftText, style = MaterialTheme.typography.bodyMedium)
                }
                if (!noEndMonth) {
                    MonthPickerField(label = "End month", month = endMonth, onMonthChange = { endMonth = it })
                }
                if (!rangeValid) {
                    Text("End month must be after start month", color = DangerRed, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onSave(name, category, startMonth.toString(), if (noEndMonth) null else endMonth.toString(), dueDayInt ?: 1) },
            ) {
                Text("Save", color = if (canSave) Cyan else MutedText, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MutedText)
            }
        },
        containerColor = PanelAlt,
        titleContentColor = SoftText,
        textContentColor = SoftText,
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun BillAmountDialog(
    bill: MonthlyBill,
    defaultMonth: YearMonth,
    onDismiss: () -> Unit,
    onSave: (String, Long) -> Unit,
) {
    var amount by remember(bill.billId) { mutableStateOf(minorToInput(bill.amountMinor)) }
    var effectiveMonth by remember(bill.billId) { mutableStateOf(defaultMonth) }
    val amountMinor = amount.toMinorOrNull()
    val canSave = amountMinor != null && amountMinor > 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Update Price", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(bill.title, color = MutedText, style = MaterialTheme.typography.bodySmall)
                ExpenseTextField(
                    value = amount,
                    onValueChange = { amount = it.cleanAmountInput() },
                    label = "New amount",
                    keyboardType = KeyboardType.Decimal,
                )
                MonthPickerField(label = "Effective from", month = effectiveMonth, onMonthChange = { effectiveMonth = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onSave(effectiveMonth.toString(), amountMinor ?: 0L) },
            ) {
                Text("Save", color = if (canSave) Cyan else MutedText, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MutedText)
            }
        },
        containerColor = PanelAlt,
        titleContentColor = SoftText,
        textContentColor = SoftText,
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message, color = MutedText) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = DangerRed, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MutedText)
            }
        },
        containerColor = PanelAlt,
        titleContentColor = SoftText,
        textContentColor = SoftText,
        shape = RoundedCornerShape(12.dp),
    )
}

private fun money(amountMinor: Long): String {
    val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    format.currency = Currency.getInstance("INR")
    return format.format(amountMinor / 100.0)
}

private fun axisMoney(amountMinor: Long): String {
    val rupees = amountMinor / 100
    return when {
        rupees >= 100_000 -> "₹${rupees / 100_000}L"
        rupees >= 1_000 -> "₹${rupees / 1_000}k"
        else -> "₹$rupees"
    }
}

private fun monthLabel(month: YearMonth): String {
    return month.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
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
