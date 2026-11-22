package com.daykit.feature.focus.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.daykit.R
import com.daykit.feature.focus.data.ShortVideoMode
import com.daykit.feature.focus.data.ShortVideoStore

internal enum class ShortVideoTarget(val packageName: String, val label: Int) {
    YouTube("com.google.android.youtube", R.string.short_video_youtube),
    Instagram("com.instagram.android", R.string.short_video_instagram),
    Facebook("com.facebook.katana", R.string.short_video_facebook),
}

internal fun Context.shortVideoPreferences(): SharedPreferences =
    getSharedPreferences("focus_short_video", Context.MODE_PRIVATE)

internal const val SHORT_VIDEO_CONSENT = "short_video_redirect_consent"

class ShortVideoBlockerService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())
    private val preferences by lazy { shortVideoPreferences() }
    private val store by lazy { ShortVideoStore(preferences) }
    private var targets = emptyList<ShortVideoTarget>()
    private var viewerTarget: ShortVideoTarget? = null
    private var viewerWindowId = -1
    private var redirectAttempts = 0
    private var nextRedirectAt = 0L
    private var lastClickAt = -1_000L

    // Active watch-session tracking for DailyLimit mode
    private var activeWatchTarget: ShortVideoTarget? = null
    private var lastTickElapsed = 0L
    private var screenOffReceiverRegistered = false

    private val refresh = Runnable { updateBlock() }
    private val usageTick = Runnable { onUsageTick() }
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        updateTargets()
    }
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                endWatchSession()
                resetRedirect()
            }
        }
    }

    override fun onServiceConnected() {
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        if (!screenOffReceiverRegistered) {
            runCatching {
                registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
                screenOffReceiverRegistered = true
            }
        }
        updateTargets()
    }

    private fun updateTargets() {
        targets = store.activeTargets()
        serviceInfo = serviceInfo.apply {
            packageNames = targets.map { it.packageName }.ifEmpty { listOf(packageName) }.toTypedArray()
        }
        if (activeWatchTarget != null && activeWatchTarget !in targets) {
            endWatchSession()
        }
        resetRedirect()
        if (targets.isNotEmpty()) scheduleCheck()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val eventPkg = event.packageName?.toString()
        if (activeWatchTarget != null &&
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventPkg != null &&
            eventPkg != activeWatchTarget?.packageName
        ) {
            endWatchSession()
        }
        if (targets.any { it.packageName == eventPkg }) {
            if (event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED) {
                lastClickAt = SystemClock.uptimeMillis()
                if (redirectAttempts >= 2) redirectAttempts = 0
                handler.removeCallbacks(refresh)
                scheduleCheck(0)
            } else {
                scheduleCheck()
            }
        }
    }

    private fun scheduleCheck(delayMillis: Long = 40) {
        if (!handler.hasCallbacks(refresh)) handler.postDelayed(refresh, delayMillis)
    }

    private fun onUsageTick() {
        val watching = activeWatchTarget ?: return
        val root = rootInActiveWindow
        if (root == null || root.packageName?.toString() != watching.packageName || !isShortVideoViewer(root, watching)) {
            endWatchSession()
            return
        }
        val now = SystemClock.elapsedRealtime()
        val delta = (now - lastTickElapsed).coerceIn(0L, MAX_TICK_DELTA_MS)
        lastTickElapsed = now
        val rule = store.addUsageMillis(watching, delta)
        if (rule.shouldRedirectNow) {
            endWatchSession()
            scheduleCheck(0)
        } else {
            handler.postDelayed(usageTick, USAGE_TICK_INTERVAL_MS)
        }
    }

    private fun startOrContinueWatchSession(target: ShortVideoTarget) {
        val now = SystemClock.elapsedRealtime()
        if (activeWatchTarget != target) {
            endWatchSession()
            activeWatchTarget = target
            lastTickElapsed = now
            handler.postDelayed(usageTick, USAGE_TICK_INTERVAL_MS)
        } else if (!handler.hasCallbacks(usageTick)) {
            val delta = (now - lastTickElapsed).coerceIn(0L, MAX_TICK_DELTA_MS)
            lastTickElapsed = now
            store.addUsageMillis(target, delta)
            handler.postDelayed(usageTick, USAGE_TICK_INTERVAL_MS)
        }
    }

    private fun endWatchSession() {
        handler.removeCallbacks(usageTick)
        val watching = activeWatchTarget ?: return
        val now = SystemClock.elapsedRealtime()
        val delta = (now - lastTickElapsed).coerceIn(0L, MAX_TICK_DELTA_MS)
        if (delta > 0L) {
            store.addUsageMillis(watching, delta)
        }
        activeWatchTarget = null
        lastTickElapsed = 0L
    }

    private fun updateBlock() {
        val root = rootInActiveWindow
        val target = targets.firstOrNull { it.packageName == root?.packageName?.toString() }
        if (root == null || target == null || !isShortVideoViewer(root, target)) {
            endWatchSession()
            resetRedirect()
            if (target != null && SystemClock.uptimeMillis() - lastClickAt < 600) {
                scheduleCheck(120)
            }
            return
        }

        val rule = store.getRule(target)
        if (rule.mode == ShortVideoMode.Off) {
            endWatchSession()
            resetRedirect()
            return
        }
        if (rule.mode == ShortVideoMode.DailyLimit && !rule.isLimitReached) {
            // Allowed within daily budget: track time and let the user watch until budget runs out.
            resetRedirect()
            startOrContinueWatchSession(target)
            return
        }

        // Either BlockAll or DailyLimit exhausted — stop timer and redirect out of the viewer.
        endWatchSession()

        if (viewerTarget != target || viewerWindowId != root.windowId) {
            viewerTarget = target
            viewerWindowId = root.windowId
            redirectAttempts = 0
        }
        if (redirectAttempts >= 2) return

        val now = SystemClock.uptimeMillis()
        if (now < nextRedirectAt) {
            scheduleCheck(nextRedirectAt - now)
            return
        }

        if (redirectAttempts == 0) {
            store.recordBlock(target)
        }

        // Try the social app's own Home tab first. Full-screen viewers often hide it.
        val returnedHome = redirectAttempts == 0 && clickHomeTab(root, target)
        redirectAttempts = if (returnedHome) 1 else 2
        nextRedirectAt = now + 250
        if (returnedHome) {
            // Recheck the same viewer before falling back to Back.
            scheduleCheck(250)
        } else if (rootInActiveWindow?.let {
                it.windowId == root.windowId && it.packageName?.toString() == target.packageName &&
                    isShortVideoViewer(it, target)
            } == true
        ) {
            performGlobalAction(GLOBAL_ACTION_BACK)
            scheduleCheck(250)
        }
    }

    private fun isShortVideoViewer(root: AccessibilityNodeInfo, target: ShortVideoTarget): Boolean {
        return when (target) {
            ShortVideoTarget.YouTube -> root.hasVisibleId(target, "reel_recycler")
            ShortVideoTarget.Instagram ->
                root.hasVisibleId(target, "reel_item_toolbar_container") ||
                    root.hasVisibleId(target, "root_clips_layout") ||
                    root.hasVisibleId(target, "clips_author_username")
            ShortVideoTarget.Facebook -> visibleNodes(root).any { node ->
                node.hasLabel("Reel details") || node.hasLabel("Reels tab details")
            }
        }
    }

    private fun clickHomeTab(root: AccessibilityNodeInfo, target: ShortVideoTarget): Boolean {
        val homeTab = when (target) {
            ShortVideoTarget.Instagram -> root.findAccessibilityNodeInfosByViewId(
                "${target.packageName}:id/feed_tab",
            ).firstOrNull { it.isVisibleToUser && it.isClickable && it.isEnabled }
            ShortVideoTarget.YouTube -> root.findAccessibilityNodeInfosByViewId(
                "${target.packageName}:id/pivot_bar",
            ).asSequence().flatMap(::visibleNodes).firstOrNull {
                it.isClickable && it.isEnabled && it.hasLabel("Home")
            }
            ShortVideoTarget.Facebook -> visibleNodes(root).firstOrNull {
                it.isClickable && it.isEnabled && it.hasLabel("Home, tab 1 of 6")
            }
        }
        return homeTab?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
    }

    private fun AccessibilityNodeInfo.hasVisibleId(target: ShortVideoTarget, id: String): Boolean =
        findAccessibilityNodeInfosByViewId("${target.packageName}:id/$id").any { it.isVisibleToUser }

    private fun AccessibilityNodeInfo.hasLabel(label: String): Boolean =
        contentDescription?.toString()?.trim().equals(label, ignoreCase = true) ||
            text?.toString()?.trim().equals(label, ignoreCase = true)

    private fun visibleNodes(root: AccessibilityNodeInfo): Sequence<AccessibilityNodeInfo> = sequence {
        val pending = ArrayDeque<AccessibilityNodeInfo>()
        pending.add(root)
        var visited = 0
        while (pending.isNotEmpty() && visited++ < 900) {
            val node = pending.removeLast()
            if (!node.isVisibleToUser) continue
            yield(node)
            for (index in node.childCount - 1 downTo 0) node.getChild(index)?.let(pending::addLast)
        }
    }

    private fun resetRedirect() {
        handler.removeCallbacks(refresh)
        viewerTarget = null
        viewerWindowId = -1
        redirectAttempts = 0
    }

    override fun onInterrupt() {
        endWatchSession()
        resetRedirect()
    }

    override fun onDestroy() {
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        if (screenOffReceiverRegistered) {
            runCatching { unregisterReceiver(screenOffReceiver) }
            screenOffReceiverRegistered = false
        }
        endWatchSession()
        resetRedirect()
        super.onDestroy()
    }

    private companion object {
        const val USAGE_TICK_INTERVAL_MS = 1_000L
        const val MAX_TICK_DELTA_MS = 5_000L
    }
}
