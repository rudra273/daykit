package com.daykit.core.designsystem

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Automated WCAG 2.1 contrast and Apple HIG System Gray (`systemGray`..`systemGray6`)
 * token invariants test.
 */
class ColorContrastTest {

    private fun srgbChannelToLinear(c: Float): Double {
        val v = c.toDouble().coerceIn(0.0, 1.0)
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun relativeLuminance(color: Color): Double {
        val r = srgbChannelToLinear(color.red)
        val g = srgbChannelToLinear(color.green)
        val b = srgbChannelToLinear(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrastRatio(fg: Color, bg: Color): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    private fun assertContrast(label: String, fg: Color, bg: Color, minRatio: Double = 4.5) {
        val ratio = contrastRatio(fg, bg)
        assertTrue(
            "$label contrast ${"%.2f".format(ratio)}:1 is below required $minRatio:1",
            ratio >= minRatio,
        )
    }

    @Test
    fun lightThemeTextAndSurfaceContrastMeetWcagAA() {
        val scheme = DayKitLightColorScheme
        val ext = LightExtendedColors

        assertEquals("Light background uses Apple systemGray6 (#F2F2F7)", AppleSystemGrays.Gray6Light, scheme.background)
        assertEquals("Light card is Pure White (#FFFFFF)", Color.White, ext.card)
        assertContrast("Light onBackground vs background", scheme.onBackground, scheme.background)
        assertContrast("Light onSurface vs card", scheme.onSurface, ext.card)
        assertContrast("Light textMuted vs card", ext.textMuted, ext.card)
        assertContrast("Light textMuted vs background", ext.textMuted, scheme.background)
        assertContrast("Light primary vs card", scheme.primary, ext.card)
        assertContrast("Light onPrimary vs primary", scheme.onPrimary, scheme.primary)
        assertContrast("Light onPrimaryContainer vs primaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer)
        assertContrast("Light onActionFill vs actionFill", ext.onActionFill, ext.actionFill)
    }

    @Test
    fun lightThemeStatusPillsMeetWcagAA() {
        val ext = LightExtendedColors
        assertContrast("Light success vs successContainer", ext.success, ext.successContainer)
        assertContrast("Light warning vs warningContainer", ext.warning, ext.warningContainer)
        assertContrast("Light danger vs dangerContainer", ext.danger, ext.dangerContainer)
    }

    @Test
    fun darkThemeSystemGrayHierarchyAndContrastMeetWcagAA() {
        val scheme = DayKitDarkColorScheme
        val ext = DarkExtendedColors

        // Apple System Gray Dark hierarchy: systemGray6 (#1C1C1E) < systemGray5 (#2C2C2E) < systemGray4 (#3A3A3C)
        assertEquals("Dark background uses Apple systemGray6 (#1C1C1E)", AppleSystemGrays.Gray6Dark, scheme.background)
        assertEquals("Dark card uses Apple systemGray5 (#2C2C2E)", AppleSystemGrays.Gray5Dark, ext.card)
        assertEquals("Dark elevated uses Apple systemGray4 (#3A3A3C)", AppleSystemGrays.Gray4Dark, ext.elevated)
        assertTrue(
            "Dark card must be lighter than base background",
            relativeLuminance(ext.card) > relativeLuminance(scheme.background),
        )
        assertTrue(
            "Dark elevated modal surface must be lighter than card",
            relativeLuminance(ext.elevated) > relativeLuminance(ext.card),
        )

        // Switch thumb / onPrimary must remain pure white in Dark Mode
        assertEquals("Dark onPrimary must be pure white", Color.White, scheme.onPrimary)

        assertContrast("Dark onBackground vs background", scheme.onBackground, scheme.background)
        assertContrast("Dark onSurface vs card", scheme.onSurface, ext.card)
        assertContrast("Dark onSurface vs elevated", scheme.onSurface, ext.elevated)
        assertContrast("Dark textMuted vs card", ext.textMuted, ext.card)
        assertContrast("Dark textMuted vs background", ext.textMuted, scheme.background)
        assertContrast("Dark primary vs card", scheme.primary, ext.card)
        assertContrast("Dark onPrimaryContainer vs primaryContainer", scheme.onPrimaryContainer, scheme.primaryContainer)
        assertContrast("Dark onActionFill vs actionFill", ext.onActionFill, ext.actionFill)
    }

    @Test
    fun darkThemeStatusPillsMeetWcagAA() {
        val ext = DarkExtendedColors
        assertContrast("Dark success vs successContainer", ext.success, ext.successContainer)
        assertContrast("Dark warning vs warningContainer", ext.warning, ext.warningContainer)
        assertContrast("Dark danger vs dangerContainer", ext.danger, ext.dangerContainer)
    }
}
