package com.daykit.feature.canvas.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.asAccentContainer
import com.daykit.core.designsystem.components.AccentIconTile
import com.daykit.core.designsystem.components.AppCard
import com.daykit.core.designsystem.components.AppTopBar
import com.daykit.core.designsystem.components.EmptyState
import com.daykit.core.designsystem.components.FilterChipButton
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.components.SectionHeader
import com.daykit.core.designsystem.extendedColors
import com.daykit.feature.widget.DayflowWidgetProvider
import com.daykit.feature.widget.ExpenseQuickAddWidgetProvider
import com.daykit.feature.widget.HabitCheckInWidgetProvider
import com.daykit.feature.widget.ReminderWidgetProvider
import com.daykit.feature.widget.WeatherGlassWidgetProvider
import kotlinx.coroutines.launch

private enum class CanvasSection(val label: String) {
    DayKitWidgets("DayKit Widgets"),
    DailyWidgets("Daily Widgets"),
    Wallpapers("Wallpapers"),
}

private data class CanvasWidgetItem(
    val id: String,
    val title: String,
    val toolName: String,
    val description: String,
    val sizeLabel: String,
    val icon: ImageVector,
    val accent: @Composable () -> Color,
    val providerClass: Class<*>,
)

@Composable
fun CanvasScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var selectedSection by rememberSaveable { mutableStateOf(CanvasSection.DayKitWidgets) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Refresh active widget counts when returning from the home screen pin dialog.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val dayKitWidgets = remember {
        listOf(
            CanvasWidgetItem(
                id = "dayflow",
                title = "Pomodoro Timer",
                toolName = "Dayflow",
                description = "Start, pause, or take a 5-minute break with a live progress ring right from your home screen.",
                sizeLabel = "2 × 3",
                icon = Icons.Rounded.AutoAwesome,
                accent = { MaterialTheme.extendedColors.accents.indigo },
                providerClass = DayflowWidgetProvider::class.java,
            ),
            CanvasWidgetItem(
                id = "habits",
                title = "Today's Habits",
                toolName = "Habits",
                description = "View active daily habits and tap to check in or update progress without opening the app.",
                sizeLabel = "2 × 2",
                icon = Icons.Rounded.TrackChanges,
                accent = { MaterialTheme.extendedColors.accents.green },
                providerClass = HabitCheckInWidgetProvider::class.java,
            ),
            CanvasWidgetItem(
                id = "expenses",
                title = "Quick Expense",
                toolName = "Expenses",
                description = "One-tap shortcut that opens a lightweight quick-add dialog over your home screen.",
                sizeLabel = "2 × 1",
                icon = Icons.Rounded.Payments,
                accent = { MaterialTheme.extendedColors.accents.pink },
                providerClass = ExpenseQuickAddWidgetProvider::class.java,
            ),
            CanvasWidgetItem(
                id = "reminders",
                title = "Upcoming Reminders",
                toolName = "Reminders",
                description = "Scrollable list of upcoming and overdue reminders with instant Complete and Snooze actions.",
                sizeLabel = "3 × 3",
                icon = Icons.Rounded.NotificationsActive,
                accent = { MaterialTheme.extendedColors.accents.orange },
                providerClass = ReminderWidgetProvider::class.java,
            ),
        )
    }

    val activeCounts = remember(refreshTrigger, dayKitWidgets) {
        val manager = AppWidgetManager.getInstance(context)
        dayKitWidgets.associate { item ->
            val count = runCatching {
                manager.getAppWidgetIds(ComponentName(context, item.providerClass)).size
            }.getOrDefault(0)
            item.id to count
        }
    }

    fun showSnack(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun requestPinProvider(providerClass: Class<*>) {
        val manager = AppWidgetManager.getInstance(context)
        if (!manager.isRequestPinAppWidgetSupported) {
            showSnack("Your launcher doesn't support pinning from apps. Long-press your home screen and choose Widgets.")
            return
        }
        val provider = ComponentName(context, providerClass)
        val requested = runCatching {
            manager.requestPinAppWidget(provider, null, null)
        }.getOrDefault(false)
        if (!requested) {
            showSnack("Could not open widget pin prompt. Long-press your home screen and choose Widgets.")
        }
    }

    fun requestPinWidget(item: CanvasWidgetItem) = requestPinProvider(item.providerClass)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { AppTopBar(title = "Canvas", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Section filter chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.lg, vertical = Spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                CanvasSection.entries.forEach { section ->
                    FilterChipButton(
                        text = section.label,
                        selected = selectedSection == section,
                        onClick = { selectedSection = section },
                    )
                }
            }

            when (selectedSection) {
                CanvasSection.DayKitWidgets -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Spacing.lg,
                            end = Spacing.lg,
                            top = Spacing.xs,
                            bottom = Spacing.xxl,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        item {
                            CanvasHeroCard(
                                totalAvailable = dayKitWidgets.size,
                                totalPinned = activeCounts.values.count { it > 0 },
                            )
                        }

                        item {
                            SectionHeader(text = "Available widgets")
                        }

                        items(dayKitWidgets, key = { it.id }) { widget ->
                            WidgetCatalogCard(
                                item = widget,
                                pinnedCount = activeCounts[widget.id] ?: 0,
                                onPin = { requestPinWidget(widget) },
                            )
                        }

                        item {
                            HowToAddWidgetCard()
                        }
                    }
                }

                CanvasSection.DailyWidgets -> {
                    DailyWidgetsGallery(
                        refreshTrigger = refreshTrigger,
                        onPinProvider = ::requestPinProvider,
                        onShowMessage = ::showSnack,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                CanvasSection.Wallpapers -> {
                    WallpaperStudioSection(
                        refreshTrigger = refreshTrigger,
                        onShowMessage = ::showSnack,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun CanvasHeroCard(
    totalAvailable: Int,
    totalPinned: Int,
) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentIconTile(
                icon = Icons.Rounded.Widgets,
                accent = MaterialTheme.extendedColors.accents.purple,
                size = 44.dp,
                iconSize = 24.dp,
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "DayKit on your Home Screen",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "$totalAvailable tool widgets ready · $totalPinned active on home screen",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun WidgetCatalogCard(
    item: CanvasWidgetItem,
    pinnedCount: Int,
    onPin: () -> Unit,
) {
    val accent = item.accent()
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AccentIconTile(
                icon = item.icon,
                accent = accent,
                size = 42.dp,
                iconSize = 22.dp,
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = item.toolName,
                    style = MaterialTheme.typography.bodySmall,
                    color = accent,
                )
            }
            BadgePill(
                text = item.sizeLabel,
                containerColor = MaterialTheme.extendedColors.inputField,
                contentColor = MaterialTheme.extendedColors.textMuted,
            )
        }

        Spacer(Modifier.height(Spacing.md))

        DayKitToolMiniPreview(widgetId = item.id, accent = accent)

        Spacer(Modifier.height(Spacing.md))

        Text(
            text = item.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.extendedColors.textMuted,
        )

        Spacer(Modifier.height(Spacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (pinnedCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.extendedColors.accents.green,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = if (pinnedCount == 1) "Added to Home" else "$pinnedCount on Home",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.extendedColors.accents.green,
                    )
                }
            } else {
                Text(
                    text = "Resizable widget",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.textMuted,
                )
            }

            PrimaryButton(
                text = if (pinnedCount > 0) "Add another" else "Add to Home",
                leadingIcon = {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                },
                onClick = onPin,
            )
        }
    }
}

@Composable
private fun DayKitToolMiniPreview(
    widgetId: String,
    accent: Color,
) {
    val isDark = isSystemInDarkTheme()
    val cardBg = if (isDark) Color(0xFF17191D) else Color(0xFFF7F7F5)
    val fg = if (isDark) Color(0xFFF3F4F6) else Color(0xFF14161A)
    val muted = if (isDark) Color(0xFF8E95A2) else Color(0xFF7B808A)
    val track = if (isDark) Color(0xFF2A2E35) else Color(0xFFE5E7EC)
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cardBg)
            .border(1.dp, if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        when (widgetId) {
            "dayflow" -> {
                // Editorial 60-tick radial Focus Timer preview (inspired by Reference Image 5)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            val outer = size.minDimension * 0.48f
                            val inner = size.minDimension * 0.36f
                            for (i in 0 until 40) {
                                val deg = -90.0 + i * (360.0 / 40.0)
                                val rad = Math.toRadians(deg)
                                drawLine(
                                    color = if (i < 16) accent else track,
                                    start = Offset(cx + (kotlin.math.cos(rad) * inner).toFloat(), cy + (kotlin.math.sin(rad) * inner).toFloat()),
                                    end = Offset(cx + (kotlin.math.cos(rad) * outer).toFloat(), cy + (kotlin.math.sin(rad) * outer).toFloat()),
                                    strokeWidth = if (i % 5 == 0) 3.2f else 2f,
                                    cap = StrokeCap.Round,
                                )
                            }
                        }
                        Text(text = "24:43", color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(6.dp).background(accent, CircleShape))
                            Text("Focus timer", color = muted, style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text("Deep work", color = fg, style = MaterialTheme.typography.titleSmall)
                        Text("Tap Start 25m or Break 5m right on Home", color = muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            "habits" -> {
                // Clean bento bar checklist preview
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(6.dp).background(accent, CircleShape))
                        Text("Today's habits", color = muted, style = MaterialTheme.typography.labelSmall)
                    }
                    listOf("Hydrate · 6/8" to 0.78f, "Read 20m · Done" to 1.0f).forEachIndexed { idx, (label, frac) ->
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(label, color = fg, style = MaterialTheme.typography.labelMedium)
                                Text("${(frac * 100).toInt()}%", color = muted, style = MaterialTheme.typography.labelSmall)
                            }
                            Box(Modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(track)) {
                                Box(Modifier.fillMaxWidth(frac).height(5.dp).clip(CircleShape).background(if (idx == 0) accent else fg))
                            }
                        }
                    }
                }
            }

            "expenses" -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(Modifier.size(6.dp).background(accent, CircleShape))
                            Text("Quick expense", color = muted, style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.height(2.dp))
                        Text("Instant one-tap entry", color = fg, style = MaterialTheme.typography.titleSmall)
                    }
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }

            else -> {
                // Editorial timeline preview for Reminders
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(6.dp).background(accent, CircleShape))
                        Text("Upcoming", color = muted, style = MaterialTheme.typography.labelSmall)
                    }
                    listOf("10:00" to "Pay utility bill", "16:30" to "Evening walk").forEachIndexed { idx, (time, title) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(time, color = muted, style = MaterialTheme.typography.labelSmall)
                            Box(Modifier.width(2.5.dp).height(16.dp).clip(CircleShape).background(if (idx == 0) accent else track))
                            Text(title, color = fg, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                            if (idx == 0) {
                                Box(Modifier.clip(CircleShape).background(accent).padding(horizontal = 8.dp, vertical = 2.dp)) {
                                    Text("Soon", color = Color.White, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgePill(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Box(
        modifier = Modifier
            .background(containerColor, CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
        )
    }
}

@Composable
private fun HowToAddWidgetCard() {
    val steps = listOf(
        "Tap \"Add to Home\" on any widget above to pin it directly.",
        "Or long-press an empty space on your phone's home screen and tap Widgets.",
        "Find DayKit in the list and drag a widget where you want it.",
    )
    AppCard(modifier = Modifier.padding(top = Spacing.xs)) {
        Text(
            text = "How widgets work",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.md))
        steps.forEachIndexed { index, step ->
            if (index > 0) Spacer(Modifier.height(Spacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primary.asAccentContainer(), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${index + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(Spacing.md))
                Text(
                    text = step,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
