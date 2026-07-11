package com.daykit.feature.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.HapticStore
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.ThemeMode
import com.daykit.core.designsystem.ThemeModeStore
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppListRow
import com.daykit.core.designsystem.components.AppRadioButton
import com.daykit.core.designsystem.components.AppSwitch
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.RowDivider
import com.daykit.core.designsystem.components.SectionHeader
import com.daykit.core.designsystem.extendedColors

@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val mode by ThemeModeStore.rememberThemeMode()
    val hapticsEnabled by HapticStore.rememberHapticsEnabled()
    val accents = MaterialTheme.extendedColors.accents

    val options = listOf(
        Triple(ThemeMode.SYSTEM, "System default", Icons.Rounded.BrightnessAuto to accents.blue),
        Triple(ThemeMode.LIGHT, "Light", Icons.Rounded.LightMode to accents.orange),
        Triple(ThemeMode.DARK, "Dark", Icons.Rounded.DarkMode to accents.indigo),
    )

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "Appearance", onBack = onBack)
        Column(
            modifier = Modifier.padding(
                start = Spacing.lg,
                end = Spacing.lg,
                top = Spacing.sm,
            ),
        ) {
            SectionHeader("Theme")
            AppCard(contentPadding = PaddingValues(0.dp)) {
                options.forEachIndexed { index, (value, label, iconAccent) ->
                    AppListRow(
                        headline = label,
                        leadingIcon = iconAccent.first,
                        leadingAccent = iconAccent.second,
                        onClick = { ThemeModeStore.set(context, value) },
                        trailing = {
                            AppRadioButton(
                                selected = mode == value,
                                onClick = { ThemeModeStore.set(context, value) },
                            )
                        },
                    )
                    if (index < options.lastIndex) RowDivider(startIndent = Spacing.lg)
                }
            }

            SectionHeader("Feedback")
            AppCard(contentPadding = PaddingValues(0.dp)) {
                AppListRow(
                    headline = "Haptic feedback",
                    leadingIcon = Icons.Rounded.Vibration,
                    leadingAccent = accents.green,
                    onClick = { HapticStore.set(context, !hapticsEnabled) },
                    trailing = {
                        AppSwitch(
                            checked = hapticsEnabled,
                            onCheckedChange = { HapticStore.set(context, it) },
                        )
                    },
                )
            }
        }
    }
}
