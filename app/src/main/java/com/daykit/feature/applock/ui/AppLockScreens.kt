package com.rudra.daykit.feature.applock.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.fragment.app.FragmentActivity
import com.rudra.daykit.AppContainer
import com.rudra.daykit.core.data.SecureSettingRepository
import com.rudra.daykit.core.permissions.AppLockPermissionState
import com.rudra.daykit.core.permissions.PermissionIntents
import com.rudra.daykit.core.ui.AppBackButton
import com.rudra.daykit.core.ui.GlassLoadingIndicator
import com.rudra.daykit.core.security.BiometricAuthenticator
import com.rudra.daykit.core.ui.Cyan
import com.rudra.daykit.core.ui.GlassBackground
import com.rudra.daykit.core.ui.PanelAlt
import com.rudra.daykit.core.ui.PrimaryButton
import com.rudra.daykit.core.ui.MutedText
import com.rudra.daykit.core.ui.SecondaryButton
import com.rudra.daykit.core.ui.SoftText
import com.rudra.daykit.core.ui.Stroke
import com.rudra.daykit.core.ui.glassSurface
import com.rudra.daykit.feature.applock.domain.InstalledApp
import com.rudra.daykit.feature.applock.domain.SamsungSecureFolderSupport
import com.rudra.daykit.feature.habit.data.Habit
import com.rudra.daykit.feature.habit.data.HabitDashboard
import com.rudra.daykit.feature.habit.data.HabitGoalType
import com.rudra.daykit.feature.habit.data.HabitLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private enum class AppLockListTab {
    Unlocked,
    Locked,
}

@Composable
fun SetupCredentialScreen(
    onCredentialReady: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    val canContinue = pin.length >= 4 && pin == confirmPin

    AppSurface {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                BrandHeader(
                    icon = Icons.Rounded.Security,
                    title = "DayKit",
                    subtitle = "Create master PIN",
                    useAppLogo = true,
                )

                SecureTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(12) },
                    label = "Master PIN",
                )
                SecureTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it.filter(Char::isDigit).take(12) },
                    label = "Confirm PIN",
                )
                if (pin.isNotEmpty() && confirmPin.isNotEmpty() && pin != confirmPin) {
                    Text(
                        text = "PINs do not match",
                        color = Color(0xFFFFA8A8),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            PrimaryButton(
                text = "Continue",
                enabled = canContinue,
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                },
                onClick = { onCredentialReady(pin) },
            )
        }
    }
}

@Composable
fun BiometricSetupScreen(
    canUseBiometric: Boolean,
    message: String?,
    onEnable: () -> Unit,
    onSkip: () -> Unit,
) {
    AppSurface {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                BrandHeader(
                    icon = Icons.Rounded.Fingerprint,
                    title = "Biometric",
                    subtitle = if (canUseBiometric) "Fast unlock" else "PIN unlock",
                )
                StatusPanel(
                    icon = Icons.Rounded.Shield,
                    title = if (canUseBiometric) "Ready" else "Unavailable",
                    value = if (canUseBiometric) "Strong biometric detected" else "Use the master PIN on this device",
                )
                message?.let {
                    Text(text = it, color = Color(0xFFFFD28F), style = MaterialTheme.typography.bodyMedium)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (canUseBiometric) {
                    PrimaryButton(
                        text = "Enable",
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(Icons.Rounded.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                        },
                        onClick = onEnable,
                    )
                }
                SecondaryButton(
                    text = if (canUseBiometric) "Skip" else "Continue",
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onSkip,
                )
            }
        }
    }
}

@Composable
fun PermissionGrantScreen(
    permissions: AppLockPermissionState,
    onRefresh: () -> Unit,
) {
    val context = LocalContext.current

    fun openSettings(block: () -> android.content.Intent) {
        try {
            context.startActivity(block())
        } catch (_: ActivityNotFoundException) {
            onRefresh()
        }
    }

    AppSurface {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                BrandHeader(
                    icon = Icons.Rounded.Settings,
                    title = "Permissions",
                    subtitle = "Local App Lock access",
                )

                PermissionRow(
                    icon = Icons.Rounded.Apps,
                    title = "Usage access",
                    granted = permissions.usageAccess,
                    onClick = { openSettings { PermissionIntents.usageAccessSettings() } },
                )
                PermissionRow(
                    icon = Icons.Rounded.Security,
                    title = "Overlay",
                    granted = permissions.overlay,
                    onClick = { openSettings { PermissionIntents.overlaySettings(context) } },
                )
            }

            PrimaryButton(
                text = "Check",
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                },
                onClick = onRefresh,
            )
        }
    }
}

