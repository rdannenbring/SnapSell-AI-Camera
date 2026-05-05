package com.snapsell.nativecamera.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

enum class AspectRatioMode(val label: String) {
    RATIO_1_1("1:1"),
    RATIO_4_3("4:3"),
    RATIO_16_9("16:9")
}

data class CaptureResult(
    val file: File,
    val width: Int,
    val height: Int
)

class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    val previewView: PreviewView
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var currentZoom: Float = 1f
    private var aspectRatio: AspectRatioMode = AspectRatioMode.RATIO_4_3

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    val isFrontCamera: Boolean
        get() = lensFacing == CameraSelector.LENS_FACING_FRONT

    suspend fun startCamera() {
        val provider = suspendCancellableCoroutine { cont ->
            ProcessCameraProvider.getInstance(context).also { future ->
                cont.resume(future.get()) { future.cancel(true) }
            }
        }
        cameraProvider = provider
        rebuildUseCases()
    }

    fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        rebuildUseCases()
    }

    fun setZoom(scale: Float) {
        currentZoom = scale.coerceIn(0.5f, 5f)
        camera?.cameraControl?.setLinearZoom(linearZoomFromScale(currentZoom))
    }

    fun setAspectRatio(ratio: AspectRatioMode) {
        aspectRatio = ratio
        // Rebuild use cases with the correct aspect ratio and rebind
        rebuildUseCases()
    }

    private fun rebuildUseCases() {
        val provider = cameraProvider ?: return

        // Preview at sensor-native aspect ratio (usually 4:3) — no ViewPort, no target ratio.
        // The Compose layout constrains the PreviewView to the target aspect ratio,
        // and FILL_CENTER center-crops identically to our software crop below.
        preview = Preview.Builder()
            .build()
            .also { it.setSurfaceProvider(previewView.surfaceProvider) }

        // Capture at max quality — we crop in software to guarantee an exact match with the preview
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setJpegQuality(95)
            .build()

        provider.unbindAll()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            camera = provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview!!, imageCapture!!)
            camera?.cameraControl?.setLinearZoom(linearZoomFromScale(currentZoom))
        } catch (e: Exception) {
            // Handle error
        }
    }

    /**
     * Fast capture — saves raw JPEG from sensor with no post-processing.
     * Use [processPhoto] later to apply rotation, mirror, and crop.
     */
    suspend fun captureRaw(outputDir: File): CaptureResult {
        val capture = imageCapture ?: throw IllegalStateException("Camera not initialized")

        val outputFile = File(outputDir, "capture_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()

        suspendCoroutine { cont ->
            capture.takePicture(
                outputOptions,
                cameraExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        cont.resume(Unit)
                    }
                    override fun onError(exc: ImageCaptureException) {
                        cont.resumeWith(Result.failure(exc))
                    }
                }
            )
        }

        // Quick size read from BitmapFactory.Options (does not decode pixels)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(outputFile.absolutePath, options)

        return CaptureResult(
            file = outputFile,
            width = options.outWidth,
            height = options.outHeight
        )
    }

    /**
     * Post-process a raw capture: apply EXIF rotation, front-camera mirror, and aspect-ratio crop.
     * Overwrites the file in place.
     */
    fun processPhoto(file: File, isFrontCamera: Boolean, ratio: AspectRatioMode) {
        // Decode with EXIF rotation applied
        val decodedBitmap = decodeWithRotation(file.absolutePath)

        // Mirror if front camera
        val mirroredBitmap = if (isFrontCamera) {
            mirrorBitmap(decodedBitmap)
        } else {
            decodedBitmap
        }

        // Software crop to exact target aspect ratio
        val finalBitmap = cropToAspectRatio(mirroredBitmap, ratio)

        // Write back to file
        val outputStream = ByteArrayOutputStream()
        finalBitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
        file.writeBytes(outputStream.toByteArray())

        // Recycle bitmaps to free memory
        if (decodedBitmap !== mirroredBitmap) decodedBitmap.recycle()
        if (mirroredBitmap !== finalBitmap) mirroredBitmap.recycle()
        finalBitmap.recycle()
    }

    /** Convenience: returns current state for metadata tracking */
    fun currentLensFacing(): Int = lensFacing
    fun currentAspectRatio(): AspectRatioMode = aspectRatio

    private fun cropToAspectRatio(bitmap: Bitmap, ratio: AspectRatioMode): Bitmap {
        val srcW = bitmap.width
        val srcH = bitmap.height

        // Determine target ratio (accounting for portrait orientation)
        val targetRatio = when (ratio) {
            AspectRatioMode.RATIO_1_1 -> 1f
            AspectRatioMode.RATIO_4_3 -> 3f / 4f  // portrait
            AspectRatioMode.RATIO_16_9 -> 9f / 16f // portrait
        }

        val srcRatio = srcW.toFloat() / srcH.toFloat()
        val cropW: Int
        val cropH: Int

        if (srcRatio > targetRatio) {
            cropH = srcH
            cropW = (srcH * targetRatio).toInt()
        } else {
            cropW = srcW
            cropH = (srcW / targetRatio).toInt()
        }

        // Center crop only — CameraX already applies zoom via setLinearZoom()
        val x = (srcW - cropW) / 2
        val y = (srcH - cropH) / 2

        return Bitmap.createBitmap(bitmap, x.coerceAtLeast(0), y.coerceAtLeast(0),
            cropW.coerceAtMost(srcW), cropH.coerceAtMost(srcH))
    }

    /**
     * Decode a JPEG file and apply EXIF rotation so the bitmap is upright.
     */
    private fun decodeWithRotation(path: String): Bitmap {
        val bitmap = BitmapFactory.decodeFile(path) ?: throw IllegalStateException("Failed to decode captured image")

        try {
            val exif = ExifInterface(path)
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )

            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            if (degrees != 0f) {
                val matrix = Matrix().apply { postRotate(degrees) }
                return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
        } catch (e: Exception) {
            // If EXIF reading fails, just return the bitmap as-is
        }

        return bitmap
    }

    private fun mirrorBitmap(bitmap: Bitmap): Bitmap {
        val matrix = Matrix().apply { postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * Convert a scale factor (0.5x-5x) to a linear zoom value (0f-1f).
     * Linear zoom is what CameraX uses — it distributes zoom steps evenly.
     */
    private fun linearZoomFromScale(scale: Float): Float {
        val minZoom = 0f  // 0.5x
        val maxZoom = 1f  // 5x
        val normalized = (scale - 0.5f) / (5f - 0.5f)
        return normalized.coerceIn(0f, 1f)
    }

    fun shutdown() {
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
    }
}