package com.daykit.core.data

import android.content.Context
import android.content.SharedPreferences
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.core.content.edit
import com.daykit.core.designsystem.PREF_APP_PREFS
import java.time.DayOfWeek
import java.time.temporal.WeekFields
import java.util.Locale

/** How long a value copied from Key Store stays on the clipboard. 0 = never cleared. */
object ClipboardClear {
    const val DEFAULT_SECONDS = 30
    val OPTIONS_SECONDS = listOf(15, 30, 60, 120, 0)

    fun sanitize(seconds: Int): Int = if (seconds in OPTIONS_SECONDS) seconds else DEFAULT_SECONDS

    fun label(seconds: Int): String = when (seconds) {
        0 -> "Never"
        60 -> "After 1 minute"
        120 -> "After 2 minutes"
        else -> "After $seconds seconds"
    }
}

/**
 * When an app unlocked through App Lock needs the PIN again. Ordered from
 * strictest to most relaxed so "is this a weakening?" is an ordinal compare.
 */
enum class AppLockRelock(val label: String, val awayMillis: Long) {
    OnLeave("When I leave the app", 0L),
    OneMinute("1 minute after leaving", 60_000L),
    FiveMinutes("5 minutes after leaving", 5 * 60_000L),
    ScreenOff("When the screen turns off", Long.MAX_VALUE),
}

enum class WeekStart(val label: String) {
    Monday("Monday"),
    Sunday("Sunday"),
    System("Device default"),
}

enum class TimeFormatPreference(val label: String) {
    System("Device default"),
    Hour12("12-hour (1:30 PM)"),
    Hour24("24-hour (13:30)"),
}

enum class StartTab(val label: String) {
    Home("Home"),
    Today("Today"),
}

object ReminderSnooze {
    const val DEFAULT_MINUTES = 10
    val OPTIONS_MINUTES = listOf(5, 10, 15, 30)

    fun sanitize(minutes: Int): Int = if (minutes in OPTIONS_MINUTES) minutes else DEFAULT_MINUTES
}

/**
 * Non-secret app preferences in the same plain [SharedPreferences] file as the
 * theme and haptics, so formatters, receivers and services read them
 * synchronously without touching Keystore or SQLCipher.
 *
 * [init] runs in `DayKitApplication.onCreate`. Before that (plain JVM tests) the
 * getters return their defaults, which keeps pure helpers testable.
 */
object AppPreferences {
    const val KEY_CLIPBOARD_CLEAR_SECONDS = "clipboard_clear_seconds"
    const val KEY_APP_LOCK_RELOCK = "app_lock_relock"
    const val KEY_HIDE_IN_RECENTS = "hide_in_recents"
    const val KEY_CURRENCY = "currency_code"
    const val KEY_WEEK_START = "week_start"
    const val KEY_TIME_FORMAT = "time_format"
    const val KEY_SNOOZE_MINUTES = "reminder_snooze_minutes"
    const val KEY_REMINDER_FULL_SCREEN = "reminder_full_screen"
    const val KEY_START_TAB = "start_tab"
    const val KEY_HOME_HIDDEN_TOOLS = "home_hidden_tools"
    const val KEY_HOME_TOOL_ORDER = "home_tool_order"

    /** Existing data was entered in rupees, so INR stays the default rather than the locale's. */
    const val DEFAULT_CURRENCY = "INR"

    @Volatile
    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    private val prefs: SharedPreferences?
        get() = appContext?.getSharedPreferences(PREF_APP_PREFS, Context.MODE_PRIVATE)

    // ── Security ──

    var clipboardClearSeconds: Int
        get() = ClipboardClear.sanitize(
            prefs?.getInt(KEY_CLIPBOARD_CLEAR_SECONDS, ClipboardClear.DEFAULT_SECONDS)
                ?: ClipboardClear.DEFAULT_SECONDS,
        )
        set(value) = edit { putInt(KEY_CLIPBOARD_CLEAR_SECONDS, ClipboardClear.sanitize(value)) }

    var appLockRelock: AppLockRelock
        get() = enumOrDefault(prefs?.getString(KEY_APP_LOCK_RELOCK, null), AppLockRelock.OnLeave)
        set(value) = edit { putString(KEY_APP_LOCK_RELOCK, value.name) }

    var hideInRecents: Boolean
        get() = prefs?.getBoolean(KEY_HIDE_IN_RECENTS, false) ?: false
        set(value) = edit { putBoolean(KEY_HIDE_IN_RECENTS, value) }

