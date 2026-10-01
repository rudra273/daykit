package com.daykit.feature.focus.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.runtime.rememberCoroutineScope
import com.daykit.core.designsystem.components.AccentIconTile
import kotlinx.coroutines.launch
import androidx.compose.material.icons.rounded.SelfImprovement
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daykit.AppContainer
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.asAccentContainer
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppIconOrMonogram
import com.daykit.core.designsystem.components.AppSwitch
import com.daykit.core.designsystem.components.AppTextButton
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.components.SecondaryButton
import com.daykit.core.designsystem.components.rememberErrorReporter
import com.daykit.core.designsystem.extendedColors
import com.daykit.core.permissions.AppLockPermissionChecker
import com.daykit.core.permissions.PermissionIntents
import com.daykit.feature.applock.domain.InstalledApp
import com.daykit.feature.focus.data.ArmedSchedule
import com.daykit.feature.focus.data.FocusAppLimit
import com.daykit.feature.focus.data.FocusGroup
import com.daykit.feature.focus.data.FocusRecurrence
import com.daykit.feature.focus.data.FocusSchedule
import com.daykit.feature.focus.data.FocusSuggestionFilter
import com.daykit.feature.focus.data.FocusUsageTracker
import com.daykit.feature.focus.service.FocusScheduleScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

/** Which nested editor, if any, has replaced the list. */
private sealed interface FocusEditor {
    data class Group(val existing: FocusGroup?) : FocusEditor
    data class Schedule(val existing: FocusSchedule?) : FocusEditor
}

/**
 * The Focus tool, organized around three modes — [FocusMode.LockNow],
 * [FocusMode.DailyLimit], [FocusMode.Routine] — with app sets (`FocusGroup`)
 * as the shared ingredient rather than a peer feature.
 *
 * Top to bottom: a "Right now" hero where every blocked app wears a ring in the
 * color of the mode that blocked it; the mode tiles (large on a first visit,
 * where they double as onboarding, then compact); the Daily limits and Routines
 * the user has set up; and the app sets row. Sections appear only once they
 * have content — the tiles are the way in, so empty placeholders aren't needed.
 *
 * A running block cannot be cancelled — Lock now blocks and Strict routines are
 * irreversible by design, and only a Normal routine offers an early exit (behind
 * the data-layer guard in `endSessionEarly`).
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

    // Weekly usage is read only when the user asks for suggestions — never in
    // the background. Once scanned, the pickers reuse it to order apps; until
    // then they stay alphabetical.
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var weeklyUsage by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }
    var scan by remember { mutableStateOf(SuggestScan.Idle) }
    var suggestionExclusions by remember { mutableStateOf<Set<String>>(emptySet()) }
    val onScan: () -> Unit = {
        if (scan != SuggestScan.Scanning) {
            scan = SuggestScan.Scanning
            scope.launch {
                val started = System.currentTimeMillis()
                val (usage, excluded) = withContext(Dispatchers.IO) {
                    val homes = FocusSuggestionFilter.homeScreenPackages(context)
                    container.focusAppLimitRepository.getWeekUsageMap()
                        .filterKeys { it !in homes } to FocusSuggestionFilter.excludedPackages(context)
                }
                // Long enough to read as "checking", so the result doesn't flash in.
                delay((SCAN_MIN_MILLIS - (System.currentTimeMillis() - started)).coerceAtLeast(0L))
                weeklyUsage = usage
                suggestionExclusions = excluded
                scan = SuggestScan.Done
            }
        }
    }

    when (val current = editor) {
        is FocusEditor.Group -> FocusGroupEditorHost(
            container = container,
            existing = current.existing,
            allSets = groups,
            weeklyUsage = weeklyUsage,
            onDone = { editor = null },
        )

        is FocusEditor.Schedule -> FocusScheduleEditorHost(
            container = container,
            existing = current.existing,
            groups = groups,
            weeklyUsage = weeklyUsage,
            onMonitorNeeded = onMonitorNeeded,
            onDone = { editor = null },
        )

        null -> FocusHome(
            container = container,
            groups = groups,
            schedules = schedules,
            weeklyUsage = weeklyUsage,
            suggestionExclusions = suggestionExclusions,
            scan = scan,
            onScan = onScan,
            onBack = onBack,
            onMonitorNeeded = onMonitorNeeded,
            onEdit = { editor = it },
        )
    }
}

/** Where the on-demand "Suggest apps to block" scan is. */
private enum class SuggestScan { Idle, Scanning, Done }

