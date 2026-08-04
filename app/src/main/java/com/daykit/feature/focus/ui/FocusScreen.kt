package com.daykit.feature.focus.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daykit.AppContainer
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppFab
import com.daykit.core.designsystem.components.AppIconOrMonogram
import com.daykit.core.designsystem.components.AppListRow
import com.daykit.core.designsystem.components.AppSwitch
import com.daykit.core.designsystem.components.AppTextButton
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.components.SecondaryButton
import com.daykit.core.designsystem.components.SectionHeader
import com.daykit.core.designsystem.components.StatTile
import com.daykit.core.designsystem.components.rememberErrorReporter
import com.daykit.core.designsystem.extendedColors
import com.daykit.core.permissions.AppLockPermissionChecker
import com.daykit.core.permissions.PermissionIntents
import com.daykit.feature.applock.domain.InstalledApp
import com.daykit.feature.focus.data.ArmedSchedule
import com.daykit.feature.focus.data.FocusGroup
import com.daykit.feature.focus.data.FocusRecurrence
import com.daykit.feature.focus.data.FocusSchedule
import com.daykit.feature.focus.service.FocusScheduleScheduler
import kotlinx.coroutines.delay

/** Which nested editor, if any, has replaced the list. */
private sealed interface FocusEditor {
    data class Group(val existing: FocusGroup?) : FocusEditor
    data class Schedule(val existing: FocusSchedule?) : FocusEditor
}

/**
 * The Focus tool: app groups, recurring schedules, and one-off blocks.
 *
 * A running block cannot be cancelled — Strict sessions and manual blocks are
 * irreversible by design, and only a Normal scheduled session offers an early
 * exit (behind the PIN). So this screen is read-only for anything already
 * running.
 *
 * The list is three fixed sections — Session, Groups, Schedules — each present
 * even when empty, so the tool explains itself without a hero empty state. Each
 * creatable section carries its own action: Groups and Schedules create from
 * their header, and the FAB blocks a single app now.
 *
 * [onMonitorNeeded] starts the App Lock monitor, which is what actually enforces
 * a block. Called after anything is armed, so blocks work for a user who has
 * never PIN-locked an app.
 */