@Composable
fun DashboardScreen(
    container: AppContainer,
    lockedCount: Int,
    onOpenAppLock: () -> Unit,
    onOpenKeyStore: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenHabit: () -> Unit,
    onOpenReminder: () -> Unit,
    onOpenExpenses: () -> Unit,
    onOpenDnsManager: () -> Unit,
    onOpenFileLocker: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var widgetPickerOpen by remember { mutableStateOf(false) }
    var expenseDialogOpen by remember { mutableStateOf(false) }
    val expenseWidgetEnabled by container.secureSettingRepository
        .observeBoolean(SecureSettingRepository.KEY_WIDGET_EXPENSES)
        .collectAsStateWithLifecycle(initialValue = false)
    val habitWidgetEnabled by container.secureSettingRepository
        .observeBoolean(SecureSettingRepository.KEY_WIDGET_HABITS)
        .collectAsStateWithLifecycle(initialValue = false)
    val habitDashboard by container.habitRepository
        .observeDashboard()
        .collectAsStateWithLifecycle(initialValue = null)
    val normalizedSearch = searchQuery.trim()
    fun matchesTool(title: String): Boolean = normalizedSearch.isBlank() ||
        title.contains(normalizedSearch, ignoreCase = true)
    val showAppLock = matchesTool("App Lock")
    val showKeyStore = matchesTool("Key Store")
    val showNotes = matchesTool("Notes")
    val showHabit = matchesTool("Habit")
    val showReminder = matchesTool("Reminder") ||
        matchesTool("Notification") ||
        matchesTool("Alarm")
    val showExpenses = matchesTool("Expenses")
    val showEditor = matchesTool("Editor")
    val showDnsManager = matchesTool("DNS") ||
        matchesTool("Ad Block") ||
        matchesTool("Private DNS")
    val showFileLocker = matchesTool("File Vault") ||
        matchesTool("File Locker") ||
        matchesTool("Hide Files") ||
        matchesTool("Images") ||
        matchesTool("Videos")
    val showSecurity = showAppLock || showKeyStore || showNotes || showFileLocker
    val showProductivity = showHabit || showReminder || showExpenses
    val showOther = showEditor || showDnsManager
    AppSurface {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "DayKit",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(14.dp)),
                ) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = SoftText,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search tools...", style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MutedText, modifier = Modifier.size(18.dp)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Cyan,
                    unfocusedBorderColor = Stroke.copy(alpha = 0.9f),
                    focusedTextColor = SoftText,
                    unfocusedTextColor = SoftText,
                    cursorColor = Cyan,
                    focusedContainerColor = Color.White.copy(alpha = 0.08f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                ),
                shape = RoundedCornerShape(20.dp)
            )

            if (normalizedSearch.isBlank()) {
                DashboardWidgetsSection(
                    expenseWidgetEnabled = expenseWidgetEnabled == true,
                    habitWidgetEnabled = habitWidgetEnabled == true,
                    habitDashboard = habitDashboard,
                    onOpenPicker = { widgetPickerOpen = true },
                    onAddExpense = { expenseDialogOpen = true },
                    onToggleHabit = { habit, completed ->
                        scope.launch {
                            container.habitRepository.saveDailyProgress(
                                habitId = habit.habitId,
                                date = LocalDate.now(),
                                minutes = if (completed && habit.goalType == HabitGoalType.Time) habit.targetMinutes.coerceAtLeast(1) else 0,
                                progressCount = if (completed && habit.goalType == HabitGoalType.Count) habit.targetCount.coerceAtLeast(1) else 0,
                                completed = completed,
                                note = "",
                            )
                        }
                    },
                )
            }

            // ── Tool grid items ──
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // SECURITY TOOLS
                if (showSecurity) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "Security",
                            color = SoftText,
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.sp,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
                if (showAppLock) item {
                    ToolGridItem(
                        iconResId = com.rudra.daykit.R.drawable.ic_app_lock,
                        title = "App Lock",
                        onClick = onOpenAppLock,
                    )
                }
                if (showKeyStore) item {
                    ToolGridItem(
                        iconResId = com.rudra.daykit.R.drawable.ic_key_store,
                        title = "Key Store",
                        onClick = onOpenKeyStore,
                    )
                }
                if (showNotes) item {
                    ToolGridItem(
                        iconResId = com.rudra.daykit.R.drawable.ic_secure_notes,
                        title = "Notes",
                        onClick = onOpenNotes,
                    )
                }
                if (showFileLocker) item {
                    ToolGridItem(
                        iconResId = com.rudra.daykit.R.drawable.ic_file_locker,
                        title = "File Vault",
                        onClick = onOpenFileLocker,
                    )
                }
                // PRODUCTIVITY & TOOLS
                if (showProductivity) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "Productivity",
                            color = SoftText,
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.sp,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }
                }
                if (showHabit) item {
                    ToolGridItem(
                        iconResId = com.rudra.daykit.R.drawable.ic_todo_tracker,
                        title = "Habit",
                        onClick = onOpenHabit,
                    )
                }
                if (showReminder) item {
                    ToolGridItem(
                        iconResId = com.rudra.daykit.R.drawable.ic_reminder,
                        title = "Reminder",
                        onClick = onOpenReminder,
                    )
                }
                if (showExpenses) item {
                    ToolGridItem(
                        iconResId = com.rudra.daykit.R.drawable.ic_expense_tracker,
                        title = "Expenses",
                        onClick = onOpenExpenses,
                    )
                }
                if (showOther) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "Other",
                            color = SoftText,
                            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.sp,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                        )
                    }
                }
                if (showEditor) item {
                    ToolGridItem(
                        iconResId = com.rudra.daykit.R.drawable.ic_notes_editor,
                        title = "Editor",
                        onClick = onOpenEditor,
                    )
                }
                if (showDnsManager) item {
                    ToolGridItem(
                        iconResId = com.rudra.daykit.R.drawable.ic_dns_manager,
                        title = "DNS",
                        onClick = onOpenDnsManager,
                    )
                }
                if (!showSecurity && !showProductivity && !showOther) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "No tools found",
                            color = MutedText,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    }
                }
            }
        }
    }

    if (widgetPickerOpen) {
        WidgetPickerDialog(
            expenseEnabled = expenseWidgetEnabled == true,
            habitEnabled = habitWidgetEnabled == true,
            onExpenseChange = { enabled ->
                scope.launch {
                    container.secureSettingRepository.putBoolean(SecureSettingRepository.KEY_WIDGET_EXPENSES, enabled)
                }
            },
            onHabitChange = { enabled ->
                scope.launch {
                    container.secureSettingRepository.putBoolean(SecureSettingRepository.KEY_WIDGET_HABITS, enabled)
                }
            },
            onDismiss = { widgetPickerOpen = false },
        )
    }

    if (expenseDialogOpen) {
        QuickExpenseDialog(
            onDismiss = { expenseDialogOpen = false },
            onSave = { name, amountMinor, category, note ->
                scope.launch {
                    container.expenseRepository.addDailyExpense(
                        expenseDate = LocalDate.now().toString(),
                        title = name,
                        category = category,
                        amountMinor = amountMinor,
                        note = note,
                    )
                    expenseDialogOpen = false
                }
            },
        )
    }
}

