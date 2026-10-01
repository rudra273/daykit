package com.daykit.core.designsystem.background

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WallpaperMathTest {
    private val window = Size(1000f, 2000f)

    @Test fun anImageWithTheWindowsAspectMapsOneToOne() {
        val region = imageRegion(Offset(100f, 400f), Size(300f, 200f), window, IntSize(500, 1000))!!
        assertEquals(IntOffset(50, 200), region.srcOffset)
        assertEquals(IntSize(150, 100), region.srcSize)
        assertEquals(IntOffset.Zero, region.dstOffset)
        assertEquals(IntSize(300, 200), region.dstSize)
    }

    /** A landscape photo on a portrait window is scaled to cover and centered, never stretched. */
    @Test fun aWiderImageIsCroppedEquallyOnBothSides() {
        // 4000x2000 covers 1000x2000 at scale 1.0; 1500 image px hidden each side.
        val full = imageRegion(Offset.Zero, window, window, IntSize(4000, 2000))!!
        assertEquals(IntOffset(1500, 0), full.srcOffset)
        assertEquals(IntSize(1000, 2000), full.srcSize)
    }

    /** Half scrolled off the top: only the visible half is drawn, in the lower half of the card. */
    @Test fun aPartlyOffScreenElementMapsOnlyItsVisiblePart() {
        val region = imageRegion(Offset(0f, -100f), Size(1000f, 200f), window, IntSize(1000, 2000))!!
        assertEquals(IntOffset(0, 0), region.srcOffset)
        assertEquals(IntSize(1000, 100), region.srcSize)
        assertEquals(IntOffset(0, 100), region.dstOffset)
        assertEquals(IntSize(1000, 100), region.dstSize)
    }

    @Test fun anElementFullyOffScreenDrawsNothing() {
        assertNull(imageRegion(Offset(0f, -500f), Size(1000f, 200f), window, IntSize(1000, 2000)))
        assertNull(imageRegion(Offset(0f, 2100f), Size(1000f, 200f), window, IntSize(1000, 2000)))
    }

    /** Stack blur must match the triangular-weighted blur it approximates, with clamped edges. */
    @Test fun stackBlurMatchesABruteForceTriangularBlur() {
        val w = 37
        val h = 5
        val radius = 4
        val source = IntArray(w * h) { i -> 0xFF000000.toInt() or ((i * 97 % 256) shl 16) or ((i * 31 % 256) shl 8) or (i * 13 % 256) }
        val fast = source.copyOf()
        StackBlur.blurPass(fast, w, h, radius, horizontal = true)

        for (y in 0 until h) for (x in 0 until w) {
            for (shift in intArrayOf(16, 8, 0)) {
                var sum = 0
                for (i in -radius..radius) {
                    val v = (source[y * w + (x + i).coerceIn(0, w - 1)] ushr shift) and 0xFF
                    sum += v * (radius + 1 - abs(i))
                }
                val expected = sum / ((radius + 1) * (radius + 1))
                val actual = (fast[y * w + x] ushr shift) and 0xFF
                assertEquals("pixel ($x,$y) channel $shift", expected, actual)
            }
        }
    }

    @Test fun stackBlurKeepsAFlatImageFlat() {
        val flat = IntArray(20 * 20) { 0xFF336699.toInt() }
        StackBlur.blurPass(flat, 20, 20, 6, horizontal = true)
        StackBlur.blurPass(flat, 20, 20, 6, horizontal = false)
        assertTrue(flat.all { it == 0xFF336699.toInt() })
    }
}