/** Below this daily average an app isn't worth suggesting. */
private const val SUGGEST_MIN_DAILY_MILLIS = 5 * 60_000L
private const val SUGGESTION_COUNT = 5
private const val SCAN_MIN_MILLIS = 900L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FocusHome(
    container: AppContainer,
    groups: List<FocusGroup>,
    schedules: List<FocusSchedule>,
    weeklyUsage: Map<String, Long>,
    suggestionExclusions: Set<String>,
    scan: SuggestScan,
    onScan: () -> Unit,
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
    val appLimits by container.focusAppLimitRepository
        .observeAppLimits()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    var installedApps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var lockPickerOpen by remember { mutableStateOf(false) }
    var quickBlockApp by remember { mutableStateOf<InstalledApp?>(null) }
    var groupToBlock by remember { mutableStateOf<FocusGroup?>(null) }
    var appLimitToEdit by remember { mutableStateOf<FocusAppLimit?>(null) }
    var appLimitPickerOpen by remember { mutableStateOf(false) }
    var appLimitTargetApp by remember { mutableStateOf<InstalledApp?>(null) }
    var todayUsage by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }

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
                todayUsage = container.focusAppLimitRepository.getTodayUsageMap()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
    }

    // Ticks only to re-render countdowns; expiry itself is pushed by the
    // repository flow and by the armed-window projection.
    var nowMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var armed by remember { mutableStateOf<List<ArmedSchedule>>(emptyList()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            armed = container.focusScheduleRepository.armedSchedules()
            todayUsage = container.focusAppLimitRepository.getTodayUsageMap()
            delay(1000L)
        }
    }
    val activeSessions = remember(armed, nowMillis) {
        armed.filter { nowMillis in it.startMillis until it.endMillis }
    }
    val nextSession = remember(armed, nowMillis) {
        armed.filter { it.startMillis > nowMillis }.minByOrNull { it.startMillis }
    }

    LaunchedEffect(Unit) {
        installedApps = container.installedAppProvider.loadLaunchableApps()
            .filterNot { it.packageName == context.packageName }
    }

    // Seed a useful starter set without guessing from display names. The
    // repository leaves the group alone once created, so it remains editable
    // just like the sets the user creates from this screen.
    LaunchedEffect(installedApps) {
        installedApps ?: return@LaunchedEffect
        errors.launchGuarded("Couldn't add the Social set.") {
            container.focusGroupRepository.ensureDefaultSocialGroup(
                installedPackageNames = installedApps.orEmpty().map { it.packageName },
            )
        }
    }

    // Keep the projection honest whenever definitions change while on screen.
    LaunchedEffect(groups, schedules) {
        errors.launchGuarded("Couldn't update your routines.") {
            val projected = container.focusScheduleRepository.reproject()
            FocusScheduleScheduler(context).arm(projected)
            armed = projected
        }
    }

    BackHandler { onBack() }

    val appsByPackage = remember(installedApps) {
        installedApps.orEmpty().associateBy { it.packageName }
    }
    val lockedPackages = remember(focusBlocks) { focusBlocks.map { it.packageName }.toSet() }
    val groupsById = remember(groups) { groups.associateBy { it.groupId } }
    val activeScheduleIds = remember(activeSessions) { activeSessions.map { it.scheduleId }.toSet() }
    // Biggest weekly use first, so the limits most likely to bite sit on top.
    val sortedLimits = remember(appLimits, weeklyUsage) {
        appLimits.sortedByDescending { weeklyUsage[it.packageName] ?: 0L }
    }
    // Heavy-use apps with nothing on them yet. An app already limited or locked
    // is handled, and essentials (WhatsApp, payments, 2FA…) are never what the
    // user means by distracting, so neither is suggested.
    val suggestions = remember(installedApps, weeklyUsage, appLimits, lockedPackages, suggestionExclusions) {
        val limited = appLimits.map { it.packageName }.toSet()
        installedApps.orEmpty()
            .filter { it.packageName !in limited && it.packageName !in lockedPackages }
            .filter { it.packageName !in suggestionExclusions }
            .filter {
                (weeklyUsage[it.packageName] ?: 0L) >= SUGGEST_MIN_DAILY_MILLIS * FocusUsageTracker.WEEK_DAYS
            }
            .sortedWith(mostUsedFirst(weeklyUsage))
            .take(SUGGESTION_COUNT)
    }

    val blockedNow = buildBlockedNow(
        focusBlocks = focusBlocks,
        appLimits = appLimits,
        todayUsage = todayUsage,
        activeSessions = activeSessions,
        labelFor = { appsByPackage[it]?.label },
        nowMillis = nowMillis,
    )

    val isFirstRun = appLimits.isEmpty() && schedules.isEmpty() && focusBlocks.isEmpty() &&
        activeSessions.isEmpty()

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
                bottom = innerPadding.calculateBottomPadding() + Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            if (!hasUsageAccess) {
                item(key = "perm-usage") {
                    WarningCard(
                        title = "Focus can't see which app is open",
                        body = "Grant Usage Access, or nothing here will actually block.",
                        actionText = "Grant Usage Access",
                        onAction = {
                            container.sensitiveKeyManager.expectingActivityResult = true
                            runCatching { context.startActivity(PermissionIntents.usageAccessSettings()) }
                        },
                    )
                }
            }

            item(key = "now") {
                RightNowCard(
                    blocked = blockedNow,
                    appsByPackage = appsByPackage,
                    activeSessions = activeSessions,
                    nextSession = nextSession,
                    nowMillis = nowMillis,
                    onEndEarly = { session ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        errors.launchGuarded("Couldn't end the routine.") {
                            container.focusScheduleRepository.endSessionEarly(
                                scheduleId = session.scheduleId,
                                startMillis = session.startMillis,
                            )
                            armed = container.focusScheduleRepository.armedSchedules()
                        }
                    },
                )
            }

            item(key = "modes") {
                val onMode: (FocusMode) -> Unit = { mode ->
                    when (mode) {
                        FocusMode.LockNow -> lockPickerOpen = true
                        FocusMode.DailyLimit -> appLimitPickerOpen = true
                        FocusMode.Routine -> onEdit(FocusEditor.Schedule(null))
                    }
                }
                if (isFirstRun) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        FocusMode.entries.forEach { mode ->
                            FocusModeTile(mode = mode, expanded = true, onClick = { onMode(mode) })
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        FocusMode.entries.forEach { mode ->
                            FocusModeTile(
                                mode = mode,
                                expanded = false,
                                onClick = { onMode(mode) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            if (hasUsageAccess) {
                item(key = "suggestions") {
                    when (scan) {
                        SuggestScan.Idle -> SuggestButton(onClick = onScan)
                        SuggestScan.Scanning -> ScanningCard()
                        SuggestScan.Done -> MostUsedCard(
                            apps = suggestions,
                            weeklyUsage = weeklyUsage,
                            onRescan = onScan,
                            onLock = { quickBlockApp = it },
                            onLimit = { app ->
                                appLimitToEdit = null
                                appLimitTargetApp = app
                            },
                        )
                    }
                }
            }

            if (appLimits.isNotEmpty()) {
                item(key = "header-limits") {
                    FocusModeHeader(mode = FocusMode.DailyLimit, title = "Daily limits")
                }
                items(sortedLimits, key = { "limit-${it.packageName}" }) { limit ->
                    val app = appsByPackage[limit.packageName]
                    val usageMillis = todayUsage[limit.packageName] ?: 0L
                    val isExceeded = limit.enabled && usageMillis >= limit.dailyLimitMinutes * 60_000L
                    AppLimitRow(
                        limit = limit,
                        app = app,
                        usageMillis = usageMillis,
                        onToggle = { enabled ->
                            if (isExceeded) {
                                errors.show("Limit reached. Locked until midnight.")
                                return@AppLimitRow
                            }
                            errors.launchGuarded("Couldn't update limit.") {
                                container.focusAppLimitRepository.setEnabled(limit.packageName, enabled)
                                if (enabled) onMonitorNeeded()
                            }
                        },
                        onEdit = {
                            if (isExceeded) {
                                errors.show("Limit reached. Editing is locked until midnight.")
                                return@AppLimitRow
                            }
                            appLimitToEdit = limit
                            appLimitTargetApp = app ?: InstalledApp(limit.packageName, limit.packageName, null)
                        },
                    )
                }
            }

            if (schedules.isNotEmpty()) {
                item(key = "header-routines") {
                    FocusModeHeader(mode = FocusMode.Routine, title = "Routines")
                }
                if (!canScheduleExact) {
                    item(key = "perm-alarm") {
                        WarningCard(
                            title = "Routines may start late",
                            body = "Android can delay them by minutes without exact alarms.",
                            actionText = "Allow exact alarms",
                            onAction = {
                                container.sensitiveKeyManager.expectingActivityResult = true
                                runCatching { context.startActivity(PermissionIntents.exactAlarmSettings(context)) }
                            },
                        )
                    }
                }
                items(schedules, key = { "schedule-${it.scheduleId}" }) { schedule ->
                    RoutineCard(
                        schedule = schedule,
                        group = groupsById[schedule.groupId],
                        isLive = schedule.scheduleId in activeScheduleIds,
                        nowMillis = nowMillis,
                        onToggle = { enabled ->
                            errors.launchGuarded("Couldn't update the routine.") {
                                container.focusScheduleRepository.setEnabled(schedule.scheduleId, enabled)
                            }
                        },
                        onEdit = { onEdit(FocusEditor.Schedule(schedule)) },
                    )
                }
            }

            item(key = "header-sets") {
                FocusModeHeader(mode = null, title = "App sets")
            }
            item(key = "sets") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    groups.forEach { group ->
                        FocusSetChip(set = group, onClick = { onEdit(FocusEditor.Group(group)) })
                    }
                    FocusNewSetChip(onClick = { onEdit(FocusEditor.Group(null)) })
                }
            }
        }
    }

    if (lockPickerOpen) {
        FocusAppPickerSheet(
            title = "Lock what?",
            apps = installedApps,
            blockedPackages = lockedPackages,
            weeklyUsage = weeklyUsage,
            sets = groups,
            onSelectSet = { set -> lockPickerOpen = false; groupToBlock = set },
            onSelect = { app -> lockPickerOpen = false; quickBlockApp = app },
            onDismiss = { lockPickerOpen = false },
        )
    }

    quickBlockApp?.let { app ->
        FocusBlockSheet(
            appLabel = app.label,
            leading = { AppIconOrMonogram(icon = app.icon, label = app.label, packageName = app.packageName) },
            onConfirm = { durationMillis ->
                quickBlockApp = null
                errors.launchGuarded("Couldn't lock ${app.label}.") {
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
            appLabel = group.name,
            leading = { SetSwatch(color = focusSetColor(group.colorIndex), count = group.packageNames.size) },
            onConfirm = { durationMillis ->
                groupToBlock = null
                errors.launchGuarded("Couldn't lock ${group.name}.") {
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

    if (appLimitPickerOpen) {
        val existingLimitPackages = remember(appLimits) { appLimits.map { it.packageName }.toSet() }
        FocusAppPickerSheet(
            title = "Limit which app?",
            apps = installedApps,
            blockedPackages = existingLimitPackages,
            weeklyUsage = weeklyUsage,
            onSelect = { app ->
                appLimitPickerOpen = false
                appLimitTargetApp = app
                appLimitToEdit = null
            },
            onDismiss = { appLimitPickerOpen = false },
        )
    }

    appLimitTargetApp?.let { app ->
        FocusAppLimitSheet(
            app = app,
            existingLimit = appLimitToEdit,
            usedTodayMillis = todayUsage[app.packageName] ?: 0L,
            weekUsageMillis = weeklyUsage[app.packageName] ?: 0L,
            onSave = { dailyLimitMinutes ->
                val targetApp = app
                appLimitTargetApp = null
                appLimitToEdit = null
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                errors.launchGuarded("Couldn't save the limit for ${targetApp.label}.") {
                    container.focusAppLimitRepository.saveAppLimit(
                        packageName = targetApp.packageName,
                        dailyLimitMinutes = dailyLimitMinutes,
                    )
                    onMonitorNeeded()
                }
            },
            onDelete = if (appLimitToEdit != null) {
                {
                    val targetApp = app
                    appLimitTargetApp = null
                    appLimitToEdit = null
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    errors.launchGuarded("Couldn't remove the limit for ${targetApp.label}.") {
                        container.focusAppLimitRepository.deleteAppLimit(targetApp.packageName)
                    }
                }
            } else null,
            onDismiss = {
                appLimitTargetApp = null
                appLimitToEdit = null
            },
        )
    }
}

/**
 * The hero: every blocked app as its icon inside a ring colored by the mode that
 * blocked it, so "why can't I open this?" is answered by color. Live Normal
 * routines get their End early button here; Strict ones show a lock instead.
 * When nothing is blocked it says so, and names the next routine if there is one.
 */
@Composable
private fun RightNowCard(
    blocked: List<BlockedNow>,
    appsByPackage: Map<String, InstalledApp>,
    activeSessions: List<ArmedSchedule>,
    nextSession: ArmedSchedule?,
    nowMillis: Long,
    onEndEarly: (ArmedSchedule) -> Unit,
) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        if (blocked.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    imageVector = Icons.Rounded.SelfImprovement,
                    contentDescription = null,
                    tint = MaterialTheme.extendedColors.success,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "All apps open",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (nextSession != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = FocusMode.Routine.icon,
                            contentDescription = null,
                            tint = FocusMode.Routine.accent,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            text = "${nextSession.label} starts ${formatOpensAt(nextSession.startMillis)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.extendedColors.textMuted,
                        )
                    }
                }
            }
            return@AppCard
        }

        Text(
            text = "Right now",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.extendedColors.textMuted,
        )
        Spacer(Modifier.height(Spacing.sm))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            items(blocked, key = { it.key }) { item ->
                val app = appsByPackage[item.packageName]
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.widthIn(max = 72.dp),
                ) {
                    CountdownRing(progress = item.progress, color = item.mode.accent, size = 54.dp) {
                        AppIconOrMonogram(icon = app?.icon, label = item.label, packageName = item.packageName)
                    }
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = item.caption,
                        style = MaterialTheme.typography.labelSmall,
                        color = item.mode.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        activeSessions.forEach { session ->
            Spacer(Modifier.height(Spacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = FocusMode.Routine.icon,
                    contentDescription = null,
                    tint = FocusMode.Routine.accent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(Spacing.sm))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = session.label.ifBlank { "Routine" },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${formatFocusRemaining((session.endMillis - nowMillis).coerceAtLeast(0L))} left",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.extendedColors.textMuted,
                    )
                }
                if (session.strict) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Strict",
                        tint = MaterialTheme.extendedColors.danger,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    SecondaryButton(text = "End early", onClick = { onEndEarly(session) })
                }
            }
        }
    }
}

