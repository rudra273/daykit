package com.daykit.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.daykit.AppContainer
import com.daykit.feature.applock.ui.AppLockScreen
import com.daykit.feature.dns.ui.DnsManagerScreen
import com.daykit.feature.editor.ui.EditorScreen
import com.daykit.feature.expense.ui.ExpenseScreen
import com.daykit.feature.filelocker.ui.FileLockerScreen
import com.daykit.feature.habit.ui.HabitScreen
import com.daykit.feature.home.ui.HomeScreen
import com.daykit.feature.keystore.ui.KeyStoreScreen
import com.daykit.feature.notes.ui.SecureNotesScreen
import com.daykit.feature.reminder.ui.ReminderScreen
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
        composable(Routes.HOME) {
            HomeScreen(
                container = container,
                lockedCount = lockedCount,
                bottomBarPadding = bottomBarPadding,
                onOpenTool = { navController.navigate(it) },
            )
        }
        composable(Routes.TODAY) {
            TodayScreen(
                container = container,
                bottomBarPadding = bottomBarPadding,
                onOpenTool = { navController.navigate(it) },
            )
        }
        composable(Routes.SETTINGS) {
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
        composable(Routes.TOOL_APPLOCK) {
            AppLockScreen(
                container = container,
                onBack = back,
                onSelectionChanged = onAppLockSelectionChanged,
            )
        }
        composable(Routes.TOOL_KEYSTORE) { KeyStoreScreen(container = container, onBack = back) }
        composable(Routes.TOOL_NOTES) { SecureNotesScreen(container = container, onBack = back) }
        composable(Routes.TOOL_FILEVAULT) { FileLockerScreen(onBack = back) }
        composable(Routes.TOOL_HABITS) { HabitScreen(container = container, onBack = back) }
        composable(Routes.TOOL_REMINDERS) { ReminderScreen(container = container, onBack = back) }
        composable(Routes.TOOL_EXPENSES) { ExpenseScreen(container = container, onBack = back) }
        composable(Routes.TOOL_EDITOR) { EditorScreen(onBack = back) }
        composable(Routes.TOOL_DNS) { DnsManagerScreen(onBack = back) }

        // ── Settings sub-screens ──
        composable(Routes.SETTINGS_BACKUP) { BackupRestoreScreen(container = container, onBack = back) }
        composable(Routes.SETTINGS_APPEARANCE) { AppearanceScreen(onBack = back) }
        composable(Routes.SETTINGS_ABOUT) { AboutAppScreen(onBack = back) }
        composable(Routes.SETTINGS_PRIVACY) { PrivacyPolicyScreen(onBack = back) }
    }
}
