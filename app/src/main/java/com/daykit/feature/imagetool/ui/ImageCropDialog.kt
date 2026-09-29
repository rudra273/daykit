package com.daykit.feature.imagetool.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.FilterChipButton
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.components.SecondaryButton
import com.daykit.feature.imagetool.domain.CropRect
import kotlin.math.max
import kotlin.math.roundToInt

private const val MAX_ZOOM = 8f

/** Aspect presets; a null ratio means "match the image". */
private enum class CropAspect(val label: String, val ratio: Float?) {
    FREE("Free", null),
    ORIGINAL("Original", null),
    SQUARE("1:1", 1f),
    R43("4:3", 4f / 3f),
    R34("3:4", 3f / 4f),
    R169("16:9", 16f / 9f),
    R916("9:16", 9f / 16f),
}

/**
 * Full-screen crop editor: pinch to zoom, drag to pan the image under a fixed frame.
 * Reports the frame as fractions of the image, so the caller can apply it at any resolution.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ImageCropDialog(
    image: ImageBitmap,
    onDismiss: () -> Unit,
    onConfirm: (CropRect) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        var aspect by remember { mutableStateOf(CropAspect.FREE) }
        var zoom by remember { mutableFloatStateOf(1f) }
        var pan by remember { mutableStateOf(Offset.Zero) }
        var frameRect by remember { mutableStateOf(Rect.Zero) }
        var imageRect by remember { mutableStateOf(Rect.Zero) }
        var freeFrame by remember { mutableStateOf<Rect?>(null) }

        Column(
            modifier = Modifier.fillMaxSize().background(Color.Black).systemBarsPadding().padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val boxW = constraints.maxWidth.toFloat()
                val boxH = constraints.maxHeight.toFloat()
                val margin = with(androidx.compose.ui.platform.LocalDensity.current) { 12.dp.toPx() }
                val availW = boxW - 2 * margin
                val availH = boxH - 2 * margin
                val imgW = image.width.toFloat()
                val imgH = image.height.toFloat()
                val free = aspect == CropAspect.FREE
                val ratio = aspect.ratio ?: (imgW / imgH)
                val frameW = if (ratio > availW / availH) availW else availH * ratio
                val frameH = if (ratio > availW / availH) availW / ratio else availH
                val center = Offset(boxW / 2, boxH / 2)

                // Base scale makes the image just cover the frame; zoom multiplies on top.
                val cover = max(frameW / imgW, frameH / imgH)
                fun clampPan(p: Offset, z: Float): Offset {
                    val maxX = (imgW * cover * z - frameW) / 2
                    val maxY = (imgH * cover * z - frameH) / 2
                    return Offset(p.x.coerceIn(-maxX, maxX), p.y.coerceIn(-maxY, maxY))
                }
                // Free mode: the image stays fitted and the frame itself is what moves.
                val fit = minOf(availW / imgW, availH / imgH)
                val scale = if (free) fit else cover * zoom
                val shownW = imgW * scale
                val shownH = imgH * scale
                val imgLeft = center.x + pan.x - shownW / 2
                val imgTop = center.y + pan.y - shownH / 2
                val frameLeft = center.x - frameW / 2
                val frameTop = center.y - frameH / 2
                imageRect = Rect(imgLeft, imgTop, imgLeft + shownW, imgTop + shownH)
                val activeFrame = if (free) (freeFrame ?: imageRect) else Rect(frameLeft, frameTop, frameLeft + frameW, frameTop + frameH)
                frameRect = activeFrame
                val minFrame = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }
                val handleSlop = with(androidx.compose.ui.platform.LocalDensity.current) { 32.dp.toPx() }

                Canvas(
                    modifier = Modifier.fillMaxSize().then(
                        if (free) {
                            Modifier.pointerInput(imageRect) {
                                awaitEachGesture {
                                    val down = awaitFirstDown()
                                    val start = freeFrame ?: imageRect
                                    val hit = FrameHit.at(start, down.position, handleSlop)
                                    do {
                                        val event = awaitPointerEvent()
                                        val pressed = event.changes.filter { it.pressed }
                                        val f = freeFrame ?: imageRect
                                        if (pressed.size >= 2) {
                                            freeFrame = f.scaledAboutCenter(event.calculateZoom(), imageRect, minFrame)
                                            event.changes.forEach { it.consume() }
                                        } else if (pressed.size == 1 && hit != null) {
                                            freeFrame = hit.apply(f, pressed[0].positionChange(), imageRect, minFrame)
                                            pressed[0].consume()
                                        }
                                    } while (event.changes.any { it.pressed })
                                }
                            }
                        } else {
                            Modifier.pointerInput(aspect, frameW, frameH) {
                                detectTransformGestures { centroid, panChange, gestureZoom, _ ->
                                    val newZoom = (zoom * gestureZoom).coerceIn(1f, MAX_ZOOM)
                                    val applied = newZoom / zoom
                                    // Zoom about the fingers, then follow their movement.
                                    val about = centroid - center
                                    pan = clampPan((pan - about) * applied + about + panChange, newZoom)
                                    zoom = newZoom
                                }
                            }
                        },
                    ),
                ) {
                    drawImage(
                        image = image,
                        dstOffset = IntOffset(imgLeft.roundToInt(), imgTop.roundToInt()),
                        dstSize = IntSize(shownW.roundToInt(), shownH.roundToInt()),
                    )
                    val frameLeft = activeFrame.left
                    val frameTop = activeFrame.top
                    val frameW = activeFrame.width
                    val frameH = activeFrame.height
                    val dim = Color.Black.copy(alpha = 0.6f)
                    drawRect(dim, Offset.Zero, Size(size.width, frameTop))
                    drawRect(dim, Offset(0f, frameTop + frameH), Size(size.width, size.height - frameTop - frameH))
                    drawRect(dim, Offset(0f, frameTop), Size(frameLeft, frameH))
                    drawRect(dim, Offset(frameLeft + frameW, frameTop), Size(size.width - frameLeft - frameW, frameH))
                    drawRect(Color.White, Offset(frameLeft, frameTop), Size(frameW, frameH), style = Stroke(2.dp.toPx()))
                    for (i in 1..2) { // rule-of-thirds guides
                        val gx = frameLeft + frameW * i / 3
                        val gy = frameTop + frameH * i / 3
                        drawLine(Color.White.copy(alpha = 0.35f), Offset(gx, frameTop), Offset(gx, frameTop + frameH), 1.dp.toPx())
                        drawLine(Color.White.copy(alpha = 0.35f), Offset(frameLeft, gy), Offset(frameLeft + frameW, gy), 1.dp.toPx())
                    }
                    if (free) {
                        val xs = listOf(frameLeft, frameLeft + frameW / 2, frameLeft + frameW)
                        val ys = listOf(frameTop, frameTop + frameH / 2, frameTop + frameH)
                        for (x in xs) for (y in ys) {
                            if (x == xs[1] && y == ys[1]) continue
                            drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(x, y))
                        }
                    }
                }
            }

            Text(if (aspect == CropAspect.FREE) "Drag corners or edges · drag inside to move · pinch to resize" else "Pinch to zoom · drag to move", color = Color.White.copy(alpha = 0.7f))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                CropAspect.entries.forEach {
                    FilterChipButton(it.label, selected = aspect == it) {
                        aspect = it; zoom = 1f; pan = Offset.Zero; freeFrame = null
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SecondaryButton(text = "Cancel", modifier = Modifier.weight(1f), onClick = onDismiss)
                PrimaryButton(
                    text = "Apply crop",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val w = imageRect.width
                        val h = imageRect.height
                        onConfirm(
                            CropRect(
                                left = ((frameRect.left - imageRect.left) / w).coerceIn(0f, 1f),
                                top = ((frameRect.top - imageRect.top) / h).coerceIn(0f, 1f),
                                right = ((frameRect.right - imageRect.left) / w).coerceIn(0f, 1f),
                                bottom = ((frameRect.bottom - imageRect.top) / h).coerceIn(0f, 1f),
                            ),
                        )
                    },
                )
            }
        }
    }
}

/** Which parts of the free frame a touch grabbed: an edge/corner resize, or a whole-frame move. */
private class FrameHit(val left: Boolean, val top: Boolean, val right: Boolean, val bottom: Boolean) {
    private val move get() = !left && !top && !right && !bottom