@Composable
private fun AppLimitRow(
    limit: FocusAppLimit,
    app: InstalledApp?,
    usageMillis: Long,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    val accent = FocusMode.DailyLimit.accent
    val limitMillis = limit.dailyLimitMinutes * 60_000L
    val isExceeded = limit.enabled && usageMillis >= limitMillis
    val progress = if (limitMillis > 0) usageMillis.toFloat() / limitMillis else 0f
    val danger = MaterialTheme.extendedColors.danger
    val muted = MaterialTheme.extendedColors.textMuted

    AppCard(modifier = Modifier.fillMaxWidth(), onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIconOrMonogram(
                icon = app?.icon,
                label = app?.label ?: limit.packageName,
                packageName = limit.packageName,
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = app?.label ?: limit.packageName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = when {
                            isExceeded -> "Locked till midnight"
                            !limit.enabled -> "Paused"
                            else -> "${FocusUsageTracker.formatUsage((limitMillis - usageMillis).coerceAtLeast(0L))} left"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            isExceeded -> danger
                            !limit.enabled -> muted
                            else -> accent
                        },
                    )
                }
                Spacer(Modifier.height(6.dp))
                FocusBudgetBar(
                    progress = if (limit.enabled) progress else 0f,
                    color = if (isExceeded) danger else accent,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${FocusUsageTracker.formatUsage(usageMillis)} of " +
                        "${FocusUsageTracker.formatUsage(limitMillis)} today",
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            AppSwitch(checked = limit.enabled, enabled = !isExceeded, onCheckedChange = onToggle)
        }
    }
}