@Composable
private fun DashboardWidgetsSection(
    expenseWidgetEnabled: Boolean,
    habitWidgetEnabled: Boolean,
    habitDashboard: HabitDashboard?,
    onOpenPicker: () -> Unit,
    onAddExpense: () -> Unit,
    onToggleHabit: (Habit, Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Widgets",
                color = SoftText,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = "+ Add",
                modifier = Modifier.width(86.dp).height(34.dp),
                textStyle = MaterialTheme.typography.labelMedium,
                onClick = onOpenPicker,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (expenseWidgetEnabled) {
                ExpenseQuickAddWidget(
                    modifier = Modifier.weight(1f),
                    onAddExpense = onAddExpense,
                )
            }
            if (habitWidgetEnabled) {
                HabitCheckInWidget(
                    dashboard = habitDashboard,
                    modifier = Modifier.weight(1f),
                    onToggleHabit = onToggleHabit,
                )
            }
        }
    }
}

@Composable
private fun ExpenseQuickAddWidget(
    modifier: Modifier,
    onAddExpense: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(116.dp)
            .glassSurface(RoundedCornerShape(18.dp), selected = false, tintStrength = 0.14f),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null, tint = Cyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Expenses",
                    color = SoftText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Text("Add today's spend", color = MutedText, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            PrimaryButton(
                text = "Add $",
                modifier = Modifier.fillMaxWidth().height(38.dp),
                leadingIcon = {
                    Icon(Icons.Rounded.AttachMoney, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                },
                onClick = onAddExpense,
            )
        }
    }
}

