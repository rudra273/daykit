package com.daykit.feature.focus.data

import android.content.Context
import android.content.SharedPreferences
import com.daykit.feature.focus.service.SHORT_VIDEO_CONSENT
import com.daykit.feature.focus.service.ShortVideoTarget
import com.daykit.feature.focus.service.shortVideoPreferences
import java.time.LocalDate

enum class ShortVideoMode(val storageValue: String, val label: String) {
    Off("off", "Off"),
    DailyLimit("limit", "Daily limit"),
    BlockAll("block", "Block all"),
    ;

    companion object {
        fun fromStorage(raw: String?): ShortVideoMode? =
            entries.firstOrNull { it.storageValue == raw }
    }
}

internal data class ShortVideoRule(
    val target: ShortVideoTarget,
    val mode: ShortVideoMode,
    val dailyLimitMinutes: Int,
    val usedTodayMillis: Long,
    val blocksToday: Int,
) {
    val limitMillis: Long
        get() = dailyLimitMinutes.coerceAtLeast(1) * 60_000L

    val remainingMillis: Long
        get() = (limitMillis - usedTodayMillis).coerceAtLeast(0L)

    val progress: Float
        get() = if (mode == ShortVideoMode.DailyLimit && limitMillis > 0L) {
            (usedTodayMillis.toFloat() / limitMillis).coerceIn(0f, 1f)
        } else {
            0f
        }

    val isLimitReached: Boolean
        get() = mode == ShortVideoMode.DailyLimit && usedTodayMillis >= limitMillis

    val shouldRedirectNow: Boolean
        get() = mode == ShortVideoMode.BlockAll || isLimitReached

    val isActive: Boolean
        get() = mode != ShortVideoMode.Off
}

internal interface ShortVideoPrefsBackend {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun getInt(key: String, default: Int): Int
    fun putInt(key: String, value: Int)
    fun getLong(key: String, default: Long): Long
    fun putLong(key: String, value: Long)
    fun getBoolean(key: String, default: Boolean): Boolean
    fun putBoolean(key: String, value: Boolean)
}

internal class SharedPrefsShortVideoBackend(
    private val prefs: SharedPreferences,
) : ShortVideoPrefsBackend {
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    override fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    override fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    override fun getLong(key: String, default: Long): Long = prefs.getLong(key, default)
    override fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    override fun getBoolean(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }
}

/**
 * Manages per-app Short Video Blocker rules (`Off`, `DailyLimit`, `BlockAll`),
 * daily watch-time accumulation, and daily redirect counters in `focus_short_video`
 * SharedPreferences.
 *
 * Backward-compatible with v1 boolean keys (`target.packageName = true` migrates
 * transparently to [ShortVideoMode.BlockAll]). Automatically resets daily counters
 * when the local calendar date rolls over at midnight.
 */
internal class ShortVideoStore(
    private val backend: ShortVideoPrefsBackend,
) {
    constructor(context: Context) : this(SharedPrefsShortVideoBackend(context.shortVideoPreferences()))
    constructor(prefs: SharedPreferences) : this(SharedPrefsShortVideoBackend(prefs))

    var hasConsent: Boolean
        get() = backend.getBoolean(SHORT_VIDEO_CONSENT, false)
        set(value) = backend.putBoolean(SHORT_VIDEO_CONSENT, value)

    fun getRule(
        target: ShortVideoTarget,
        today: String = LocalDate.now().toString(),
    ): ShortVideoRule {
        val pkg = target.packageName
        val storedMode = ShortVideoMode.fromStorage(backend.getString(modeKey(pkg)))
        val mode = storedMode ?: if (backend.getBoolean(pkg, false)) {
            ShortVideoMode.BlockAll
        } else {
            ShortVideoMode.Off
        }
        val limitMinutes = backend.getInt(limitKey(pkg), DEFAULT_LIMIT_MINUTES)
            .coerceIn(1, MAX_LIMIT_MINUTES)

        val storedDate = backend.getString(dateKey(pkg))
        val isToday = storedDate == today
        val usedMillis = if (isToday) backend.getLong(usedKey(pkg), 0L).coerceAtLeast(0L) else 0L
        val blocksToday = if (isToday) backend.getInt(blocksKey(pkg), 0).coerceAtLeast(0) else 0

        return ShortVideoRule(
            target = target,
            mode = mode,
            dailyLimitMinutes = limitMinutes,
            usedTodayMillis = usedMillis,
            blocksToday = blocksToday,
        )
    }

    fun getAllRules(today: String = LocalDate.now().toString()): List<ShortVideoRule> =
        ShortVideoTarget.entries.map { getRule(it, today) }

    fun activeTargets(today: String = LocalDate.now().toString()): List<ShortVideoTarget> {
        if (!hasConsent) return emptyList()
        return ShortVideoTarget.entries.filter { getRule(it, today).isActive }
    }

    fun saveRule(
        target: ShortVideoTarget,
        mode: ShortVideoMode,
        dailyLimitMinutes: Int = DEFAULT_LIMIT_MINUTES,
    ) {
        val pkg = target.packageName
        val clampedMinutes = dailyLimitMinutes.coerceIn(1, MAX_LIMIT_MINUTES)
        backend.putString(modeKey(pkg), mode.storageValue)
        backend.putInt(limitKey(pkg), clampedMinutes)
        // Keep legacy boolean in sync so any reader checking `prefs.getBoolean(pkg)` sees active state.
        backend.putBoolean(pkg, mode != ShortVideoMode.Off)
    }

    /**
     * Adds [deltaMillis] of active short-video watch time for [target] on [today]
     * and returns the updated [ShortVideoRule].
     */
    fun addUsageMillis(
        target: ShortVideoTarget,
        deltaMillis: Long,
        today: String = LocalDate.now().toString(),
    ): ShortVideoRule {
        if (deltaMillis <= 0L) return getRule(target, today)
        val pkg = target.packageName
        ensureDateCurrent(pkg, today)
        val updated = (backend.getLong(usedKey(pkg), 0L).coerceAtLeast(0L) + deltaMillis)
        backend.putLong(usedKey(pkg), updated)
        return getRule(target, today)
    }

    /** Records one automatic redirect out of [target]'s short-video viewer on [today]. */
    fun recordBlock(
        target: ShortVideoTarget,
        today: String = LocalDate.now().toString(),
    ): ShortVideoRule {
        val pkg = target.packageName
        ensureDateCurrent(pkg, today)
        val updated = backend.getInt(blocksKey(pkg), 0).coerceAtLeast(0) + 1
        backend.putInt(blocksKey(pkg), updated)
        return getRule(target, today)
    }

    private fun ensureDateCurrent(pkg: String, today: String) {
        if (backend.getString(dateKey(pkg)) != today) {
            backend.putString(dateKey(pkg), today)
            backend.putLong(usedKey(pkg), 0L)
            backend.putInt(blocksKey(pkg), 0)
        }
    }

    companion object {
        const val DEFAULT_LIMIT_MINUTES = 15
        const val MAX_LIMIT_MINUTES = 180

        private fun modeKey(pkg: String) = "mode_$pkg"
        private fun limitKey(pkg: String) = "limit_min_$pkg"
        private fun dateKey(pkg: String) = "usage_date_$pkg"
        private fun usedKey(pkg: String) = "used_ms_$pkg"
        private fun blocksKey(pkg: String) = "blocks_$pkg"
    }
}
