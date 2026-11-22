package com.daykit.feature.focus.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.daykit.R
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.asAccentContainer
import com.daykit.core.designsystem.components.AccentIconTile
import com.daykit.core.designsystem.components.AppBottomSheet
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppIconOrMonogram
import com.daykit.core.designsystem.components.FilterChipButton
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.components.SegmentedControl
import com.daykit.core.designsystem.components.StatTile
import com.daykit.core.designsystem.extendedColors
import com.daykit.core.designsystem.tabularNums
import com.daykit.feature.focus.data.FocusUsageTracker
import com.daykit.feature.focus.data.ShortVideoMode
import com.daykit.feature.focus.data.ShortVideoRule
import com.daykit.feature.focus.data.ShortVideoStore
import com.daykit.feature.focus.service.SHORT_VIDEO_CONSENT
import com.daykit.feature.focus.service.ShortVideoBlockerService
import com.daykit.feature.focus.service.ShortVideoTarget
import com.daykit.feature.focus.service.shortVideoPreferences

internal fun Context.isShortVideoServiceEnabled(): Boolean {
    val manager = getSystemService(AccessibilityManager::class.java) ?: return false
    val component = ComponentName(this, ShortVideoBlockerService::class.java)
    return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { ComponentName.unflattenFromString(it.id) == component }
}

private val SHORT_VIDEO_PRESETS = listOf(5, 10, 15, 20, 30)

/**
 * State holder for the Short Video Blocker tab inside Focus. Observes
 * `focus_short_video` SharedPreferences reactively so live watch time and
 * block counts update immediately when returning from a social app.
 */
internal class ShortVideoUiState(
    val serviceEnabled: Boolean,
    val consent: Boolean,
    val rules: List<ShortVideoRule>,
    val onOpenDisclosure: () -> Unit,
    val onSelectTarget: (ShortVideoRule) -> Unit,
) {
    val ready: Boolean get() = serviceEnabled && consent
    val activeCount: Int get() = if (ready) rules.count { it.isActive } else 0
    val totalWatchedMillis: Long get() = rules.sumOf { it.usedTodayMillis }
    val totalBlocksToday: Int get() = rules.sumOf { it.blocksToday }
}

@Composable
internal fun rememberShortVideoUiState(
    onOpeningSettings: (Boolean) -> Unit,
    onLockedMessage: (String) -> Unit,
): ShortVideoUiState {
    val context = LocalContext.current
    val preferences = remember(context) { context.shortVideoPreferences() }
    val store = remember(preferences) { ShortVideoStore(preferences) }

    var serviceEnabled by remember { mutableStateOf(context.isShortVideoServiceEnabled()) }
    var consent by remember { mutableStateOf(store.hasConsent) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var showDisclosure by remember { mutableStateOf(false) }
    var editingTarget by remember { mutableStateOf<ShortVideoTarget?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        serviceEnabled = context.isShortVideoServiceEnabled()
        consent = store.hasConsent
        refreshTick++
    }

    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            consent = store.hasConsent
            refreshTick++
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val rules = remember(refreshTick, consent, serviceEnabled) {
        store.getAllRules()
    }

    editingTarget?.let { target ->
        val currentRule = rules.firstOrNull { it.target == target } ?: store.getRule(target)
        ShortVideoRuleSheet(
            rule = currentRule,
            onSave = { mode, minutes ->
                store.saveRule(target, mode, minutes)
                refreshTick++
                editingTarget = null
            },
            onDismiss = { editingTarget = null },
        )
    }

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text(stringResource(R.string.short_video_disclosure_title)) },
            text = { Text(stringResource(R.string.short_video_service_description)) },
            confirmButton = {
                TextButton(onClick = {
                    store.hasConsent = true
                    consent = true
                    showDisclosure = false
                    if (!serviceEnabled) {
                        onOpeningSettings(true)
                        runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                            .onFailure {
                                onOpeningSettings(false)
                                Toast.makeText(context, R.string.short_video_settings_error, Toast.LENGTH_SHORT).show()
                            }
                    }
                }) {
                    Text(
                        stringResource(
                            if (serviceEnabled) R.string.short_video_agree else R.string.short_video_disclosure_accept,
                        ),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosure = false }) {
                    Text(stringResource(R.string.short_video_disclosure_cancel))
                }
            },
        )
    }

    return ShortVideoUiState(
        serviceEnabled = serviceEnabled,
        consent = consent,
        rules = rules,
        onOpenDisclosure = { showDisclosure = true },
        onSelectTarget = { rule ->
            if (!serviceEnabled || !consent) {
                showDisclosure = true
            } else if (rule.isLimitReached) {
                onLockedMessage("Limit reached. Locked until midnight.")
            } else {
                editingTarget = rule.target
            }
        },
    )
}

