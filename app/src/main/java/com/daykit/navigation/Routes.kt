package com.daykit.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.ui.graphics.vector.ImageVector

/** Route constants for the NavHost. All destinations are parameter-less. */
object Routes {
    const val HOME = "home"
    const val TODAY = "today"
    const val SETTINGS = "settings"

    const val TOOL_APPLOCK = "tool/applock"
    const val TOOL_KEYSTORE = "tool/keystore"
    const val TOOL_NOTES = "tool/notes"
    const val TOOL_FILEVAULT = "tool/filevault"
    const val TOOL_HABITS = "tool/habits"
    const val TOOL_REMINDERS = "tool/reminders"
    const val TOOL_EXPENSES = "tool/expenses"
    const val TOOL_EDITOR = "tool/editor"
    const val TOOL_DNS = "tool/dns"

    const val SETTINGS_BACKUP = "settings/backup"
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_ABOUT = "settings/about"
    const val SETTINGS_PRIVACY = "settings/privacy"
}

/** The three bottom-navigation destinations. */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME(Routes.HOME, "Home", Icons.Rounded.GridView),
    TODAY(Routes.TODAY, "Today", Icons.Rounded.Today),
    SETTINGS(Routes.SETTINGS, "Settings", Icons.Rounded.Settings),
}

val TOP_LEVEL_ROUTES: Set<String> = TopLevelDestination.entries.map { it.route }.toSet()
