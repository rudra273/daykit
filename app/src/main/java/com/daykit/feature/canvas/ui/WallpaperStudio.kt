package com.daykit.feature.canvas.ui

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daykit.DayKitApplication
import com.daykit.core.designsystem.Spacing
import com.daykit.core.designsystem.components.FilterChipButton
import com.daykit.core.designsystem.components.PrimaryButton
import com.daykit.core.designsystem.extendedColors
import com.daykit.feature.dailywidgets.DailyWidgetDataProvider
import com.daykit.feature.dailywidgets.DailyWidgetStyle
import com.daykit.feature.dailywidgets.DailyWidgetType
import com.daykit.feature.dailywidgets.WallpaperLayoutPrefs
import com.daykit.feature.dailywidgets.WallpaperWidgetSlot
import com.daykit.feature.wallpaper.LiquidGlassWallpaperRenderer
import com.daykit.feature.wallpaper.WallpaperPreset
import com.daykit.feature.wallpaper.WeatherGlassWallpaperService
import com.daykit.feature.weather.WeatherPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private fun isDayKitLiveWallpaperActive(context: Context): Boolean {
    return runCatching {
        val info = WallpaperManager.getInstance(context).wallpaperInfo
        info?.packageName == context.packageName &&
            info.serviceName == WeatherGlassWallpaperService::class.java.name
    }.getOrDefault(false)
}

private fun markExpectingActivityResult(context: Context) {
    (context.applicationContext as? DayKitApplication)
        ?.container
        ?.sensitiveKeyManager
        ?.expectingActivityResult = true
}

/**
 * Interactive Multi-Widget Live Wallpaper Studio under Canvas -> Wallpapers.
 * Lets the user pick a background photo, add multiple Liquid Glass & Solid widgets via "+ Widget",
 * drag-and-drop to position & resize each widget directly on a phone preview, apply the 60fps
 * Live Wallpaper, or remove it cleanly.
 */