/**
 * A routine as a glance: name and set, which weekdays (letters lit in purple),
 * and a 24h band with a tick at the current time, so "is it on now?" needs no
 * reading. A lock icon marks Strict.
 */
@Composable
private fun RoutineCard(
    schedule: FocusSchedule,
    group: FocusGroup?,
    isLive: Boolean,
    nowMillis: Long,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
) {
    val accent = FocusMode.Routine.accent
    val muted = MaterialTheme.extendedColors.textMuted
    val now = Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).toLocalTime()
    AppCard(modifier = Modifier.fillMaxWidth(), onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = schedule.label.ifBlank { group?.name ?: "Routine" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (schedule.enabled) MaterialTheme.colorScheme.onSurface else muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (schedule.strict) {
                        Spacer(Modifier.width(Spacing.xs))
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Strict",
                            tint = MaterialTheme.extendedColors.danger,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    if (isLive) {
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            text = "On now",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = accent,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(accent.asAccentContainer())
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (group != null) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(focusSetColor(group.colorIndex)),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = "${group?.name ?: "Deleted set"} · " +
                            "${FocusRecurrence.formatTime(schedule.startHour, schedule.startMinute)}–" +
                            FocusRecurrence.formatTime(schedule.endHour, schedule.endMinute),
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(Spacing.sm))
            AppSwitch(checked = schedule.enabled, onCheckedChange = onToggle)
        }
        Spacer(Modifier.height(Spacing.sm))
        DayLetters(daysMask = schedule.daysMask, accent = if (schedule.enabled) accent else muted)
        Spacer(Modifier.height(6.dp))
        DayBand(
            startMinute = schedule.startHour * 60 + schedule.startMinute,
            endMinute = schedule.endHour * 60 + schedule.endMinute,
            accent = if (schedule.enabled) accent else muted.copy(alpha = 0.4f),
            nowMinute = now.hour * 60 + now.minute,
        )
    }
}

