package com.daykit.feature.imagetool.domain

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

enum class OutputFormat(val label: String, val extension: String, val mime: String, val lossy: Boolean) {
    JPEG("JPEG", "jpg", "image/jpeg", true),
    PNG("PNG", "png", "image/png", false),
    WEBP("WebP", "webp", "image/webp", true),
}

/** Crop window as fractions (0..1) of the upright image, so it is independent of resolution. */
data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float)

data class ImageOptions(
    val format: OutputFormat,
    /** Longest edge in px, or null to keep the original size. */
    val maxDimension: Int?,
    /** Desired maximum output size in bytes, or null for a fixed high quality. */
    val targetBytes: Long?,
    val crop: CropRect? = null,
)

data class ImageSource(val name: String, val width: Int, val height: Int, val bytes: Long)

class ProcessedImage(
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val format: OutputFormat,
    /** False when a target size was requested but could not be reached. */
    val targetMet: Boolean,
)

/** Pure helpers, kept free of Android types so they can be unit-tested on the JVM. */
object ImageMath {
    const val DEFAULT_QUALITY = 92
    const val MIN_QUALITY = 5
    const val SHRINK_STEP = 0.85f
    const val MAX_SHRINKS = 12
    const val MIN_EDGE = 16

    /** Scales (w, h) down so the longest edge is at most [maxDimension]; never upscales. */
    fun fitWithin(width: Int, height: Int, maxDimension: Int?): Pair<Int, Int> {
        if (maxDimension == null || max(width, height) <= maxDimension) return width to height
        val scale = maxDimension.toFloat() / max(width, height)
        return (width * scale).roundToInt().coerceAtLeast(1) to (height * scale).roundToInt().coerceAtLeast(1)
    }

    /** Pixel size of the [crop] window within a [width] x [height] image. */
    fun croppedSize(width: Int, height: Int, crop: CropRect?): Pair<Int, Int> {
        if (crop == null) return width to height
        return ((crop.right - crop.left) * width).roundToInt().coerceAtLeast(1) to
            ((crop.bottom - crop.top) * height).roundToInt().coerceAtLeast(1)
    }

    /**
     * Scale (<= 1) to decode the whole image at so the cropped region's longest edge is at most
     * [maxDimension]; decoding at this scale avoids holding a full-resolution bitmap.
     */
    fun decodeScale(width: Int, height: Int, crop: CropRect?, maxDimension: Int?): Float {
        val (cw, ch) = croppedSize(width, height, crop)
        val (fw, _) = fitWithin(cw, ch, maxDimension)
        return (fw.toFloat() / cw).coerceAtMost(1f)
    }

    /**
     * Highest quality in [MIN_QUALITY]..100 whose encoded size is <= [targetBytes], assuming
     * size grows with quality. Null when even [MIN_QUALITY] is too large.
     */
    fun searchQuality(targetBytes: Long, encodedSize: (Int) -> Long): Int? {
        if (encodedSize(100) <= targetBytes) return 100
        if (encodedSize(MIN_QUALITY) > targetBytes) return null
        var lo = MIN_QUALITY
        var hi = 100
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (encodedSize(mid) <= targetBytes) lo = mid else hi = mid
        }
        return lo
    }

    fun formatSize(bytes: Long): String = when {
        bytes >= 1_048_576 -> "%.2f MB".format(bytes / 1_048_576.0)
        bytes >= 1024 -> "%.0f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }
}

object ImageProcessor {
    fun readSource(context: Context, uri: Uri): ImageSource {
        var name = "image"
        var size = 0L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        var w = 0
        var h = 0
        // Header-only decode: reports post-rotation dimensions without allocating pixels.
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            w = info.size.width
            h = info.size.height
            decoder.setTargetSize(1, 1)
        }.recycle()
        return ImageSource(name, w, h, size)
    }

    /** Upright, downsampled copy for the crop editor. */
    fun loadPreview(context: Context, uri: Uri, maxEdge: Int = 1600): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val (w, h) = ImageMath.fitWithin(info.size.width, info.size.height, maxEdge)
            decoder.setTargetSize(w, h)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

    /**
     * Decodes (applying EXIF rotation), resizes, and re-encodes. Re-encoding writes only pixel
     * data, so EXIF — GPS location, device model, timestamps — never reaches the output.
     */
    fun process(context: Context, uri: Uri, options: ImageOptions): ProcessedImage {
        val crop = options.crop
        var bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            val scale = ImageMath.decodeScale(info.size.width, info.size.height, crop, options.maxDimension)
            decoder.setTargetSize(
                (info.size.width * scale).roundToInt().coerceAtLeast(1),
                (info.size.height * scale).roundToInt().coerceAtLeast(1),
            )
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        if (crop != null) {
            val x = (crop.left * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
            val y = (crop.top * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
            val w = ((crop.right - crop.left) * bitmap.width).roundToInt().coerceIn(1, bitmap.width - x)
            val h = ((crop.bottom - crop.top) * bitmap.height).roundToInt().coerceIn(1, bitmap.height - y)
            bitmap = Bitmap.createBitmap(bitmap, x, y, w, h)
        }
        if (options.format == OutputFormat.JPEG && bitmap.hasAlpha()) bitmap = bitmap.flattenOnWhite()

        val target = options.targetBytes
        if (target == null) {
            return ProcessedImage(bitmap.encode(options.format, ImageMath.DEFAULT_QUALITY), bitmap.width, bitmap.height, options.format, true)
        }

        var shrinks = 0
        while (true) {
            val quality = if (options.format.lossy) {
                ImageMath.searchQuality(target) { bitmap.encode(options.format, it).size.toLong() }
            } else {
                if (bitmap.encode(options.format, 100).size <= target) 100 else null
            }
            if (quality != null) {
                return ProcessedImage(bitmap.encode(options.format, quality), bitmap.width, bitmap.height, options.format, true)
            }
            val nextW = (bitmap.width * ImageMath.SHRINK_STEP).roundToInt()
            val nextH = (bitmap.height * ImageMath.SHRINK_STEP).roundToInt()
            if (shrinks >= ImageMath.MAX_SHRINKS || nextW < ImageMath.MIN_EDGE || nextH < ImageMath.MIN_EDGE) {
                val q = if (options.format.lossy) ImageMath.MIN_QUALITY else 100
                return ProcessedImage(bitmap.encode(options.format, q), bitmap.width, bitmap.height, options.format, false)
            }
            bitmap = Bitmap.createScaledBitmap(bitmap, nextW, nextH, true)
            shrinks++
        }
    }

    private fun Bitmap.flattenOnWhite(): Bitmap {
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(this@flattenOnWhite, 0f, 0f, null)
        }
        return out
    }

    @Suppress("DEPRECATION")
    private fun Bitmap.encode(format: OutputFormat, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val compressFormat = when (format) {
            OutputFormat.JPEG -> Bitmap.CompressFormat.JPEG
            OutputFormat.PNG -> Bitmap.CompressFormat.PNG
            OutputFormat.WEBP -> Bitmap.CompressFormat.WEBP_LOSSY
        }
        compress(compressFormat, quality, out)
        return out.toByteArray()
    }
}
