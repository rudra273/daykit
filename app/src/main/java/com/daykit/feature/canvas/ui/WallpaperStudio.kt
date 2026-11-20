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
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import com.daykit.feature.wallpaper.WeatherGlassWallpaperService
import com.daykit.feature.weather.WeatherPrefs
import kotlinx.coroutines.Dispatchers
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
 * Interactive Live Wallpaper Studio under Canvas -> Wallpapers.
 * Lets the user pick a background photo, toggle live widgets (starting with Glass Weather),
 * drag-and-drop to position & resize widgets directly on a phone preview, apply the 60fps
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

    var snapshot by remember(refreshTrigger) { mutableStateOf(WeatherPrefs.loadSnapshot(context)) }
    val useFahrenheit = remember(refreshTrigger) { WeatherPrefs.useFahrenheit(context) }

    var hasWallpaperPhoto by remember { mutableStateOf(WeatherPrefs.hasWallpaperPhoto(context)) }
    var photoVersion by remember { mutableLongStateOf(WeatherPrefs.wallpaperPhotoVersion(context)) }
    var weatherEnabled by remember { mutableStateOf(WeatherPrefs.wallpaperWeatherEnabled(context)) }
    var onlyOnFirstPage by remember { mutableStateOf(WeatherPrefs.onlyOnFirstPage(context)) }
    var cubeCenterX by remember { mutableFloatStateOf(WeatherPrefs.wallpaperCubeX(context)) }
    var cubeCenterY by remember { mutableFloatStateOf(WeatherPrefs.wallpaperCubeY(context)) }
    var cubeScale by remember { mutableFloatStateOf(WeatherPrefs.wallpaperCubeScale(context)) }

    var isLiveActive by remember(refreshTrigger) { mutableStateOf(isDayKitLiveWallpaperActive(context)) }
    var isRemoving by remember { mutableStateOf(false) }

    var previewBgBitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(photoVersion, hasWallpaperPhoto) {
        previewBgBitmap = if (hasWallpaperPhoto) {
            withContext(Dispatchers.IO) {
                val file = WeatherPrefs.wallpaperPhotoFile(context)
                if (file.exists()) {
                    val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
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
                    photoVersion = WeatherPrefs.wallpaperPhotoVersion(context)
                    onShowMessage("Wallpaper background updated")
                } else {
                    onShowMessage("Could not load selected photo")
                }
            }
        }
    }

    fun updateLayout(x: Float, y: Float, scale: Float) {
        cubeCenterX = x
        cubeCenterY = y
        cubeScale = scale
        WeatherPrefs.saveWallpaperCubeLayout(context, x, y, scale)
    }

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
                text = if (weatherEnabled) "Drag widget to place anywhere" else "Pick a photo & add widgets",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extendedColors.textMuted,
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
                ),
        ) {
            val density = LocalDensity.current
            val frameW = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
            val frameH = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)

            val bg = previewBgBitmap
            if (bg != null && !bg.isRecycled) {
                Image(
                    bitmap = bg.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (weatherEnabled) {
                val cardSizePx = (frameW * cubeScale).coerceIn(frameW * 0.35f, frameW * 0.92f)
                val cardSizeDp = with(density) { cardSizePx.toDp() }
                val cardCorner = cardSizeDp * 0.22f
                val cardShape = RoundedCornerShape(cardCorner)

                val marginX = frameW * 0.03f
                val marginY = frameH * 0.04f
                val leftPx = (frameW * cubeCenterX - cardSizePx * 0.5f)
                    .coerceIn(marginX, (frameW - cardSizePx - marginX).coerceAtLeast(marginX))
                val topPx = (frameH * cubeCenterY - cardSizePx * 0.5f)
                    .coerceIn(marginY, (frameH - cardSizePx - marginY).coerceAtLeast(marginY))

                val latestX by rememberUpdatedState(cubeCenterX)
                val latestY by rememberUpdatedState(cubeCenterY)
                val latestScale by rememberUpdatedState(cubeScale)

                Box(
                    modifier = Modifier
                        .offset { IntOffset(leftPx.roundToInt(), topPx.roundToInt()) }
                        .size(cardSizeDp)
                        .clip(cardShape)
                        .pointerInput(frameW, frameH) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val nextX = (latestX + dragAmount.x / frameW).coerceIn(0.15f, 0.85f)
                                val nextY = (latestY + dragAmount.y / frameH).coerceIn(0.12f, 0.88f)
                                updateLayout(nextX, nextY, latestScale)
                            }
                        },
                ) {
                    if (bg != null && !bg.isRecycled) {
                        Image(
                            bitmap = bg.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(8.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF14202F).copy(alpha = 0.45f))
                            .border(1.25.dp, Color.White.copy(alpha = 0.72f), cardShape)
                            .padding(cardSizeDp * 0.105f),
                    ) {
                        val textScale = (cubeScale / 0.68f).coerceIn(0.55f, 1.25f)
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
                                fontSize = (26f * textScale).sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                text = snapshot.conditionLabel,
                                color = Color.White,
                                fontSize = (11f * textScale).sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                            Text(
                                text = highLowText,
                                color = Color.White.copy(alpha = 0.86f),
                                fontSize = (9.5f * textScale).sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.sm))

        // Studio control chips: Photo, Widget layers, Page mode, Reset
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChipButton(
                text = if (hasWallpaperPhoto) "Change Photo" else "Pick Photo",
                selected = hasWallpaperPhoto,
                onClick = {
                    markExpectingActivityResult(context)
                    photoPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )
            FilterChipButton(
                text = if (weatherEnabled) "Weather ✓" else "+ Weather",
                selected = weatherEnabled,
                onClick = {
                    val next = !weatherEnabled
                    weatherEnabled = next
                    WeatherPrefs.setWallpaperWeatherEnabled(context, next)
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
            if (weatherEnabled) {
                Text(
                    text = "Reset",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.extendedColors.textMuted,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { updateLayout(0.50f, 0.28f, 0.68f) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }

        if (weatherEnabled) {
            Spacer(Modifier.height(Spacing.xs))
            val normalizedSize = ((cubeScale - 0.35f) / (0.92f - 0.35f)).coerceIn(0f, 1f)
            WallpaperSizeSlider(
                label = "Widget Size",
                value = normalizedSize,
                valueLabel = "${(cubeScale * 100).roundToInt()}%",
                onValueChange = { frac ->
                    val nextScale = 0.35f + (0.92f - 0.35f) * frac.coerceIn(0f, 1f)
                    updateLayout(cubeCenterX, cubeCenterY, nextScale)
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

            PrimaryButton(
                text = if (isLiveActive) "Re-apply" else "Apply Wallpaper",
                leadingIcon = {
                    Icon(Icons.Rounded.Wallpaper, contentDescription = null, modifier = Modifier.size(17.dp))
                },
                onClick = {
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
                },
                modifier = Modifier.weight(1.35f),
            )
        }
    }
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
