package com.daykit.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.ui.graphics.vector.ImageVector
import com.daykit.core.data.CustomNavTab

/** Route constants for the NavHost. All destinations are parameter-less. */
object Routes {
    const val HOME = "home"
    const val TODAY = "today"
    const val CUSTOM_TAB = "custom_tab"
    const val SETTINGS = "settings"

    const val TOOL_APPLOCK = "tool/applock"
    const val TOOL_KEYSTORE = "tool/keystore"
    const val TOOL_NOTES = "tool/notes"
    const val TOOL_FILEVAULT = "tool/filevault"
    const val TOOL_FOCUS = "tool/focus"
    const val TOOL_DAYFLOW = "tool/dayflow"
    const val TOOL_HABITS = "tool/habits"
    const val TOOL_REMINDERS = "tool/reminders"
    const val TOOL_EXPENSES = "tool/expenses"
    const val TOOL_EDITOR = "tool/editor"
    const val TOOL_IMAGE = "tool/image"
    const val TOOL_SCANNER = "tool/scanner"
    const val TOOL_DNS = "tool/dns"
    const val TOOL_EVENTLIGHT = "tool/eventlight"
    const val TOOL_DEVICE_MANAGER = "tool/device_manager"
    const val TOOL_CANVAS = "tool/canvas"

    const val SETTINGS_SECURITY = "settings/security"
    const val SETTINGS_GENERAL = "settings/general"
    const val SETTINGS_HOME_LAYOUT = "settings/home_layout"
    const val SETTINGS_NOTIFICATIONS = "settings/notifications"
    const val SETTINGS_DATA = "settings/data"
    const val SETTINGS_BACKUP = "settings/backup"
    const val SETTINGS_APPEARANCE = "settings/appearance"
    const val SETTINGS_ABOUT = "settings/about"
    const val SETTINGS_PRIVACY = "settings/privacy"
}

/** The four bottom-navigation destinations. */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val inactiveIcon: ImageVector,
) {
    HOME(Routes.HOME, "Home", Icons.Rounded.GridView, Icons.Outlined.GridView),
    TODAY(Routes.TODAY, "Today", Icons.Rounded.Today, Icons.Outlined.Today),
    CUSTOM(Routes.CUSTOM_TAB, CustomNavTab.Focus.navLabel, Icons.Rounded.HourglassTop, Icons.Outlined.HourglassEmpty),
    SETTINGS(Routes.SETTINGS, "Settings", Icons.Rounded.Settings, Icons.Outlined.Settings),
}

fun CustomNavTab.activeIcon(): ImageVector = when (this) {
    CustomNavTab.Focus -> Icons.Rounded.HourglassTop
    CustomNavTab.Habits -> Icons.Rounded.TaskAlt
    CustomNavTab.Dayflow -> Icons.Rounded.AutoAwesome
    CustomNavTab.Expenses -> Icons.Rounded.AccountBalanceWallet
    CustomNavTab.Reminders -> Icons.Rounded.NotificationsActive
    CustomNavTab.Notes -> Icons.Rounded.Description
    CustomNavTab.KeyStore -> Icons.Rounded.Key
    CustomNavTab.FileVault -> Icons.Rounded.FolderSpecial
    CustomNavTab.AppLock -> Icons.Rounded.Lock
    CustomNavTab.DeviceManager -> Icons.Rounded.PhoneAndroid
    CustomNavTab.DnsManager -> Icons.Rounded.Dns
    CustomNavTab.Canvas -> Icons.Rounded.Widgets
}

fun CustomNavTab.inactiveIcon(): ImageVector = when (this) {
    CustomNavTab.Focus -> Icons.Outlined.HourglassEmpty
    CustomNavTab.Habits -> Icons.Outlined.TaskAlt
    CustomNavTab.Dayflow -> Icons.Outlined.AutoAwesome
    CustomNavTab.Expenses -> Icons.Outlined.AccountBalanceWallet
    CustomNavTab.Reminders -> Icons.Outlined.NotificationsActive
    CustomNavTab.Notes -> Icons.Outlined.Description
    CustomNavTab.KeyStore -> Icons.Outlined.Key
    CustomNavTab.FileVault -> Icons.Outlined.FolderSpecial
    CustomNavTab.AppLock -> Icons.Outlined.Lock
    CustomNavTab.DeviceManager -> Icons.Outlined.PhoneAndroid
    CustomNavTab.DnsManager -> Icons.Outlined.Dns
    CustomNavTab.Canvas -> Icons.Outlined.Widgets
}

val TOP_LEVEL_ROUTES: Set<String> = TopLevelDestination.entries.map { it.route }.toSet()
