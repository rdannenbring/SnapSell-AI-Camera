package com.snapsell.nativecamera.data

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.coroutines.resumeWithException

/**
 * Processes AI filters on-device using ML Kit and software-based effects.
 * - ENHANCE: Software contrast/saturation boost (no ML needed)
 * - WHITE_BG: ML Kit Selfie Segmentation to separate foreground, composite onto white
 */
object LocalFilterProcessor {

    /**
     * Apply a local (on-device) filter to a bitmap.
     * Only ENHANCE and WHITE_BG are supported locally.
     */
    suspend fun applyLocalFilter(bitmap: Bitmap, filter: AiFilter): Bitmap {
        return when (filter) {
            AiFilter.ENHANCE -> applyEnhanceFilter(bitmap)
            AiFilter.WHITE_BG -> applyWhiteBgFilter(bitmap)
            AiFilter.LIFESTYLE -> throw UnsupportedOperationException(
                "Lifestyle filter requires cloud AI. Cannot run on-device."
            )
        }
    }

    /**
     * Software-based enhancement: contrast + saturation boost.
     */
    private fun applyEnhanceFilter(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()

        val cm = ColorMatrix()
        cm.setSaturation(1.8f)
        val contrastMatrix = ColorMatrix(floatArrayOf(
            1.5f, 0f, 0f, 0f, -30f,
            0f, 1.5f, 0f, 0f, -30f,
            0f, 0f, 1.5f, 0f, -30f,
            0f, 0f, 0f, 1.1f, 0f
        ))
        cm.postConcat(contrastMatrix)
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        return result
    }

    /**
     * ML Kit Selfie Segmentation to remove background and composite onto white.
     * Falls back to a software brightness-based approach if ML Kit fails.
     */
    suspend fun applyWhiteBgFilter(bitmap: Bitmap): Bitmap {
        return try {
            applyWhiteBgMlKit(bitmap)
        } catch (e: Exception) {
            android.util.Log.w("LocalFilter", "ML Kit segmentation failed, using software fallback: ${e.message}")
            applyWhiteBgSoftwareFallback(bitmap)
        }
    }

    /**
     * Use ML Kit Selfie Segmentation to extract the subject and place on white background.
     */
    private suspend fun applyWhiteBgMlKit(bitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)

        val options = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .build()
        val segmenter: Segmenter = Segmentation.getClient(options)

        val mask = suspendCancellableCoroutine<ByteBuffer> { cont ->
            segmenter.process(inputImage)
                .addOnSuccessListener { segmentationMask ->
                    cont.resume(segmentationMask.buffer) {}
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }
        }

        // Build foreground-only bitmap using the mask
        val bgPixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(bgPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        mask.rewind()
        val fgPixels = IntArray(bitmap.width * bitmap.height)
        for (i in bgPixels.indices) {
            val confidence = mask.float
            val alpha = (confidence * 255).toInt().coerceIn(0, 255)
            val pixel = bgPixels[i]
            fgPixels[i] = (pixel and 0x00FFFFFF) or (alpha shl 24)
        }

        val foreground = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        foreground.setPixels(fgPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        // Composite onto white background
        val whiteBg = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(whiteBg)
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawBitmap(foreground, 0f, 0f, null)

        whiteBg
    }

    /**
     * Software-based fallback: push light areas to white (high-key effect).
     */
    private fun applyWhiteBgSoftwareFallback(bitmap: Bitmap): Bitmap {
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint()

        val brightMatrix = ColorMatrix(floatArrayOf(
            1.2f, 0.1f, 0.1f, 0f, 60f,
            0.1f, 1.2f, 0.1f, 0f, 60f,
            0.1f, 0.1f, 1.2f, 0f, 60f,
            0f, 0f, 0f, 1f, 0f
        ))
        paint.colorFilter = ColorMatrixColorFilter(brightMatrix)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)

        // Pixel-level: push near-white pixels to pure white
        val pixels = IntArray(result.width * result.height)
        result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = android.graphics.Color.red(p)
            val g = android.graphics.Color.green(p)
            val b = android.graphics.Color.blue(p)
            val brightness = (r + g + b) / 3f
            if (brightness > 140) {
                val whiteBlend = ((brightness - 140f) / 115f).coerceIn(0f, 1f)
                val nr = (r + (255 - r) * whiteBlend * 0.9f).toInt().coerceIn(0, 255)
                val ng = (g + (255 - g) * whiteBlend * 0.9f).toInt().coerceIn(0, 255)
                val nb = (b + (255 - b) * whiteBlend * 0.9f).toInt().coerceIn(0, 255)
                pixels[i] = android.graphics.Color.argb(
                    android.graphics.Color.alpha(p), nr, ng, nb
                )
            }
        }
        result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)

        return result
    }
}