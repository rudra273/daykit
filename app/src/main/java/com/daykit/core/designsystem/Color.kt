package com.daykit.core.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * "Meta Classic" palette — Facebook production colors.
 * Light = pure white + #1877F2. Dark = charcoal #18191A + #2D88FF.
 *
 * Raw tokens are private; screens consume [androidx.compose.material3.MaterialTheme.colorScheme]
 * and the semantic [ExtendedColors] layer via [extendedColors].
 */

// region Light raw tokens
private val LightPrimary = Color(0xFF1877F2)
private val LightPrimaryContainer = Color(0xFFE7F3FF)
private val LightOnPrimaryContainer = Color(0xFF1877F2)
// Soft gray page background applied to the whole screen — header, content, and
// bottom nav all sit on this. Only cards are white (via [LightCard]).
private val LightBackground = Color(0xFFF0F2F5)
private val LightSurface = Color(0xFFF0F2F5)
private val LightSurfaceVariant = Color(0xFFF0F2F5)
private val LightText = Color(0xFF050505)
private val LightMuted = Color(0xFF65676B)
private val LightOutline = Color(0xFFCED0D4)
private val LightDivider = Color(0xFFE4E6EB)
private val LightError = Color(0xFFE41E3F)
private val LightSuccess = Color(0xFF31A24C)
private val LightWarning = Color(0xFFF7B928)
private val LightCard = Color(0xFFFFFFFF)
private val LightElevated = Color(0xFFFFFFFF)
private val LightInputField = Color(0xFFF0F2F5)
// endregion

// region Dark raw tokens
private val DarkPrimary = Color(0xFF2D88FF)
private val DarkPrimaryContainer = Color(0xFF1E3A5F)
private val DarkOnPrimaryContainer = Color(0xFF9BC4FF)
private val DarkBackground = Color(0xFF18191A)
private val DarkSurface = Color(0xFF242526)
private val DarkSurfaceVariant = Color(0xFF3A3B3C)
private val DarkText = Color(0xFFE4E6EB)
private val DarkMuted = Color(0xFFB0B3B8)
private val DarkOutline = Color(0xFF55575A)
private val DarkDivider = Color(0xFF3E4042)
private val DarkError = Color(0xFFF02849)
private val DarkSuccess = Color(0xFF45BD62)
private val DarkWarning = Color(0xFFF7B928)
private val DarkCard = Color(0xFF242526)
private val DarkElevated = Color(0xFF3A3B3C)
private val DarkInputField = Color(0xFF3A3B3C)
// endregion

val DayKitLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightMuted,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = LightText,
    tertiary = LightSuccess,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE6F5EA),
    onTertiaryContainer = Color(0xFF1D7A35),
    error = LightError,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDE7EB),
    onErrorContainer = Color(0xFFC0142F),
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightMuted,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F8FA),
    surfaceContainer = Color(0xFFF0F2F5),
    surfaceContainerHigh = Color(0xFFE9EBEE),
    surfaceContainerHighest = Color(0xFFE4E6EB),
    outline = LightOutline,
    outlineVariant = LightDivider,
    inverseSurface = Color(0xFF242526),
    inverseOnSurface = Color(0xFFE4E6EB),
    inversePrimary = DarkPrimary,
    scrim = Color(0xFF000000),
)

val DayKitDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkMuted,
    onSecondary = Color(0xFF18191A),
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkText,
    tertiary = DarkSuccess,
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF1F3527),
    onTertiaryContainer = Color(0xFF7EDD96),
    error = DarkError,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF3B1D24),
    onErrorContainer = Color(0xFFFF98A8),
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkMuted,
    surfaceContainerLowest = Color(0xFF18191A),
    surfaceContainerLow = Color(0xFF1E1F20),
    surfaceContainer = Color(0xFF242526),
    surfaceContainerHigh = Color(0xFF303132),
    surfaceContainerHighest = Color(0xFF3A3B3C),
    outline = DarkOutline,
    outlineVariant = DarkDivider,
    inverseSurface = Color(0xFFE4E6EB),
    inverseOnSurface = Color(0xFF18191A),
    inversePrimary = LightPrimary,
    scrim = Color(0xFF000000),
)

/**
 * Nine per-tool accent hues (Facebook-menu-style colorful icon tiles).
 * Light and dark variants supplied via [LightAccentColors] / [DarkAccentColors].
 */
@Immutable
data class AccentColors(
    val blue: Color,
    val teal: Color,
    val green: Color,
    val red: Color,
    val orange: Color,
    val yellow: Color,
    val purple: Color,
    val pink: Color,
    val indigo: Color,
)

private val LightAccentColors = AccentColors(
    blue = Color(0xFF1877F2),
    teal = Color(0xFF0E9F8E),
    green = Color(0xFF31A24C),
    red = Color(0xFFE41E3F),
    orange = Color(0xFFF3752B),
    yellow = Color(0xFFF7B928),
    purple = Color(0xFF7B39C9),
    pink = Color(0xFFD6249F),
    indigo = Color(0xFF4B4AEF),
)

private val DarkAccentColors = AccentColors(
    blue = Color(0xFF2D88FF),
    teal = Color(0xFF2ABBA7),
    green = Color(0xFF45BD62),
    red = Color(0xFFF02849),
    orange = Color(0xFFFF8A3D),
    yellow = Color(0xFFF7B928),
    purple = Color(0xFF9360F7),
    pink = Color(0xFFF35BC7),
    indigo = Color(0xFF6C6CFF),
)

/**
 * Semantic color layer beyond the M3 [androidx.compose.material3.ColorScheme].
 * [card] is the opaque fill for the top bar and bottom navigation bar.
 */
@Immutable
data class ExtendedColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val card: Color,
    val elevated: Color,
    val divider: Color,
    val textMuted: Color,
    val inputField: Color,
    val isDark: Boolean,
    val accents: AccentColors,
)

val LightExtendedColors = ExtendedColors(
    success = LightSuccess,
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFE6F5EA),
    warning = LightWarning,
    warningContainer = Color(0xFFFEF3D7),
    danger = LightError,
    dangerContainer = Color(0xFFFDE7EB),
    card = LightCard,
    elevated = LightElevated,
    divider = LightDivider,
    textMuted = LightMuted,
    inputField = LightInputField,
    isDark = false,
    accents = LightAccentColors,
)

val DarkExtendedColors = ExtendedColors(
    success = DarkSuccess,
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFF1F3527),
    warning = DarkWarning,
    warningContainer = Color(0xFF3D3320),
    danger = DarkError,
    dangerContainer = Color(0xFF3B1D24),
    card = DarkCard,
    elevated = DarkElevated,
    divider = DarkDivider,
    textMuted = DarkMuted,
    inputField = DarkInputField,
    isDark = true,
    accents = DarkAccentColors,
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/** Tint an accent color for use as a filled icon-tile background over [ExtendedColors.card]. */
@Composable
@ReadOnlyComposable
fun Color.asAccentContainer(): Color =
    copy(alpha = if (LocalExtendedColors.current.isDark) 0.20f else 0.12f)
