package com.daykit.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.daykit.AppContainer
import com.daykit.core.data.AppPreferences
import com.daykit.core.data.CustomNavTab
import com.daykit.core.designsystem.SolidPage
import com.daykit.core.designsystem.background.LocalPageBackground
import com.daykit.core.designsystem.background.pageBackground
import com.daykit.feature.applock.ui.AppLockScreen
import com.daykit.feature.canvas.ui.CanvasScreen
import com.daykit.feature.dayflow.ui.DayflowScreen
import com.daykit.feature.devicemanager.ui.DeviceManagerScreen
import com.daykit.feature.dns.ui.DnsManagerScreen
import com.daykit.feature.editor.ui.EditorScreen
import com.daykit.feature.eventlight.ui.EventLightScreen
import com.daykit.feature.expense.ui.ExpenseScreen
import com.daykit.feature.filelocker.ui.FileLockerScreen
import com.daykit.feature.focus.ui.FocusScreen
import com.daykit.feature.habit.ui.HabitScreen
import com.daykit.feature.home.ui.HomeScreen
import com.daykit.feature.imagetool.ui.ImageToolScreen
import com.daykit.feature.keystore.ui.KeyStoreScreen
import com.daykit.feature.notes.ui.SecureNotesScreen
import com.daykit.feature.reminder.ui.ReminderScreen
import com.daykit.feature.scanner.ui.DocumentScannerScreen
import com.daykit.feature.settings.ui.AboutAppScreen
import com.daykit.feature.settings.ui.AppearanceScreen
import com.daykit.feature.settings.ui.BackupRestoreScreen
import com.daykit.feature.settings.ui.DataStorageScreen
import com.daykit.feature.settings.ui.GeneralSettingsScreen
import com.daykit.feature.settings.ui.HomeLayoutSettingsScreen
import com.daykit.feature.settings.ui.NotificationSettingsScreen
import com.daykit.feature.settings.ui.PrivacyPolicyScreen
import com.daykit.feature.settings.ui.SecuritySettingsScreen
import com.daykit.feature.settings.ui.SettingsScreen
import com.daykit.feature.today.ui.TodayScreen

