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
import com.daykit.core.data.CustomNavTab
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
import com.daykit.navigation.activeIcon
import java.time.format.TextStyle
import java.util.Locale

private enum class GeneralPicker { Currency, WeekStart, TimeFormat, StartTab, CustomNavTab }

@Composable
fun GeneralSettingsScreen(onBack: () -> Unit) {
    val accents = MaterialTheme.extendedColors.accents
    var picker by remember { mutableStateOf<GeneralPicker?>(null) }

    val currency by AppPreferences.rememberPreference(AppPreferences.KEY_CURRENCY) { AppPreferences.currencyCode }
    val weekStart by AppPreferences.rememberPreference(AppPreferences.KEY_WEEK_START) { AppPreferences.weekStart }
    val timeFormat by AppPreferences.rememberPreference(AppPreferences.KEY_TIME_FORMAT) { AppPreferences.timeFormat }
    val startTab by AppPreferences.rememberPreference(AppPreferences.KEY_START_TAB) { AppPreferences.startTab }
    val customNavTab by AppPreferences.rememberPreference(AppPreferences.KEY_CUSTOM_NAV_TAB) { AppPreferences.customNavTab }

    SettingsSubPage(title = "General", onBack = onBack) {
        item { SectionHeader("Region", topPadding = 0.dp) }
        item {
            AppCard(contentPadding = PaddingValues(0.dp)) {
                AppListRow(
                    headline = "Currency",
                    trailingValue = currency,
                    leadingIcon = Icons.Rounded.Payments,
                    leadingAccent = accents.pink,
                    trailing = { NavChevron() },
                    onClick = { picker = GeneralPicker.Currency },
                )
                RowDivider()
                AppListRow(
                    headline = "First Day of Week",
                    trailingValue = weekStartLabel(weekStart),
                    leadingIcon = Icons.Rounded.CalendarViewWeek,
                    leadingAccent = accents.green,
                    trailing = { NavChevron() },
                    onClick = { picker = GeneralPicker.WeekStart },
                )
                RowDivider()
                AppListRow(
                    headline = "Time Format",
                    trailingValue = timeFormatLabel(timeFormat),
                    leadingIcon = Icons.Rounded.Schedule,
                    leadingAccent = accents.blue,
                    trailing = { NavChevron() },
                    onClick = { picker = GeneralPicker.TimeFormat },
                )
            }
        }
        item { SectionHeader("Navigation") }
        item {
            AppCard(contentPadding = PaddingValues(0.dp)) {
                AppListRow(
                    headline = "Navigation Bar Tab",
                    trailingValue = customNavTab.label,
                    leadingIcon = customNavTab.activeIcon(),
                    leadingAccent = accents.red,
                    trailing = { NavChevron() },
                    onClick = { picker = GeneralPicker.CustomNavTab },
                )
                RowDivider()
                AppListRow(
                    headline = "Open On",
                    trailingValue = startTabLabel(startTab, customNavTab),
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
            label = { startTabLabel(it, customNavTab) },
            onDismiss = { picker = null },
            onSelect = {
                AppPreferences.startTab = it
                picker = null
            },
        )
        GeneralPicker.CustomNavTab -> OptionSheet(
            title = "Navigation Bar Tab",
            description = "Choose which tool appears in the bottom navigation bar.",
            options = CustomNavTab.entries,
            selected = customNavTab,
            label = { it.label },
            onDismiss = { picker = null },
            onSelect = {
                AppPreferences.customNavTab = it
                picker = null
            },
        )
        null -> Unit
    }
}

private fun startTabLabel(startTab: StartTab, customNavTab: CustomNavTab): String = when (startTab) {
    StartTab.Custom -> customNavTab.label
    else -> startTab.label
}

private fun weekStartLabel(weekStart: WeekStart): String = when (weekStart) {
    WeekStart.System -> {
        val day = AppPreferences.systemFirstDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault())
        "System ($day)"
    }
    else -> weekStart.label
}

private fun timeFormatLabel(format: TimeFormatPreference): String = when (format) {
    TimeFormatPreference.System -> if (AppPreferences.systemIs24Hour()) "System (24h)" else "System (12h)"
    TimeFormatPreference.Hour12 -> "12-hour"
    TimeFormatPreference.Hour24 -> "24-hour"
}
