package com.snapsell.nativecamera.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.snapsell.nativecamera.ui.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cloud-based image analysis using the Gemini API.
 * Requires internet connection and a valid API key.
 */
class CloudImageProvider(private val context: Context) : ImageAnalysisProvider {

    override val isLocal: Boolean = false

    override suspend fun analyzeImage(bitmap: Bitmap, prompt: String?): ImageAnalysisResult {
        val name = suggestName(bitmap)
        return ImageAnalysisResult(
            labels = emptyList(),
            description = "Analyzed via cloud AI",
            suggestedName = name,
            confidence = 0.9f,
            usedLocal = false
        )
    }

    override suspend fun suggestName(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val apiKey = AppSettings.getGeminiApiKey(context)
        if (apiKey.isBlank()) return@withContext "unnamed_item"

        val base64Image = compressBitmapForApi(bitmap)
        val model = resolveNamingModel()

        try {
            return@withContext requestName(apiKey, base64Image, model)
        } catch (e: Exception) {
            // Fallback to default model
            val fallbackModel = "gemini-2.0-flash"
            if (model == fallbackModel) return@withContext "unnamed_item"
            try {
                return@withContext requestName(apiKey, base64Image, fallbackModel)
            } catch (_: Exception) {
                return@withContext "unnamed_item"
            }
        }
    }

    private fun resolveNamingModel(): String {
        val useSameModel = AppSettings.getUseSameModel(context)
        return if (useSameModel) {
            AppSettings.getGeminiImageEditModel(context)
        } else {
            AppSettings.getGeminiNamingModel(context)
        }
    }

    private fun requestName(apiKey: String, base64Image: String, model: String): String {
        val normalizedModel = model.removePrefix("models/").trim()
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$normalizedModel:generateContent?key=$apiKey")

        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", "image/jpeg")
                                put("data", base64Image)
                            })
                        })
                        put(JSONObject().apply {
                            put("text", "What is this item? Reply with only a short product name (under 40 characters), no quotes, no extra text.")
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("responseModalities", JSONArray().apply { put("TEXT") })
                put("temperature", 0.4)
                put("maxOutputTokens", 60)
            })
        }

        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        try {
            connection.outputStream.use { os ->
                os.write(jsonBody.toString().toByteArray(Charsets.UTF_8))
            }
            val responseCode = connection.responseCode
            if (responseCode != 200) {
                throw Exception("API error ($responseCode)")
            }
            val responseBody = connection.inputStream.bufferedReader().readText()
            val responseJson = JSONObject(responseBody)
            val parts = responseJson.getJSONArray("candidates")
                .getJSONObject(0).getJSONObject("content")
                .getJSONArray("parts")

            for (i in 0 until parts.length()) {
                val text = parts.optJSONObject(i)?.optString("text")?.trim()
                if (!text.isNullOrBlank()) {
                    return text.removeSurrounding("\"").removeSurrounding("*")
                }
            }
            throw Exception("No text in response")
        } finally {
            connection.disconnect()
        }
    }

    private fun compressBitmapForApi(bitmap: Bitmap, maxDim: Int = 1024): String {
        val scale = minOf(1f, minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height))
        val scaled = if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap
        val baos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }
}