package com.daykit.core.designsystem.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * In-window backdrop blur (the "haze" technique). A scrollable content root records
 * itself into a [GraphicsLayer] via [frostSource]; a bar drawn on top samples that
 * layer with a [BlurEffect] via [frostedBackdrop]. No cross-window blur (unavailable
 * for Popup-based surfaces); dialogs use platform FLAG_BLUR_BEHIND, sheets a scrim,
 * lock screens a self-blur.
 *
 * Graceful fallback: pass [FrostState] = null to the bar for a solid tint + hairline.
 */
class FrostState internal constructor(
    internal val layer: GraphicsLayer,
) {
    // Window bounds of the content root, so bars can offset-sample the correct region.
    internal var sourceTopLeft: Offset = Offset.Zero
    internal var barTopLeft: Offset = Offset.Zero
}

@Composable
fun rememberFrostState(): FrostState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { FrostState(layer) }
}

/** Apply to the screen's scrollable content root. Draws content exactly once, via the layer. */
fun Modifier.frostSource(state: FrostState): Modifier = this
    .onGloballyPositioned { state.sourceTopLeft = it.boundsInWindow().topLeft }
    .drawWithContent {
        state.layer.record { this@drawWithContent.drawContent() }
        drawLayer(state.layer)
    }

/**
 * Apply to a top bar / navigation bar overlaying [frostSource] content.
 * Falls back to a solid [tint] fill when [state] is null.
 */
fun Modifier.frostedBackdrop(
    state: FrostState?,
    tint: Color,
    blurRadius: Dp = 24.dp,
    @Suppress("UNUSED_PARAMETER") shape: Shape? = null,
): Modifier {
    if (state == null) {
        return this.drawWithContent {
            drawRect(tint)
            drawContent()
        }
    }
    return this
        .onGloballyPositioned { state.barTopLeft = it.boundsInWindow().topLeft }
        .drawWithContent {
            state.layer.renderEffect = BlurEffect(blurRadius.toPx(), blurRadius.toPx(), TileMode.Clamp)
            // Sample the region of the source that sits behind this bar.
            val dx = state.sourceTopLeft.x - state.barTopLeft.x
            val dy = state.sourceTopLeft.y - state.barTopLeft.y
            translate(left = dx, top = dy) {
                drawLayer(state.layer)
            }
            drawRect(tint)
            drawContent()
        }
}
