package com.daykit.feature.focus.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.daykit.R

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
    private var targets = emptyList<ShortVideoTarget>()
    private var viewerTarget: ShortVideoTarget? = null
    private var viewerWindowId = -1
    private var redirectAttempts = 0
    private var nextRedirectAt = 0L
    private var lastClickAt = -1_000L
    private val refresh = Runnable { updateBlock() }
    private val preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        updateTargets()
    }

    override fun onServiceConnected() {
        preferences.registerOnSharedPreferenceChangeListener(preferenceListener)
        updateTargets()
    }

    private fun updateTargets() {
        targets = ShortVideoTarget.entries.filter {
            preferences.getBoolean(SHORT_VIDEO_CONSENT, false) &&
                preferences.getBoolean(it.packageName, false)
        }
        serviceInfo = serviceInfo.apply {
            packageNames = targets.map { it.packageName }.ifEmpty { listOf(packageName) }.toTypedArray()
        }
        resetRedirect()
        if (targets.isNotEmpty()) scheduleCheck()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (targets.any { it.packageName == event.packageName?.toString() }) {
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

    private fun updateBlock() {
        val root = rootInActiveWindow
        val target = targets.firstOrNull { it.packageName == root?.packageName?.toString() }
        if (root == null || target == null || !isShortVideoViewer(root, target)) {
            resetRedirect()
            if (target != null && SystemClock.uptimeMillis() - lastClickAt < 600) {
                scheduleCheck(120)
            }
            return
        }
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

    override fun onInterrupt() = resetRedirect()

    override fun onDestroy() {
        preferences.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        resetRedirect()
        super.onDestroy()
    }
}
