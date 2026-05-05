package com.snapsell.nativecamera.data

import android.content.Context
import android.graphics.Bitmap

/**
 * User-selectable AI analysis mode.
 * LOCAL = On-device ML Kit (privacy-first, no internet required)
 * CLOUD = Cloud LLM via Gemini API (maximum detail)
 */
enum class AiAnalysisMode(val displayName: String, val description: String) {
    LOCAL("On-Device (Local)", "Uses your device's hardware. No internet required. Optimized for Pixel 10+."),
    CLOUD("Cloud AI", "Uses cloud AI for maximum detail. Requires internet & API key.")
}

/**
 * Result of an image analysis operation.
 */
data class ImageAnalysisResult(
    val labels: List<String> = emptyList(),
    val description: String = "",
    val suggestedName: String = "",
    val confidence: Float = 0f,
    val usedLocal: Boolean = false
)

/**
 * Interface for image analysis providers (Strategy pattern).
 */
interface ImageAnalysisProvider {
    /** Whether this provider uses on-device (local) processing */
    val isLocal: Boolean

    /**
     * Analyze an image and return labels/description.
     * @param bitmap The image to analyze
     * @param prompt Optional text prompt for cloud providers
     * @return Analysis result
     */
    suspend fun analyzeImage(bitmap: Bitmap, prompt: String? = null): ImageAnalysisResult

    /**
     * Suggest a file/item name from an image.
     * @param bitmap The image to analyze
     * @return A short descriptive name
     */
    suspend fun suggestName(bitmap: Bitmap): String
}

/**
 * Factory that returns the appropriate provider based on user settings.
 * Falls back to CLOUD if LOCAL is selected but hardware doesn't support it.
 */
object ImageAnalysisFactory {

    private const val PREF_AI_ANALYSIS_MODE = "pref_ai_analysis_mode"

    fun getAnalysisMode(context: Context): AiAnalysisMode {
        val raw = context.getSharedPreferences("snapsell_prefs", Context.MODE_PRIVATE)
            .getString(PREF_AI_ANALYSIS_MODE, null)
        if (raw == null) return AiAnalysisMode.CLOUD
        return AiAnalysisMode.entries.find { it.name == raw } ?: AiAnalysisMode.CLOUD
    }

    fun setAnalysisMode(context: Context, mode: AiAnalysisMode) {
        context.getSharedPreferences("snapsell_prefs", Context.MODE_PRIVATE)
            .edit().putString(PREF_AI_ANALYSIS_MODE, mode.name).apply()
    }

    /**
     * Check if on-device AI is available on this hardware.
     * ML Kit Image Labeling works on all devices, but we check for enhanced capabilities.
     */
    fun isLocalAiAvailable(): Boolean {
        // ML Kit Image Labeling runs on all Android devices — always available
        return true
    }

    /**
     * Get the appropriate provider based on user preference.
     * Falls back to CLOUD if LOCAL is unavailable.
     */
    fun getProvider(context: Context): ImageAnalysisProvider {
        val mode = getAnalysisMode(context)
        return when (mode) {
            AiAnalysisMode.LOCAL -> {
                if (isLocalAiAvailable()) {
                    LocalImageProvider(context)
                } else {
                    // Graceful fallback to cloud
                    CloudImageProvider(context)
                }
            }
            AiAnalysisMode.CLOUD -> CloudImageProvider(context)
        }
    }
}