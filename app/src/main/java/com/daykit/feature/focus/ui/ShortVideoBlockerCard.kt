package com.daykit.feature.focus.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.daykit.R
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppSwitch
import com.daykit.feature.focus.service.SHORT_VIDEO_CONSENT
import com.daykit.feature.focus.service.ShortVideoBlockerService
import com.daykit.feature.focus.service.ShortVideoTarget
import com.daykit.feature.focus.service.shortVideoPreferences

@Composable
internal fun ShortVideoBlockerCard() {
    val context = LocalContext.current
    val preferences = remember(context) { context.shortVideoPreferences() }
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    val component = remember(context) { ComponentName(context, ShortVideoBlockerService::class.java) }
    fun isServiceEnabled(): Boolean =
        manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { ComponentName.unflattenFromString(it.id) == component }

    var serviceEnabled by remember { mutableStateOf(isServiceEnabled()) }
    var consent by remember { mutableStateOf(preferences.getBoolean(SHORT_VIDEO_CONSENT, false)) }
    var showDisclosure by remember { mutableStateOf(false) }
    var selected by remember {
        mutableStateOf(ShortVideoTarget.entries.filter { preferences.getBoolean(it.packageName, false) }.toSet())
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { serviceEnabled = isServiceEnabled() }

    AppCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Text(stringResource(R.string.short_video_blocker_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.short_video_blocker_summary),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!serviceEnabled || !consent) {
                TextButton(onClick = { showDisclosure = true }) {
                    Text(stringResource(
                        if (serviceEnabled) R.string.short_video_review_access else R.string.short_video_enable_access,
                    ))
                }
            }
            ShortVideoTarget.entries.forEach { target ->
                val label = stringResource(target.label)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label)
                    AppSwitch(
                        checked = target in selected,
                        enabled = (serviceEnabled && consent) || target in selected,
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
                        runCatching { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                            .onFailure {
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