@Composable
fun WallpaperStudioSection(
    refreshTrigger: Int,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDark = isSystemInDarkTheme()

    val snapshot = remember(refreshTrigger) { WeatherPrefs.loadSnapshot(context) }
    val useFahrenheit = remember(refreshTrigger) { WeatherPrefs.useFahrenheit(context) }

    var hasWallpaperPhoto by remember { mutableStateOf(WeatherPrefs.hasWallpaperPhoto(context)) }
    var photoVersion by remember { mutableLongStateOf(WeatherPrefs.wallpaperPhotoVersion(context)) }
    var selectedPreset by remember {
        mutableStateOf(WallpaperPreset.fromId(WeatherPrefs.wallpaperPresetId(context)))
    }
    var onlyOnFirstPage by remember { mutableStateOf(WeatherPrefs.onlyOnFirstPage(context)) }
    var slots by remember { mutableStateOf(WallpaperLayoutPrefs.loadSlots(context)) }
    var selectedUid by remember { mutableStateOf(slots.firstOrNull()?.uid) }
    var showAddWidgetDialog by remember { mutableStateOf(false) }

    var isLiveActive by remember(refreshTrigger) { mutableStateOf(isDayKitLiveWallpaperActive(context)) }
    var isRemoving by remember { mutableStateOf(false) }
    var isApplyingStatic by remember { mutableStateOf(false) }

    var liveAnimSeconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(selectedPreset) {
        if (selectedPreset?.isLive == true) {
            var startNanos = 0L
            while (true) {
                withFrameNanos { frameNanos ->
                    if (startNanos == 0L) startNanos = frameNanos
                    liveAnimSeconds = (frameNanos - startNanos) / 1_000_000_000f
                }
            }
        } else {
            liveAnimSeconds = 0f
        }
    }

    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1000L)
        }
    }

    val solarState = remember(refreshTrigger, nowMillis / 60_000L) {
        DailyWidgetDataProvider.loadSolarArcState(context, nowMillis)
    }
    val stepsState = remember(refreshTrigger, nowMillis / 60_000L) {
        DailyWidgetDataProvider.loadStepsState(context, nowMillis)
    }
    val batteryState = remember(refreshTrigger, nowMillis / 30_000L) {
        DailyWidgetDataProvider.loadBatteryState(context)
    }
    val agendaState = remember(refreshTrigger, nowMillis / 60_000L) {
        DailyWidgetDataProvider.loadAgendaState(nowMillis)
    }

    var previewBgBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var photoThumbBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(photoVersion, hasWallpaperPhoto, selectedPreset) {
        val preset = selectedPreset
        previewBgBitmap = when {
            preset != null && preset.isLive -> null
            preset != null && !preset.isLive -> withContext(Dispatchers.Default) {
                LiquidGlassWallpaperRenderer.renderBitmap(preset, 540, 1170)
            }
            hasWallpaperPhoto -> withContext(Dispatchers.IO) {
                val file = WeatherPrefs.wallpaperPhotoFile(context)
                if (file.exists()) {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                    runCatching { BitmapFactory.decodeFile(file.absolutePath, opts) }.getOrNull()
                } else null
            }
            else -> null
        }
        photoThumbBitmap = if (hasWallpaperPhoto) {
            withContext(Dispatchers.IO) {
                val file = WeatherPrefs.wallpaperPhotoFile(context)
                if (file.exists()) {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 6 }
                    runCatching { BitmapFactory.decodeFile(file.absolutePath, opts) }.getOrNull()
                } else null
            }
        } else {
            null
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = WeatherPrefs.importWallpaperPhoto(context, uri)
                if (ok) {
                    hasWallpaperPhoto = true
                    selectedPreset = null
                    photoVersion = WeatherPrefs.wallpaperPhotoVersion(context)
                    onShowMessage("Wallpaper photo updated")
                } else {
                    onShowMessage("Could not load selected photo")
                }
            }
        }
    }

    fun selectPreset(preset: WallpaperPreset) {
        selectedPreset = preset
        WeatherPrefs.setWallpaperPreset(context, preset.id)
        photoVersion = WeatherPrefs.wallpaperPhotoVersion(context)
    }

    fun selectPhotoBackdrop() {
        if (hasWallpaperPhoto) {
            selectedPreset = null
            WeatherPrefs.setWallpaperPreset(context, null)
            photoVersion = WeatherPrefs.wallpaperPhotoVersion(context)
        } else {
            markExpectingActivityResult(context)
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
    }

    fun persistSlots(next: List<WallpaperWidgetSlot>) {
        slots = next
        WallpaperLayoutPrefs.saveSlots(context, next)
    }

    fun updateSlot(uid: String, transform: (WallpaperWidgetSlot) -> WallpaperWidgetSlot) {
        val next = slots.map { if (it.uid == uid) transform(it) else it }
        persistSlots(next)
    }

    fun addWidget(type: DailyWidgetType) {
        // Pick a smart non-overlapping vertical slot based on current count
        val candidateYs = listOf(0.24f, 0.52f, 0.78f, 0.38f, 0.66f)
        val chosenY = candidateYs.firstOrNull { cy ->
            slots.none { kotlin.math.abs(it.centerY - cy) < 0.14f }
        } ?: (0.25f + (slots.size % 4) * 0.18f).coerceAtMost(0.82f)

        val newSlot = WallpaperWidgetSlot(
            type = type,
            centerX = 0.50f,
            centerY = chosenY,
            widthFraction = type.defaultWidthFraction,
        )
        persistSlots(slots + newSlot)
        selectedUid = newSlot.uid
    }

    fun removeSlot(uid: String) {
        val next = slots.filterNot { it.uid == uid }
        persistSlots(next)
        if (selectedUid == uid) {
            selectedUid = next.lastOrNull()?.uid
        }
    }

    val selectedSlot = slots.firstOrNull { it.uid == selectedUid }
    val tempText = "${snapshot.displayTemp(useFahrenheit)}${snapshot.unitSymbol(useFahrenheit)}"
    val highLowText = "H:${snapshot.displayHigh(useFahrenheit)}°  L:${snapshot.displayLow(useFahrenheit)}°"

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.lg, vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Header status row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = when {
                    slots.isEmpty() -> "Pick a photo & tap + Widget"
                    selectedSlot != null -> "Drag ${selectedSlot.type.title} · ${slots.size} on canvas"
                    else -> "Tap a widget to select or drag (${slots.size})"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extendedColors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (isLiveActive) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.extendedColors.accents.green,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "Active",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.extendedColors.accents.green,
                    )
                }
            }
        }

        // Interactive phone-ratio wallpaper canvas
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .aspectRatio(9f / 19.5f)
                .clip(RoundedCornerShape(28.dp))
                .border(1.5.dp, MaterialTheme.extendedColors.divider, RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        0.0f to Color(0xFF102238),
                        0.55f to Color(0xFF182C44),
                        1.0f to Color(0xFF0C141E),
                    ),
                )
                .pointerInput(Unit) {
                    detectTapGestures { selectedUid = null }
                },
        ) {
            val density = LocalDensity.current
            val frameW = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
            val frameH = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

            val activeLivePreset = selectedPreset?.takeIf { it.isLive }
            val bg = previewBgBitmap
            if (activeLivePreset != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawIntoCanvas { canvas ->
                        LiquidGlassWallpaperRenderer.draw(
                            canvas = canvas.nativeCanvas,
                            preset = activeLivePreset,
                            width = size.width,
                            height = size.height,
                            timeSeconds = liveAnimSeconds,
                        )
                    }
                }
            } else if (bg != null && !bg.isRecycled) {
                Image(
                    bitmap = bg.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            slots.forEach { slot ->
                val isSelected = slot.uid == selectedUid
                val cardWPx = (frameW * slot.widthFraction).coerceIn(frameW * 0.30f, frameW * 0.94f)
                val cardHPx = (cardWPx / slot.effectiveAspectRatio).coerceIn(frameH * 0.10f, frameH * 0.62f)
                val cardWDp = with(density) { cardWPx.toDp() }
                val cardHDp = with(density) { cardHPx.toDp() }
                val cornerDp = when (slot.type) {
                    DailyWidgetType.GLASS_WEATHER -> cardWDp * 0.22f
                    DailyWidgetType.SOLAR_ARC -> minOf(cardWDp, cardHDp) * 0.22f
                    else -> cardHDp * 0.18f
                }
                val cardShape = RoundedCornerShape(cornerDp)

                val marginX = frameW * 0.03f
                val marginY = frameH * 0.04f
                val leftPx = (frameW * slot.centerX - cardWPx * 0.5f)
                    .coerceIn(marginX, (frameW - cardWPx - marginX).coerceAtLeast(marginX))
                val topPx = (frameH * slot.centerY - cardHPx * 0.5f)
                    .coerceIn(marginY, (frameH - cardHPx - marginY).coerceAtLeast(marginY))

                val latestSlot by rememberUpdatedState(slot)

                Box(
                    modifier = Modifier
                        .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                        .size(width = cardWDp, height = cardHDp)
                        .clip(cardShape)
                        .then(
                            if (isSelected) {
                                Modifier.border(2.dp, Color(0xFF4C9BFF), cardShape)
                            } else {
                                Modifier
                            },
                        )
                        .pointerInput(slot.uid, frameW, frameH) {
                            detectTapGestures { selectedUid = slot.uid }
                        }
                        .pointerInput(slot.uid, frameW, frameH) {
                            detectDragGestures(
                                onDragStart = { selectedUid = slot.uid },
                            ) { change, dragAmount ->
                                change.consume()
                                val cur = latestSlot
                                val nextX = (cur.centerX + dragAmount.x / frameW).coerceIn(0.10f, 0.90f)
                                val nextY = (cur.centerY + dragAmount.y / frameH).coerceIn(0.08f, 0.92f)
                                updateSlot(cur.uid) { it.copy(centerX = nextX, centerY = nextY) }
                            }
                        },
                ) {
                    if (slot.type.style == DailyWidgetStyle.LIQUID_GLASS && bg != null && !bg.isRecycled) {
                        Image(
                            bitmap = bg.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(8.dp),
                        )
                    }

                    if (slot.type == DailyWidgetType.GLASS_WEATHER) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF14202F).copy(alpha = 0.45f))
                                .border(1.25.dp, Color.White.copy(alpha = 0.72f), cardShape)
                                .padding(cardWDp * 0.105f),
                        ) {
                            val textScale = (slot.widthFraction / 0.68f).coerceIn(0.50f, 1.25f)
                            Column(modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = snapshot.city,
                                    color = Color.White.copy(alpha = 0.92f),
                                    fontSize = (11f * textScale).sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = tempText,
                                    color = Color.White,
                                    fontSize = (24f * textScale).sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = snapshot.conditionLabel,
                                    color = Color.White,
                                    fontSize = (10.5f * textScale).sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                                Text(
                                    text = highLowText,
                                    color = Color.White.copy(alpha = 0.86f),
                                    fontSize = (9f * textScale).sp,
                                    maxLines = 1,
                                )
                            }
                        }
                    } else {
                        DailyWidgetLivePreview(
                            type = slot.type,
                            nowMillis = nowMillis,
                            solarState = solarState,
                            stepsState = stepsState,
                            batteryState = batteryState,
                            agendaState = agendaState,
                            darkSurface = selectedPreset?.isDark ?: isDark,
                            overWallpaperBlur = slot.type.style == DailyWidgetStyle.LIQUID_GLASS &&
                                (bg != null || activeLivePreset != null),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // Quick remove badge when selected
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color(0xCC111418))
                                .clickable { removeSlot(slot.uid) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Remove widget",
                                tint = Color.White,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.sm))

        // DayKit procedural Liquid Glass wallpapers (2 Live + 2 Static, Light & Dark) + User Photo
        WallpaperBackdropStrip(
            selectedPreset = selectedPreset,
            hasPhoto = hasWallpaperPhoto,
            photoThumb = photoThumbBitmap,
            onSelectPreset = ::selectPreset,
            onSelectPhoto = ::selectPhotoBackdrop,
        )

        Spacer(Modifier.height(Spacing.xs))

        // Studio control chips: + Widget, Pick Photo, Page mode, Clear Photo, Clear Widgets
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChipButton(
                text = "+ Widget",
                selected = true,
                onClick = { showAddWidgetDialog = true },
            )
            FilterChipButton(
                text = if (hasWallpaperPhoto) "Change Photo" else "Pick Photo",
                selected = selectedPreset == null && hasWallpaperPhoto,
                onClick = {
                    markExpectingActivityResult(context)
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )
            FilterChipButton(
                text = if (onlyOnFirstPage) "Page 1 only" else "All pages",
                selected = onlyOnFirstPage,
                onClick = {
                    val next = !onlyOnFirstPage
                    onlyOnFirstPage = next
                    WeatherPrefs.setOnlyOnFirstPage(context, next)
                },
            )
            if (hasWallpaperPhoto) {
                Text(
                    text = "Clear Photo",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.extendedColors.textMuted,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            WeatherPrefs.clearWallpaperPhoto(context)
                            hasWallpaperPhoto = false
                            photoVersion = WeatherPrefs.wallpaperPhotoVersion(context)
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
            if (slots.isNotEmpty()) {
                Text(
                    text = "Clear Widgets",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.extendedColors.textMuted,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable {
                            persistSlots(emptyList())
                            selectedUid = null
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }

        if (selectedSlot != null) {
            Spacer(Modifier.height(Spacing.xs))
            if (selectedSlot.type == DailyWidgetType.SOLAR_ARC) {
                val isWide = selectedSlot.effectiveAspectRatio >= 1.38f
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChipButton(
                        text = "Square (2×2)",
                        selected = !isWide,
                        onClick = {
                            updateSlot(selectedSlot.uid) {
                                it.copy(aspectRatioOverride = 1.0f, widthFraction = it.widthFraction.coerceAtMost(0.72f))
                            }
                        },
                    )
                    FilterChipButton(
                        text = "Wide (4×2)",
                        selected = isWide,
                        onClick = {
                            updateSlot(selectedSlot.uid) {
                                it.copy(aspectRatioOverride = 2.0f, widthFraction = it.widthFraction.coerceAtLeast(0.78f))
                            }
                        },
                    )
                }
                Spacer(Modifier.height(Spacing.xs))
            }
            val normalizedSize = ((selectedSlot.widthFraction - 0.34f) / (0.94f - 0.34f)).coerceIn(0f, 1f)
            WallpaperSizeSlider(
                label = "${selectedSlot.type.title} Size",
                value = normalizedSize,
                valueLabel = "${(selectedSlot.widthFraction * 100).roundToInt()}%",
                onValueChange = { frac ->
                    val nextW = 0.34f + (0.94f - 0.34f) * frac.coerceIn(0f, 1f)
                    updateSlot(selectedSlot.uid) { it.copy(widthFraction = nextW) }
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(Spacing.sm))

        // Bottom actions: Remove Live Wallpaper + Apply Live Wallpaper
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = {
                    scope.launch {
                        isRemoving = true
                        val cleared = withContext(Dispatchers.IO) {
                            runCatching {
                                WallpaperManager.getInstance(context).clear(WallpaperManager.FLAG_SYSTEM)
                                true
                            }.getOrElse {
                                runCatching {
                                    @Suppress("DEPRECATION")
                                    WallpaperManager.getInstance(context).clear()
                                    true
                                }.getOrDefault(false)
                            }
                        }
                        isRemoving = false
                        isLiveActive = isDayKitLiveWallpaperActive(context)
                        if (cleared) {
                            onShowMessage("Live wallpaper removed")
                        } else {
                            markExpectingActivityResult(context)
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_SET_WALLPAPER))
                            }.onFailure {
                                onShowMessage("Could not remove wallpaper automatically. Change it from system wallpaper settings.")
                            }
                        }
                    }
                },
                enabled = !isRemoving,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text(text = if (isRemoving) "Removing…" else "Remove", maxLines = 1)
            }

            val applyAsStaticDirect = selectedPreset != null && !selectedPreset!!.isLive && slots.isEmpty()
            PrimaryButton(
                text = when {
                    isApplyingStatic -> "Applying…"
                    applyAsStaticDirect -> "Set Static"
                    isLiveActive -> "Re-apply"
                    else -> "Apply Wallpaper"
                },
                leadingIcon = {
                    Icon(Icons.Rounded.Wallpaper, contentDescription = null, modifier = Modifier.size(17.dp))
                },
                enabled = !isApplyingStatic,
                onClick = {
                    val staticPreset = selectedPreset?.takeIf { !it.isLive && slots.isEmpty() }
                    if (staticPreset != null) {
                        scope.launch {
                            isApplyingStatic = true
                            val applied = withContext(Dispatchers.Default) {
                                runCatching {
                                    val dm = context.resources.displayMetrics
                                    val targetW = dm.widthPixels.coerceIn(720, 1440)
                                    val targetH = dm.heightPixels.coerceIn(1280, 2880)
                                    val bmp = LiquidGlassWallpaperRenderer.renderBitmap(staticPreset, targetW, targetH)
                                    try {
                                        WallpaperManager.getInstance(context).setBitmap(bmp)
                                        true
                                    } finally {
                                        bmp.recycle()
                                    }
                                }.getOrDefault(false)
                            }
                            isApplyingStatic = false
                            isLiveActive = isDayKitLiveWallpaperActive(context)
                            if (applied) {
                                onShowMessage("${staticPreset.title} set as static wallpaper")
                            } else {
                                onShowMessage("Could not set static wallpaper on this device")
                            }
                        }
                    } else {
                        markExpectingActivityResult(context)
                        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                            putExtra(
                                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                ComponentName(context, WeatherGlassWallpaperService::class.java),
                            )
                        }
                        val started = runCatching { context.startActivity(intent) }.isSuccess
                        if (!started) {
                            runCatching {
                                context.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
                            }.onFailure {
                                onShowMessage("Live wallpaper picker is not available on this device.")
                            }
                        }
                    }
                },
                modifier = Modifier.weight(1.35f),
            )
        }
    }

    if (showAddWidgetDialog) {
        AddWallpaperWidgetDialog(
            existingTypes = slots.map { it.type }.toSet(),
            onDismiss = { showAddWidgetDialog = false },
            onSelect = { type ->
                showAddWidgetDialog = false
                addWidget(type)
                onShowMessage("Added ${type.title} to wallpaper")
            },
        )
    }
}