/**
 * Populates the "Shorts" tab inside FocusScreen's LazyColumn.
 */
internal fun LazyListScope.shortVideoTabItems(
    state: ShortVideoUiState,
) {
    item(key = "shorts-hero") {
        ShortVideoHeroCard(state = state)
    }

    if (!state.ready) {
        item(key = "shorts-setup") {
            ShortVideoSetupCard(
                serviceEnabled = state.serviceEnabled,
                onEnableClick = state.onOpenDisclosure,
            )
        }
    }

    item(key = "shorts-header") {
        FocusModeHeader(
            mode = null,
            title = stringResource(if (state.ready) R.string.short_video_choose_apps else R.string.short_video_supported_apps),
        )
    }

    item(key = "shorts-apps") {
        ShortVideoAppsCard(
            rules = state.rules,
            ready = state.ready,
            onSelectRule = state.onSelectTarget,
        )
    }
}

@Composable
private fun ShortVideoHeroCard(state: ShortVideoUiState) {
    val pink = MaterialTheme.extendedColors.accents.pink
    val teal = FocusMode.DailyLimit.accent
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            AccentIconTile(
                icon = Icons.Rounded.SlowMotionVideo,
                accent = pink,
                size = 44.dp,
                iconSize = 24.dp,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.short_video_blocker_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = when {
                        !state.ready -> "Enable Accessibility shield to block or cap Shorts & Reels"
                        state.activeCount == 0 -> stringResource(R.string.short_video_blocker_summary)
                        else -> pluralStringResource(R.plurals.short_video_active_apps, state.activeCount, state.activeCount)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.textMuted,
                )
            }
        }
        Spacer(Modifier.height(Spacing.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            StatTile(
                label = "Watched today",
                value = FocusUsageTracker.formatUsage(state.totalWatchedMillis),
                icon = Icons.Rounded.Timer,
                accent = teal,
                modifier = Modifier.weight(1f),
            )
            StatTile(
                label = "Redirects today",
                value = state.totalBlocksToday.toString(),
                icon = Icons.Rounded.Shield,
                accent = pink,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ShortVideoSetupCard(
    serviceEnabled: Boolean,
    onEnableClick: () -> Unit,
) {
    val accent = MaterialTheme.extendedColors.accents.pink
    AppCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.short_video_setup_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.short_video_setup_steps),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.extendedColors.textMuted,
        )
        Spacer(Modifier.height(Spacing.md))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.extendedColors.inputField, MaterialTheme.shapes.small)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SetupStepChip(number = 1, text = "Allow in Accessibility", accent = accent, modifier = Modifier.weight(1f))
            SetupStepChip(number = 2, text = "Set limit or block", accent = accent, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(Spacing.md))
        PrimaryButton(
            text = stringResource(
                if (serviceEnabled) R.string.short_video_review_access
                else R.string.short_video_enable_access,
            ),
            modifier = Modifier.fillMaxWidth(),
            onClick = onEnableClick,
        )
    }
}

