package com.daykit.feature.settings.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.ThemeMode
import com.daykit.core.designsystem.ThemeModeStore
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppListRow
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.RowDivider

@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val mode by ThemeModeStore.rememberThemeMode()

    val options = listOf(
        ThemeMode.SYSTEM to "System default",
        ThemeMode.LIGHT to "Light",
        ThemeMode.DARK to "Dark",
    )

    Column(Modifier.fillMaxSize()) {
        AppTopBar(title = "Appearance", onBack = onBack)
        AppCard(modifier = Modifier.padding(Spacing.lg)) {
            options.forEachIndexed { index, (value, label) ->
                AppListRow(
                    headline = label,
                    onClick = { ThemeModeStore.set(context, value) },
                    trailing = {
                        RadioButton(
                            selected = mode == value,
                            onClick = { ThemeModeStore.set(context, value) },
                            colors = androidx.compose.material3.RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary,
                            ),
                        )
                    },
                )
                if (index < options.lastIndex) RowDivider()
            }
        }
    }
}
