@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.daykit.feature.settings.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import com.daykit.core.data.AppPreferences
import com.daykit.core.data.StartTab
import com.daykit.core.data.TimeFormatPreference
import com.daykit.core.data.WeekStart
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppListRow
import com.daykit.core.designsystem.components.RowDivider
import com.daykit.core.designsystem.components.SectionHeader
import com.daykit.core.designsystem.extendedColors
import com.daykit.core.util.Money
import com.daykit.core.util.TimeFormat
import java.time.format.TextStyle
import java.util.Locale

private enum class GeneralPicker { Currency, WeekStart, TimeFormat, StartTab }

@Composable
fun GeneralSettingsScreen(onBack: () -> Unit) {
    val accents = MaterialTheme.extendedColors.accents
    var picker by remember { mutableStateOf<GeneralPicker?>(null) }

    val currency by AppPreferences.rememberPreference(AppPreferences.KEY_CURRENCY) { AppPreferences.currencyCode }
    val weekStart by AppPreferences.rememberPreference(AppPreferences.KEY_WEEK_START) { AppPreferences.weekStart }
    val timeFormat by AppPreferences.rememberPreference(AppPreferences.KEY_TIME_FORMAT) { AppPreferences.timeFormat }
    val startTab by AppPreferences.rememberPreference(AppPreferences.KEY_START_TAB) { AppPreferences.startTab }

    SettingsSubPage(title = "General", onBack = onBack) {
        item { SectionHeader("Region", topPadding = 0.dp) }
        item {
            AppCard(contentPadding = PaddingValues(0.dp)) {
                AppListRow(
                    headline = "Currency",
                    supporting = Money.displayName(currency),
                    leadingIcon = Icons.Rounded.Payments,
                    leadingAccent = accents.pink,
                    trailing = { NavChevron() },
                    onClick = { picker = GeneralPicker.Currency },
                )
                RowDivider(startIndent = Spacing.lg)
                AppListRow(
                    headline = "First Day of Week",
                    supporting = weekStartLabel(weekStart),
                    leadingIcon = Icons.Rounded.CalendarViewWeek,
                    leadingAccent = accents.green,
                    trailing = { NavChevron() },
                    onClick = { picker = GeneralPicker.WeekStart },
                )
                RowDivider(startIndent = Spacing.lg)
                AppListRow(
                    headline = "Time Format",
                    supporting = timeFormatLabel(timeFormat),
                    leadingIcon = Icons.Rounded.Schedule,
                    leadingAccent = accents.blue,
                    trailing = { NavChevron() },
                    onClick = { picker = GeneralPicker.TimeFormat },
                )
            }
        }
        item {
            SettingsFootnote(
                "Changing the currency only changes the symbol shown; amounts you already entered are not converted.",
            )
        }
        item { SectionHeader("Startup") }
        item {
            AppCard(contentPadding = PaddingValues(0.dp)) {
                AppListRow(
                    headline = "Open On",
                    supporting = startTab.label,
                    leadingIcon = Icons.Rounded.SpaceDashboard,
                    leadingAccent = accents.orange,
                    trailing = { NavChevron() },
                    onClick = { picker = GeneralPicker.StartTab },
                )
            }
        }
    }

    when (picker) {
        GeneralPicker.Currency -> OptionSheet(
            title = "Currency",
            description = "Used by Expenses and the Today summary.",
            options = (listOf(currency) + Money.COMMON_CURRENCIES).distinct(),
            selected = currency,
            label = Money::displayName,
            onDismiss = { picker = null },
            onSelect = {
                AppPreferences.currencyCode = it
                picker = null
            },
        )
        GeneralPicker.WeekStart -> OptionSheet(
            title = "First Day of Week",
            description = "Used by Habits, Expenses and the mood calendar.",
            options = WeekStart.entries,
            selected = weekStart,
            label = ::weekStartLabel,
            onDismiss = { picker = null },
            onSelect = {
                AppPreferences.weekStart = it
                picker = null
            },
        )
        GeneralPicker.TimeFormat -> OptionSheet(
            title = "Time Format",
            description = null,
            options = TimeFormatPreference.entries,
            selected = timeFormat,
            label = ::timeFormatLabel,
            onDismiss = { picker = null },
            onSelect = {
                AppPreferences.timeFormat = it
                picker = null
            },
        )
        GeneralPicker.StartTab -> OptionSheet(
            title = "Open On",
            description = "The tab DayKit shows when you open it.",
            options = StartTab.entries,
            selected = startTab,
            label = { it.label },
            onDismiss = { picker = null },
            onSelect = {
                AppPreferences.startTab = it
                picker = null
            },
        )
        null -> Unit
    }
}

private fun weekStartLabel(weekStart: WeekStart): String = when (weekStart) {
    WeekStart.System -> {
        val day = AppPreferences.systemFirstDayOfWeek().getDisplayName(TextStyle.FULL, Locale.getDefault())
        "${weekStart.label} ($day)"
    }
    else -> weekStart.label
}

private fun timeFormatLabel(format: TimeFormatPreference): String = when (format) {
    TimeFormatPreference.System -> "${format.label} (${TimeFormat.format(13, 30, AppPreferences.systemIs24Hour())})"
    else -> format.label
}
