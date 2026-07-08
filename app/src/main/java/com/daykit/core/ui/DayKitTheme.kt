package com.daykit.core.ui

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.delay

enum class AppTheme {
    SKY_BLUE,
    ZINC_ROSE,
    SPACE_BLACK,
}

enum class AppBackground {
    DARK_GLASS,
    LIGHT_GLASS,
    AURORA,
}

const val PREF_APP_THEME = "app_theme"
const val PREF_APP_BACKGROUND = "app_background"
const val PREF_GLASS_OPACITY = "glass_opacity"
const val PREF_GLASS_BLUR = "glass_blur"

private const val DEFAULT_GLASS_OPACITY = 0f
private const val DEFAULT_GLASS_BLUR = 20f

data class GlassMorphSettings(
    val opacity: Float = DEFAULT_GLASS_OPACITY,
    val blur: Float = DEFAULT_GLASS_BLUR,
)

val LocalAppTheme = staticCompositionLocalOf { AppTheme.SPACE_BLACK }
val LocalAppBackground = staticCompositionLocalOf { AppBackground.DARK_GLASS }
val LocalGlassMorphSettings = staticCompositionLocalOf { GlassMorphSettings() }

val IsLightBackground @Composable get() = LocalAppBackground.current == AppBackground.LIGHT_GLASS

val Cyan @Composable get() = when (LocalAppTheme.current) {
    AppTheme.SKY_BLUE -> Color(0xFF38BDF8)
    AppTheme.ZINC_ROSE -> Color(0xFFF43F5E)
    AppTheme.SPACE_BLACK -> if (IsLightBackground) Color(0xFF0F172A) else Color(0xFFE5E7EB)
}
val Teal @Composable get() = when (LocalAppTheme.current) {
    AppTheme.SKY_BLUE -> Color(0xFF7DD3FC)
    AppTheme.ZINC_ROSE -> Color(0xFFFB7185)
    AppTheme.SPACE_BLACK -> if (IsLightBackground) Color(0xFF334155) else Color(0xFF9CA3AF)
}
val DeepBackground @Composable get() = if (IsLightBackground) Color(0xFFF8FAFC) else Color(0xFF09090B)
val Panel @Composable get() = if (IsLightBackground) Color(0xFFE2E8F0) else Color(0xFF18181B)
val PanelAlt @Composable get() = if (IsLightBackground) Color(0xFFCBD5E1) else Color(0xFF27272A)
val SoftText @Composable get() = if (IsLightBackground) Color(0xFF0F172A) else if (LocalAppTheme.current == AppTheme.SKY_BLUE) Color(0xFFF1F5F9) else Color(0xFFFAFAFA)
val MutedText @Composable get() = when (LocalAppTheme.current) {
    AppTheme.SKY_BLUE -> if (IsLightBackground) Color(0xFF475569) else Color(0xFF64748B)
    AppTheme.ZINC_ROSE -> if (IsLightBackground) Color(0xFF64748B) else Color(0xFF71717A)
    AppTheme.SPACE_BLACK -> if (IsLightBackground) Color(0xFF475569) else Color(0xFFA1A1AA)
}
val Stroke @Composable get() = if (IsLightBackground) Color(0xFFCBD5E1) else Color(0xFF27272A)
val Amber @Composable get() = Cyan
val DangerRed @Composable get() = if (LocalAppTheme.current == AppTheme.SKY_BLUE) Color(0xFFF87171) else Color(0xFFF43F5E)

val AmberMuted @Composable get() = Cyan.copy(alpha = 0.15f)
val DangerRedMuted @Composable get() = DangerRed.copy(alpha = 0.2f)
val Indigo @Composable get() = Color(0xFF3F3F46)
val NavyBlue @Composable get() = PanelAlt
val CardGlow @Composable get() = Teal.copy(alpha = 0.5f)

