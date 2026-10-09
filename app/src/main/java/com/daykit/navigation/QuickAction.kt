package com.daykit.navigation

import java.util.UUID

/** Only these notification destinations may enter through MainActivity. */
enum class QuickAction(val intentAction: String, val route: String) {
    Reminders("com.daykit.action.REMINDERS", Routes.TOOL_REMINDERS),
    AddReminder("com.daykit.action.ADD_REMINDER", Routes.TOOL_REMINDERS);

    companion object {
        fun fromIntentAction(action: String?): QuickAction? =
            entries.firstOrNull { it.intentAction == action }
    }
}

// A fresh request also lets repeated taps open the form on an existing screen.
data class QuickActionRequest(
    val action: QuickAction,
    val id: String = UUID.randomUUID().toString(),
)
