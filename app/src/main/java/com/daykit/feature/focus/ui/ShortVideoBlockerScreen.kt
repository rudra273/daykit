package com.daykit.feature.focus.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.daykit.R
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AccentIconTile
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppIconOrMonogram
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.components.AppSwitch
import com.daykit.feature.focus.service.SHORT_VIDEO_CONSENT
import com.daykit.feature.focus.service.ShortVideoBlockerService
import com.daykit.feature.focus.service.ShortVideoTarget
import com.daykit.feature.focus.service.shortVideoPreferences

private fun Context.isShortVideoServiceEnabled(): Boolean {
    val manager = getSystemService(AccessibilityManager::class.java)
    val component = ComponentName(this, ShortVideoBlockerService::class.java)
    return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .any { ComponentName.unflattenFromString(it.id) == component }
}

@Composable
internal fun ShortVideoBlockerEntry(onClick: () -> Unit) {
    val context = LocalContext.current
    fun protectedCount(): Int {
        val preferences = context.shortVideoPreferences()
        if (!preferences.getBoolean(SHORT_VIDEO_CONSENT, false) || !context.isShortVideoServiceEnabled()) {
            return 0
        }
        return ShortVideoTarget.entries.count { preferences.getBoolean(it.packageName, false) }
    }
    var count by remember(context) { mutableStateOf(protectedCount()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { count = protectedCount() }

    AppCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            AccentIconTile(Icons.Rounded.PlayCircleOutline, MaterialTheme.colorScheme.primary, size = 40.dp)
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.short_video_blocker_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (count == 0) stringResource(R.string.short_video_entry_description)
                    else pluralStringResource(R.plurals.short_video_active_apps, count, count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ShortVideoBlockerScreen(onBack: () -> Unit, onOpeningSettings: (Boolean) -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val preferences = remember(context) { context.shortVideoPreferences() }
    var serviceEnabled by remember { mutableStateOf(context.isShortVideoServiceEnabled()) }
    var consent by remember { mutableStateOf(preferences.getBoolean(SHORT_VIDEO_CONSENT, false)) }
    var showDisclosure by remember { mutableStateOf(false) }
    var selected by remember {
        mutableStateOf(ShortVideoTarget.entries.filter { preferences.getBoolean(it.packageName, false) }.toSet())
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { serviceEnabled = context.isShortVideoServiceEnabled() }

    val ready = serviceEnabled && consent
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AppTopBar(title = stringResource(R.string.short_video_blocker_title), onBack = onBack)
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(
                start = Spacing.lg,
                end = Spacing.lg,
                top = padding.calculateTopPadding() + Spacing.sm,
                bottom = padding.calculateBottomPadding() + Spacing.xxl,
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    AccentIconTile(Icons.Rounded.PlayCircleOutline, MaterialTheme.colorScheme.primary, size = 48.dp)
                    Text(stringResource(R.string.short_video_intro_title), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        stringResource(R.string.short_video_blocker_summary),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!ready) {
                item {
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                            Text(stringResource(R.string.short_video_setup_title), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.short_video_setup_steps),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            PrimaryButton(
                                text = stringResource(
                                    if (serviceEnabled) R.string.short_video_review_access
                                    else R.string.short_video_enable_access,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                                onClick = { showDisclosure = true },
                            )
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text(
                        stringResource(if (ready) R.string.short_video_choose_apps else R.string.short_video_supported_apps),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    AppCard(modifier = Modifier.fillMaxWidth()) {
                        ShortVideoTarget.entries.forEachIndexed { index, target ->
                            val label = stringResource(target.label)
                            val icon = remember(context, target) {
                                runCatching { context.packageManager.getApplicationIcon(target.packageName) }.getOrNull()
                            }
                            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.sm),
                                horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIconOrMonogram(icon, label, target.packageName)
                                Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                if (ready) {
                                    AppSwitch(
                                        checked = target in selected,
                                        onCheckedChange = { checked ->
                                            preferences.edit().putBoolean(target.packageName, checked).apply()
                                            selected = if (checked) selected + target else selected - target
                                        },
                                        modifier = Modifier.semantics { contentDescription = label },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text(stringResource(R.string.short_video_disclosure_title)) },
            text = { Text(stringResource(R.string.short_video_service_description)) },
            confirmButton = {
                TextButton(onClick = {
                    preferences.edit().putBoolean(SHORT_VIDEO_CONSENT, true).apply()
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
                    Text(stringResource(
                        if (serviceEnabled) R.string.short_video_agree else R.string.short_video_disclosure_accept,
                    ))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisclosure = false }) {
                    Text(stringResource(R.string.short_video_disclosure_cancel))
                }
            },
        )
    }
}