@Composable
fun Modifier.glassSurface(
    shape: RoundedCornerShape,
    selected: Boolean = false,
    tintStrength: Float = 0.10f,
    shadowElevation: Float = 4f,
): Modifier {
    val glass = LocalGlassMorphSettings.current
    val lightBackground = IsLightBackground
    val opacity = glass.opacity.coerceIn(0f, 100f) / 100f
    val blur = glass.blur.coerceIn(0f, 100f) / 100f
    val elevationWeight = shadowElevation.coerceIn(0f, 8f) / 8f
    val theme = LocalAppTheme.current
    val tint = Teal
    val selectedBoost = if (selected) 0.012f else 0f
    val themeGlassTint = when (theme) {
        AppTheme.SKY_BLUE -> 0.030f
        AppTheme.ZINC_ROSE -> 0.026f
        AppTheme.SPACE_BLACK -> 0f
    }
    val frostAlpha = if (lightBackground) {
        (0.10f + opacity * 0.16f + blur * 0.04f + tintStrength * 0.06f + selectedBoost).coerceIn(0.10f, 0.30f)
    } else {
        (0.035f + opacity * 0.18f + blur * 0.055f + tintStrength * 0.08f + selectedBoost).coerceIn(0.035f, 0.24f)
    }
    val topLightAlpha = if (lightBackground) {
        (0.20f + blur * 0.18f + selectedBoost).coerceIn(0.20f, 0.42f)
    } else {
        (0.10f + blur * 0.16f + selectedBoost).coerceIn(0.10f, 0.30f)
    }
    val middleClearAlpha = (0.014f + opacity * 0.035f + blur * 0.018f).coerceIn(0.014f, 0.08f)
    val bottomShadeAlpha = if (lightBackground) {
        (0.035f + opacity * 0.065f + elevationWeight * 0.022f).coerceIn(0.035f, 0.13f)
    } else {
        (0.018f + opacity * 0.04f + elevationWeight * 0.018f).coerceIn(0.018f, 0.08f)
    }
    val rimAlpha = if (lightBackground) {
        (0.28f + blur * 0.28f + selectedBoost).coerceIn(0.28f, 0.58f)
    } else {
        (0.16f + blur * 0.22f + selectedBoost).coerceIn(0.16f, 0.44f)
    }
    val tintAlpha = if (theme == AppTheme.SPACE_BLACK) {
        (tintStrength * 0.010f * opacity).coerceIn(0f, 0.010f)
    } else {
        (themeGlassTint + tintStrength * 0.012f * opacity).coerceIn(0.018f, 0.042f)
    }
    val shineAlpha = (0.035f + blur * 0.15f).coerceIn(0.035f, 0.19f)

    return this
        .clip(shape)
        .drawWithCache {
            val verticalGlass = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = topLightAlpha),
                    Color.White.copy(alpha = frostAlpha),
                    Color.White.copy(alpha = middleClearAlpha),
                    Color.Black.copy(alpha = bottomShadeAlpha),
                )
            )
            val topShine = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = shineAlpha),
                    Color.Transparent,
                ),
                center = androidx.compose.ui.geometry.Offset(size.width * 0.18f, 0f),
                radius = size.width * 0.95f,
            )
            onDrawBehind {
                drawRect(verticalGlass)
                drawRect(tint.copy(alpha = tintAlpha))
                drawRect(topShine)
            }
        }
        .border(0.7.dp, Color.White.copy(alpha = rimAlpha), shape)
}

