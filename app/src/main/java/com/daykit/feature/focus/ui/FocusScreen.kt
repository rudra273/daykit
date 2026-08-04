package com.daykit.feature.focus.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.daykit.AppContainer
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppIconOrMonogram
import com.daykit.core.designsystem.components.AppListRow
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.EmptyState
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.components.SectionHeader
import com.daykit.core.designsystem.components.rememberErrorReporter
import com.daykit.core.designsystem.extendedColors
import com.daykit.core.permissions.AppLockPermissionChecker
import com.daykit.core.permissions.PermissionIntents
import com.daykit.feature.applock.domain.InstalledApp
import kotlinx.coroutines.delay

/**
 * The Focus tool: start and watch strict timed blocks on individual apps.
 *
 * A block cannot be cancelled — not even with the PIN — so this screen is
 * deliberately read-only for anything already running; the only action it offers
 * is starting a new block.
 *
 * [onMonitorNeeded] starts the App Lock monitor service, which is what actually
 * enforces a block. It's invoked after starting one so a block works even for a
 * user who has never locked an app.
 */
@Composable
fun FocusScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onMonitorNeeded: () -> Unit,
) {
    val context = LocalContext.current
    val errors = rememberErrorReporter()
    val haptics = LocalHapticFeedback.current

    val focusBlocks by container.focusRepository
        .observeFocusBlocks()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var installedApps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var pickerVisible by remember { mutableStateOf(false) }
    var durationSheetApp by remember { mutableStateOf<InstalledApp?>(null) }

    // Usage Access is what lets the monitor see which app is in the foreground;
    // without it a block silently does nothing. Re-checked on resume because the
    // user grants it in Settings, outside this activity.
    var hasUsageAccess by remember { mutableStateOf(AppLockPermissionChecker.hasUsageAccess(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageAccess = AppLockPermissionChecker.hasUsageAccess(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    // Only re-renders the countdown text; dropping an expired block is the
    // repository flow's job.
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(focusBlocks.isNotEmpty()) {
        if (focusBlocks.isEmpty()) return@LaunchedEffect
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    LaunchedEffect(Unit) {
        installedApps = container.installedAppProvider.loadLaunchableApps()
            .filterNot { it.packageName == context.packageName }
    }

    BackHandler { onBack() }

    val blockedPackages = remember(focusBlocks) { focusBlocks.map { it.packageName }.toSet() }
    val sortedBlocks = remember(focusBlocks) { focusBlocks.sortedBy { it.lockUntilMillis } }
    val appsByPackage = remember(installedApps) {
        installedApps.orEmpty().associateBy { it.packageName }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(errors.host) },
        topBar = { AppTopBar(title = "Focus", onBack = onBack) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = innerPadding.calculateTopPadding() + Spacing.sm,
                bottom = Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            if (!hasUsageAccess) {
                item(key = "permission") {
                    UsageAccessWarning(
                        onGrant = {
                            container.sensitiveKeyManager.expectingActivityResult = true
                            runCatching { context.startActivity(PermissionIntents.usageAccessSettings()) }
                        },
                    )
                    Spacer(Modifier.height(Spacing.sm))
                }
            }

            item(key = "start") {
                PrimaryButton(
                    text = "Start a focus block",
                    onClick = { pickerVisible = true },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(Spacing.md))
            }

            if (sortedBlocks.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.Timer,
                        title = "No active focus blocks",
                        description = "Block an app for a set time. Once it starts it can't be " +
                            "undone — not even with your PIN — until the timer ends.",
                        modifier = Modifier.padding(top = Spacing.xl),
                    )
                }
            } else {
                item(key = "header-active") { SectionHeader("Active") }
                items(sortedBlocks, key = { it.packageName }) { block ->
                    val app = appsByPackage[block.packageName]
                    val remaining = (block.lockUntilMillis - nowMillis).coerceAtLeast(0L)
                    AppListRow(
                        headline = app?.label ?: block.label,
                        supporting = "${formatFocusRemaining(remaining)} left",
                        leading = {
                            AppIconOrMonogram(
                                icon = app?.icon,
                                label = app?.label ?: block.label,
                                packageName = block.packageName,
                            )
                        },
                        trailing = {
                            Text(
                                text = formatFocusRemaining(remaining),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                    )
                }
            }
        }
    }

    if (pickerVisible) {
        FocusAppPickerSheet(
            apps = installedApps,
            blockedPackages = blockedPackages,
            onSelect = { app ->
                pickerVisible = false
                durationSheetApp = app
            },
            onDismiss = { pickerVisible = false },
        )
    }

    durationSheetApp?.let { app ->
        FocusBlockSheet(
            appLabel = app.label,
            onConfirm = { durationMillis ->
                durationSheetApp = null
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                errors.launchGuarded("Couldn't start the focus block for ${app.label}.") {
                    container.focusRepository.startFocusBlock(
                        packageName = app.packageName,
                        label = app.label,
                        durationMillis = durationMillis,
                    )
                    // A block on an app that was never PIN-locked still needs the
                    // monitor running to be enforced.
                    onMonitorNeeded()
                }
            },
            onDismiss = { durationSheetApp = null },
        )
    }
}

/**
 * Shown when Usage Access is missing. Deliberately blunt: without it a started
 * block is silently unenforced, which is worse than refusing to pretend.
 */
@Composable
private fun UsageAccessWarning(onGrant: () -> Unit) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.extendedColors.accents.orange,
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = "Focus blocks need Usage Access",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = "Without it DayKit can't tell which app is open, so a block won't " +
                "actually stop you from using it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.extendedColors.textMuted,
        )
        Spacer(Modifier.height(Spacing.sm))
        PrimaryButton(text = "Grant Usage Access", onClick = onGrant)
    }
}