/**
 * The week's heaviest apps that nothing covers yet, each with a bar against the
 * top one and two one-tap actions drawn as the mode icons in their own colors —
 * hourglass to Lock now, gauge to set a Daily limit — straight into that
 * app's sheet, skipping the picker.
 */
@Composable
private fun MostUsedCard(
    apps: List<InstalledApp>,
    weeklyUsage: Map<String, Long>,
    onRescan: () -> Unit,
    onLock: (InstalledApp) -> Unit,
    onLimit: (InstalledApp) -> Unit,
) {
    val top = apps.maxOfOrNull { weeklyUsage[it.packageName] ?: 0L }?.coerceAtLeast(1L) ?: 1L
    val muted = MaterialTheme.extendedColors.textMuted
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Most used this week",
                style = MaterialTheme.typography.titleSmall,
                color = muted,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRescan, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = "Check again",
                    tint = muted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (apps.isEmpty()) {
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "Nothing heavy left to block. Nice.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        apps.forEach { app ->
            val week = weeklyUsage[app.packageName] ?: 0L
            Spacer(Modifier.height(Spacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIconOrMonogram(icon = app.icon, label = app.label, packageName = app.packageName)
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = app.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            text = formatDailyAverage(week).orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = muted,
                            maxLines = 1,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    FocusBudgetBar(
                        progress = week.toFloat() / top,
                        color = muted.copy(alpha = 0.5f),
                        height = 4.dp,
                    )
                }
                Spacer(Modifier.width(Spacing.sm))
                ModeIconButton(
                    mode = FocusMode.LockNow,
                    contentDescription = "Lock ${app.label} now",
                    onClick = { onLock(app) },
                )
                Spacer(Modifier.width(Spacing.xs))
                ModeIconButton(
                    mode = FocusMode.DailyLimit,
                    contentDescription = "Set a daily limit for ${app.label}",
                    onClick = { onLimit(app) },
                )
            }
        }
    }
}

