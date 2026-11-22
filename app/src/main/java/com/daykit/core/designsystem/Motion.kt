package com.daykit.core.designsystem

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset

/**
 * Apple HIG spring physics and motion tokens, with automatic [LocalReduceMotion] compliance.
 */
object DayKitMotion {
    /** Critically-damped iOS navigation push/pop spring. */
    val NavOffsetSpring: FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = 0.92f,
        stiffness = 420f,
    )

    /** Smooth tab-bar slide spring. */
    val TabOffsetSpring: FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = 0.88f,
        stiffness = 460f,
    )

    /** Responsive control spring for switches, segmented thumbs, and drag lifts. */
    fun <T> controlSpring(): FiniteAnimationSpec<T> = spring(
        dampingRatio = 0.78f,
        stiffness = Spring.StiffnessMedium,
    )

    /** Quick tactile spring for card & button press feedback. */
    val PressFloatSpring: AnimationSpec<Float> = spring(
        dampingRatio = 0.75f,
        stiffness = 650f,
    )

    val FadeFast: FiniteAnimationSpec<Float> = tween(140)
}

/**
 * Apple-style tactile press-scale micro-interaction.
 *
 * Non-consumingly observes pointer down/up in [PointerEventPass.Initial] so standard
 * `clickable`, `LazyColumn`, and `LazyVerticalGrid` scroll gestures work without
 * interference. Automatically becomes a no-op when [LocalReduceMotion] is enabled.
 */
fun Modifier.pressScale(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
): Modifier = composed {
    if (!enabled || LocalReduceMotion.current) return@composed this
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = DayKitMotion.PressFloatSpring,
        label = "pressScale",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(enabled) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                pressed = true
                waitForUpOrCancellation(pass = PointerEventPass.Initial)
                pressed = false
            }
        }
}
