package com.daykit.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.daykit.AppContainer
import androidx.fragment.app.FragmentActivity
import com.daykit.core.designsystem.extendedColors

/**
 * Root of the in-app UI (after the onboarding gates). Hosts the NavHost and the
 * frosted bottom navigation bar, which is only shown on the three top-level tabs.
 */
@Composable
fun RootScaffold(
    activity: FragmentActivity,
    container: AppContainer,
    lockedCount: Int,
    onAppLockSelectionChanged: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val onTopLevel = currentDestination?.hierarchy?.any { it.route in TOP_LEVEL_ROUTES } == true

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = onTopLevel,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.extendedColors.barTint,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    TopLevelDestination.entries.forEach { dest ->
                        val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label, style = MaterialTheme.typography.labelMedium) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.extendedColors.textMuted,
                                unselectedTextColor = MaterialTheme.extendedColors.textMuted,
                            ),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(Modifier.fillMaxSize()) {
            DayKitNavHost(
                navController = navController,
                activity = activity,
                container = container,
                lockedCount = lockedCount,
                onAppLockSelectionChanged = onAppLockSelectionChanged,
                bottomBarPadding = innerPadding,
            )
        }
    }
}
