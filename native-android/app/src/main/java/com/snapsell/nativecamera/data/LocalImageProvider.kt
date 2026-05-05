package com.snapsell.nativecamera.data

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * On-device image analysis using ML Kit Image Labeling.
 * No internet required. Runs entirely on the device.
 */
class LocalImageProvider(private val context: Context) : ImageAnalysisProvider {

    override val isLocal: Boolean = true

    private val labeler by lazy {
        ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.5f)
                .build()
        )
    }

    override suspend fun analyzeImage(bitmap: Bitmap, prompt: String?): ImageAnalysisResult {
        val labels = labelImage(bitmap)
        val topLabels = labels.map { it.text }
        val description = buildDescription(labels)
        val suggestedName = buildNameFromLabels(topLabels)

        return ImageAnalysisResult(
            labels = topLabels,
            description = description,
            suggestedName = suggestedName,
            confidence = labels.firstOrNull()?.confidence ?: 0f,
            usedLocal = true
        )
    }

    override suspend fun suggestName(bitmap: Bitmap): String {
        val labels = labelImage(bitmap)
        return buildNameFromLabels(labels.map { it.text })
    }

    private suspend fun labelImage(bitmap: Bitmap): List<com.google.mlkit.vision.label.ImageLabel> =
        suspendCancellableCoroutine { continuation ->
            val image = InputImage.fromBitmap(bitmap, 0)
            labeler.process(image)
                .addOnSuccessListener { labels ->
                    continuation.resume(labels)
                }
                .addOnFailureListener { e ->
                    continuation.resumeWithException(e)
                }
        }

    private fun buildDescription(labels: List<com.google.mlkit.vision.label.ImageLabel>): String {
        if (labels.isEmpty()) return "No objects detected."
        val topLabels = labels.take(5)
        return buildString {
            append("Detected: ")
            topLabels.forEachIndexed { index, label ->
                if (index > 0) append(", ")
                append("${label.text} (${(label.confidence * 100).toInt()}%)")
            }
        }
    }

    private fun buildNameFromLabels(labels: List<String>): String {
        if (labels.isEmpty()) return "unnamed_item"
        // Take the top 1-2 labels and create a clean filename
        val nameParts = labels.take(2).map { label ->
            label.replace(Regex("[^a-zA-Z0-9 ]"), "").trim().lowercase()
                .replace(Regex("\\s+"), "_")
        }
        return nameParts.joinToString("_").take(40)
    }
}