/** Entry to the on-demand scan; nothing reads a week of usage until this is tapped. */
@Composable
private fun SuggestButton(onClick: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentIconTile(
                icon = Icons.Rounded.AutoAwesome,
                accent = MaterialTheme.colorScheme.primary,
                size = 36.dp,
                iconSize = 20.dp,
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = "Suggest apps to block",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.extendedColors.textMuted,
            )
        }
    }
}

@Composable
private fun ScanningCard() {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = "Checking your last 7 days…",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** A round button showing [mode]'s icon on its tint — the mode's color is the label. */
@Composable
private fun ModeIconButton(mode: FocusMode, contentDescription: String, onClick: () -> Unit) {
    val accent = mode.accent
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(accent.asAccentContainer())
            .clickable(onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = mode.icon,
            contentDescription = contentDescription,
            tint = accent,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** The set's color as a tile with its app count, standing in for an app icon. */
@Composable
private fun SetSwatch(color: Color, count: Int) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(color.asAccentContainer()),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

/**
 * Locks every app in [group] for [durationMillis].
 *
 * Goes through the same duration + hold-to-lock sheet as a single app rather
 * than assuming a length: these blocks are irreversible, and silently committing
 * the user to an arbitrary hour across several apps at one tap would be a trap.
 *
 * An app already locked until later than this block would end is skipped —
 * `startBlock` replaces the existing entry, so locking the set for 30m would
 * otherwise cut a running 3h lock short, breaking "a lock can't be shortened".
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
    val until = System.currentTimeMillis() + durationMillis
    group.packageNames.forEach { pkg ->
        val existingUntil = container.focusRepository.focusBlockUntil(pkg)
        if (existingUntil != null && existingUntil >= until) return@forEach
        container.focusRepository.startFocusBlock(
            packageName = pkg,
            label = appsByPackage[pkg]?.label ?: pkg,
            durationMillis = durationMillis,
        )
    }
    onMonitorNeeded()
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
    allSets: List<FocusGroup>,
    weeklyUsage: Map<String, Long>,
    onDone: () -> Unit,
    onSaved: (groupId: String) -> Unit = {},
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
        otherSets = allSets.filter { it.groupId != existing?.groupId },
        weeklyUsage = weeklyUsage,
        onSave = { name, colorIndex, packages ->
            errors.launchGuarded("Couldn't save the app set.") {
                val id = container.focusGroupRepository.saveGroup(
                    groupId = existing?.groupId,
                    name = name,
                    colorIndex = colorIndex,
                    packageNames = packages,
                )
                onSaved(id)
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
    weeklyUsage: Map<String, Long>,
    onMonitorNeeded: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val errors = rememberErrorReporter()
    // Set by the editor's "New set" chip; the sheet opens over the page and hands
    // the new set's id back so the routine selects it without a second tap.
    var onSetCreated by remember { mutableStateOf<((String) -> Unit)?>(null) }

    FocusScheduleEditorPage(
        existing = existing,
        groups = groups,
        onCreateSet = { callback -> onSetCreated = callback },
        onSave = { draft ->
            errors.launchGuarded("Couldn't save the routine.") {
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

    onSetCreated?.let { callback ->
        FocusGroupEditorHost(
            container = container,
            existing = null,
            allSets = groups,
            weeklyUsage = weeklyUsage,
            onSaved = callback,
            onDone = { onSetCreated = null },
        )
    }
}