@Composable
fun FocusScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onMonitorNeeded: () -> Unit,
) {
    var editor by remember { mutableStateOf<FocusEditor?>(null) }

    val groups by container.focusGroupRepository
        .observeGroups()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val schedules by container.focusScheduleRepository
        .observeSchedules()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    when (val current = editor) {
        is FocusEditor.Group -> FocusGroupEditorHost(
            container = container,
            existing = current.existing,
            onDone = { editor = null },
        )

        is FocusEditor.Schedule -> FocusScheduleEditorHost(
            container = container,
            existing = current.existing,
            groups = groups,
            onMonitorNeeded = onMonitorNeeded,
            onDone = { editor = null },
        )

        null -> FocusHome(
            container = container,
            groups = groups,
            schedules = schedules,
            onBack = onBack,
            onMonitorNeeded = onMonitorNeeded,
            onEdit = { editor = it },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FocusHome(
    container: AppContainer,
    groups: List<FocusGroup>,
    schedules: List<FocusSchedule>,
    onBack: () -> Unit,
    onMonitorNeeded: () -> Unit,
    onEdit: (FocusEditor) -> Unit,
) {
    val context = LocalContext.current
    val errors = rememberErrorReporter()
    val haptics = LocalHapticFeedback.current

    val focusBlocks by container.focusRepository
        .observeFocusBlocks()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var installedApps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var quickBlockApp by remember { mutableStateOf<InstalledApp?>(null) }
    var quickPickerOpen by remember { mutableStateOf(false) }
    var groupToBlock by remember { mutableStateOf<FocusGroup?>(null) }

    // Granted outside this activity, so re-check on resume.
    var hasUsageAccess by remember { mutableStateOf(AppLockPermissionChecker.hasUsageAccess(context)) }
    var canScheduleExact by remember {
        mutableStateOf(AppLockPermissionChecker.canScheduleExactAlarms(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasUsageAccess = AppLockPermissionChecker.hasUsageAccess(context)
                canScheduleExact = AppLockPermissionChecker.canScheduleExactAlarms(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    // Ticks only to re-render countdown text; expiry itself is pushed by the
    // repository flow and by the armed-window projection.
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var activeSessions by remember { mutableStateOf<List<ArmedSchedule>>(emptyList()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            activeSessions = container.focusScheduleRepository.armedSchedules()
                .filter { nowMillis in it.startMillis until it.endMillis }
            delay(1000L)
        }
    }

    LaunchedEffect(Unit) {
        installedApps = container.installedAppProvider.loadLaunchableApps()
            .filterNot { it.packageName == context.packageName }
    }

    // Keep the projection honest whenever definitions change while on screen.
    LaunchedEffect(groups, schedules) {
        errors.launchGuarded("Couldn't update your focus schedules.") {
            val armed = container.focusScheduleRepository.reproject()
            FocusScheduleScheduler(context).arm(armed)
        }
    }

    BackHandler { onBack() }

    val appsByPackage = remember(installedApps) {
        installedApps.orEmpty().associateBy { it.packageName }
    }
    val blockedPackages = remember(focusBlocks) { focusBlocks.map { it.packageName }.toSet() }
    val sortedBlocks = remember(focusBlocks) { focusBlocks.sortedBy { it.lockUntilMillis } }
    val groupsById = remember(groups) { groups.associateBy { it.groupId } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(errors.host) },
        topBar = { AppTopBar(title = "Focus", onBack = onBack) },
        // Straight to the app picker: groups and schedules are created from their
        // own section headers, so the FAB means exactly one thing — block an app now.
        floatingActionButton = {
            AppFab(
                icon = Icons.Rounded.Add,
                contentDescription = "Block one app now",
                onClick = { quickPickerOpen = true },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = innerPadding.calculateTopPadding() + Spacing.sm,
                // FAB clearance, as every other tool screen does.
                bottom = Spacing.xxl + 72.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (!hasUsageAccess) {
                item(key = "perm-usage") {
                    WarningCard(
                        title = "Focus blocks need Usage Access",
                        body = "Without it DayKit can't tell which app is open, so a block " +
                            "won't actually stop you from using it.",
                        actionText = "Grant Usage Access",
                        onAction = {
                            container.sensitiveKeyManager.expectingActivityResult = true
                            runCatching { context.startActivity(PermissionIntents.usageAccessSettings()) }
                        },
                    )
                }
            }
            if (!canScheduleExact && schedules.isNotEmpty()) {
                item(key = "perm-alarm") {
                    WarningCard(
                        title = "Schedules may start late",
                        body = "Without the alarms & reminders permission Android can delay a " +
                            "scheduled session by several minutes.",
                        actionText = "Allow exact alarms",
                        onAction = {
                            container.sensitiveKeyManager.expectingActivityResult = true
                            runCatching { context.startActivity(PermissionIntents.exactAlarmSettings(context)) }
                        },
                    )
                }
            }

            // No create action: a session is only ever started by a schedule
            // firing or by blocking apps below, never from here.
            item(key = "header-session") { FocusSectionHeader("Session") }

            if (activeSessions.isEmpty() && sortedBlocks.isEmpty()) {
                item(key = "empty-session") {
                    FocusSectionEmpty("Nothing is blocked right now.")
                }
            }

            items(activeSessions, key = { "session-${it.scheduleId}-${it.startMillis}" }) { session ->
                SessionHeroCard(
                    session = session,
                    remainingMillis = (session.endMillis - nowMillis).coerceAtLeast(0L),
                    onEndEarly = if (session.strict) {
                        null
                    } else {
                        {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            errors.launchGuarded("Couldn't end the session.") {
                                container.focusScheduleRepository.endSessionEarly(
                                    scheduleId = session.scheduleId,
                                    startMillis = session.startMillis,
                                )
                                activeSessions = container.focusScheduleRepository.armedSchedules()
                                    .filter { System.currentTimeMillis() in it.startMillis until it.endMillis }
                            }
                        }
                    },
                )
            }

            items(sortedBlocks, key = { "block-${it.packageName}" }) { block ->
                val app = appsByPackage[block.packageName]
                val remaining = (block.lockUntilMillis - nowMillis).coerceAtLeast(0L)
                AppListRow(
                    headline = app?.label ?: block.label,
                    supporting = "Locked · ${formatFocusRemaining(remaining)} left",
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

            if (groups.isNotEmpty() || schedules.isNotEmpty()) {
                item(key = "stats") {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        StatTile(
                            label = "Groups",
                            value = groups.size.toString(),
                            accent = MaterialTheme.extendedColors.accents.blue,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            label = "Schedules",
                            value = schedules.count { it.enabled }.toString(),
                            accent = MaterialTheme.extendedColors.accents.purple,
                            modifier = Modifier.weight(1f),
                        )
                        StatTile(
                            label = "Blocked now",
                            value = (activeSessions.sumOf { it.packageNames.size } + sortedBlocks.size)
                                .toString(),
                            accent = MaterialTheme.extendedColors.accents.orange,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            item(key = "header-groups") {
                FocusSectionHeader(
                    title = "Groups",
                    actionText = "Create",
                    onAction = { onEdit(FocusEditor.Group(null)) },
                )
            }
            if (groups.isEmpty()) {
                item(key = "empty-groups") {
                    FocusSectionEmpty("Group the apps that distract you, then block them together.")
                }
            }
            items(groups, key = { "group-${it.groupId}" }) { group ->
                GroupRow(
                    group = group,
                    onStart = { groupToBlock = group },
                    onEdit = { onEdit(FocusEditor.Group(group)) },
                )
            }

            item(key = "header-schedules") {
                FocusSectionHeader(
                    title = "Schedules",
                    // A schedule blocks a group, so there is nothing to schedule
                    // until one exists; the placeholder below says so.
                    actionText = if (groups.isNotEmpty()) "Create" else null,
                    onAction = if (groups.isNotEmpty()) {
                        { onEdit(FocusEditor.Schedule(null)) }
                    } else {
                        null
                    },
                )
            }
            if (schedules.isEmpty()) {
                item(key = "empty-schedules") {
                    FocusSectionEmpty(
                        if (groups.isEmpty()) {
                            "Create a group first, then block it at the same time each week."
                        } else {
                            "Block a group at the same time each week."
                        },
                    )
                }
            }
            items(schedules, key = { "schedule-${it.scheduleId}" }) { schedule ->
                ScheduleRow(
                    schedule = schedule,
                    groupName = groupsById[schedule.groupId]?.name ?: "Deleted group",
                    onToggle = { enabled ->
                        errors.launchGuarded("Couldn't update the schedule.") {
                            container.focusScheduleRepository
                                .setEnabled(schedule.scheduleId, enabled)
                        }
                    },
                    onEdit = { onEdit(FocusEditor.Schedule(schedule)) },
                )
            }
        }
    }

    if (quickPickerOpen) {
        FocusAppPickerSheet(
            apps = installedApps,
            blockedPackages = blockedPackages,
            onSelect = { app -> quickPickerOpen = false; quickBlockApp = app },
            onDismiss = { quickPickerOpen = false },
        )
    }

    quickBlockApp?.let { app ->
        FocusBlockSheet(
            appLabel = app.label,
            onConfirm = { durationMillis ->
                quickBlockApp = null
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                errors.launchGuarded("Couldn't start the focus block for ${app.label}.") {
                    container.focusRepository.startFocusBlock(
                        packageName = app.packageName,
                        label = app.label,
                        durationMillis = durationMillis,
                    )
                    onMonitorNeeded()
                }
            },
            onDismiss = { quickBlockApp = null },
        )
    }

    groupToBlock?.let { group ->
        FocusBlockSheet(
            appLabel = "${group.name} (${group.packageNames.size} apps)",
            onConfirm = { durationMillis ->
                groupToBlock = null
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                errors.launchGuarded("Couldn't start ${group.name}.") {
                    startGroupBlock(
                        container = container,
                        group = group,
                        appsByPackage = appsByPackage,
                        durationMillis = durationMillis,
                        onMonitorNeeded = onMonitorNeeded,
                    )
                }
            },
            onDismiss = { groupToBlock = null },
        )
    }
}

/**
 * Blocks every app in [group] for [durationMillis].
 *
 * Goes through the same duration+confirm sheet as a single app rather than
 * assuming a length: these blocks are irreversible, and silently committing the
 * user to an arbitrary hour across several apps at one tap would be a trap.
 *
 * [appsByPackage] supplies real labels — storing the package name would show
 * "com.instagram.android" on the lock screen.
 */
private suspend fun startGroupBlock(
    container: AppContainer,
    group: FocusGroup,
    appsByPackage: Map<String, InstalledApp>,
    durationMillis: Long,
    onMonitorNeeded: () -> Unit,
) {
    group.packageNames.forEach { pkg ->
        container.focusRepository.startFocusBlock(
            packageName = pkg,
            label = appsByPackage[pkg]?.label ?: pkg,
            durationMillis = durationMillis,
        )
    }
    onMonitorNeeded()
}

@Composable
private fun SessionHeroCard(
    session: ArmedSchedule,
    remainingMillis: Long,
    onEndEarly: (() -> Unit)?,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = session.label.ifBlank { "Focus session" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${formatFocusRemaining(remainingMillis)} left",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = "${session.packageNames.size} app" +
                "${if (session.packageNames.size == 1) "" else "s"} blocked",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.extendedColors.textMuted,
        )
        Spacer(Modifier.height(Spacing.sm))
        if (onEndEarly == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.extendedColors.danger,
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = "Strict · can't be ended",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.extendedColors.danger,
                )
            }
        } else {
            SecondaryButton(text = "End early", onClick = onEndEarly)
        }
    }
}

@Composable
private fun GroupRow(
    group: FocusGroup,
    onStart: () -> Unit,
    onEdit: () -> Unit,
) {
    val accents = MaterialTheme.extendedColors.accents
    val palette = listOf(
        accents.blue, accents.teal, accents.green, accents.red,
        accents.orange, accents.yellow, accents.purple, accents.pink, accents.indigo,
    )
    AppListRow(
        headline = group.name,
        supporting = "${group.packageNames.size} app" +
            if (group.packageNames.size == 1) "" else "s",
        leadingIcon = Icons.Rounded.Timer,
        leadingAccent = palette[group.colorIndex.coerceIn(palette.indices)],
        trailing = {
            SecondaryButton(
                text = "Start",
                enabled = group.packageNames.isNotEmpty(),
                onClick = onStart,
            )
        },
        onClick = onEdit,
    )
}

@Composable
private fun ScheduleRow(
    schedule: FocusSchedule,
    groupName: String,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    val window = "${FocusRecurrence.formatTime(schedule.startHour, schedule.startMinute)} – " +
        FocusRecurrence.formatTime(schedule.endHour, schedule.endMinute)
    AppListRow(
        headline = schedule.label.ifBlank { groupName },
        supporting = "${FocusRecurrence.describe(schedule.daysMask)} · $window" +
            if (schedule.strict) " · Strict" else "",
        leadingIcon = Icons.Rounded.Schedule,
        leadingAccent = if (schedule.strict) {
            MaterialTheme.extendedColors.danger
        } else {
            MaterialTheme.extendedColors.accents.purple
        },
        trailing = {
            AppSwitch(checked = schedule.enabled, onCheckedChange = onToggle)
        },
        onClick = onEdit,
    )
}

/**
 * A [SectionHeader] with an optional create action on the right.
 *
 * Groups and Schedules each own their create button so the three sections are
 * always visible and self-explanatory, even when every one of them is empty —
 * that replaces the old single full-bleed empty-state card, which hid Schedules
 * behind "create a group first" and said nothing about sessions.
 */
@Composable
private fun FocusSectionHeader(
    title: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionHeader(text = title, modifier = Modifier.weight(1f))
        if (actionText != null && onAction != null) {
            AppTextButton(text = actionText, onClick = onAction)
        }
    }
}

/** Quiet one-liner placeholder for an empty section — not a hero card. */
@Composable
private fun FocusSectionEmpty(text: String) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(Spacing.md),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.extendedColors.textMuted,
        )
    }
}

/** Blunt warning card: a silently unenforced block is worse than saying so. */
@Composable
private fun WarningCard(
    title: String,
    body: String,
    actionText: String,
    onAction: () -> Unit,
) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.extendedColors.accents.orange,
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(Spacing.xs))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.extendedColors.textMuted,
        )
        Spacer(Modifier.height(Spacing.sm))
        PrimaryButton(text = actionText, onClick = onAction)
    }
}

@Composable
private fun FocusGroupEditorHost(
    container: AppContainer,
    existing: FocusGroup?,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val errors = rememberErrorReporter()
    var installedApps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    LaunchedEffect(Unit) {
        installedApps = container.installedAppProvider.loadLaunchableApps()
            .filterNot { it.packageName == context.packageName }
    }

    // Only the sheet shows; a backdrop Scaffold would double the top bar.
    Box(modifier = Modifier.fillMaxSize()) {
        SnackbarHost(errors.host)
    }

    FocusGroupEditorSheet(
        existing = existing,
        apps = installedApps,
        onSave = { name, colorIndex, packages ->
            errors.launchGuarded("Couldn't save the group.") {
                container.focusGroupRepository.saveGroup(
                    groupId = existing?.groupId,
                    name = name,
                    colorIndex = colorIndex,
                    packageNames = packages,
                )
                onDone()
            }
        },
        onDismiss = onDone,
    )
}

@Composable
private fun FocusScheduleEditorHost(
    container: AppContainer,
    existing: FocusSchedule?,
    groups: List<FocusGroup>,
    onMonitorNeeded: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val errors = rememberErrorReporter()

    FocusScheduleEditorPage(
        existing = existing,
        groups = groups,
        onSave = { draft ->
            errors.launchGuarded("Couldn't save the schedule.") {
                container.focusScheduleRepository.saveSchedule(
                    scheduleId = existing?.scheduleId,
                    groupId = draft.groupId,
                    label = draft.label,
                    startHour = draft.startHour,
                    startMinute = draft.startMinute,
                    endHour = draft.endHour,
                    endMinute = draft.endMinute,
                    daysMask = draft.daysMask,
                    strict = draft.strict,
                )
                val armed = container.focusScheduleRepository.reproject()
                FocusScheduleScheduler(context).arm(armed)
                onMonitorNeeded()
                onDone()
            }
        },
        onDismiss = onDone,
    )
}
