package com.daykit.navigation

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

internal fun adjacentTopLevelRoute(route: String?, forward: Boolean): String? {
    val tabs = TopLevelDestination.entries
    val index = tabs.indexOfFirst { it.route == route }
    if (index < 0) return null
    return tabs.getOrNull(index + if (forward) 1 else -1)?.route
}

/** Children get the first chance to consume drags (e.g. a horizontal chart). */
internal fun Modifier.tabSwipeNavigation(
    route: String?,
    thresholdPx: Float,
    edgePx: Float,
    isRtl: Boolean,
    onNavigate: (String) -> Unit,
): Modifier = if (route !in TOP_LEVEL_ROUTES) this else pointerInput(route, isRtl) {
    var distance = 0f
    var startedAtEdge = false
    detectHorizontalDragGestures(
        onDragStart = { position ->
            distance = 0f
            startedAtEdge = position.x < edgePx || position.x > size.width - edgePx
        },
        onHorizontalDrag = { _, amount -> distance += amount },
        onDragCancel = { distance = 0f },
        onDragEnd = {
            if (!startedAtEdge && kotlin.math.abs(distance) >= thresholdPx) {
                adjacentTopLevelRoute(route, forward = if (isRtl) distance > 0 else distance < 0)
                    ?.let(onNavigate)
            }
            distance = 0f
        },
    )
}
