package com.daykit.feature.eventlight.service

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.daykit.feature.eventlight.data.EventLightSettings

/**
 * Draws a colored border overlay on top of every other app. Unlike
 * [com.daykit.feature.applock.service.LockOverlayController], this overlay must never
 * intercept touches or keys — the app underneath (e.g. a video call app) needs all
 * input — so it is not-focusable/not-touchable and skips FLAG_SECURE entirely.
 */
class EventLightOverlayController(private val context: Context) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var composeView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null
    private var settingsState: androidx.compose.runtime.MutableState<EventLightSettings>? = null

    fun show(settings: EventLightSettings) {
        if (composeView != null) {
            update(settings)
            return
        }

        val owner = OverlayLifecycleOwner().also { it.start() }
        val state = mutableStateOf(settings)
        val view = ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                val current by state
                EventLightBorderContent(current)
            }
        }

        windowManager.addView(view, layoutParams())
        composeView = view
        lifecycleOwner = owner
        settingsState = state
    }

    fun update(settings: EventLightSettings) {
        settingsState?.value = settings
    }

    fun dismiss() {
        composeView?.let { runCatching { windowManager.removeView(it) } }
        composeView = null
        settingsState = null
        lifecycleOwner?.destroy()
        lifecycleOwner = null
    }

    private fun layoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            // Window-level alpha is separate from per-pixel content alpha and defaults
            // to 1f, but set it explicitly so nothing can cap the border below fully
            // opaque regardless of the opacity setting.
            alpha = 1f
        }
    }
}

@Composable
private fun EventLightBorderContent(settings: EventLightSettings) {
    // Brightness scales color intensity (toward black), independent of opacity, so
    // "fully solid" on the transparency control always means alpha = 1 regardless of
    // where brightness is set — the two controls no longer multiply into one alpha.
    val base = Color(settings.colorArgb)
    val color = Color(
        red = base.red * settings.brightness,
        green = base.green * settings.brightness,
        blue = base.blue * settings.brightness,
        alpha = settings.opacity,
    )
    val thickness = settings.thicknessDp.dp

    Box(Modifier.fillMaxSize()) {
        if (settings.topEnabled) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(thickness)
                    .align(Alignment.TopCenter)
                    .background(color),
            )
        }
        if (settings.bottomEnabled) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(thickness)
                    .align(Alignment.BottomCenter)
                    .background(color),
            )
        }
        if (settings.leftEnabled) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(thickness)
                    .align(Alignment.CenterStart)
                    .background(color),
            )
        }
        if (settings.rightEnabled) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(thickness)
                    .align(Alignment.CenterEnd)
                    .background(color),
            )
        }
    }
}

private class OverlayLifecycleOwner :
    LifecycleOwner,
    SavedStateRegistryOwner,
    ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val viewModelStore = ViewModelStore()
    override val lifecycle: Lifecycle = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    fun start() {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}