@Composable
private fun HabitCheckInWidget(
    dashboard: HabitDashboard?,
    modifier: Modifier,
    onToggleHabit: (Habit, Boolean) -> Unit,
) {
    val activeHabits = dashboard?.buildHabits.orEmpty()
    Box(
        modifier = modifier
            .height(116.dp)
            .glassSurface(RoundedCornerShape(18.dp), selected = false, tintStrength = 0.14f),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Cyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Habits",
                    color = SoftText,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
            }
            when {
                dashboard == null -> Text("Loading...", color = MutedText, style = MaterialTheme.typography.bodySmall)
                activeHabits.isEmpty() -> Text("No active habits", color = MutedText, style = MaterialTheme.typography.bodySmall)
                else -> LazyColumn(modifier = Modifier.weight(1f)) {
                    items(activeHabits, key = { it.habitId }) { habit ->
                        CompactHabitRow(
                            habit = habit,
                            log = dashboard.logFor(habit.habitId),
                            onToggleHabit = onToggleHabit,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactHabitRow(
    habit: Habit,
    log: HabitLog?,
    onToggleHabit: (Habit, Boolean) -> Unit,
) {
    val completed = isHabitLogComplete(habit, log)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggleHabit(habit, !completed) }
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (completed) Icons.Rounded.Check else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = if (completed) "Checked in" else "Check in",
            tint = if (completed) Cyan else MutedText,
            modifier = Modifier.size(17.dp),
        )
        Text(
            text = habit.name,
            color = SoftText,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${(habitProgress(habit, log) * 100).roundToInt()}%",
            color = MutedText,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun WidgetPickerDialog(
    expenseEnabled: Boolean,
    habitEnabled: Boolean,
    onExpenseChange: (Boolean) -> Unit,
    onHabitChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add widgets", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WidgetToggleRow(
                    title = "Expenses",
                    subtitle = "Quick add daily spend",
                    checked = expenseEnabled,
                    onCheckedChange = onExpenseChange,
                )
                WidgetToggleRow(
                    title = "Habits",
                    subtitle = "Compact daily check-in",
                    checked = habitEnabled,
                    onCheckedChange = onHabitChange,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = Cyan, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = PanelAlt,
        titleContentColor = SoftText,
        textContentColor = SoftText,
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
private fun WidgetToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = SoftText, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MutedText, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun QuickExpenseDialog(
    onDismiss: () -> Unit,
    onSave: (String, Long, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(DASHBOARD_EXPENSE_CATEGORIES.first()) }
    var note by remember { mutableStateOf("") }
    val amountMinor = amount.toDashboardMinorOrNull()
    val canSave = name.isNotBlank() && amountMinor != null && amountMinor > 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add daily expense", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DashboardTextField(value = name, onValueChange = { name = it }, label = "Name")
                DashboardTextField(
                    value = amount,
                    onValueChange = { amount = it.cleanDashboardAmountInput() },
                    label = "Amount",
                    keyboardType = KeyboardType.Decimal,
                )
                DashboardCategoryPicker(category = category, onCategoryChange = { category = it })
                DashboardTextField(value = note, onValueChange = { note = it.take(160) }, label = "Note")
            }
        },
        confirmButton = {
            TextButton(
                enabled = canSave,
                onClick = { onSave(name, amountMinor ?: 0L, category, note) },
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
        shape = RoundedCornerShape(14.dp),
    )
}

@Composable
private fun DashboardTextField(
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
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
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
private fun DashboardCategoryPicker(
    category: String,
    onCategoryChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Category", color = MutedText, style = MaterialTheme.typography.bodySmall)
                Text(category, color = SoftText, fontWeight = FontWeight.SemiBold)
            }
            Icon(Icons.AutoMirrored.Rounded.ReceiptLong, contentDescription = null, tint = Cyan, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(PanelAlt),
        ) {
            DASHBOARD_EXPENSE_CATEGORIES.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = SoftText) },
                    onClick = {
                        onCategoryChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

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

private fun isHabitLogComplete(habit: Habit, log: HabitLog?): Boolean {
    if (log == null) return false
    return when (habit.goalType) {
        HabitGoalType.Time -> if (habit.targetMinutes <= 0) log.minutes > 0 || log.completed else log.minutes >= habit.targetMinutes
        HabitGoalType.Count -> if (habit.targetCount <= 0) log.progressCount > 0 || log.completed else log.progressCount >= habit.targetCount
        HabitGoalType.Check -> log.completed
    }
}

private fun String.cleanDashboardAmountInput(): String {
    val filtered = filter { it.isDigit() || it == '.' }
    val firstDot = filtered.indexOf('.')
    return if (firstDot == -1) {
        filtered.take(9)
    } else {
        filtered.take(firstDot + 1) + filtered.drop(firstDot + 1).filter(Char::isDigit).take(2)
    }
}

private fun String.toDashboardMinorOrNull(): Long? {
    val value = toDoubleOrNull() ?: return null
    return (value * 100.0).roundToLong()
}

private val DASHBOARD_EXPENSE_CATEGORIES = listOf(
    "General",
    "Food",
    "Travel",
    "Shopping",
    "Bills",
    "Health",
    "Work",
    "Other",
)

@Composable
private fun ToolGridItem(
    iconResId: Int,
    title: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .glassSurface(
                    shape = RoundedCornerShape(18.dp),
                    selected = false,
                    tintStrength = 0.16f,
                    shadowElevation = 2f,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = iconResId),
                contentDescription = null,
                tint = Cyan,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            fontWeight = FontWeight.SemiBold,
            color = SoftText,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun AppLockScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onSelectionChanged: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val scope = rememberCoroutineScope()
    val biometricAuthenticator = remember(activity) { BiometricAuthenticator(activity) }
    var unlocked by remember { mutableStateOf(false) }
    var unlockPin by remember { mutableStateOf("") }
    var unlockError by remember { mutableStateOf<String?>(null) }
    var biometricEnabled by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(AppLockListTab.Unlocked) }
    var tabSwipeOffset by remember { mutableStateOf(0f) }
    var installedApps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var toolLocked by remember { mutableStateOf<Boolean?>(null) }
    val isToolLocked = toolLocked
    val lockedApps by container.appLockRepository
        .observeLockedApps()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val lockedPackages = remember(lockedApps) { lockedApps.map { it.packageName }.toSet() }
    val secureFolderAvailable = remember(context) { SamsungSecureFolderSupport.isAvailable(context) }

    BackHandler {
        if (query.isNotBlank()) {
            query = ""
        } else {
            onBack()
        }
    }

    fun tryBiometricUnlock(enabled: Boolean = biometricEnabled) {
        if (!enabled || !biometricAuthenticator.canAuthenticate()) return
        biometricAuthenticator.authenticate(
            title = "Unlock App Lock",
            subtitle = "Manage protected apps",
            onSuccess = {
                unlocked = true
                unlockPin = ""
                unlockError = null
            },
            onError = { unlockError = it },
        )
    }

    LaunchedEffect(Unit) {
        installedApps = container.installedAppProvider.loadLaunchableApps()
    }

    LaunchedEffect(secureFolderAvailable) {
        if (secureFolderAvailable) {
            container.appLockRepository.setLocked(
                packageName = SamsungSecureFolderSupport.PACKAGE_NAME,
                label = "Secure Folder",
                locked = false,
            )
        }
    }

    LaunchedEffect(Unit) {
        val storedToolLocked = container.secureSettingRepository
            .getBoolean(SecureSettingRepository.KEY_TOOL_LOCK_APP_LOCK) != false
        val storedBiometricEnabled = container.secureSettingRepository
            .getBoolean(SecureSettingRepository.KEY_BIOMETRIC_ENABLED) == true
        biometricEnabled = storedBiometricEnabled
        if (storedToolLocked && storedBiometricEnabled) {
            tryBiometricUnlock(storedBiometricEnabled)
        }
        container.secureSettingRepository
            .observeBoolean(SecureSettingRepository.KEY_TOOL_LOCK_APP_LOCK)
            .collect { locked ->
                toolLocked = locked ?: true
            }
    }

    LaunchedEffect(isToolLocked) {
        if (isToolLocked == false) {
            unlocked = true
            unlockPin = ""
            unlockError = null
        }
    }

    if (isToolLocked == null) {
        AppSurface {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                GlassLoadingIndicator()
            }
        }
        return
    }

    if (isToolLocked == true && !unlocked) {
        UtilityUnlockScreen(
            title = "App Lock",
            subtitle = "Enter master PIN to manage locked apps",
            pin = unlockPin,
            error = unlockError,
            biometricEnabled = biometricEnabled,
            onBack = onBack,
            onPinChange = {
                unlockPin = it.filter(Char::isDigit).take(12)
                unlockError = null
            },
            onUnlock = {
                scope.launch {
                    val pin = unlockPin
                    val valid = withContext(Dispatchers.Default) {
                        container.credentialRepository.verify(pin.toCharArray())
                    }
                    if (valid) {
                        unlocked = true
                        unlockPin = ""
                    } else {
                        unlockError = "Wrong PIN"
                        unlockPin = ""
                    }
                }
            },
            onBiometric = { tryBiometricUnlock() },
        )
        return
    }

    val filteredApps = remember(installedApps, query, lockedPackages) {
        installedApps.orEmpty().filter { app ->
            query.isBlank() ||
                app.label.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
        }.sortedWith(
            compareByDescending<InstalledApp> { it.packageName in lockedPackages }
                .thenBy { it.label.lowercase() }
        )
    }
    val unlockedApps = remember(filteredApps, lockedPackages) {
        filteredApps.filterNot { it.packageName in lockedPackages }
    }
    val lockedVisibleApps = remember(filteredApps, lockedPackages) {
        filteredApps.filter { it.packageName in lockedPackages }
    }
    val recommendedApps = remember(unlockedApps, context.packageName) {
        unlockedApps
            .filter { it.isRecommendedApp(context.packageName) }
            .sortedWith(compareBy<InstalledApp> { it.recommendationPriority(context.packageName) }.thenBy { it.label.lowercase() })
    }
    val otherUnlockedApps = remember(unlockedApps, recommendedApps) {
        val recommendedPackages = recommendedApps.map { it.packageName }.toSet()
        unlockedApps.filterNot { it.packageName in recommendedPackages }
    }

    GlassBackground {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(WindowInsets.statusBars.asPaddingValues())
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppBackButton(onClick = onBack)
                    Spacer(Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "App Lock",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "${lockedApps.size} apps protected",
                            color = MutedText,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
        ) { padding ->
            if (installedApps == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    GlassLoadingIndicator()
                }
            } else {
                val onCheckedChange: (InstalledApp, Boolean) -> Unit = { app, checked ->
                    scope.launch {
                        container.appLockRepository.setLocked(
                            packageName = app.packageName,
                            label = app.label,
                            locked = checked,
                        )
                        onSelectionChanged()
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 20.dp)
                        .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Cyan.copy(alpha = 0.85f)) },
                        placeholder = { Text("Search apps") },
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Cyan.copy(alpha = 0.8f),
                            unfocusedBorderColor = SoftText.copy(alpha = 0.2f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Cyan,
                            focusedPlaceholderColor = MutedText,
                            unfocusedPlaceholderColor = MutedText,
                            focusedContainerColor = Color.White.copy(alpha = 0.08f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                        ),
                    )
                    AppLockTabSelector(
                        selectedTab = selectedTab,
                        unlockedCount = unlockedApps.size,
                        lockedCount = lockedVisibleApps.size,
                        onTabSelected = { selectedTab = it },
                    )
                    AnimatedContent(
                        targetState = selectedTab,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                        .pointerInput(selectedTab) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    when {
                                        tabSwipeOffset < -80f -> selectedTab = AppLockListTab.Locked
                                        tabSwipeOffset > 80f -> selectedTab = AppLockListTab.Unlocked
                                    }
                                    tabSwipeOffset = 0f
                                },
                                onDragCancel = { tabSwipeOffset = 0f },
                                onHorizontalDrag = { _, dragAmount ->
                                    tabSwipeOffset += dragAmount
                                },
                            )
                        },
                        transitionSpec = {
                            if (targetState == AppLockListTab.Locked) {
                                (slideInHorizontally { width -> width } + fadeIn()) togetherWith
                                    (slideOutHorizontally { width -> -width } + fadeOut())
                            } else {
                                (slideInHorizontally { width -> -width } + fadeIn()) togetherWith
                                    (slideOutHorizontally { width -> width } + fadeOut())
                            }
                        },
                        label = "appLockTabContent",
                    ) { tab ->
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (tab == AppLockListTab.Unlocked) {
                                if (recommendedApps.isNotEmpty()) {
                                    item { AppListSectionHeader("Recommended apps") }
                                    items(recommendedApps, key = { "recommended-${it.packageName}" }) { app ->
                                        AppSelectionRow(
                                            app = app,
                                            checked = false,
                                            lockDisabled = secureFolderAvailable &&
                                                app.packageName == SamsungSecureFolderSupport.PACKAGE_NAME,
                                            onCheckedChange = { checked -> onCheckedChange(app, checked) },
                                        )
                                    }
                                }
                                if (otherUnlockedApps.isNotEmpty()) {
                                    item { AppListSectionHeader("All other apps") }
                                    items(otherUnlockedApps, key = { "other-${it.packageName}" }) { app ->
                                        AppSelectionRow(
                                            app = app,
                                            checked = false,
                                            lockDisabled = secureFolderAvailable &&
                                                app.packageName == SamsungSecureFolderSupport.PACKAGE_NAME,
                                            onCheckedChange = { checked -> onCheckedChange(app, checked) },
                                        )
                                    }
                                }
                                if (unlockedApps.isEmpty()) {
                                    item { EmptyAppListState(if (query.isBlank()) "No unlocked apps" else "No unlocked apps found") }
                                }
                            } else {
                                if (lockedVisibleApps.isEmpty()) {
                                    item { EmptyAppListState(if (query.isBlank()) "No locked apps yet" else "No locked apps found") }
                                }
                                items(lockedVisibleApps, key = { "locked-${it.packageName}" }) { app ->
                                    AppSelectionRow(
                                        app = app,
                                        checked = true,
                                        lockDisabled = secureFolderAvailable &&
                                            app.packageName == SamsungSecureFolderSupport.PACKAGE_NAME,
                                        onCheckedChange = { checked -> onCheckedChange(app, checked) },
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

@Composable
private fun AppLockTabSelector(
    selectedTab: AppLockListTab,
    unlockedCount: Int,
    lockedCount: Int,
    onTabSelected: (AppLockListTab) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
    ) {
        val tabWidth = maxWidth / 2
        val indicatorOffset by animateDpAsState(
            targetValue = if (selectedTab == AppLockListTab.Locked) tabWidth else 0.dp,
            label = "appLockTabIndicatorOffset",
        )

        Column {
            Row(modifier = Modifier.fillMaxWidth()) {
                AppLockTabButton(
                    title = "Unlocked",
                    count = unlockedCount,
                    selected = selectedTab == AppLockListTab.Unlocked,
                    modifier = Modifier.weight(1f),
                    onClick = { onTabSelected(AppLockListTab.Unlocked) },
                )
                AppLockTabButton(
                    title = "Locked",
                    count = lockedCount,
                    selected = selectedTab == AppLockListTab.Locked,
                    modifier = Modifier.weight(1f),
                    onClick = { onTabSelected(AppLockListTab.Locked) },
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = indicatorOffset)
                        .width(tabWidth),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(72.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Cyan),
                    )
                }
            }
        }
    }
}

@Composable
private fun AppLockTabButton(
    title: String,
    count: Int,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val titleColor by animateColorAsState(
        targetValue = if (selected) Cyan else MutedText,
        label = "appLockTabTitleColor",
    )
    val countColor by animateColorAsState(
        targetValue = if (selected) SoftText else MutedText,
        label = "appLockTabCountColor",
    )

    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = titleColor,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = count.toString(),
            color = countColor,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun AppListSectionHeader(title: String) {
    Text(
        text = title,
        color = MutedText,
        fontWeight = FontWeight.SemiBold,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun EmptyAppListState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            color = MutedText,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

private fun InstalledApp.isRecommendedApp(ownPackageName: String): Boolean =
    recommendationPriority(ownPackageName) < Int.MAX_VALUE

private fun InstalledApp.recommendationPriority(ownPackageName: String): Int {
    val labelText = label.lowercase()
    val packageText = packageName.lowercase()
    val combined = "$labelText $packageText"

    return when {
        packageName == ownPackageName || labelText == "daykit" -> 0
        listOf(
            "whatsapp",
            "telegram",
            "signal",
            "instagram",
            "facebook",
            "messenger",
            "snapchat",
            "discord",
            "linkedin",
        ).any(combined::contains) -> 1
        listOf(
            "bank",
            "upi",
            "gpay",
            "google pay",
            "phonepe",
            "paytm",
            "paypal",
            "bhim",
            "wallet",
            "finance",
        ).any(combined::contains) -> 2
        listOf(
            "gallery",
            "photos",
            "photo",
            "camera",
            "media",
        ).any(combined::contains) -> 3
        else -> Int.MAX_VALUE
    }
}

@Composable
private fun UtilityUnlockScreen(
    title: String,
    subtitle: String,
    pin: String,
    error: String?,
    biometricEnabled: Boolean,
    onBack: () -> Unit,
    onPinChange: (String) -> Unit,
    onUnlock: () -> Unit,
    onBiometric: () -> Unit,
) {
    AppSurface {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppBackButton(onClick = onBack)
                    Spacer(Modifier.width(4.dp))
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                }
                BrandHeader(
                    icon = Icons.Rounded.Lock,
                    title = title,
                    subtitle = subtitle,
                )
                SecureTextField(
                    value = pin,
                    onValueChange = onPinChange,
                    label = "Master PIN",
                )
                error?.let {
                    Text(text = it, color = Color(0xFFFFA8A8), style = MaterialTheme.typography.bodyMedium)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (biometricEnabled) {
                    SecondaryButton(
                        text = "Use fingerprint",
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            Icon(Icons.Rounded.Fingerprint, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                        },
                        onClick = onBiometric,
                    )
                }
                PrimaryButton(
                    text = "Unlock",
                    enabled = pin.length >= 4,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Rounded.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    },
                    onClick = onUnlock,
                )
            }
        }
    }
}

@Composable
private fun AppSurface(content: @Composable () -> Unit) {
    GlassBackground {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.statusBars.asPaddingValues())
                .padding(WindowInsets.navigationBars.asPaddingValues())
                .padding(16.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun BrandHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    useAppLogo: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (useAppLogo) {
            AppLogoBadge()
        } else {
            IconBadge(icon)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = subtitle,
                color = SoftText,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun AppLogoBadge() {
    Image(
        painter = painterResource(id = com.rudra.daykit.R.drawable.enew),
        contentDescription = null,
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(18.dp)),
    )
}

@Composable
private fun IconBadge(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Cyan.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Cyan, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun SecureTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    var visible by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (visible) "Hide" else "Show",
                    tint = SoftText,
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        shape = RoundedCornerShape(18.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Cyan,
            unfocusedBorderColor = SoftText.copy(alpha = 0.18f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Cyan,
            focusedLabelColor = Cyan,
            unfocusedLabelColor = MutedText,
            focusedContainerColor = Color.White.copy(alpha = 0.08f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
        ),
    )
}

@Composable
private fun StatusPanel(
    icon: ImageVector,
    title: String,
    value: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(20.dp), selected = false, shadowElevation = 2f)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Cyan, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(text = title, fontWeight = FontWeight.SemiBold)
            Text(text = value, color = MutedText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    granted: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(20.dp), selected = granted, shadowElevation = 2f)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (granted) Cyan else SoftText, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = if (granted) "Granted" else "Open",
            color = if (granted) Cyan else SoftText,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AppSelectionRow(
    app: InstalledApp,
    checked: Boolean,
    lockDisabled: Boolean = false,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(RoundedCornerShape(18.dp), selected = checked, shadowElevation = 2f)
            .clickable {
                if (!lockDisabled) {
                    onCheckedChange(!checked)
                }
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = app.label.firstOrNull()?.uppercase() ?: "#",
            color = if (checked) Cyan else SoftText,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(28.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = when {
                    lockDisabled -> "Protected by Samsung"
                    checked -> "Locked app"
                    else -> "Unlocked"
                },
                color = MutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clickable {
                    if (!lockDisabled) {
                        onCheckedChange(!checked)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when {
                    lockDisabled -> Icons.Rounded.Shield
                    checked -> Icons.Rounded.Lock
                    else -> Icons.Rounded.LockOpen
                },
                contentDescription = when {
                    lockDisabled -> "${app.label} is protected by Samsung"
                    checked -> "Unlock ${app.label}"
                    else -> "Lock ${app.label}"
                },
                tint = if (lockDisabled) MutedText else Cyan,
                modifier = Modifier.size(21.dp),
            )
        }
    }
}
