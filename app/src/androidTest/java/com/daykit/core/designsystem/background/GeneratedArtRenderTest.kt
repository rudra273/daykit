package com.daykit.core.designsystem.background

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Renders every generated background in both themes. Asserts they draw at the
 * requested size with real variation (not a blank fill), and writes PNGs to the
 * app's external files dir (`art-preview/`) for a visual check.
 */
@RunWith(AndroidJUnit4::class)
class GeneratedArtRenderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun everyGeneratedBackgroundRendersWithDetail() {
        val out = File(context.getExternalFilesDir(null), "art-preview").apply { mkdirs() }
        PageBackgroundKind.entries.filter(GeneratedArt::isGenerated).forEach { kind ->
            listOf(false, true).forEach { dark ->
                val bitmap = GeneratedArt.render(kind, dark, 1080, 2340)
                assertEquals(1080, bitmap.width)
                assertEquals(2340, bitmap.height)
                val samples = (0 until 400).map { i -> bitmap.getPixel((i * 37) % 1080, (i * 113) % 2340) }.toSet()
                assertTrue("$kind dark=$dark looks blank", samples.size > 50)
                File(out, "${kind.name.lowercase()}-${if (dark) "dark" else "light"}.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
    }
}