@Composable
fun DayKitNavHost(
    navController: NavHostController,
    activity: FragmentActivity,
    container: AppContainer,
    lockedCount: Int,
    onAppLockSelectionChanged: () -> Unit,
    bottomBarPadding: PaddingValues,
) {
    val back: () -> Unit = { navController.popBackStack() }
    val direction = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1 else 1

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = {
            val from = TopLevelDestination.entries.indexOfFirst { it.route == initialState.destination.route }
            val to = TopLevelDestination.entries.indexOfFirst { it.route == targetState.destination.route }
            if (from >= 0 && to >= 0 && from != to) {
                slideInHorizontally(tween(220)) { width -> (if (to > from) width else -width) * direction }
            } else EnterTransition.None
        },
        exitTransition = {
            val from = TopLevelDestination.entries.indexOfFirst { it.route == initialState.destination.route }
            val to = TopLevelDestination.entries.indexOfFirst { it.route == targetState.destination.route }
            if (from >= 0 && to >= 0 && from != to) {
                slideOutHorizontally(tween(220)) { width -> (if (to > from) -width else width) * direction }
            } else ExitTransition.None
        },
        popEnterTransition = { EnterTransition.None },
        popExitTransition = { ExitTransition.None },
    ) {
        // ── Tabs ──
        opaqueComposable(Routes.HOME) {
            HomeScreen(
                container = container,
                lockedCount = lockedCount,
                bottomBarPadding = bottomBarPadding,
                onOpenTool = { navController.navigate(it) },
            )
        }
        opaqueComposable(Routes.TODAY) {
            TodayScreen(
                container = container,
                bottomBarPadding = bottomBarPadding,
                onOpenTool = { navController.navigate(it) },
            )
        }
        opaqueComposable(Routes.CUSTOM_TAB) {
            val customNavTab by AppPreferences.rememberPreference(AppPreferences.KEY_CUSTOM_NAV_TAB) {
                AppPreferences.customNavTab
            }
            val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val dockClearance = (bottomBarPadding.calculateBottomPadding() - navBarInset).coerceAtLeast(0.dp)
            Box(Modifier.fillMaxSize().padding(bottom = dockClearance)) {
                when (customNavTab) {
                    CustomNavTab.Focus -> FocusScreen(
                        container = container,
                        onBack = null,
                        onMonitorNeeded = onAppLockSelectionChanged,
                    )
                    CustomNavTab.Habits -> HabitScreen(container = container, onBack = null)
                    CustomNavTab.Dayflow -> DayflowScreen(container = container, onBack = null)
                    CustomNavTab.Expenses -> ExpenseScreen(container = container, onBack = null)
                    CustomNavTab.Reminders -> ReminderScreen(container = container, onBack = null)
                    CustomNavTab.Notes -> SecureNotesScreen(container = container, onBack = null)
                    CustomNavTab.KeyStore -> KeyStoreScreen(container = container, onBack = null)
                    CustomNavTab.FileVault -> FileLockerScreen(container = container, onBack = null)
                    CustomNavTab.AppLock -> AppLockScreen(
                        container = container,
                        onBack = null,
                        onSelectionChanged = onAppLockSelectionChanged,
                    )
                    CustomNavTab.DeviceManager -> DeviceManagerScreen(
                        onBack = null,
                        onExpectActivityResult = { container.sensitiveKeyManager.expectingActivityResult = true },
                    )
                    CustomNavTab.DnsManager -> DnsManagerScreen(onBack = null)
                    CustomNavTab.Canvas -> CanvasScreen(onBack = null)
                }
            }
        }
        opaqueComposable(Routes.SETTINGS) {
            SettingsScreen(
                bottomBarPadding = bottomBarPadding,
                onOpenSecurity = { navController.navigate(Routes.SETTINGS_SECURITY) },
                onOpenGeneral = { navController.navigate(Routes.SETTINGS_GENERAL) },
                onOpenHomeLayout = { navController.navigate(Routes.SETTINGS_HOME_LAYOUT) },
                onOpenNotifications = { navController.navigate(Routes.SETTINGS_NOTIFICATIONS) },
                onOpenDataStorage = { navController.navigate(Routes.SETTINGS_DATA) },
                onOpenBackupRestore = { navController.navigate(Routes.SETTINGS_BACKUP) },
                onOpenAppearance = { navController.navigate(Routes.SETTINGS_APPEARANCE) },
                onOpenAboutApp = { navController.navigate(Routes.SETTINGS_ABOUT) },
                onOpenPrivacyPolicy = { navController.navigate(Routes.SETTINGS_PRIVACY) },
            )
        }

        // ── Tools ──
        opaqueComposable(Routes.TOOL_APPLOCK) {
            AppLockScreen(
                container = container,
                onBack = back,
                onSelectionChanged = onAppLockSelectionChanged,
            )
        }
        opaqueComposable(Routes.TOOL_KEYSTORE) { KeyStoreScreen(container = container, onBack = back) }
        opaqueComposable(Routes.TOOL_NOTES) { SecureNotesScreen(container = container, onBack = back) }
        opaqueComposable(Routes.TOOL_FILEVAULT) { FileLockerScreen(container = container, onBack = back) }
        opaqueComposable(Routes.TOOL_FOCUS) {
            FocusScreen(
                container = container,
                onBack = back,
                onMonitorNeeded = onAppLockSelectionChanged,
            )
        }
        opaqueComposable(Routes.TOOL_HABITS) { HabitScreen(container = container, onBack = back) }
        opaqueComposable(Routes.TOOL_DAYFLOW) { DayflowScreen(container = container, onBack = back) }
        opaqueComposable(Routes.TOOL_REMINDERS) { ReminderScreen(container = container, onBack = back) }
        opaqueComposable(Routes.TOOL_EXPENSES) { ExpenseScreen(container = container, onBack = back) }
        opaqueComposable(Routes.TOOL_EDITOR) {
            // A typing screen: solid, plain page whatever the wallpaper.
            SolidPage {
                EditorScreen(
                    onBack = back,
                    onExpectActivityResult = { container.sensitiveKeyManager.expectingActivityResult = true },
                )
            }
        }
        opaqueComposable(Routes.TOOL_IMAGE) { ImageToolScreen(container = container, onBack = back) }
        opaqueComposable(Routes.TOOL_SCANNER) { DocumentScannerScreen(container = container, onBack = back) }
        opaqueComposable(Routes.TOOL_DNS) { DnsManagerScreen(onBack = back) }
        opaqueComposable(Routes.TOOL_EVENTLIGHT) { EventLightScreen(onBack = back) }
        opaqueComposable(Routes.TOOL_DEVICE_MANAGER) {
            DeviceManagerScreen(
                onBack = back,
                onExpectActivityResult = { container.sensitiveKeyManager.expectingActivityResult = true },
            )
        }
        opaqueComposable(Routes.TOOL_CANVAS) { CanvasScreen(onBack = back) }

        // ── Settings sub-screens ──
        opaqueComposable(Routes.SETTINGS_SECURITY) { SecuritySettingsScreen(container = container, onBack = back) }
        opaqueComposable(Routes.SETTINGS_GENERAL) { GeneralSettingsScreen(onBack = back) }
        opaqueComposable(Routes.SETTINGS_HOME_LAYOUT) { HomeLayoutSettingsScreen(onBack = back) }
        opaqueComposable(Routes.SETTINGS_NOTIFICATIONS) { NotificationSettingsScreen(onBack = back) }
        opaqueComposable(Routes.SETTINGS_DATA) { DataStorageScreen(container = container, onBack = back) }
        opaqueComposable(Routes.SETTINGS_BACKUP) { BackupRestoreScreen(container = container, onBack = back) }
        opaqueComposable(Routes.SETTINGS_APPEARANCE) { AppearanceScreen(container = container, onBack = back) }
        opaqueComposable(Routes.SETTINGS_ABOUT) { AboutAppScreen(onBack = back) }
        opaqueComposable(Routes.SETTINGS_PRIVACY) { PrivacyPolicyScreen(onBack = back) }
    }
}

/**
 * Keep predictive-back previews from showing an earlier screen through this
 * destination: every page paints the (opaque) page background itself. Over a
 * wallpaper, screens' own `colorScheme.background` fills become transparent so
 * the wallpaper shows; over a plain page nothing changes.
 */
private fun NavGraphBuilder.opaqueComposable(route: String, content: @Composable () -> Unit) {
    composable(route) {
        Box(Modifier.fillMaxSize().pageBackground()) {
            // Always the same tree shape: switching between two branches here would
            // dispose and rebuild the whole screen (losing its state) whenever a
            // background finishes loading or the user picks a different one.
            val scheme = MaterialTheme.colorScheme
            val pageScheme = if (LocalPageBackground.current.isDecorated) {
                scheme.copy(background = Color.Transparent)
            } else {
                scheme
            }
            MaterialTheme(colorScheme = pageScheme, content = content)
        }
    }
}
