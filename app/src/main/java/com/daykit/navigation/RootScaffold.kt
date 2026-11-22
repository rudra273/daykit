package com.daykit.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.daykit.AppContainer
import com.daykit.core.data.AppPreferences
import com.daykit.core.data.StartTab
import com.daykit.core.designsystem.LocalReduceMotion
import com.daykit.core.designsystem.background.LocalPageBackground
import com.daykit.core.designsystem.components.floatingDockSurface
import com.daykit.core.designsystem.extendedColors

private val DockOuterShape = RoundedCornerShape(32.dp)
private val DockInnerPillShape = RoundedCornerShape(26.dp)
private val DockHeight = 60.dp
private val DockInnerPadding = 4.dp

/**
 * Root of the in-app UI (after the onboarding gates). Hosts the NavHost and the
 * floating bottom capsule navigation dock on the three top-level tabs.
 *
 * The capsule pill shape is always used; its surface material adapts to the user's
 * selected card style ([floatingDockSurface]: opaque card in Flat/Clay, frosted
 * Liquid Glass when Liquid glass is enabled).
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

    // Open on the user's chosen tab once per launch. Home stays the graph's start
    // destination so back from Today still lands on Home, as with a tab tap.
    var startTabApplied by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!startTabApplied) {
            startTabApplied = true
            if (AppPreferences.startTab == StartTab.Today) {
                navController.navigate(Routes.TODAY) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                }
            }
        }
    }

    // Media shared into the app goes to the file vault, which consumes the URIs.
    val pendingShares by container.pendingVaultShares.collectAsStateWithLifecycle()
    LaunchedEffect(pendingShares) {
        if (pendingShares.isNotEmpty()) {
            navController.navigate(Routes.TOOL_FILEVAULT) { launchSingleTop = true }
        }
    }

    val quickAction by container.pendingQuickAction.collectAsStateWithLifecycle()
    LaunchedEffect(quickAction) {
        val request = quickAction ?: return@LaunchedEffect
        navController.navigate(request.action.route) { launchSingleTop = true }
        if (request.action == QuickAction.Reminders) {
            container.pendingQuickAction.compareAndSet(request, null)
        }
    }

    fun navigateTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    val density = LocalDensity.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val reduceMotion = LocalReduceMotion.current
    val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    var bottomBarHeight by remember { mutableStateOf(78.dp + navigationInset) }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().tabSwipeNavigation(
                route = currentDestination?.route,
                thresholdPx = with(density) { 64.dp.toPx() },
                edgePx = with(density) { 24.dp.toPx() },
                isRtl = isRtl,
                onNavigate = ::navigateTab,
            ),
        ) {
            DayKitNavHost(
                navController = navController,
                activity = activity,
                container = container,
                lockedCount = lockedCount,
                onAppLockSelectionChanged = onAppLockSelectionChanged,
                bottomBarPadding = PaddingValues(bottom = bottomBarHeight),
            )
        }
        if (onTopLevel) {
            val baseSolid = LocalPageBackground.current.solid
            val ext = MaterialTheme.extendedColors
            val tabs = TopLevelDestination.entries
            val selectedIndex = tabs.indexOfFirst { dest ->
                currentDestination?.hierarchy?.any { it.route == dest.route } == true
            }.coerceAtLeast(0)

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { bottomBarHeight = with(density) { it.height.toDp() } },
                contentAlignment = Alignment.BottomCenter,
            ) {
                // Soft scroll-edge vignette so cards scrolling under the floating dock
                // dissolve smoothly rather than colliding harshly with the tab icons.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.55f to baseSolid.copy(alpha = if (ext.isDark) 0.72f else 0.76f),
                                1.0f to baseSolid.copy(alpha = if (ext.isDark) 0.94f else 0.96f),
                            ),
                        ),
                )

                // Floating capsule tab dock (opaque card in Flat/Clay, frosted glass in Liquid Glass)
                BoxWithConstraints(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(start = 32.dp, end = 32.dp, bottom = 10.dp)
                        .widthIn(max = 360.dp)
                        .fillMaxWidth()
                        .height(DockHeight)
                        .floatingDockSurface(shape = DockOuterShape)
                        .padding(DockInnerPadding),
                ) {
                    val slotWidth = maxWidth / tabs.size
                    val pillOffset by animateDpAsState(
                        targetValue = slotWidth * selectedIndex,
                        animationSpec = if (reduceMotion) {
                            snap()
                        } else {
                            spring(
                                dampingRatio = 0.82f,
                                stiffness = Spring.StiffnessMedium,
                            )
                        },
                        label = "dock pill offset",
                    )
                    val activePillFill = if (ext.isDark) {
                        ext.accents.blue.copy(alpha = 0.22f)
                    } else {
                        ext.accents.blue.copy(alpha = 0.12f)
                    }

                    // Sliding active tab highlight capsule
                    Box(
                        modifier = Modifier
                            .offset(x = pillOffset)
                            .width(slotWidth)
                            .fillMaxHeight()
                            .clip(DockInnerPillShape)
                            .background(activePillFill),
                    )

                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        tabs.forEachIndexed { index, dest ->
                            val selected = index == selectedIndex
                            val tint by animateColorAsState(
                                targetValue = if (selected) ext.accents.blue else ext.textMuted,
                                animationSpec = if (reduceMotion) snap() else tween(180),
                                label = "tab tint",
                            )
                            val iconScale by animateFloatAsState(
                                targetValue = if (selected) 1.0f else 0.92f,
                                animationSpec = if (reduceMotion) {
                                    snap()
                                } else {
                                    spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)
                                },
                                label = "tab scale",
                            )
                            val interactionSource = remember { MutableInteractionSource() }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(DockInnerPillShape)
                                    .clickable(
                                        interactionSource = interactionSource,
                                        indication = null,
                                        role = Role.Tab,
                                        onClickLabel = dest.label,
                                    ) {
                                        navigateTab(dest.route)
                                    }
                                    .semantics { this.selected = selected },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Crossfade(
                                    targetState = selected,
                                    animationSpec = if (reduceMotion) snap() else tween(160),
                                    label = "tab icon",
                                ) { active ->
                                    Icon(
                                        imageVector = if (active) dest.icon else dest.inactiveIcon,
                                        contentDescription = null,
                                        tint = tint,
                                        modifier = Modifier.size(22.dp).scale(iconScale),
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = dest.label,
                                    color = tint,
                                    fontSize = 11.sp,
                                    lineHeight = 13.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