@Composable
fun GlassFilterButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val accent = Cyan
    val selectedFillAlpha = if (IsLightBackground) 0.18f else 0.20f
    val selectedBorderAlpha = if (IsLightBackground) 0.78f else 0.88f
    Box(
        modifier = modifier
            .glassSurface(
                shape = shape,
                selected = selected,
                tintStrength = if (selected) 0.30f else 0.08f,
            )
            .then(
                if (selected) {
                    Modifier
                        .background(accent.copy(alpha = selectedFillAlpha), shape)
                        .border(1.2.dp, accent.copy(alpha = selectedBorderAlpha), shape)
                } else {
                    Modifier
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = when {
                !enabled -> MutedText
                selected -> accent
                else -> SoftText
            },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun GlassLoadingIndicator(
    modifier: Modifier = Modifier,
    delayMillis: Long = 260L,
) {
    var visible by remember { mutableStateOf(delayMillis <= 0L) }
    LaunchedEffect(delayMillis) {
        if (delayMillis > 0L) {
            delay(delayMillis)
            visible = true
        }
    }
    if (!visible) return

    val transition = rememberInfiniteTransition(label = "glass-loader")
    val xOffset by transition.animateFloat(
        initialValue = -88f,
        targetValue = 196f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 820, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "glass-loader-offset",
    )
    val shape = RoundedCornerShape(999.dp)
    val accent = Cyan

    Box(
        modifier = modifier
            .width(188.dp)
            .height(7.dp)
            .clip(shape)
            .background(Stroke.copy(alpha = if (IsLightBackground) 0.34f else 0.28f), shape)
            .border(0.7.dp, Color.White.copy(alpha = if (IsLightBackground) 0.48f else 0.20f), shape),
    ) {
        Box(
            modifier = Modifier
                .offset(x = xOffset.dp)
                .width(76.dp)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            accent.copy(alpha = 0.34f),
                            accent,
                            accent.copy(alpha = 0.34f),
                            Color.Transparent,
                        )
                    ),
                    shape,
                ),
        )
    }
}

@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val background = LocalAppBackground.current
    val backgroundModifier = when (background) {
        AppBackground.DARK_GLASS -> modifier.darkGlassBackground()
        AppBackground.LIGHT_GLASS -> modifier.lightGlassBackground()
        AppBackground.AURORA -> modifier.auroraGlassBackground()
    }

    Box(modifier = backgroundModifier) {
        content()
    }
}

private fun Modifier.darkGlassBackground(): Modifier = this
    .fillMaxSize()
    .background(Color(0xFF09090B))
    .background(
        Brush.verticalGradient(
            listOf(
                Color(0xFF27272A).copy(alpha = 0.18f),
                Color(0xFF09090B).copy(alpha = 0.88f),
                Color(0xFF09090B),
            )
        )
    )
    .background(
        Brush.linearGradient(
            listOf(
                Color(0xFF22D3EE).copy(alpha = 0.075f),
                Color.Transparent,
                Color(0xFFF43F5E).copy(alpha = 0.052f),
            )
        )
    )
    .background(
        Brush.linearGradient(
            listOf(
                Color(0xFFA78BFA).copy(alpha = 0.040f),
                Color.Transparent,
                Color(0xFF34D399).copy(alpha = 0.030f),
            )
        )
    )

private fun Modifier.lightGlassBackground(): Modifier = this
    .fillMaxSize()
    .background(Color(0xFFF8FAFC))
    .background(
        Brush.verticalGradient(
            listOf(
                Color(0xFFFFFFFF),
                Color(0xFFE0F2FE).copy(alpha = 0.70f),
                Color(0xFFF8FAFC),
            )
        )
    )
    .background(
        Brush.linearGradient(
            listOf(
                Color(0xFF7DD3FC).copy(alpha = 0.22f),
                Color.Transparent,
                Color(0xFFFDA4AF).copy(alpha = 0.18f),
            )
        )
    )

private fun Modifier.auroraGlassBackground(): Modifier = this
    .fillMaxSize()
    .background(Color(0xFF050816))
    .background(
        Brush.linearGradient(
            listOf(
                Color(0xFF22D3EE).copy(alpha = 0.22f),
                Color(0xFF312E81).copy(alpha = 0.42f),
                Color(0xFFF43F5E).copy(alpha = 0.18f),
            )
        )
    )
    .background(
        Brush.radialGradient(
            listOf(
                Color(0xFF34D399).copy(alpha = 0.22f),
                Color.Transparent,
            )
        )
    )
    .background(
        Brush.verticalGradient(
            listOf(
                Color.Transparent,
                Color(0xFF050816).copy(alpha = 0.78f),
            )
        )
    )

