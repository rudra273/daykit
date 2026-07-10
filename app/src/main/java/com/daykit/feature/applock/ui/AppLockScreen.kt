package com.daykit.feature.applock.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.daykit.core.designsystem.components.AppSwitch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daykit.AppContainer
import com.daykit.core.data.SecureSettingRepository
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.asAccentContainer
import com.daykit.core.designsystem.components.AppListRow
import com.daykit.core.designsystem.components.EmptyState
import com.daykit.core.designsystem.components.FilterChipButton
import com.daykit.core.designsystem.components.LoadingIndicator
import com.daykit.core.designsystem.components.SearchAppTopBar
import com.daykit.core.designsystem.components.SectionHeader
import com.daykit.core.designsystem.extendedColors
import com.daykit.core.security.BiometricAuthenticator
import com.daykit.core.security.errorMessageOrNull
import com.daykit.feature.applock.domain.InstalledApp
import com.daykit.feature.applock.domain.SamsungSecureFolderSupport
import com.daykit.feature.lock.ui.ToolUnlockScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AppLockTab {
    All,
    Locked,
    Unlocked,
}

@Composable
fun AppLockScreen(
    container: AppContainer,
    onBack: () -> Unit,
    onSelectionChanged: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as FragmentActivity
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val biometricAuthenticator = remember(activity) { BiometricAuthenticator(activity) }
    var unlocked by remember { mutableStateOf(false) }
    var unlockPin by remember { mutableStateOf("") }
    var unlockError by remember { mutableStateOf<String?>(null) }
    var biometricEnabled by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(AppLockTab.All) }
    var installedApps by remember { mutableStateOf<List<InstalledApp>?>(null) }
    var toolLocked by remember { mutableStateOf<Boolean?>(null) }
    val isToolLocked = toolLocked
    val lockedApps by container.appLockRepository
        .observeLockedApps()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val lockedPackages = remember(lockedApps) { lockedApps.map { it.packageName }.toSet() }
    val secureFolderAvailable = remember(context) { SamsungSecureFolderSupport.isAvailable(context) }

    BackHandler {
        if (searchActive) {
            searchActive = false
            query = ""
        } else if (query.isNotBlank()) {
            query = ""
        } else {
            onBack()
        }
    }

    fun tryBiometricUnlock(enabled: Boolean = biometricEnabled) {
        if (!enabled || !biometricAuthenticator.canAuthenticate()) return
        biometricAuthenticator.authenticate(
            title = "Unlock App Lock",
            subtitle = "Manage protected apps",
            onSuccess = {
                unlocked = true
                unlockPin = ""
                unlockError = null
            },
            onError = { unlockError = it },
        )
    }

    LaunchedEffect(Unit) {
        installedApps = container.installedAppProvider.loadLaunchableApps()
    }

    LaunchedEffect(secureFolderAvailable) {
        if (secureFolderAvailable) {
            container.appLockRepository.setLocked(
                packageName = SamsungSecureFolderSupport.PACKAGE_NAME,
                label = "Secure Folder",
                locked = false,
            )
        }
    }

    LaunchedEffect(Unit) {
        val storedToolLocked = container.secureSettingRepository
            .getBoolean(SecureSettingRepository.KEY_TOOL_LOCK_APP_LOCK) != false
        val storedBiometricEnabled = container.secureSettingRepository
            .getBoolean(SecureSettingRepository.KEY_BIOMETRIC_ENABLED) == true
        biometricEnabled = storedBiometricEnabled
        if (storedToolLocked && storedBiometricEnabled) {
            tryBiometricUnlock(storedBiometricEnabled)
        }
        container.secureSettingRepository
            .observeBoolean(SecureSettingRepository.KEY_TOOL_LOCK_APP_LOCK)
            .collect { locked ->
                toolLocked = locked ?: true
            }
    }

    LaunchedEffect(isToolLocked) {
        if (isToolLocked == false) {
            unlocked = true
            unlockPin = ""
            unlockError = null
        }
    }

    if (isToolLocked == null) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { _ ->
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                LoadingIndicator()
            }
        }
        return
    }

    if (isToolLocked == true && !unlocked) {
        ToolUnlockScreen(
            title = "App Lock",
            subtitle = "Enter master PIN to manage locked apps",
            pin = unlockPin,
            error = unlockError,
            biometricEnabled = biometricEnabled,
            icon = Icons.Rounded.Lock,
            onBack = onBack,
            onPinChange = {
                unlockPin = it.filter(Char::isDigit).take(12)
                unlockError = null
            },
            onUnlock = {
                scope.launch {
                    val pin = unlockPin
                    val result = withContext(Dispatchers.Default) {
                        container.credentialRepository.verify(pin.toCharArray())
                    }
                    if (result is com.daykit.core.security.PinVerifyResult.Success) {
                        unlocked = true
                        unlockPin = ""
                    } else {
                        unlockError = result.errorMessageOrNull()
                        unlockPin = ""
                    }
                }
            },
            onBiometric = { tryBiometricUnlock() },
        )
        return
    }

    val filteredApps = remember(installedApps, query, lockedPackages) {
        installedApps.orEmpty().filter { app ->
            query.isBlank() ||
                app.label.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
        }.sortedWith(
            compareByDescending<InstalledApp> { it.packageName in lockedPackages }
                .thenBy { it.label.lowercase() }
        )
    }
    val unlockedApps = remember(filteredApps, lockedPackages) {
        filteredApps.filterNot { it.packageName in lockedPackages }
    }
    val lockedVisibleApps = remember(filteredApps, lockedPackages) {
        filteredApps.filter { it.packageName in lockedPackages }
    }
    val recommendedApps = remember(unlockedApps, context.packageName) {
        unlockedApps
            .filter { it.isRecommendedApp(context.packageName) }
            .sortedWith(compareBy<InstalledApp> { it.recommendationPriority(context.packageName) }.thenBy { it.label.lowercase() })
    }
    val otherUnlockedApps = remember(unlockedApps, recommendedApps) {
        val recommendedPackages = recommendedApps.map { it.packageName }.toSet()
        unlockedApps.filterNot { it.packageName in recommendedPackages }
    }

    val listState = rememberLazyListState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            SearchAppTopBar(
                title = "App Lock",
                query = query,
                onQueryChange = { query = it },
                searchActive = searchActive,
                onSearchActiveChange = { searchActive = it; if (!it) query = "" },
                onBack = onBack,
                searchPlaceholder = "Search apps",
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (installedApps == null) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(innerPadding),
                    contentAlignment = Alignment.Center,
                ) {
                    LoadingIndicator()
                }
            } else {
                val onCheckedChange: (InstalledApp, Boolean) -> Unit = { app, checked ->
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    scope.launch {
                        container.appLockRepository.setLocked(
                            packageName = app.packageName,
                            label = app.label,
                            locked = checked,
                        )
                        onSelectionChanged()
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = innerPadding.calculateTopPadding() + Spacing.sm,
                        bottom = Spacing.xxl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    item(key = "tabs") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Spacing.xs),
                            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                        ) {
                            FilterChipButton(
                                text = "All",
                                selected = selectedTab == AppLockTab.All,
                                onClick = { selectedTab = AppLockTab.All },
                            )
                            FilterChipButton(
                                text = "Locked",
                                selected = selectedTab == AppLockTab.Locked,
                                onClick = { selectedTab = AppLockTab.Locked },
                            )
                            FilterChipButton(
                                text = "Unlocked",
                                selected = selectedTab == AppLockTab.Unlocked,
                                onClick = { selectedTab = AppLockTab.Unlocked },
                            )
                        }
                    }

                    when (selectedTab) {
                        AppLockTab.Locked -> {
                            if (lockedVisibleApps.isEmpty()) {
                                item(key = "empty-locked") {
                                    AppLockEmptyState(
                                        searchActive = query.isNotBlank(),
                                        emptyMessage = "No locked apps yet",
                                        searchMessage = "No locked apps found",
                                    )
                                }
                            } else {
                                appRows(
                                    prefix = "locked",
                                    apps = lockedVisibleApps,
                                    lockedPackages = lockedPackages,
                                    secureFolderAvailable = secureFolderAvailable,
                                    onCheckedChange = onCheckedChange,
                                )
                            }
                        }

                        AppLockTab.Unlocked -> {
                            if (recommendedApps.isNotEmpty()) {
                                item(key = "header-recommended") { SectionHeader("Recommended") }
                                appRows(
                                    prefix = "recommended",
                                    apps = recommendedApps,
                                    lockedPackages = lockedPackages,
                                    secureFolderAvailable = secureFolderAvailable,
                                    onCheckedChange = onCheckedChange,
                                )
                            }
                            if (otherUnlockedApps.isNotEmpty()) {
                                item(key = "header-other") { SectionHeader("All apps") }
                                appRows(
                                    prefix = "other",
                                    apps = otherUnlockedApps,
                                    lockedPackages = lockedPackages,
                                    secureFolderAvailable = secureFolderAvailable,
                                    onCheckedChange = onCheckedChange,
                                )
                            }
                            if (unlockedApps.isEmpty()) {
                                item(key = "empty-unlocked") {
                                    AppLockEmptyState(
                                        searchActive = query.isNotBlank(),
                                        emptyMessage = "No unlocked apps",
                                        searchMessage = "No unlocked apps found",
                                    )
                                }
                            }
                        }

                        AppLockTab.All -> {
                            if (lockedVisibleApps.isNotEmpty()) {
                                item(key = "header-locked") { SectionHeader("Locked") }
                                appRows(
                                    prefix = "all-locked",
                                    apps = lockedVisibleApps,
                                    lockedPackages = lockedPackages,
                                    secureFolderAvailable = secureFolderAvailable,
                                    onCheckedChange = onCheckedChange,
                                )
                            }
                            if (recommendedApps.isNotEmpty()) {
                                item(key = "header-all-recommended") { SectionHeader("Recommended") }
                                appRows(
                                    prefix = "all-recommended",
                                    apps = recommendedApps,
                                    lockedPackages = lockedPackages,
                                    secureFolderAvailable = secureFolderAvailable,
                                    onCheckedChange = onCheckedChange,
                                )
                            }
                            if (otherUnlockedApps.isNotEmpty()) {
                                item(key = "header-all-other") { SectionHeader("All apps") }
                                appRows(
                                    prefix = "all-other",
                                    apps = otherUnlockedApps,
                                    lockedPackages = lockedPackages,
                                    secureFolderAvailable = secureFolderAvailable,
                                    onCheckedChange = onCheckedChange,
                                )
                            }
                            if (filteredApps.isEmpty()) {
                                item(key = "empty-all") {
                                    AppLockEmptyState(
                                        searchActive = query.isNotBlank(),
                                        emptyMessage = "No apps found",
                                        searchMessage = "No apps found",
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.appRows(
    prefix: String,
    apps: List<InstalledApp>,
    lockedPackages: Set<String>,
    secureFolderAvailable: Boolean,
    onCheckedChange: (InstalledApp, Boolean) -> Unit,
) {
    items(apps, key = { "$prefix-${it.packageName}" }) { app ->
        val isLocked = app.packageName in lockedPackages
        val lockDisabled = secureFolderAvailable &&
            app.packageName == SamsungSecureFolderSupport.PACKAGE_NAME
        AppLockAppRow(
            app = app,
            isLocked = isLocked,
            lockDisabled = lockDisabled,
            onCheckedChange = { checked -> onCheckedChange(app, checked) },
        )
    }
}

@Composable
private fun AppLockAppRow(
    app: InstalledApp,
    isLocked: Boolean,
    lockDisabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val accents = MaterialTheme.extendedColors.accents
    val palette = listOf(
        accents.blue,
        accents.teal,
        accents.green,
        accents.red,
        accents.orange,
        accents.yellow,
        accents.purple,
        accents.pink,
        accents.indigo,
    )
    val accent = palette[(Math.floorMod(app.packageName.hashCode(), palette.size))]
    val supporting = when {
        lockDisabled -> "Protected by Samsung"
        else -> app.packageName
    }
    AppListRow(
        headline = app.label,
        supporting = supporting,
        leading = {
            if (app.icon != null) {
                AppIconTile(icon = app.icon)
            } else {
                AppMonogramTile(letter = app.label.firstOrNull()?.uppercase() ?: "#", accent = accent)
            }
        },
        trailing = {
            AppSwitch(
                checked = isLocked,
                onCheckedChange = { onCheckedChange(it) },
                enabled = !lockDisabled,
            )
        },
    )
}

@Composable
private fun AppIconTile(icon: android.graphics.drawable.Drawable) {
    val bitmap = remember(icon) { icon.toBitmap(width = 96, height = 96).asImageBitmap() }
    Image(
        bitmap = bitmap,
        contentDescription = null,
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp)),
    )
}

@Composable
private fun AppMonogramTile(letter: String, accent: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .background(
                color = accent.asAccentContainer(),
                shape = RoundedCornerShape(10.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = letter,
            color = accent,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AppLockEmptyState(
    searchActive: Boolean,
    emptyMessage: String,
    searchMessage: String,
) {
    EmptyState(
        icon = if (searchActive) Icons.Rounded.SearchOff else Icons.Rounded.Lock,
        title = if (searchActive) searchMessage else emptyMessage,
        modifier = Modifier.padding(top = Spacing.xl),
    )
}

private fun InstalledApp.isRecommendedApp(ownPackageName: String): Boolean =
    recommendationPriority(ownPackageName) < Int.MAX_VALUE

private fun InstalledApp.recommendationPriority(ownPackageName: String): Int {
    val labelText = label.lowercase()
    val packageText = packageName.lowercase()
    val combined = "$labelText $packageText"

    return when {
        packageName == ownPackageName || labelText == "daykit" -> 0
        listOf(
            "whatsapp",
            "telegram",
            "signal",
            "instagram",
            "facebook",
            "messenger",
            "snapchat",
            "discord",
            "linkedin",
        ).any(combined::contains) -> 1
        listOf(
            "bank",
            "upi",
            "gpay",
            "google pay",
            "phonepe",
            "paytm",
            "paypal",
            "bhim",
            "wallet",
            "finance",
        ).any(combined::contains) -> 2
        listOf(
            "gallery",
            "photos",
            "photo",
            "camera",
            "media",
        ).any(combined::contains) -> 3
        else -> Int.MAX_VALUE
    }
}
