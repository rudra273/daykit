package com.daykit.core.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Apple HIG iOS/iPadOS System Gray scale & semantic palette, harmonized with the
 * Hinge Coast brand identity (#0088FF blue · #00C0E8 cyan · #00C8B3 mint).
 *
 * Reference: https://developer.apple.com/design/human-interface-guidelines/color#iOS-iPadOS-system-gray-colors
 *
 * Light Mode uses Apple's grouped surface hierarchy (matching the Apple Store iOS app):
 *   - Page background: `systemGray6` (#F2F2F7, R:242 G:242 B:247)
 *   - Card surface:    Pure White (#FFFFFF, R:255 G:255 B:255)
 *   - Input / Track:   `systemGray5` (#E5E5EA, R:229 G:229 B:234)
 *   - Elevated modal:  Pure White (#FFFFFF)
 * Dark Mode uses Apple's Elevated Dark hierarchy:
 *   - Page background: `systemGray6` Dark (#1C1C1E, R:28 G:28 B:30)
 *   - Card surface:    `systemGray5` Dark (#2C2C2E, R:44 G:44 B:46)
 *   - Elevated / Pill: `systemGray4` Dark (#3A3A3C, R:58 G:58 B:60)
 *   - Outline / Track: `systemGray3` Dark (#48484A, R:72 G:72 B:74)
 *   - Tertiary:        `systemGray2` Dark (#636366 / #7C7C80)
 *   - Muted label:     `systemGray`  Dark Increased Contrast (#AEAEB2, R:174 G:174 B:178)
 */

// region Apple iOS / iPadOS System Gray Tokens
object AppleSystemGrays {
    // Default (Light)
    val GrayLight = Color(0xFF8E8E93)       // systemGray  (142, 142, 147)
    val Gray2Light = Color(0xFFAEAEB2)      // systemGray2 (174, 174, 178)
    val Gray3Light = Color(0xFFC7C7CC)      // systemGray3 (199, 199, 204)
    val Gray4Light = Color(0xFFD1D1D6)      // systemGray4 (209, 209, 214)
    val Gray5Light = Color(0xFFE5E5EA)      // systemGray5 (229, 229, 234)
    val Gray6Light = Color(0xFFF2F2F7)      // systemGray6 (242, 242, 247)

    // Default (Dark)
    val GrayDark = Color(0xFF8E8E93)        // systemGray  (142, 142, 147)
    val Gray2Dark = Color(0xFF636366)       // systemGray2 (99, 99, 102)
    val Gray3Dark = Color(0xFF48484A)       // systemGray3 (72, 72, 74)
    val Gray4Dark = Color(0xFF3A3A3C)       // systemGray4 (58, 58, 60)
    val Gray5Dark = Color(0xFF2C2C2E)       // systemGray5 (44, 44, 46)
    val Gray6Dark = Color(0xFF1C1C1E)       // systemGray6 (28, 28, 30)

    // Increased Contrast (Light)
    val GrayLightHighContrast = Color(0xFF6C6C70)   // (108, 108, 112)
    val Gray2LightHighContrast = Color(0xFF8E8E93)  // (142, 142, 147)
    val Gray3LightHighContrast = Color(0xFFAEAEB2)  // (174, 174, 178)
    val Gray4LightHighContrast = Color(0xFFBCBCC0)  // (188, 188, 192)
    val Gray5LightHighContrast = Color(0xFFD8D8DC)  // (216, 216, 220)
    val Gray6LightHighContrast = Color(0xFFEBEBF0)  // (235, 235, 240)

    // Increased Contrast (Dark)
    val GrayDarkHighContrast = Color(0xFFAEAEB2)    // (174, 174, 178)
    val Gray2DarkHighContrast = Color(0xFF7C7C80)   // (124, 124, 128)
    val Gray3DarkHighContrast = Color(0xFF545456)   // (84, 84, 86)
    val Gray4DarkHighContrast = Color(0xFF444446)   // (68, 68, 70)
    val Gray5DarkHighContrast = Color(0xFF363638)   // (54, 54, 56)
    val Gray6DarkHighContrast = Color(0xFF242426)   // (36, 36, 38)
}
// endregion

// region Light raw tokens (Apple Store iOS app: #F2F2F7 systemGray6 canvas + #FFFFFF pure white cards)
private val LightPrimary = Color(0xFF0070E0)
private val LightPrimaryContainer = Color(0xFFE3F1FF)
private val LightOnPrimaryContainer = Color(0xFF004F9E)
private val LightBackground = AppleSystemGrays.Gray6Light           // #F2F2F7 (systemGray6 page canvas)
private val LightSurface = AppleSystemGrays.Gray6Light              // #F2F2F7
private val LightSurfaceVariant = AppleSystemGrays.Gray5Light       // #E5E5EA
private val LightText = Color(0xFF000000)
private val LightMuted = AppleSystemGrays.GrayLightHighContrast     // #6C6C70 (>= 4.68:1 on #F2F2F7, 5.25:1 on #FFFFFF)
private val LightTertiary = AppleSystemGrays.GrayLight              // #8E8E93
private val LightOutline = AppleSystemGrays.Gray3Light              // #C7C7CC
private val LightDivider = Color(0x293C3C43)
private val LightHairlineBorder = Color(0x12000000)
private val LightError = Color(0xFFC20013)
private val LightSuccess = Color(0xFF1A7532)
private val LightWarning = Color(0xFF994A00)
private val LightCard = Color(0xFFFFFFFF)                           // Pure White #FFFFFF cards on #F2F2F7 canvas
private val LightElevated = Color(0xFFFFFFFF)                       // Elevated sheets/dialogs
private val LightInputField = AppleSystemGrays.Gray5Light           // #E5E5EA (systemGray5 fields & tracks)
private val LightActionFill = Color(0xFF0070E0)
// endregion

// region Dark raw tokens (Apple Elevated Dark Mode: systemGray6 -> systemGray5 -> systemGray4)
private val DarkPrimary = Color(0xFF47A1FF)
private val DarkPrimaryContainer = Color(0xFF0F3156)
private val DarkOnPrimaryContainer = Color(0xFF99CEFF)
private val DarkBackground = AppleSystemGrays.Gray6Dark             // #1C1C1E (soft dark graphite base)
private val DarkSurface = AppleSystemGrays.Gray5Dark                // #2C2C2E (grouped card surface)
private val DarkSurfaceVariant = AppleSystemGrays.Gray4Dark         // #3A3A3C (elevated / control surface)
private val DarkText = Color(0xFFFFFFFF)
private val DarkMuted = AppleSystemGrays.GrayDarkHighContrast       // #AEAEB2 (>= 5.3:1 on #2C2C2E, 7.0:1 on #1C1C1E)
private val DarkTertiary = AppleSystemGrays.Gray2DarkHighContrast   // #7C7C80
private val DarkOutline = AppleSystemGrays.Gray3Dark                // #48484A
private val DarkDivider = Color(0x52545458)                         // subtle inner card separator
private val DarkHairlineBorder = Color(0x16FFFFFF)                  // subtle top/edge specular rim
private val DarkError = Color(0xFFFF453A)
private val DarkSuccess = Color(0xFF30D158)
private val DarkWarning = Color(0xFFFF9F0A)
private val DarkCard = AppleSystemGrays.Gray5Dark                   // #2C2C2E
private val DarkElevated = AppleSystemGrays.Gray4Dark               // #3A3A3C
private val DarkInputField = AppleSystemGrays.Gray4Dark             // #3A3A3C
private val DarkActionFill = Color(0xFF0070E0)
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
    tertiaryContainer = Color(0xFFE4F6E8),
    onTertiaryContainer = Color(0xFF146329),
    error = LightError,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE9EC),
    onErrorContainer = Color(0xFF9E0010),
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightMuted,
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F7FA),
    surfaceContainer = AppleSystemGrays.Gray6Light,
    surfaceContainerHigh = AppleSystemGrays.Gray5Light,
    surfaceContainerHighest = AppleSystemGrays.Gray4Light,
    outline = LightOutline,
    outlineVariant = LightDivider,
    inverseSurface = AppleSystemGrays.Gray6Dark,
    inverseOnSurface = AppleSystemGrays.Gray6Light,
    inversePrimary = DarkPrimary,
    scrim = Color(0xFF000000),
)

val DayKitDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkMuted,
    onSecondary = AppleSystemGrays.Gray6Dark,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkText,
    tertiary = DarkSuccess,
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF142E1B),
    onTertiaryContainer = Color(0xFF75E893),
    error = DarkError,
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF3B1518),
    onErrorContainer = Color(0xFFFF9EA2),
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkMuted,
    surfaceContainerLowest = AppleSystemGrays.Gray6Dark,
    surfaceContainerLow = AppleSystemGrays.Gray6DarkHighContrast,
    surfaceContainer = AppleSystemGrays.Gray5Dark,
    surfaceContainerHigh = AppleSystemGrays.Gray4Dark,
    surfaceContainerHighest = AppleSystemGrays.Gray3Dark,
    outline = DarkOutline,
    outlineVariant = DarkDivider,
    inverseSurface = AppleSystemGrays.Gray6Light,
    inverseOnSurface = AppleSystemGrays.Gray6Dark,
    inversePrimary = LightPrimary,
    scrim = Color(0xFF000000),
)

/**
 * Calibrated Apple HIG & Hinge Coast accent hues.
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
    val cyan: Color = Color(0xFF00C0E8),
    val mint: Color = Color(0xFF00C8B3),
)

private val LightAccentColors = AccentColors(
    blue = Color(0xFF0088FF),
    teal = Color(0xFF00A8B5),
    green = Color(0xFF34C759),
    red = Color(0xFFFF3B30),
    orange = Color(0xFFFF9500),
    yellow = Color(0xFFE6A100),
    purple = Color(0xFFAF52DE),
    pink = Color(0xFFFF2D55),
    indigo = Color(0xFF5856D6),
    cyan = Color(0xFF00C0E8),
    mint = Color(0xFF00C8B3),
)

private val DarkAccentColors = AccentColors(
    blue = Color(0xFF0A84FF),
    teal = Color(0xFF40C8E0),
    green = Color(0xFF30D158),
    red = Color(0xFFFF453A),
    orange = Color(0xFFFF9F0A),
    yellow = Color(0xFFFFD60A),
    purple = Color(0xFFBF5AF2),
    pink = Color(0xFFFF375F),
    indigo = Color(0xFF5E5CE6),
    cyan = Color(0xFF64D2FF),
    mint = Color(0xFF63E6E2),
)

/**
 * Semantic color layer beyond the M3 [androidx.compose.material3.ColorScheme].
 * [card] is the opaque card fill (`systemGray5` #2C2C2E in Dark Mode);
 * [elevated] is the lifted sheet/dialog/menu fill (`systemGray4` #3A3A3C in Dark Mode);
 * [actionFill] keeps white action text legible (>= 4.5:1 in both modes).
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
    val hairlineBorder: Color,
    val textMuted: Color,
    val textTertiary: Color,
    val inputField: Color,
    /** Text-field boxes: always solid and plain, even in Liquid glass mode. */
    val fieldFill: Color,
    val actionFill: Color,
    val onActionFill: Color,
    val isDark: Boolean,
    val accents: AccentColors,
)

val LightExtendedColors = ExtendedColors(
    success = LightSuccess,
    onSuccess = Color(0xFFFFFFFF),
    successContainer = Color(0xFFE4F6E8),
    warning = LightWarning,
    warningContainer = Color(0xFFFFF3E0),
    danger = LightError,
    dangerContainer = Color(0xFFFFE9EC),
    card = LightCard,
    elevated = LightElevated,
    divider = LightDivider,
    hairlineBorder = LightHairlineBorder,
    textMuted = LightMuted,
    textTertiary = LightTertiary,
    inputField = LightInputField,
    fieldFill = LightInputField,
    actionFill = LightActionFill,
    onActionFill = Color.White,
    isDark = false,
    accents = LightAccentColors,
)

val DarkExtendedColors = ExtendedColors(
    success = DarkSuccess,
    onSuccess = Color(0xFF000000),
    successContainer = Color(0xFF142E1B),
    warning = DarkWarning,
    warningContainer = Color(0xFF33240C),
    danger = DarkError,
    dangerContainer = Color(0xFF3B1518),
    card = DarkCard,
    elevated = DarkElevated,
    divider = DarkDivider,
    hairlineBorder = DarkHairlineBorder,
    textMuted = DarkMuted,
    textTertiary = DarkTertiary,
    inputField = DarkInputField,
    fieldFill = DarkInputField,
    actionFill = DarkActionFill,
    onActionFill = Color.White,
    isDark = true,
    accents = DarkAccentColors,
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/** Tint an accent color for use as a filled icon-tile background over [ExtendedColors.card]. */
@Composable
@ReadOnlyComposable
fun Color.asAccentContainer(): Color =
    copy(alpha = if (LocalExtendedColors.current.isDark) 0.22f else 0.14f)
