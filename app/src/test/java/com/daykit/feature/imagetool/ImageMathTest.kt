package com.daykit.feature.imagetool

import com.daykit.feature.imagetool.domain.CropRect
import com.daykit.feature.imagetool.domain.ImageMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageMathTest {
    @Test
    fun fitWithin_neverUpscales() {
        assertEquals(800 to 600, ImageMath.fitWithin(800, 600, 2048))
        assertEquals(800 to 600, ImageMath.fitWithin(800, 600, null))
    }

    @Test
    fun fitWithin_scalesLongestEdge() {
        assertEquals(1000 to 500, ImageMath.fitWithin(4000, 2000, 1000))
        assertEquals(500 to 1000, ImageMath.fitWithin(2000, 4000, 1000))
    }

    @Test
    fun searchQuality_returnsHighestQualityUnderTarget() {
        // size == quality * 10 bytes
        assertEquals(50, ImageMath.searchQuality(500) { it * 10L })
        assertEquals(100, ImageMath.searchQuality(5000) { it * 10L })
    }

    @Test
    fun searchQuality_nullWhenUnreachable() {
        assertNull(ImageMath.searchQuality(10) { it * 10L })
    }

    @Test
    fun formatSize_picksUnit() {
        assertEquals("512 B", ImageMath.formatSize(512))
        assertEquals("200 KB", ImageMath.formatSize(200 * 1024L))
        assertEquals("1.50 MB", ImageMath.formatSize(1_572_864))
    }

    @Test
    fun croppedSize_appliesFractions() {
        assertEquals(1000 to 500, ImageMath.croppedSize(2000, 1000, CropRect(0f, 0f, 0.5f, 0.5f)))
        assertEquals(500 to 250, ImageMath.croppedSize(2000, 1000, CropRect(0.25f, 0.25f, 0.5f, 0.5f)))
        assertEquals(2000 to 1000, ImageMath.croppedSize(2000, 1000, null))
    }

    @Test
    fun decodeScale_targetsCroppedRegion() {
        val crop = CropRect(0f, 0f, 0.5f, 0.5f) // 2000x1000 -> 1000x500
        assertEquals(0.5f, ImageMath.decodeScale(2000, 1000, crop, 500), 0.001f)
        assertEquals(1f, ImageMath.decodeScale(2000, 1000, crop, 4000), 0.001f)
        assertEquals(1f, ImageMath.decodeScale(2000, 1000, crop, null), 0.001f)
    }
}