    // ── General ──

    var currencyCode: String
        get() = prefs?.getString(KEY_CURRENCY, null)
            ?.takeIf { code -> runCatching { java.util.Currency.getInstance(code) }.isSuccess }
            ?: DEFAULT_CURRENCY
        set(value) = edit { putString(KEY_CURRENCY, value) }

    var weekStart: WeekStart
        get() = enumOrDefault(prefs?.getString(KEY_WEEK_START, null), WeekStart.Monday)
        set(value) = edit { putString(KEY_WEEK_START, value.name) }

    /** The resolved first day of the week, following the device locale for [WeekStart.System]. */
    fun firstDayOfWeek(): DayOfWeek = when (weekStart) {
        WeekStart.Monday -> DayOfWeek.MONDAY
        WeekStart.Sunday -> DayOfWeek.SUNDAY
        WeekStart.System -> systemFirstDayOfWeek()
    }

    fun systemFirstDayOfWeek(): DayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek

    var timeFormat: TimeFormatPreference
        get() = enumOrDefault(prefs?.getString(KEY_TIME_FORMAT, null), TimeFormatPreference.System)
        set(value) = edit { putString(KEY_TIME_FORMAT, value.name) }

    /** Whether times render as 13:30. Defaults to 12-hour when no context is available. */
    fun is24Hour(): Boolean = when (timeFormat) {
        TimeFormatPreference.Hour12 -> false
        TimeFormatPreference.Hour24 -> true
        TimeFormatPreference.System -> systemIs24Hour()
    }

    fun systemIs24Hour(): Boolean = appContext?.let(DateFormat::is24HourFormat) ?: false

    var startTab: StartTab
        get() = enumOrDefault(prefs?.getString(KEY_START_TAB, null), StartTab.Home)
        set(value) = edit { putString(KEY_START_TAB, value.name) }

    // ── Reminders ──

    var snoozeMinutes: Int
        get() = ReminderSnooze.sanitize(
            prefs?.getInt(KEY_SNOOZE_MINUTES, ReminderSnooze.DEFAULT_MINUTES) ?: ReminderSnooze.DEFAULT_MINUTES,
        )
        set(value) = edit { putInt(KEY_SNOOZE_MINUTES, ReminderSnooze.sanitize(value)) }

    var reminderFullScreen: Boolean
        get() = prefs?.getBoolean(KEY_REMINDER_FULL_SCREEN, true) ?: true
        set(value) = edit { putBoolean(KEY_REMINDER_FULL_SCREEN, value) }

    // ── Home ──

    var homeHiddenTools: Set<String>
        get() = prefs?.getStringSet(KEY_HOME_HIDDEN_TOOLS, null)?.toSet() ?: emptySet()
        set(value) = edit { putStringSet(KEY_HOME_HIDDEN_TOOLS, value.toSet()) }

    /** Routes in the user's order. Tools missing from it keep their catalog position. */
    var homeToolOrder: List<String>
        get() = prefs?.getString(KEY_HOME_TOOL_ORDER, null)
            ?.split(',')
            ?.filter { it.isNotBlank() }
            ?: emptyList()
        set(value) = edit { putString(KEY_HOME_TOOL_ORDER, value.joinToString(",")) }

    fun resetHomeLayout() = edit {
        remove(KEY_HOME_HIDDEN_TOOLS)
        remove(KEY_HOME_TOOL_ORDER)
    }

    /**
     * Compose state for [read] that updates whenever one of [keys] changes,
     * wherever the change was made.
     */
    @Composable
    fun <T> rememberPreference(vararg keys: String, read: () -> T): State<T> {
        val state = remember { mutableStateOf(read()) }
        val target = prefs
        DisposableEffect(target) {
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == null || key in keys) state.value = read()
            }
            target?.registerOnSharedPreferenceChangeListener(listener)
            onDispose { target?.unregisterOnSharedPreferenceChangeListener(listener) }
        }
        return state
    }

    /** Registers [listener] for the process lifetime; used by app-scoped mirrors. */
    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs?.registerOnSharedPreferenceChangeListener(listener)
    }

    private inline fun edit(crossinline block: SharedPreferences.Editor.() -> Unit) {
        prefs?.edit { block() }
    }

    private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
        name?.let { stored -> enumValues<E>().firstOrNull { it.name == stored } } ?: default
}
