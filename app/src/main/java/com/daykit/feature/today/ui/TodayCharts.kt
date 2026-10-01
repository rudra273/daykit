package com.daykit.feature.today.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.extendedColors

/** 0 → 1 once, when the chart first enters composition. */
@Composable
internal fun rememberEntrance(durationMillis: Int = 900): Float {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis, easing = FastOutSlowInEasing))
    }
    return progress.value
}

internal data class Ring(val progress: Float, val color: Color)

/** Concentric progress rings, outermost first. */
@Composable
internal fun ActivityRings(
    rings: List<Ring>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 12.dp,
    gap: Dp = 4.dp,
    center: @Composable () -> Unit = {},
) {
    val entrance = rememberEntrance(1100)
    val track = MaterialTheme.extendedColors.inputField
    val animated = rings.map { ring ->
        animateFloatAsState(ring.progress.coerceIn(0f, 1f), tween(600), label = "ring").value
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val step = stroke + gap.toPx()
            rings.forEachIndexed { index, ring ->
                val inset = stroke / 2 + index * step
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                if (arcSize.width <= 0f) return@forEachIndexed
                val topLeft = Offset(inset, inset)
                drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                val sweep = 360f * animated[index] * entrance
                if (sweep > 0.5f) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            0f to ring.color.copy(alpha = 0.55f),
                            (sweep / 360f).coerceAtLeast(0.01f) to ring.color,
                            1f to ring.color,
                            center = this.center,
                        ),
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
        center()
    }
}

/**
 * Seven rounded bars with weekday labels. [highlight] gets the full accent; the rest
 * are faded so today reads first. Values are normalised to [maxValue] (or the largest).
 */
@Composable
internal fun WeekBarChart(
    values: List<Float>,
    labels: List<String>,
    color: Color,
    modifier: Modifier = Modifier,
    highlight: Int = values.lastIndex,
    maxValue: Float? = null,
    chartHeight: Dp = 84.dp,
    valueLabels: List<String>? = null,
) {
    val entrance = rememberEntrance()
    val track = MaterialTheme.extendedColors.inputField
    val muted = MaterialTheme.extendedColors.textMuted
    val max = (maxValue ?: values.maxOrNull() ?: 0f).coerceAtLeast(0.0001f)
    Column(modifier) {
        if (valueLabels != null) {
            Row(Modifier.fillMaxWidth()) {
                valueLabels.forEachIndexed { i, label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (i == highlight) color else muted,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xs))
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(chartHeight)
                .semantics { contentDescription = "Weekly chart" },
        ) {
            val slot = size.width / values.size
            val barWidth = (slot * 0.46f).coerceAtMost(28.dp.toPx())
            val radius = CornerRadius(barWidth / 2, barWidth / 2)
            values.forEachIndexed { i, value ->
                val left = slot * i + (slot - barWidth) / 2
                drawRoundRect(track, Offset(left, 0f), Size(barWidth, size.height), radius)
                val h = size.height * (value / max).coerceIn(0f, 1f) * entrance
                if (h > 0f) {
                    val barColor = if (i == highlight) color else color.copy(alpha = 0.45f)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(barColor, barColor.copy(alpha = barColor.alpha * 0.7f)),
                            startY = size.height - h,
                            endY = size.height,
                        ),
                        topLeft = Offset(left, size.height - h),
                        size = Size(barWidth, h.coerceAtLeast(barWidth)),
                        cornerRadius = radius,
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.xs))
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (i == highlight) FontWeight.Bold else FontWeight.Normal,
                    color = if (i == highlight) MaterialTheme.colorScheme.onSurface else muted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Cumulative spend across the month: a filled curve up to today, plus a dashed
 * straight line from 0 to [limit] at month end showing an even pace.
 */
@Composable
internal fun SpendTrendChart(
    cumulative: List<Long>,
    daysInMonth: Int,
    limit: Long,
    color: Color,
    modifier: Modifier = Modifier,
    paceColor: Color = MaterialTheme.extendedColors.textMuted,
) {
    val entrance = rememberEntrance(1000)
    val grid = MaterialTheme.extendedColors.divider
    Canvas(modifier.semantics { contentDescription = "Spending this month" }) {
        if (cumulative.isEmpty() || daysInMonth < 2) return@Canvas
        val top = (maxOf(cumulative.last(), limit).toFloat() * 1.08f).coerceAtLeast(1f)
        val pad = 6.dp.toPx()
        val w = size.width - pad * 2
        val h = size.height - pad * 2
        fun x(day: Int) = pad + w * day / (daysInMonth - 1).toFloat()
        fun y(v: Long) = pad + h - h * (v / top)

        for (i in 0..3) {
            val gy = pad + h * i / 3f
            drawLine(grid, Offset(pad, gy), Offset(size.width - pad, gy), 1.dp.toPx())
        }
        if (limit > 0) {
            drawLine(
                paceColor.copy(alpha = 0.7f),
                Offset(x(0), y(0)),
                Offset(x(daysInMonth - 1), y(limit)),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
            )
        }

        val visible = (cumulative.size * entrance).toInt().coerceIn(1, cumulative.size)
        val line = Path()
        cumulative.take(visible).forEachIndexed { day, value ->
            val px = x(day)
            val py = y(value)
            if (day == 0) line.moveTo(px, py) else {
                // Smooth with a horizontal-tangent cubic between neighbours.
                val prevX = x(day - 1)
                val prevY = y(cumulative[day - 1])
                val mid = (prevX + px) / 2
                line.cubicTo(mid, prevY, mid, py, px, py)
            }
        }
        val lastX = x(visible - 1)
        val area = Path().apply {
            addPath(line)
            lineTo(lastX, pad + h)
            lineTo(x(0), pad + h)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0f)), startY = pad, endY = pad + h))
        drawPath(line, color, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        val lastY = y(cumulative[visible - 1])
        drawCircle(color.copy(alpha = 0.25f), 7.dp.toPx(), Offset(lastX, lastY))
        drawCircle(color, 4.dp.toPx(), Offset(lastX, lastY))
    }
}

internal data class Slice(val label: String, val value: Long, val color: Color)

/** Donut with rounded, gapped segments. */
@Composable
internal fun DonutChart(
    slices: List<Slice>,
    modifier: Modifier = Modifier,
    strokeWidth: Dp = 14.dp,
    center: @Composable () -> Unit = {},
) {
    val entrance = rememberEntrance(1000)
    val track = MaterialTheme.extendedColors.inputField
    val total = slices.sumOf { it.value }.toFloat()
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            if (total <= 0f) return@Canvas
            val gap = if (slices.size > 1) 6f else 0f
            var start = -90f
            slices.forEach { slice ->
                val sweep = 360f * (slice.value / total) * entrance
                if (sweep - gap > 0.5f) {
                    drawArc(
                        slice.color, start + gap / 2, sweep - gap, false,
                        Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt),
                    )
                }
                start += sweep
            }
        }
        center()
    }
}

/** A legend swatch + label + value row. */
@Composable
internal fun LegendRow(color: Color, label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Canvas(Modifier.size(10.dp)) { drawCircle(color) }
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.extendedColors.textMuted,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
    }
}
