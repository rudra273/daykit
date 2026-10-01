package com.daykit.feature.home.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.PhotoSizeSelectLarge
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material.icons.rounded.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.daykit.core.designsystem.AccentColors
import com.daykit.navigation.Routes

data class ToolTile(
    val route: String,
    val name: String,
    val description: String,
    val icon: ImageVector,
    val accent: @Composable () -> Color,
    val keywords: List<String>,
)

data class ToolSection(val title: String, val tools: List<ToolTile>)

/** Every Home tool, grouped as Home shows them before the user's layout is applied. */
fun homeToolCatalog(accents: AccentColors): List<ToolSection> = listOf(
    ToolSection(
        "Security",
        listOf(
            ToolTile(Routes.TOOL_APPLOCK, "App Lock", "Protect selected apps", Icons.Rounded.Lock, { accents.blue },
                listOf("app lock", "lock")),
            ToolTile(Routes.TOOL_KEYSTORE, "Key Store", "Store private values", Icons.Rounded.VpnKey, { accents.indigo },
                listOf("key store", "password", "vault")),
            ToolTile(Routes.TOOL_NOTES, "Notes", "Keep private notes", Icons.Rounded.Notes, { accents.teal },
                listOf("notes", "secure notes")),
            ToolTile(Routes.TOOL_FILEVAULT, "File Vault", "Protect photos & videos", Icons.Rounded.Folder, { accents.purple },
                listOf("file vault", "file locker", "hide files", "images", "videos")),
        ),
    ),
    ToolSection(
        "Productivity",
        listOf(
            ToolTile(Routes.TOOL_DAYFLOW, "Dayflow", "Pomodoro, journal & mood", Icons.Rounded.AutoAwesome, { accents.indigo },
                listOf("dayflow", "pomodoro", "journal", "mood", "timer")),
            ToolTile(Routes.TOOL_HABITS, "Habits", "Build daily routines", Icons.Rounded.TrackChanges, { accents.green },
                listOf("habit", "habits")),
            ToolTile(Routes.TOOL_FOCUS, "Focus", "Block distractions", Icons.Rounded.Timer, { accents.red },
                listOf("focus", "focus block", "block app", "distraction", "screen time")),
            ToolTile(Routes.TOOL_EXPENSES, "Expenses", "Track monthly spending", Icons.Rounded.Payments, { accents.pink },
                listOf("expenses", "budget", "money")),
        ),
    ),
    ToolSection(
        "Utilities",
        listOf(
            ToolTile(Routes.TOOL_DEVICE_MANAGER, "Device Manager", "Usage, storage & battery", Icons.Rounded.PhoneAndroid, { accents.teal },
                listOf("device manager", "usage", "storage", "battery", "largest files", "apps")),
            ToolTile(Routes.TOOL_REMINDERS, "Reminders", "Stay on top of tasks", Icons.Rounded.NotificationsActive, { accents.orange },
                listOf("reminder", "notification", "alarm")),
            ToolTile(Routes.TOOL_SCANNER, "Document Scanner", "Scan documents", Icons.Rounded.DocumentScanner, { accents.blue },
                listOf("scanner", "scan document", "document", "camera", "pdf")),
            ToolTile(Routes.TOOL_IMAGE, "Image Tool", "Resize, compress & strip location", Icons.Rounded.PhotoSizeSelectLarge, { accents.green },
                listOf("image", "photo", "resize", "compress", "convert", "exif", "location", "metadata", "jpeg", "png", "webp")),
            ToolTile(Routes.TOOL_EDITOR, "Editor", "Write text files", Icons.Rounded.EditNote, { accents.yellow },
                listOf("editor", "document", "text", "pdf")),
            ToolTile(Routes.TOOL_DNS, "DNS Manager", "Set up Private DNS", Icons.Rounded.Dns, { accents.red },
                listOf("dns", "ad block", "private dns")),
            ToolTile(Routes.TOOL_EVENTLIGHT, "Event Light", "Light for video calls", Icons.Rounded.FlashOn, { accents.yellow },
                listOf("event light", "ring light", "video call light", "night light", "border light")),
        ),
    ),
)

/**
 * Applies the user's Home layout: tools in [order] come first in that order
 * within their section, the rest keep catalog order. Hidden tools are kept here
 * (the settings editor needs them) and filtered by the caller.
 */
fun List<ToolSection>.ordered(order: List<String>): List<ToolSection> = map { section ->
    val rank = order.withIndex().associate { (index, route) -> route to index }
    section.copy(
        tools = section.tools.withIndex()
            .sortedBy { (catalogIndex, tool) -> rank[tool.route] ?: (order.size + catalogIndex) }
            .map { it.value },
    )
}
