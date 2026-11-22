package com.daykit.feature.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.widget.RemoteViews
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.daykit.R
import com.daykit.core.permissions.PermissionIntents
import com.daykit.feature.eventlight.data.EVENT_LIGHT_MAX_THICKNESS_DP
import com.daykit.feature.eventlight.data.EVENT_LIGHT_MIN_THICKNESS_DP
import com.daykit.feature.eventlight.data.EventLightGradientStops
import com.daykit.feature.eventlight.data.EventLightSettings
import com.daykit.feature.eventlight.data.EventLightStore
import com.daykit.feature.eventlight.data.edgeLightTintColor
import com.daykit.feature.eventlight.service.EventLightService
import com.daykit.feature.eventlight.service.FrontCameraMonitor
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class EventLightWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        updateWidgets(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidgets(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE -> {
                val current = EventLightStore.get(context)
                if (current.enabled) turnOff(context) else turnOn(context)
            }
            ACTION_TURN_ON -> turnOn(context)
            ACTION_TURN_OFF -> turnOff(context)
            ACTION_SET_THICKNESS -> {
                val fraction = intent.getFloatExtra(EXTRA_FRACTION, -1f)
                if (fraction in 0f..1f) {
                    val dp = EVENT_LIGHT_MIN_THICKNESS_DP +
                        fraction * (EVENT_LIGHT_MAX_THICKNESS_DP - EVENT_LIGHT_MIN_THICKNESS_DP)
                    EventLightStore.setThickness(context, dp)
                    refreshAll(context)
                }
            }
            ACTION_SET_COLOR_TEMP -> {
                val fraction = intent.getFloatExtra(EXTRA_FRACTION, -1f)
                if (fraction in 0f..1f) {
                    EventLightStore.setColorTemp(context, fraction)
                    refreshAll(context)
                }
            }
        }
    }

    private fun turnOn(context: Context) {
        FrontCameraMonitor.dismissNotification(context)
        if (!Settings.canDrawOverlays(context)) {
            runCatching {
                val settingsIntent = PermissionIntents.overlaySettings(context).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(settingsIntent)
            }
            refreshAll(context)
            return
        }
        EventLightStore.setEnabled(context, true)
        EventLightService.start(context)
        refreshAll(context)
    }

    private fun turnOff(context: Context) {
        FrontCameraMonitor.dismissNotification(context)
        EventLightStore.setEnabled(context, false)
        EventLightService.stop(context)
        refreshAll(context)
    }

    companion object {
        const val ACTION_TOGGLE = "com.daykit.feature.widget.ACTION_TOGGLE_EVENT_LIGHT"
        const val ACTION_TURN_ON = "com.daykit.feature.widget.ACTION_TURN_ON_EVENT_LIGHT"
        const val ACTION_TURN_OFF = "com.daykit.feature.widget.ACTION_TURN_OFF_EVENT_LIGHT"
        const val ACTION_SET_THICKNESS = "com.daykit.feature.widget.ACTION_SET_EVENT_LIGHT_THICKNESS"
        const val ACTION_SET_COLOR_TEMP = "com.daykit.feature.widget.ACTION_SET_EVENT_LIGHT_TEMP"
        private const val EXTRA_FRACTION = "extra_fraction"
        private const val WIDE_THRESHOLD_DP = 210

        private val THICK_HIT_IDS = intArrayOf(
            R.id.hit_thick_0,
            R.id.hit_thick_1,
            R.id.hit_thick_2,
            R.id.hit_thick_3,
            R.id.hit_thick_4,
            R.id.hit_thick_5,
            R.id.hit_thick_6,
        )

        private val TEMP_HIT_IDS = intArrayOf(
            R.id.hit_temp_0,
            R.id.hit_temp_1,
            R.id.hit_temp_2,
            R.id.hit_temp_3,
            R.id.hit_temp_4,
            R.id.hit_temp_5,
            R.id.hit_temp_6,
        )

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, EventLightWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                updateWidgets(context, manager, ids)
            }
        }

        fun updateWidgets(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
            if (appWidgetIds.isEmpty()) return
            val settings = EventLightStore.get(context)
            val isOn = settings.enabled && Settings.canDrawOverlays(context)

            val togglePendingIntent = PendingIntent.getBroadcast(
                context,
                ACTION_TOGGLE.hashCode(),
                Intent(context, EventLightWidgetProvider::class.java).apply { action = ACTION_TOGGLE },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

            appWidgetIds.forEach { appWidgetId ->
                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                val minWidthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 160).coerceAtLeast(72)
                val minHeightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 84).coerceAtLeast(72)
                val isWide = minWidthDp >= WIDE_THRESHOLD_DP

                val views = if (isWide) {
                    val bitmap = renderWideWidgetBitmap(
                        widthDp = minWidthDp,
                        heightDp = minHeightDp,
                        settings = settings,
                        isOn = isOn,
                    )
                    RemoteViews(context.packageName, R.layout.widget_event_light_wide).apply {
                        setImageViewBitmap(R.id.widget_event_light_canvas, bitmap)
                        setOnClickPendingIntent(R.id.widget_event_light_toggle_zone, togglePendingIntent)
                        bindSliderHitZones(context, this, THICK_HIT_IDS, ACTION_SET_THICKNESS, 100)
                        bindSliderHitZones(context, this, TEMP_HIT_IDS, ACTION_SET_COLOR_TEMP, 200)
                    }
                } else {
                    val bitmap = renderCompactWidgetBitmap(
                        widthDp = minWidthDp,
                        heightDp = minHeightDp,
                        settings = settings,
                        isOn = isOn,
                    )
                    RemoteViews(context.packageName, R.layout.widget_event_light).apply {
                        setImageViewBitmap(R.id.widget_event_light_canvas, bitmap)
                        setOnClickPendingIntent(R.id.widget_root, togglePendingIntent)
                        setOnClickPendingIntent(R.id.widget_event_light_canvas, togglePendingIntent)
                    }
                }
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        private fun bindSliderHitZones(
            context: Context,
            views: RemoteViews,
            hitIds: IntArray,
            action: String,
            requestCodeBase: Int,
        ) {
            val lastIndex = (hitIds.size - 1).coerceAtLeast(1)
            hitIds.forEachIndexed { index, viewId ->
                val fraction = index.toFloat() / lastIndex.toFloat()
                val intent = Intent(context, EventLightWidgetProvider::class.java).apply {
                    this.action = action
                    putExtra(EXTRA_FRACTION, fraction)
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    requestCodeBase + index,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                views.setOnClickPendingIntent(viewId, pi)
            }
        }

        /**
         * Compact mode (< 210dp wide): just the glowing macOS Edge Light rectangle icon.
         */
        private fun renderCompactWidgetBitmap(
            widthDp: Int,
            heightDp: Int,
            settings: EventLightSettings,
            isOn: Boolean,
        ): Bitmap {
            val aspect = (widthDp.toFloat() / heightDp.toFloat()).coerceIn(0.65f, 3.2f)
            val basePx = 360f
            val w: Int
            val h: Int
            if (aspect >= 1f) {
                h = basePx.roundToInt()
                w = (basePx * aspect).roundToInt().coerceAtMost(960)
            } else {
                w = basePx.roundToInt()
                h = (basePx / aspect).roundToInt().coerceAtMost(960)
            }

            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val minDim = min(w, h).toFloat()

            val cardMargin = minDim * 0.025f
            val cardRect = RectF(cardMargin, cardMargin, w - cardMargin, h - cardMargin)
            val cardCorner = minDim * 0.24f

            drawObsidianCardBase(canvas, cardRect, cardCorner, settings, isOn, fullWash = true)

            val saveCount = canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(cardRect, cardCorner, cardCorner, Path.Direction.CW) })

            val ringInset = minDim * 0.14f
            val ringRect = RectF(
                cardRect.left + ringInset,
                cardRect.top + ringInset,
                cardRect.right - ringInset,
                cardRect.bottom - ringInset,
            )
            val ringCorner = (cardCorner - ringInset * 0.65f).coerceAtLeast(minDim * 0.10f)
            val ringStroke = minDim * 0.072f
            drawGlowingRing(canvas, ringRect, ringCorner, ringStroke, settings, isOn)

            val cx = w / 2f
            val cy = h / 2f - minDim * 0.055f
            val glyphSize = minDim * 0.22f
            drawCenterIndicator(canvas, cx, cy, glyphSize, minDim, settings, isOn)

            canvas.restoreToCount(saveCount)
            drawGlassRim(canvas, cardRect, cardCorner, minDim * 0.012f, isOn)

            return bitmap
        }

        /**
         * Wide mode (>= 210dp wide): glowing macOS rectangle toggle on the left 30%,
         * and BOTH macOS Edge Light sliders (thickness + yellow->white->blue) on the right 70%.
         */
        private fun renderWideWidgetBitmap(
            widthDp: Int,
            heightDp: Int,
            settings: EventLightSettings,
            isOn: Boolean,
        ): Bitmap {
            val scale = 3f
            val w = (widthDp * scale).roundToInt().coerceIn(620, 1280)
            val h = (heightDp * scale).roundToInt().coerceIn(240, 720)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val minDim = min(w, h).toFloat()

            val cardMargin = minDim * 0.02f
            val cardRect = RectF(cardMargin, cardMargin, w - cardMargin, h - cardMargin)
            val cardCorner = minDim * 0.24f

            drawObsidianCardBase(canvas, cardRect, cardCorner, settings, isOn, fullWash = false)

            val saveCount = canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(cardRect, cardCorner, cardCorner, Path.Direction.CW) })

            val splitX = w * 0.30f

            // --- Left 30%: Glowing Edge Light rectangle icon ---
            val leftPad = minDim * 0.11f
            val iconRect = RectF(
                cardRect.left + leftPad,
                cardRect.top + leftPad,
                splitX - leftPad * 0.55f,
                cardRect.bottom - leftPad,
            )
            val iconMin = min(iconRect.width(), iconRect.height())
            val iconCorner = iconMin * 0.22f
            val iconStroke = iconMin * 0.092f
            drawGlowingRing(canvas, iconRect, iconCorner, iconStroke, settings, isOn)

            val iconCx = iconRect.centerX()
            val iconCy = iconRect.centerY() - iconMin * 0.055f
            drawCenterIndicator(canvas, iconCx, iconCy, iconMin * 0.25f, iconMin, settings, isOn)

            // Subtle vertical divider between icon and controls
            val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x1AFFFFFF
                strokeWidth = (scale * 1f).coerceAtLeast(2f)
            }
            canvas.drawLine(
                splitX,
                cardRect.top + minDim * 0.16f,
                splitX,
                cardRect.bottom - minDim * 0.16f,
                dividerPaint,
            )

            // --- Right 70%: Both macOS Edge Light sliders ---
            // Matches widget_event_light_wide.xml paddingStart=28dp, paddingEnd=14dp
            val controlsLeft = splitX + (8f * scale)
            val iconCenterX = controlsLeft + (10f * scale)
            val trackStartX = splitX + (28f * scale)
            val trackEndX = w - (14f * scale)
            val trackWidth = (trackEndX - trackStartX).coerceAtLeast(40f)

            val topRowY = h * 0.30f
            val bottomRowY = h * 0.70f
            val sliderIconSize = (20f * scale).coerceAtMost(minDim * 0.22f)
            val sliderIconColor = 0xD9FFFFFF.toInt()

            // 1) Top slider: Thickness (hollow sun + slim track + white pill thumb)
            drawSunGlyph(canvas, iconCenterX, topRowY, sliderIconSize, sliderIconColor, striped = false)

            val thicknessFraction = (
                (settings.thicknessDp - EVENT_LIGHT_MIN_THICKNESS_DP) /
                    (EVENT_LIGHT_MAX_THICKNESS_DP - EVENT_LIGHT_MIN_THICKNESS_DP)
                ).coerceIn(0f, 1f)

            val slimTrackH = (7f * scale).coerceAtMost(minDim * 0.085f)
            val thumbW = (26f * scale).coerceAtMost(minDim * 0.30f)
            val thumbH = (19f * scale).coerceAtMost(minDim * 0.22f)
            val usableThick = (trackWidth - thumbW).coerceAtLeast(1f)
            val thumbLeft = trackStartX + thicknessFraction * usableThick
            val thumbCenterX = thumbLeft + thumbW / 2f

            // Inactive track
            val inactiveTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF3E424B.toInt()
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(
                RectF(trackStartX, topRowY - slimTrackH / 2f, trackEndX, topRowY + slimTrackH / 2f),
                slimTrackH / 2f,
                slimTrackH / 2f,
                inactiveTrackPaint,
            )

            // Active track
            val activeTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFA4A9B4.toInt()
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(
                RectF(trackStartX, topRowY - slimTrackH / 2f, thumbCenterX, topRowY + slimTrackH / 2f),
                slimTrackH / 2f,
                slimTrackH / 2f,
                activeTrackPaint,
            )

            // White pill thumb
            val pillThumbRect = RectF(thumbLeft, topRowY - thumbH / 2f, thumbLeft + thumbW, topRowY + thumbH / 2f)
            val thumbShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x40000000
                maskFilter = BlurMaskFilter(3f * scale, BlurMaskFilter.Blur.NORMAL)
            }
            canvas.drawRoundRect(
                RectF(pillThumbRect.left, pillThumbRect.top + scale, pillThumbRect.right, pillThumbRect.bottom + scale),
                thumbH / 2f,
                thumbH / 2f,
                thumbShadowPaint,
            )
            val whiteThumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                style = Paint.Style.FILL
            }
            canvas.drawRoundRect(pillThumbRect, thumbH / 2f, thumbH / 2f, whiteThumbPaint)

            // 2) Bottom slider: Color temperature (striped sun + yellow->white->blue gradient pill + ringed circle thumb)
            drawSunGlyph(canvas, iconCenterX, bottomRowY, sliderIconSize, sliderIconColor, striped = true)

            val gradBarH = (26f * scale).coerceAtMost(minDim * 0.28f)
            val gradRect = RectF(trackStartX, bottomRowY - gradBarH / 2f, trackEndX, bottomRowY + gradBarH / 2f)
            val gradPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
                shader = LinearGradient(
                    gradRect.left,
                    bottomRowY,
                    gradRect.right,
                    bottomRowY,
                    EventLightGradientStops.map { it.toArgb() }.toIntArray(),
                    null,
                    Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRoundRect(gradRect, gradBarH / 2f, gradBarH / 2f, gradPaint)

            // Subtle rim around gradient pill
            val gradRimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x26000000
                style = Paint.Style.STROKE
                strokeWidth = scale * 0.75f
            }
            canvas.drawRoundRect(gradRect, gradBarH / 2f, gradBarH / 2f, gradRimPaint)

            // Color temperature circular thumb (selected color inside crisp white ring)
            val tempEdgePad = 2.5f * scale
            val circleRadius = (gradBarH / 2f - tempEdgePad).coerceAtLeast(4f)
            val usableTemp = (trackWidth - (circleRadius + tempEdgePad) * 2f).coerceAtLeast(1f)
            val circleCx = trackStartX + circleRadius + tempEdgePad + settings.colorTemp.coerceIn(0f, 1f) * usableTemp

            val selectedTint = edgeLightTintColor(settings.colorTemp).toArgb()
            val circleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = selectedTint
                style = Paint.Style.FILL
            }
            val circleRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFFFFF.toInt()
                style = Paint.Style.STROKE
                strokeWidth = (2.8f * scale).coerceAtMost(circleRadius * 0.35f)
            }
            val circleOuterStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x2E000000
                style = Paint.Style.STROKE
                strokeWidth = scale * 0.75f
            }
            canvas.drawCircle(circleCx, bottomRowY, circleRadius, circleFillPaint)
            canvas.drawCircle(circleCx, bottomRowY, circleRadius - circleRingPaint.strokeWidth / 2f, circleRingPaint)
            canvas.drawCircle(circleCx, bottomRowY, circleRadius, circleOuterStroke)

            canvas.restoreToCount(saveCount)
            drawGlassRim(canvas, cardRect, cardCorner, minDim * 0.012f, isOn)

            return bitmap
        }

        private fun drawObsidianCardBase(
            canvas: Canvas,
            cardRect: RectF,
            cardCorner: Float,
            settings: EventLightSettings,
            isOn: Boolean,
            fullWash: Boolean,
        ) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
                style = Paint.Style.FILL
                color = if (isOn) 0xFF0C0E12.toInt() else 0xFF17191F.toInt()
            }
            canvas.drawRoundRect(cardRect, cardCorner, cardCorner, bgPaint)

            if (isOn && fullWash) {
                val save = canvas.save()
                canvas.clipPath(Path().apply { addRoundRect(cardRect, cardCorner, cardCorner, Path.Direction.CW) })
                val tint = edgeLightTintColor(settings.colorTemp)
                val washPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
                    shader = RadialGradient(
                        cardRect.centerX(),
                        cardRect.centerY(),
                        maxOf(cardRect.width(), cardRect.height()) * 0.72f,
                        intArrayOf(
                            tint.copy(alpha = 0.04f).toArgb(),
                            tint.copy(alpha = 0.14f).toArgb(),
                        ),
                        floatArrayOf(0f, 1f),
                        Shader.TileMode.CLAMP,
                    )
                }
                canvas.drawRect(cardRect, washPaint)
                canvas.restoreToCount(save)
            }
        }

        private fun drawGlowingRing(
            canvas: Canvas,
            ringRect: RectF,
            ringCorner: Float,
            ringStroke: Float,
            settings: EventLightSettings,
            isOn: Boolean,
        ) {
            if (isOn) {
                val tint = edgeLightTintColor(settings.colorTemp)
                val outerGlow = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = ringStroke * 2.3f
                    color = tint.copy(alpha = 0.58f).toArgb()
                    maskFilter = BlurMaskFilter((ringStroke * 1.55f).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
                }
                val midBloom = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = ringStroke * 1.35f
                    color = lerp(tint, Color.White, 0.30f).copy(alpha = 0.85f).toArgb()
                    maskFilter = BlurMaskFilter((ringStroke * 0.68f).coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
                }
                val coreTube = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = ringStroke * 0.82f
                    color = lerp(Color.White, tint, 0.22f).copy(alpha = 0.98f).toArgb()
                    maskFilter = BlurMaskFilter((ringStroke * 0.18f).coerceAtLeast(0.5f), BlurMaskFilter.Blur.NORMAL)
                }
                val innerFilament = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = ringStroke * 0.38f
                    color = 0xE6FFFFFF.toInt()
                }
                canvas.drawRoundRect(ringRect, ringCorner, ringCorner, outerGlow)
                canvas.drawRoundRect(ringRect, ringCorner, ringCorner, midBloom)
                canvas.drawRoundRect(ringRect, ringCorner, ringCorner, coreTube)
                canvas.drawRoundRect(ringRect, ringCorner, ringCorner, innerFilament)
            } else {
                val offRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = ringStroke * 0.72f
                    color = 0xFF3A3F4B.toInt()
                }
                canvas.drawRoundRect(ringRect, ringCorner, ringCorner, offRing)
            }
        }

        private fun drawCenterIndicator(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            glyphSize: Float,
            referenceDim: Float,
            settings: EventLightSettings,
            isOn: Boolean,
        ) {
            val tint = edgeLightTintColor(settings.colorTemp)
            val glyphColor = if (isOn) 0xF2FFFFFF.toInt() else 0xFF7A808E.toInt()
            drawSunGlyph(canvas, cx, cy, glyphSize, glyphColor, striped = false)

            val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (isOn) lerp(Color.White, tint, 0.35f).copy(alpha = 0.90f).toArgb() else 0xFF6B717E.toInt()
                textSize = referenceDim * 0.105f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                letterSpacing = 0.14f
            }
            val labelY = cy + glyphSize * 0.78f + labelPaint.textSize * 0.45f
            canvas.drawText(if (isOn) "ON" else "OFF", cx, labelY, labelPaint)
        }

        private fun drawGlassRim(
            canvas: Canvas,
            cardRect: RectF,
            cardCorner: Float,
            strokeWidth: Float,
            isOn: Boolean,
        ) {
            val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                color = if (isOn) 0x26FFFFFF else 0x1AFFFFFF
            }
            canvas.drawRoundRect(cardRect, cardCorner, cardCorner, rimPaint)
        }

        private fun drawSunGlyph(
            canvas: Canvas,
            cx: Float,
            cy: Float,
            size: Float,
            color: Int,
            striped: Boolean,
        ) {
            val stroke = (size * 0.095f).coerceAtLeast(2f)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = stroke
                strokeCap = Paint.Cap.ROUND
            }
            val r = size * 0.23f
            val rayInner = size * 0.36f
            val rayOuter = size * 0.48f
            canvas.drawCircle(cx, cy, r, paint)

            if (striped) {
                val stripePaint = Paint(paint).apply { strokeWidth = stroke * 0.72f }
                for (step in 0..2) {
                    val y = cy + r * (0.15f + step * 0.32f)
                    val dy = y - cy
                    val halfChord = sqrt((r * r - dy * dy).coerceAtLeast(0f))
                    if (halfChord > 0.5f) {
                        canvas.drawLine(cx - halfChord, y, cx + halfChord, y, stripePaint)
                    }
                }
            }

            for (i in 0 until 8) {
                val rad = Math.toRadians(i * 45.0)
                val dx = cos(rad).toFloat()
                val dy = sin(rad).toFloat()
                canvas.drawLine(
                    cx + dx * rayInner,
                    cy + dy * rayInner,
                    cx + dx * rayOuter,
                    cy + dy * rayOuter,
                    paint,
                )
            }
        }
    }
}