@Composable
private fun WallpaperBackdropStrip(
    selectedPreset: WallpaperPreset?,
    hasPhoto: Boolean,
    photoThumb: Bitmap?,
    onSelectPreset: (WallpaperPreset) -> Unit,
    onSelectPhoto: () -> Unit,
) {
    val presetThumbs = remember {
        WallpaperPreset.entries.associateWith { preset ->
            LiquidGlassWallpaperRenderer.renderBitmap(preset, 200, 132, timeSeconds = 1.8f).asImageBitmap()
        }
    }
    val cardShape = RoundedCornerShape(14.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WallpaperPreset.entries.forEach { preset ->
            val isSelected = selectedPreset == preset
            val borderColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.extendedColors.divider
            }
            Box(
                modifier = Modifier
                    .width(106.dp)
                    .height(62.dp)
                    .clip(cardShape)
                    .border(if (isSelected) 2.dp else 1.dp, borderColor, cardShape)
                    .clickable { onSelectPreset(preset) },
            ) {
                presetThumbs[preset]?.let { bmp ->
                    Image(
                        bitmap = bmp,
                        contentDescription = preset.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Bottom contrast scrim for title legibility
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.45f to Color.Black.copy(alpha = 0.12f),
                                1.0f to Color.Black.copy(alpha = 0.62f),
                            ),
                        ),
                )

                // Top badges: Live/Static pill + Light/Dark icon
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.44f))
                            .padding(horizontal = 5.dp, vertical = 1.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (preset.isLive) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = Color(0xFF7CE8FF),
                                modifier = Modifier.size(9.dp),
                            )
                        }
                        Text(
                            text = if (preset.isLive) "Live" else "Static",
                            color = Color.White,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.44f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (preset.isDark) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                            contentDescription = if (preset.isDark) "Dark theme" else "Light theme",
                            tint = if (preset.isDark) Color(0xFFC8D8FF) else Color(0xFFFFE59E),
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }

                // Title + selection checkmark
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = preset.title,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSelected) {
                        Spacer(Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }
        }

        // Custom user photo swatch
        val isPhotoSelected = selectedPreset == null && hasPhoto
        Box(
            modifier = Modifier
                .width(86.dp)
                .height(62.dp)
                .clip(cardShape)
                .background(MaterialTheme.extendedColors.inputField)
                .border(
                    width = if (isPhotoSelected) 2.dp else 1.dp,
                    color = if (isPhotoSelected) MaterialTheme.colorScheme.primary else MaterialTheme.extendedColors.divider,
                    shape = cardShape,
                )
                .clickable { onSelectPhoto() },
            contentAlignment = Alignment.Center,
        ) {
            if (photoThumb != null && !photoThumb.isRecycled) {
                Image(
                    bitmap = photoThumb.asImageBitmap(),
                    contentDescription = "My Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                1.0f to Color.Black.copy(alpha = 0.62f),
                            ),
                        ),
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "My Photo",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    if (isPhotoSelected) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PhotoLibrary,
                        contentDescription = null,
                        tint = MaterialTheme.extendedColors.textMuted,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "My Photo",
                        color = MaterialTheme.extendedColors.textMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddWallpaperWidgetDialog(
    existingTypes: Set<DailyWidgetType>,
    onDismiss: () -> Unit,
    onSelect: (DailyWidgetType) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add widget to wallpaper") },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(DailyWidgetType.entries, key = { it.id }) { type ->
                    val alreadyAdded = type in existingTypes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.extendedColors.inputField)
                            .clickable { onSelect(type) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = type.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "· ${type.style.label}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (type.style == DailyWidgetStyle.LIQUID_GLASS) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.extendedColors.textMuted
                                    },
                                )
                            }
                            Text(
                                text = type.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.extendedColors.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = if (alreadyAdded) Icons.Rounded.Add else Icons.Rounded.Add,
                            contentDescription = "Add ${type.title}",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )
}

@Composable
private fun WallpaperSizeSlider(
    label: String,
    value: Float,
    valueLabel: String,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    BoxWithConstraints(
        modifier = modifier
            .height(44.dp)
            .clip(shape)
            .background(Color(0xFF0D1014).copy(alpha = 0.74f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onValueChange((offset.x / size.width.toFloat()).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onValueChange((change.position.x / size.width.toFloat()).coerceIn(0f, 1f))
                }
            },
    ) {
        val clamped = value.coerceIn(0f, 1f)
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(clamped.coerceAtLeast(0.001f))
                .background(
                    Brush.horizontalGradient(
                        0.0f to Color.White.copy(alpha = 0.14f),
                        1.0f to Color.White.copy(alpha = 0.30f),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.94f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Text(
                text = valueLabel,
                style = TextStyle(
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFeatureSettings = "tnum",
                ),
            )
        }
    }
}
