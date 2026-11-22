package com.daykit.feature.eventlight.service

import android.content.Context
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.Dp
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
import com.daykit.feature.eventlight.data.edgeLightTintColor

/**
 * Draws a continuous macOS-style glowing rounded-rectangle ring light overlay on top
 * of every other app. Unlike [com.daykit.feature.applock.service.LockOverlayController],
 * this overlay must never intercept touches or keys — the app underneath (e.g. a video
 * call app) needs all input — so it is not-focusable/not-touchable and skips FLAG_SECURE.
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
                EventLightRingCanvas(
                    thicknessDp = current.thicknessDp,
                    colorTemp = current.colorTemp,
                    cornerRadiusDp = 42.dp,
                    edgeInsetDp = 8.dp,
                    modifier = Modifier.fillMaxSize(),
                )
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
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            alpha = 1f
        }
    }
}

/**
 * Renders the multi-layered feathered macOS Edge Light ring:
 * - Wide ambient glow in the selected color temperature
 * - Soft mid bloom blending the tint into white
 * - Bright incandescent core tube with slight feathering (no hard harsh edge)
 * - Subtle inner white highlight giving luminous LED depth
 */
@Composable
fun EventLightRingCanvas(
    thicknessDp: Float,
    colorTemp: Float,
    cornerRadiusDp: Dp = 42.dp,
    edgeInsetDp: Dp = 8.dp,
    modifier: Modifier = Modifier,
) {
    val tint = edgeLightTintColor(colorTemp)
    val outerGlowColor = tint.copy(alpha = 0.52f).toArgb()
    val midBloomColor = lerp(tint, Color.White, 0.30f).copy(alpha = 0.82f).toArgb()
    val coreTubeColor = lerp(Color.White, tint, 0.24f).copy(alpha = 0.97f).toArgb()
    val innerFilamentColor = Color.White.copy(alpha = 0.84f).toArgb()

    val outerPaint = remember { createRingPaint() }
    val midPaint = remember { createRingPaint() }
    val corePaint = remember { createRingPaint() }
    val innerPaint = remember { createRingPaint() }
    val rect = remember { RectF() }

    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas

        val thicknessPx = thicknessDp.dp.toPx().coerceAtLeast(2f)
        val inset = edgeInsetDp.toPx() + thicknessPx * 0.55f
        val maxInset = (size.minDimension * 0.35f).coerceAtLeast(1f)
        val safeInset = inset.coerceAtMost(maxInset)

        rect.set(
            safeInset,
            safeInset,
            size.width - safeInset,
            size.height - safeInset,
        )
        if (rect.width() <= 0f || rect.height() <= 0f) return@Canvas

        val cornerRadius = cornerRadiusDp.toPx()
            .coerceAtMost(minOf(rect.width(), rect.height()) * 0.28f)

        // 1. Wide ambient glow
        outerPaint.color = outerGlowColor
        outerPaint.strokeWidth = thicknessPx * 2.15f
        outerPaint.maskFilter = BlurMaskFilter(
            (thicknessPx * 1.45f).coerceAtLeast(1f),
            BlurMaskFilter.Blur.NORMAL,
        )

        // 2. Mid bloom halo
        midPaint.color = midBloomColor
        midPaint.strokeWidth = thicknessPx * 1.30f
        midPaint.maskFilter = BlurMaskFilter(
            (thicknessPx * 0.65f).coerceAtLeast(1f),
            BlurMaskFilter.Blur.NORMAL,
        )

        // 3. Incandescent luminous core tube
        corePaint.color = coreTubeColor
        corePaint.strokeWidth = thicknessPx * 0.78f
        corePaint.maskFilter = BlurMaskFilter(
            (thicknessPx * 0.20f).coerceAtLeast(0.5f),
            BlurMaskFilter.Blur.NORMAL,
        )

        // 4. Inner bright specular filament
        innerPaint.color = innerFilamentColor
        innerPaint.strokeWidth = thicknessPx * 0.36f
        innerPaint.maskFilter = BlurMaskFilter(
            (thicknessPx * 0.08f).coerceAtLeast(0.5f),
            BlurMaskFilter.Blur.NORMAL,
        )

        drawIntoCanvas { canvas ->
            val nc = canvas.nativeCanvas
            nc.drawRoundRect(rect, cornerRadius, cornerRadius, outerPaint)
            nc.drawRoundRect(rect, cornerRadius, cornerRadius, midPaint)
            nc.drawRoundRect(rect, cornerRadius, cornerRadius, corePaint)
            nc.drawRoundRect(rect, cornerRadius, cornerRadius, innerPaint)
        }
    }
}

private fun createRingPaint(): Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
    style = Paint.Style.STROKE
    strokeJoin = Paint.Join.ROUND
    strokeCap = Paint.Cap.ROUND
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
