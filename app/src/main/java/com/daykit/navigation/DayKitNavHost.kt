package com.daykit.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.daykit.AppContainer
import com.daykit.feature.applock.ui.AppLockScreen
import com.daykit.feature.focus.ui.FocusScreen
import com.daykit.feature.dayflow.ui.DayflowScreen
import com.daykit.feature.dns.ui.DnsManagerScreen
import com.daykit.feature.editor.ui.EditorScreen
import com.daykit.feature.eventlight.ui.EventLightScreen
import com.daykit.feature.expense.ui.ExpenseScreen
import com.daykit.feature.filelocker.ui.FileLockerScreen
import com.daykit.feature.habit.ui.HabitScreen
import com.daykit.feature.home.ui.HomeScreen
import com.daykit.feature.keystore.ui.KeyStoreScreen
import com.daykit.feature.notes.ui.SecureNotesScreen
import com.daykit.feature.reminder.ui.ReminderScreen
import com.daykit.feature.scanner.ui.DocumentScannerScreen
import com.daykit.feature.settings.ui.AboutAppScreen
import com.daykit.feature.settings.ui.AppearanceScreen
import com.daykit.feature.settings.ui.BackupRestoreScreen
import com.daykit.feature.settings.ui.PrivacyPolicyScreen
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

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        // Instant screen switches — no fade/scale animation.
        enterTransition = { EnterTransition.None },
        exitTransition = { ExitTransition.None },
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
        opaqueComposable(Routes.SETTINGS) {
            SettingsScreen(
                container = container,
                bottomBarPadding = bottomBarPadding,
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
        opaqueComposable(Routes.TOOL_EDITOR) { EditorScreen(onBack = back) }
        opaqueComposable(Routes.TOOL_SCANNER) { DocumentScannerScreen(container = container, onBack = back) }
        opaqueComposable(Routes.TOOL_DNS) { DnsManagerScreen(onBack = back) }
        opaqueComposable(Routes.TOOL_EVENTLIGHT) { EventLightScreen(onBack = back) }

        // ── Settings sub-screens ──
        opaqueComposable(Routes.SETTINGS_BACKUP) { BackupRestoreScreen(container = container, onBack = back) }
        opaqueComposable(Routes.SETTINGS_APPEARANCE) { AppearanceScreen(onBack = back) }
        opaqueComposable(Routes.SETTINGS_ABOUT) { AboutAppScreen(onBack = back) }
        opaqueComposable(Routes.SETTINGS_PRIVACY) { PrivacyPolicyScreen(onBack = back) }
    }
}

/** Keep predictive-back previews from showing an earlier screen through this destination. */
private fun NavGraphBuilder.opaqueComposable(route: String, content: @Composable () -> Unit) {
    composable(route) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            content()
        }
    }
}
