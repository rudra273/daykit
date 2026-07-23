package com.daykit.feature.eventlight.ui

import android.content.ActivityNotFoundException
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppListRow
import com.daykit.core.designsystem.components.AppSlider
import com.daykit.core.designsystem.components.AppSwitch
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.components.RowDivider
import com.daykit.core.designsystem.components.SectionHeader
import com.daykit.core.designsystem.extendedColors
import com.daykit.core.permissions.PermissionIntents
import com.daykit.feature.eventlight.data.EventLightStore
import com.daykit.feature.eventlight.service.EventLightService
import kotlin.math.roundToInt

private val BorderColorOptions = listOf(
    "White" to 0xFFFFFFFF.toInt(),
    "Warm white" to 0xFFFFE4B5.toInt(),
    "Cool white" to 0xFFE0F0FF.toInt(),
    "Blue" to 0xFF1877F2.toInt(),
    "Green" to 0xFF31A24C.toInt(),
    "Pink" to 0xFFD6249F.toInt(),
)

@Composable
fun EventLightScreen(onBack: () -> Unit) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val settings by EventLightStore.rememberSettings()
    var overlayGranted by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    fun refreshPermission() {
        overlayGranted = Settings.canDrawOverlays(context)
    }

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "Event Light", onBack = onBack)
        Column(
            modifier = Modifier
                .padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.sm),
        ) {
            if (!overlayGranted) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.VisibilityOff,
                            contentDescription = null,
                            tint = MaterialTheme.extendedColors.textMuted,
                        )
                        Spacer(Modifier.width(Spacing.md))
                        Column {
                            Text(
                                text = "Display over other apps",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "Needed so the border can show on top of a video call app.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.extendedColors.textMuted,
                            )
                        }
                    }
                    Spacer(Modifier.height(Spacing.md))
                    PrimaryButton(
                        text = "Grant permission",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            try {
                                context.startActivity(PermissionIntents.overlaySettings(context))
                            } catch (_: ActivityNotFoundException) {
                                refreshPermission()
                            }
                        },
                    )
                }
                Spacer(Modifier.height(Spacing.md))
            }

            SectionHeader("Event Light")
            AppCard(contentPadding = PaddingValues(0.dp)) {
                AppListRow(
                    headline = "Show border",
                    supporting = "Lights your face like a ring light during video calls.",
                    enabled = overlayGranted,
                    trailing = {
                        AppSwitch(
                            checked = settings.enabled,
                            enabled = overlayGranted,
                            onCheckedChange = { enabled ->
                                refreshPermission()
                                if (enabled && !overlayGranted) return@AppSwitch
                                EventLightStore.setEnabled(context, enabled)
                                if (enabled) {
                                    EventLightService.start(context)
                                } else {
                                    EventLightService.stop(context)
                                }
                            },
                        )
                    },
                )
            }

            SectionHeader("Border color")
            AppCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    BorderColorOptions.forEach { (label, argb) ->
                        ColorSwatch(
                            label = label,
                            argb = argb,
                            selected = settings.colorArgb == argb,
                            onClick = { EventLightStore.setColor(context, argb) },
                        )
                    }
                }
            }

            SectionHeader("Border thickness")
            AppCard {
                AppSlider(
                    value = settings.thicknessDp,
                    valueRange = 8f..64f,
                    onValueChange = { EventLightStore.setThickness(context, it) },
                )
                Text(
                    text = "${settings.thicknessDp.roundToInt()} dp",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.extendedColors.textMuted,
                )
            }

            SectionHeader("Brightness")
            AppCard {
                AppSlider(
                    value = settings.brightness,
                    valueRange = 0.2f..1f,
                    onValueChange = { EventLightStore.setBrightness(context, it) },
                )
            }

            SectionHeader("Transparency")
            AppCard {
                // The slider reads as "how transparent" (max = fully see-through), so it's
                // the inverse of the stored opacity (max = fully opaque).
                AppSlider(
                    value = 1f - settings.opacity,
                    valueRange = 0f..1f,
                    onValueChange = { EventLightStore.setOpacity(context, 1f - it) },
                )
                Text(
                    text = "${((1f - settings.opacity) * 100).roundToInt()}% transparent",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.extendedColors.textMuted,
                )
            }

            SectionHeader("Edges")
            AppCard(contentPadding = PaddingValues(0.dp)) {
                val edges = listOf(
                    Triple("Top", settings.topEnabled) { v: Boolean -> EventLightStore.setTopEnabled(context, v) },
                    Triple("Bottom", settings.bottomEnabled) { v: Boolean -> EventLightStore.setBottomEnabled(context, v) },
                    Triple("Left", settings.leftEnabled) { v: Boolean -> EventLightStore.setLeftEnabled(context, v) },
                    Triple("Right", settings.rightEnabled) { v: Boolean -> EventLightStore.setRightEnabled(context, v) },
                )
                edges.forEachIndexed { index, (label, checked, setter) ->
                    AppListRow(
                        headline = label,
                        trailing = {
                            AppSwitch(checked = checked, onCheckedChange = setter)
                        },
                    )
                    if (index < edges.lastIndex) RowDivider(startIndent = Spacing.lg)
                }
            }
            Spacer(Modifier.height(Spacing.xl))
        }
    }
}

@Composable
private fun ColorSwatch(
    label: String,
    argb: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(argb))
                .border(1.dp, MaterialTheme.extendedColors.divider, CircleShape)
                .clickable(onClick = onClick),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = label,
                    tint = if (isColorLight(argb)) Color.Black else Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private fun isColorLight(argb: Int): Boolean {
    val color = Color(argb)
    val luminance = 0.299f * color.red + 0.587f * color.green + 0.114f * color.blue
    return luminance > 0.6f
}
