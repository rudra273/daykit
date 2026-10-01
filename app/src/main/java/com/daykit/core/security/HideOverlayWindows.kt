package com.daykit.core.security

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import java.util.WeakHashMap

/**
 * Hides other apps' overlay windows while the calling composable is on screen,
 * so a malicious "display over other apps" window can't draw a fake keypad over
 * a credential prompt and capture the PIN or password.
 *
 * Call it from every surface that takes the master credential. It is ref-counted
 * per window, so a dialog inside an already protected screen doesn't switch the
 * protection off when it closes. Outside an activity (the App Lock overlay, which
 * is itself an overlay window) it does nothing.
 *
 * DayKit's own overlays (Event Light, the lock overlay) are hidden too while the
 * prompt is up, which is fine: neither is useful during credential entry.
 */
@Composable
fun HideOverlayWindows() {
    val window = LocalContext.current.findActivity()?.window ?: return
    DisposableEffect(window) {
        OverlayHider.acquire(window)
        onDispose { OverlayHider.release(window) }
    }
}

private object OverlayHider {
    private val holders = WeakHashMap<Window, Int>()

    fun acquire(window: Window) {
        val count = holders[window] ?: 0
        if (count == 0) window.setHideOverlayWindows(true)
        holders[window] = count + 1
    }

    fun release(window: Window) {
        val count = (holders[window] ?: return) - 1
        if (count <= 0) {
            holders.remove(window)
            window.setHideOverlayWindows(false)
        } else {
            holders[window] = count
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
