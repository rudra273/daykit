package com.daykit.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.scale
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
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

    // Media shared into the app goes to the file vault, which consumes the URIs.
    val pendingShares by container.pendingVaultShares.collectAsStateWithLifecycle()
    LaunchedEffect(pendingShares) {
        if (pendingShares.isNotEmpty()) {
            navController.navigate(Routes.TOOL_FILEVAULT) { launchSingleTop = true }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            // Shown instantly on tabs, hidden instantly on tool screens — no slide animation.
            if (onTopLevel) {
                Row(
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().heightIn(min = 60.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TopLevelDestination.entries.forEach { dest ->
                        val selected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                        val tint by animateColorAsState(
                            targetValue = if (selected) MaterialTheme.extendedColors.accents.blue else MaterialTheme.extendedColors.textMuted,
                            animationSpec = tween(180), label = "tab tint",
                        )
                        val iconScale by animateFloatAsState(
                            targetValue = if (selected) 1f else 0.92f,
                            animationSpec = tween(180), label = "tab scale",
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 60.dp)
                                .clickable(role = Role.Tab, onClickLabel = dest.label) {
                                    navController.navigate(dest.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                                .semantics { this.selected = selected },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Crossfade(targetState = selected, animationSpec = tween(180), label = "tab icon") { active ->
                                Icon(
                                    imageVector = if (active) dest.icon else dest.inactiveIcon,
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(24.dp).scale(iconScale),
                                )
                            }
                            Text(dest.label, style = MaterialTheme.typography.labelMedium, color = tint)
                        }
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
