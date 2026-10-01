package com.daykit.core.designsystem.background

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.toSize

/** Paints the page background behind this (full-screen) element. */
@Composable
fun Modifier.pageBackground(): Modifier =
    this then BackdropElement(LocalPageBackground.current, frosted = false, shape = RectangleShape, tint = Color.Transparent)

/**
 * Glass backing: the frosted page background that lies behind this element,
 * clipped to [shape] and washed with [tint]. Because the background is ours and
 * drawn in window coordinates, this is a true see-through look without a
 * (costly, API-limited) live backdrop blur.
 */
@Composable
fun Modifier.frostedBackdrop(shape: Shape, tint: Color): Modifier =
    this then BackdropElement(LocalPageBackground.current, frosted = true, shape = shape, tint = tint)

private data class BackdropElement(
    val background: PageBackground,
    val frosted: Boolean,
    val shape: Shape,
    val tint: Color,
) : ModifierNodeElement<BackdropNode>() {
    override fun create() = BackdropNode(background, frosted, shape, tint)

    override fun update(node: BackdropNode) {
        node.background = background
        node.frosted = frosted
        node.shape = shape
        node.tint = tint
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = if (frosted) "frostedBackdrop" else "pageBackground"
        properties["shape"] = shape
        properties["tint"] = tint
    }
}

/**
 * Tracks where the element sits in the window and redraws only when that moves
 * (scrolling). Position changes invalidate draw, never composition or layout.
 */
private class BackdropNode(
    var background: PageBackground,
    var frosted: Boolean,
    var shape: Shape,
    var tint: Color,
) : Modifier.Node(), DrawModifierNode, GlobalPositionAwareModifierNode {
    private var origin = Offset.Zero
    private var window = Size.Zero

    private var outlineSize = Size.Unspecified
    private var outlineShape: Shape? = null
    private var clip: Path? = null

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        val newOrigin = coordinates.positionInWindow()
        val newWindow = coordinates.findRootCoordinates().size.toSize()
        if (newOrigin != origin || newWindow != window) {
            origin = newOrigin
            window = newWindow
            invalidateDraw()
        }
    }

    override fun ContentDrawScope.draw() {
        val path = clipPathFor(size)
        if (path == null) {
            drawBackdrop()
        } else {
            clipPath(path) { drawBackdrop() }
        }
        drawContent()
    }

    private fun DrawScope.drawBackdrop() {
        with(background) { drawRegion(origin, window, frosted) }
        if (tint.alpha > 0f) drawRect(tint)
    }

    /** Null for a rectangle (no clip needed); cached until the size or shape changes. */
    private fun ContentDrawScope.clipPathFor(size: Size): Path? {
        if (shape === RectangleShape) return null
        if (size != outlineSize || shape != outlineShape) {
            val outline = shape.createOutline(size, layoutDirection, this)
            clip = if (outline is Outline.Rectangle) null else Path().apply { addOutline(outline) }
            outlineSize = size
            outlineShape = shape
        }
        return clip
    }
}
