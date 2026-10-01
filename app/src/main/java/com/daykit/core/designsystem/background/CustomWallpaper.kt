package com.daykit.core.designsystem.background

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/**
 * The user's own background photo.
 *
 * Importing decodes the picked image, downscales it and re-encodes it as a JPEG
 * in app-private storage. Re-encoding drops all metadata (GPS location, camera,
 * timestamps), so only the pixels are kept. Nothing leaves the device, and the
 * original in the gallery is untouched.
 */
object CustomWallpaper {
    /** Longest edge kept; enough for a sharp full-screen page on current phones. */
    private const val MAX_EDGE = 2400
    /** The frosted copy is blurred at this fraction of the size; cheap and smoother. */
    private const val FROST_SCALE = 0.125f
    private const val FROST_RADIUS = 10
    private const val FROST_SATURATION = 1.35f

    private fun file(context: Context) = File(File(context.filesDir, "wallpaper"), "custom.jpg")

    /** Bumped on every import/delete so the theme reloads; 0 until first read. */
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision

    /** The last loaded image, so new activities/windows don't decode it again. */
    private var cache: Pair<CacheKey, PageBackground.Image>? = null
    private data class CacheKey(val modified: Long, val solid: Color, val scrim: Color)

    fun exists(context: Context): Boolean = file(context).isFile

    /** Replaces the stored photo with [uri]. Throws if the image can't be read. */
    suspend fun import(context: Context, uri: Uri) = withContext(Dispatchers.IO) {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_EDGE) {
                val scale = MAX_EDGE.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1),
                )
            }
        }
        val target = file(context)
        target.parentFile?.mkdirs()
        // Write then rename, so a crash mid-write never leaves a truncated photo.
        val temp = File(target.parentFile, "custom.jpg.tmp")
        try {
            temp.outputStream().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)) { "Could not save the photo" }
            }
            check(temp.renameTo(target)) { "Could not save the photo" }
        } finally {
            bitmap.recycle()
            temp.delete()
        }
        _revision.value = System.nanoTime()
    }

    fun delete(context: Context) {
        file(context).delete()
        cache = null
        _revision.value = System.nanoTime()
    }

    /** A small decode of the stored photo for previews, or null if there is none. */
    fun thumbnail(context: Context, maxEdge: Int): Bitmap? {
        val photo = file(context)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(photo.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        return BitmapFactory.decodeFile(photo.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /**
     * Loads the stored photo ready to draw: a scrim of [scrim] baked in for text
     * legibility, plus a blurred, slightly more saturated copy for glass.
     * Null if there is no photo or it can't be decoded.
     */
    suspend fun load(context: Context, solid: Color, scrim: Color): PageBackground.Image? {
        val photo = file(context)
        val key = CacheKey(photo.lastModified(), solid, scrim)
        cache?.let { (cachedKey, image) -> if (cachedKey == key) return image }
        return decode(photo, solid, scrim)?.also { cache = key to it }
    }

    private suspend fun decode(photo: File, solid: Color, scrim: Color): PageBackground.Image? =
        withContext(Dispatchers.Default) {
            val decoded = BitmapFactory.decodeFile(photo.path) ?: return@withContext null
            val sharp = decoded.copy(Bitmap.Config.ARGB_8888, true)
            decoded.recycle()

            val blurred = Bitmap.createScaledBitmap(
                sharp,
                (sharp.width * FROST_SCALE).roundToInt().coerceAtLeast(1),
                (sharp.height * FROST_SCALE).roundToInt().coerceAtLeast(1),
                true,
            ).let { small ->
                val vivid = Bitmap.createBitmap(small.width, small.height, Bitmap.Config.ARGB_8888)
                Canvas(vivid).drawBitmap(small, 0f, 0f, saturationPaint(FROST_SATURATION))
                if (small !== sharp) small.recycle()
                StackBlur.blur(vivid, FROST_RADIUS)
                vivid
            }

            Canvas(sharp).drawColor(scrim.toArgb())
            Canvas(blurred).drawColor(scrim.toArgb())
            PageBackground.Image(solid = solid, sharp = sharp.asImageBitmap(), blurred = blurred.asImageBitmap())
        }

    private fun saturationPaint(saturation: Float) = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(saturation) })
    }
}

/**
 * Mario Klingemann's Stack Blur: a close, fast approximation of a Gaussian blur,
 * in place on an ARGB_8888 bitmap. Run on a small copy; cost is O(pixels), not
 * O(pixels × radius).
 */
internal object StackBlur {
    fun blur(bitmap: Bitmap, radius: Int) {
        require(bitmap.config == Bitmap.Config.ARGB_8888 && bitmap.isMutable)
        if (radius < 1) return
        val w = bitmap.width
        val h = bitmap.height
        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        blurPass(pixels, w, h, radius, horizontal = true)
        blurPass(pixels, w, h, radius, horizontal = false)
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
    }

    /** One direction of the separable blur over every row (or column). */
    internal fun blurPass(pixels: IntArray, w: Int, h: Int, radius: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val length = if (horizontal) w else h
        val div = 2 * radius + 1
        // Sum of the triangular weights 1, 2, …, r+1, …, 2, 1.
        val weightSum = (radius + 1) * (radius + 1)
        val stack = IntArray(div)
        val line = IntArray(length)

        for (l in 0 until lines) {
            for (i in 0 until length) line[i] = pixels[index(l, i, w, horizontal)]
            for (shift in intArrayOf(24, 16, 8, 0)) {
                var sum = 0
                var inSum = 0
                var outSum = 0
                for (i in -radius..radius) {
                    val v = (line[i.coerceIn(0, length - 1)] ushr shift) and 0xFF
                    stack[i + radius] = v
                    val weight = radius + 1 - kotlin.math.abs(i)
                    sum += v * weight
                    if (i > 0) inSum += v else outSum += v
                }
                var stackPointer = radius
                val out = IntArray(length)
                for (i in 0 until length) {
                    out[i] = sum / weightSum
                    sum -= outSum
                    val stackStart = (stackPointer - radius + div) % div
                    outSum -= stack[stackStart]
                    val next = (line[(i + radius + 1).coerceAtMost(length - 1)] ushr shift) and 0xFF
                    stack[stackStart] = next
                    inSum += next
                    sum += inSum
                    stackPointer = (stackPointer + 1) % div
                    val moved = stack[stackPointer]
                    outSum += moved
                    inSum -= moved
                }
                for (i in 0 until length) {
                    val idx = index(l, i, w, horizontal)
                    pixels[idx] = (pixels[idx] and (0xFF shl shift).inv()) or (out[i] shl shift)
                }
            }
        }
    }

    private fun index(line: Int, i: Int, w: Int, horizontal: Boolean) =
        if (horizontal) line * w + i else i * w + line
}