@Composable
private fun SetupStepChip(
    number: Int,
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(accent.asAccentContainer(), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$number",
                style = MaterialTheme.typography.labelSmall.tabularNums,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ShortVideoAppsCard(
    rules: List<ShortVideoRule>,
    ready: Boolean,
    onSelectRule: (ShortVideoRule) -> Unit,
) {
    val context = LocalContext.current
    val pink = MaterialTheme.extendedColors.accents.pink
    val teal = FocusMode.DailyLimit.accent
    val danger = MaterialTheme.extendedColors.danger
    val muted = MaterialTheme.extendedColors.textMuted

    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
    ) {
        rules.forEachIndexed { index, rule ->
            val target = rule.target
            val label = stringResource(target.label)
            val icon = remember(context, target) {
                runCatching { context.packageManager.getApplicationIcon(target.packageName) }.getOrNull()
            }
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 52.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.extendedColors.divider,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectRule(rule) }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIconOrMonogram(icon, label, target.packageName)
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    val subtitle = when {
                        !ready -> "Setup required"
                        rule.mode == ShortVideoMode.Off -> "Allowed · No restrictions"
                        rule.mode == ShortVideoMode.BlockAll -> "Always blocked · Regular app stays open"
                        rule.isLimitReached -> "Locked till midnight · ${rule.dailyLimitMinutes}m limit reached"
                        else -> "${FocusUsageTracker.formatUsage(rule.usedTodayMillis)} of ${rule.dailyLimitMinutes}m today"
                    }
                    val subtitleColor = when {
                        !ready -> muted
                        rule.isLimitReached -> danger
                        rule.mode == ShortVideoMode.BlockAll -> pink
                        rule.mode == ShortVideoMode.DailyLimit -> teal
                        else -> muted
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall.tabularNums,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (ready && rule.mode == ShortVideoMode.DailyLimit) {
                        Spacer(Modifier.height(6.dp))
                        FocusBudgetBar(
                            progress = rule.progress,
                            color = if (rule.isLimitReached) danger else teal,
                            height = 5.dp,
                        )
                    }
                }
                if (ready && rule.isLimitReached) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(danger.asAccentContainer()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Locked until midnight",
                            tint = danger,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                } else {
                    val badgeText = when {
                        !ready -> "Set up"
                        rule.mode == ShortVideoMode.Off -> "Off"
                        rule.mode == ShortVideoMode.BlockAll -> "Blocked"
                        else -> "${rule.dailyLimitMinutes}m"
                    }
                    val badgeAccent: Color? = when {
                        !ready || rule.mode == ShortVideoMode.Off -> null
                        rule.mode == ShortVideoMode.BlockAll -> pink
                        else -> teal
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (badgeAccent != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(badgeAccent.asAccentContainer())
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = badgeText,
                                    style = MaterialTheme.typography.labelMedium.tabularNums,
                                    fontWeight = FontWeight.SemiBold,
                                    color = badgeAccent,
                                )
                            }
                        } else {
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = muted,
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = muted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ShortVideoRuleSheet(
    rule: ShortVideoRule,
    onSave: (mode: ShortVideoMode, dailyLimitMinutes: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val target = rule.target
    val label = stringResource(target.label)
    val icon = remember(context, target) {
        runCatching { context.packageManager.getApplicationIcon(target.packageName) }.getOrNull()
    }

    var selectedMode by remember(rule) { mutableStateOf(rule.mode) }
    val initialPreset = SHORT_VIDEO_PRESETS.firstOrNull { it == rule.dailyLimitMinutes }
    var selectedPreset by remember(rule) { mutableIntStateOf(initialPreset ?: 15) }
    var customOpen by remember(rule) { mutableStateOf(initialPreset == null) }
    var customMinutes by remember(rule) { mutableIntStateOf(rule.dailyLimitMinutes) }

    val totalMinutes = (if (customOpen) customMinutes else selectedPreset).coerceIn(1, ShortVideoStore.MAX_LIMIT_MINUTES)
    val limitMillis = totalMinutes * 60_000L
    val leftMillis = (limitMillis - rule.usedTodayMillis).coerceAtLeast(0L)
    val exhausted = leftMillis == 0L

    val pink = MaterialTheme.extendedColors.accents.pink
    val teal = FocusMode.DailyLimit.accent
    val danger = MaterialTheme.extendedColors.danger
    val activeAccent = when (selectedMode) {
        ShortVideoMode.BlockAll -> pink
        ShortVideoMode.DailyLimit -> teal
        ShortVideoMode.Off -> MaterialTheme.colorScheme.primary
    }

    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.lg, vertical = Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                AppIconOrMonogram(icon = icon, label = label, packageName = target.packageName)
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Regular app stays open",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.extendedColors.textMuted,
                    )
                }
            }

            Spacer(Modifier.height(Spacing.md))
            val modes = ShortVideoMode.entries
            SegmentedControl(
                items = modes.map { it.label },
                selectedIndex = modes.indexOf(selectedMode).coerceAtLeast(0),
                onSelect = { selectedMode = modes[it] },
            )

            Spacer(Modifier.height(Spacing.lg))

            when (selectedMode) {
                ShortVideoMode.DailyLimit -> {
                    Text(
                        text = FocusUsageTracker.formatUsage(limitMillis),
                        style = MaterialTheme.typography.displaySmall.tabularNums,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "of short videos a day",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.extendedColors.textMuted,
                    )

                    Spacer(Modifier.height(Spacing.md))
                    FocusBudgetBar(
                        progress = if (limitMillis > 0) rule.usedTodayMillis.toFloat() / limitMillis else 0f,
                        color = if (exhausted) danger else teal,
                        height = 10.dp,
                    )
                    Spacer(Modifier.height(Spacing.xs))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${FocusUsageTracker.formatUsage(rule.usedTodayMillis)} watched today",
                            style = MaterialTheme.typography.bodySmall.tabularNums,
                            color = MaterialTheme.extendedColors.textMuted,
                        )
                        Text(
                            text = if (exhausted) "Locks now" else "${FocusUsageTracker.formatUsage(leftMillis)} left",
                            style = MaterialTheme.typography.bodySmall.tabularNums,
                            fontWeight = FontWeight.SemiBold,
                            color = if (exhausted) danger else teal,
                        )
                    }

                    Spacer(Modifier.height(Spacing.lg))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        SHORT_VIDEO_PRESETS.forEach { preset ->
                            FilterChipButton(
                                text = "${preset}m",
                                selected = !customOpen && selectedPreset == preset,
                                onClick = {
                                    selectedPreset = preset
                                    customOpen = false
                                },
                            )
                        }
                        FilterChipButton(
                            text = "Custom",
                            selected = customOpen,
                            onClick = {
                                if (!customOpen) customMinutes = selectedPreset
                                customOpen = true
                            },
                        )
                    }

                    if (customOpen) {
                        Spacer(Modifier.height(Spacing.md))
                        FocusDurationStepper(
                            minutes = customMinutes,
                            onMinutesChange = { customMinutes = it },
                            maxMinutes = ShortVideoStore.MAX_LIMIT_MINUTES,
                        )
                    }

                    Spacer(Modifier.height(Spacing.lg))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Bedtime,
                            contentDescription = null,
                            tint = MaterialTheme.extendedColors.textMuted,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text(
                            text = "Refills at midnight · Locked once reached",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.extendedColors.textMuted,
                        )
                    }
                }

                ShortVideoMode.BlockAll -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .background(pink.asAccentContainer())
                            .padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Block,
                            contentDescription = null,
                            tint = pink,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = "Exits $label immediately whenever opened. Feeds, search, and messages stay available.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                ShortVideoMode.Off -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .background(MaterialTheme.extendedColors.inputField)
                            .padding(Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayCircleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.extendedColors.textMuted,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            text = "$label can be watched without restrictions.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.extendedColors.textMuted,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            FocusAccentButton(
                text = "Save rule",
                accent = activeAccent,
                onClick = { onSave(selectedMode, totalMinutes) },
            )
        }
    }
}