    fun apply(f: Rect, d: Offset, bounds: Rect, min: Float): Rect {
        if (move) {
            val dx = d.x.coerceIn(bounds.left - f.left, bounds.right - f.right)
            val dy = d.y.coerceIn(bounds.top - f.top, bounds.bottom - f.bottom)
            return f.translate(dx, dy)
        }
        return Rect(
            left = if (left) (f.left + d.x).coerceIn(bounds.left, f.right - min) else f.left,
            top = if (top) (f.top + d.y).coerceIn(bounds.top, f.bottom - min) else f.top,
            right = if (right) (f.right + d.x).coerceIn(f.left + min, bounds.right) else f.right,
            bottom = if (bottom) (f.bottom + d.y).coerceIn(f.top + min, bounds.bottom) else f.bottom,
        )
    }

    companion object {
        fun at(f: Rect, p: Offset, slop: Float): FrameHit? {
            val withinX = p.x in (f.left - slop)..(f.right + slop)
            val withinY = p.y in (f.top - slop)..(f.bottom + slop)
            val l = withinY && kotlin.math.abs(p.x - f.left) <= slop
            val r = withinY && kotlin.math.abs(p.x - f.right) <= slop
            val t = withinX && kotlin.math.abs(p.y - f.top) <= slop
            val b = withinX && kotlin.math.abs(p.y - f.bottom) <= slop
            // A small frame can put both edges in reach; prefer the nearer one.
            val left = l && (!r || kotlin.math.abs(p.x - f.left) <= kotlin.math.abs(p.x - f.right))
            val right = r && !left
            val top = t && (!b || kotlin.math.abs(p.y - f.top) <= kotlin.math.abs(p.y - f.bottom))
            val bottom = b && !top
            if (left || right || top || bottom) return FrameHit(left, top, right, bottom)
            return if (f.contains(p)) FrameHit(false, false, false, false) else null
        }
    }
}

private fun Rect.scaledAboutCenter(factor: Float, bounds: Rect, min: Float): Rect {
    if (factor <= 0f || factor.isNaN()) return this
    val w = (width * factor).coerceIn(min, bounds.width)
    val h = (height * factor).coerceIn(min, bounds.height)
    val cx = center.x.coerceIn(bounds.left + w / 2, bounds.right - w / 2)
    val cy = center.y.coerceIn(bounds.top + h / 2, bounds.bottom - h / 2)
    return Rect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
}
