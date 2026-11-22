package com.daykit.core.designsystem

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * True when the user has enabled Android's "Remove animations" / Reduce Motion setting
 * (`ANIMATOR_DURATION_SCALE == 0f`). Motion primitives check this to replace spatial
 * translations and spring bounces with subtle cross-fades or instant state transitions.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * True when the user prefers high contrast / reduced transparency in system accessibility
 * settings. Translucent glass surfaces fall back to crisp opaque elevated fills.
 */
val LocalReduceTransparency = staticCompositionLocalOf { false }

private fun isAnimatorDisabled(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}.getOrDefault(false)

private fun isHighTextContrastEnabled(context: Context): Boolean = runCatching {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
    val method = AccessibilityManager::class.java.getDeclaredMethod("isHighTextContrastEnabled")
    method.isAccessible = true
    (method.invoke(am) as? Boolean) == true
}.getOrDefault(false)

@Composable
internal fun rememberSystemReduceMotion(): State<Boolean> {
    val context = LocalContext.current
    val state = remember(context) { mutableStateOf(isAnimatorDisabled(context)) }
    DisposableEffect(context) {
        val resolver = context.contentResolver
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                state.value = isAnimatorDisabled(context)
            }
        }
        if (uri != null) {
            runCatching { resolver.registerContentObserver(uri, false, observer) }
        }
        onDispose {
            runCatching { resolver.unregisterContentObserver(observer) }
        }
    }
    return state
}

@Composable
internal fun rememberSystemReduceTransparency(): State<Boolean> {
    val context = LocalContext.current
    return remember(context) { mutableStateOf(isHighTextContrastEnabled(context)) }
}