@Composable
fun DayKitTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val sharedPrefs = remember(context) { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    var themeName by remember { mutableStateOf(sharedPrefs.getString(PREF_APP_THEME, AppTheme.SPACE_BLACK.name) ?: AppTheme.SPACE_BLACK.name) }
    var backgroundName by remember { mutableStateOf(sharedPrefs.getString(PREF_APP_BACKGROUND, AppBackground.AURORA.name) ?: AppBackground.AURORA.name) }
    var glassOpacity by remember { mutableStateOf(sharedPrefs.getFloat(PREF_GLASS_OPACITY, DEFAULT_GLASS_OPACITY)) }
    var glassBlur by remember { mutableStateOf(sharedPrefs.getFloat(PREF_GLASS_BLUR, DEFAULT_GLASS_BLUR)) }
    
    DisposableEffect(sharedPrefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            when (key) {
                PREF_APP_THEME -> themeName = prefs.getString(PREF_APP_THEME, AppTheme.SPACE_BLACK.name) ?: AppTheme.SPACE_BLACK.name
                PREF_APP_BACKGROUND -> backgroundName = prefs.getString(PREF_APP_BACKGROUND, AppBackground.AURORA.name) ?: AppBackground.AURORA.name
                PREF_GLASS_OPACITY -> glassOpacity = prefs.getFloat(PREF_GLASS_OPACITY, DEFAULT_GLASS_OPACITY)
                PREF_GLASS_BLUR -> glassBlur = prefs.getFloat(PREF_GLASS_BLUR, DEFAULT_GLASS_BLUR)
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    
    val currentTheme = try { AppTheme.valueOf(themeName) } catch (e: Exception) { AppTheme.SPACE_BLACK }
    val currentBackground = try { AppBackground.valueOf(backgroundName) } catch (e: Exception) { AppBackground.DARK_GLASS }
    val useDarkSystemBarIcons = currentBackground == AppBackground.LIGHT_GLASS
    val glassSettings = GlassMorphSettings(
        opacity = glassOpacity,
        blur = glassBlur,
    )

    val window = (context as? Activity)?.window
    if (window != null) {
        DisposableEffect(window, useDarkSystemBarIcons) {
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = useDarkSystemBarIcons
                isAppearanceLightNavigationBars = useDarkSystemBarIcons
            }
            onDispose {}
        }
    }
    
    CompositionLocalProvider(
        LocalAppTheme provides currentTheme,
        LocalAppBackground provides currentBackground,
        LocalGlassMorphSettings provides glassSettings,
    ) {
        val colors = darkColorScheme(
            primary = Cyan,
            onPrimary = if (IsLightBackground) Color.White else Color(0xFF001716),
            secondary = Teal,
            onSecondary = if (IsLightBackground) Color.White else Color(0xFF001716),
            background = DeepBackground,
            onBackground = SoftText,
            surface = Panel,
            onSurface = SoftText,
            surfaceVariant = PanelAlt,
            onSurfaceVariant = SoftText,
            outline = Stroke,
        )

        MaterialTheme(
            colorScheme = colors,
            content = {
                Surface(color = DeepBackground, content = content)
            },
        )
    }
}

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Cyan,
            disabledContainerColor = PanelAlt,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContentColor = MutedText,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        leadingIcon?.invoke()
        Text(text = text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (enabled) Stroke else Stroke.copy(alpha = 0.5f)),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            contentColor = Cyan,
            disabledContentColor = MutedText,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        leadingIcon?.invoke()
        Text(text = text, fontWeight = FontWeight.SemiBold, style = textStyle)
    }
}

@Composable
fun AppBackButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(50.dp)
            .height(40.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(38.dp)
                .glassSurface(RoundedCornerShape(19.dp), selected = false),
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = SoftText,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
fun FullWidthDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Stroke.copy(alpha = 0.5f)),
    )
}
