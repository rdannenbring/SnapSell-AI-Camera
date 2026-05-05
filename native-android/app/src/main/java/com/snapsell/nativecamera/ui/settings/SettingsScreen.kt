package com.snapsell.nativecamera.ui.settings

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snapsell.nativecamera.camera.AspectRatioMode
import androidx.activity.compose.BackHandler
import com.snapsell.nativecamera.data.AiAnalysisMode
import com.snapsell.nativecamera.data.ImageAnalysisFactory
import com.snapsell.nativecamera.ui.theme.Primary
import com.snapsell.nativecamera.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
private val PREFS_NAME = "snapsell_prefs"
private val KEY_GEMINI_API_KEY = "gemini_api_key"
private val KEY_API_KEY_OPENAI = "api_key_openai"
private val KEY_API_KEY_ANTHROPIC = "api_key_anthropic"
private val KEY_API_KEY_GOOGLE_VERTEX_AI = "api_key_google_vertex_ai"
private val KEY_API_KEY_MISTRAL = "api_key_mistral"
private val KEY_API_KEY_GROQ = "api_key_groq"
private val KEY_API_KEY_OPENROUTER = "api_key_openrouter"
private val KEY_API_KEY_LITELLM_LOCAL = "api_key_litellm_local"
private val KEY_API_KEY_OPENAI_COMPATIBLE = "api_key_openai_compatible"
private val KEY_DEFAULT_ASPECT_RATIO = "default_aspect_ratio"
private val KEY_IMAGE_QUALITY = "image_quality"
private val KEY_SHOW_PREVIEW = "show_preview_after_capture"
private val KEY_SAVE_LOCATION = "save_location"
private val KEY_AUTO_SUGGEST_FILENAME = "auto_suggest_filename"
private val KEY_GEMINI_IMAGE_EDIT_MODEL = "gemini_image_edit_model"
private val KEY_GEMINI_NAMING_MODEL = "gemini_naming_model"
private val KEY_USE_SAME_MODEL = "gemini_use_same_model"
private val KEY_AI_PROVIDER = "ai_provider"
private val KEY_ALLOW_REMOTE_FOR_COMPLEX_AI = "allow_remote_for_complex_ai"

private const val DEFAULT_IMAGE_EDIT_MODEL = "gemini-2.5-flash-image"
private const val DEFAULT_NAMING_MODEL = "gemini-2.0-flash"

private fun providerFallbackModels(provider: AiProvider): List<String> {
    return when (provider) {
        AiProvider.GOOGLE_VERTEX_AI -> listOf(DEFAULT_IMAGE_EDIT_MODEL, DEFAULT_NAMING_MODEL)
        else -> emptyList()
    }
}

enum class AiProvider(val displayName: String) {
    OPENAI("OpenAI"),
    ANTHROPIC("Anthropic"),
    GOOGLE_VERTEX_AI("Google Vertex AI / Gemini"),
    MISTRAL("Mistral"),
    GROQ("Groq"),
    OPENROUTER("OpenRouter"),
    LITELLM_LOCAL("LiteLLM (Local)"),
    OPENAI_COMPATIBLE("Generic OpenAI-Compatible")
}

private val PROVIDER_DEFAULT_ENDPOINTS = mapOf(
    AiProvider.OPENAI to "https://api.openai.com/v1",
    AiProvider.ANTHROPIC to "https://api.anthropic.com",
    AiProvider.GOOGLE_VERTEX_AI to "https://aiplatform.googleapis.com/v1",
    AiProvider.MISTRAL to "https://api.mistral.ai/v1",
    AiProvider.GROQ to "https://api.groq.com/openai/v1",
    AiProvider.OPENROUTER to "https://openrouter.ai/api/v1",
)

private const val KEY_AI_CUSTOM_ENDPOINT_LITELLM = "ai_custom_endpoint_litellm"
private const val KEY_AI_CUSTOM_ENDPOINT_OPENAI_COMPAT = "ai_custom_endpoint_openai_compat"

object AppSettings {
    /**
     * Returns the user-configured Gemini API key, or the BuildConfig default
     * (injected from .env for debug builds) as fallback.
     */
    fun getGeminiApiKey(context: Context): String {
        return getApiKey(context, AiProvider.GOOGLE_VERTEX_AI)
    }

    /** Returns true if the user has explicitly set an API key in settings. */
    fun hasUserApiKey(context: Context): Boolean {
        return getUserApiKey(context, AiProvider.GOOGLE_VERTEX_AI).isNotBlank()
    }

    fun setGeminiApiKey(context: Context, key: String) {
        setApiKey(context, AiProvider.GOOGLE_VERTEX_AI, key)
    }

    private fun apiKeyPrefKey(provider: AiProvider): String = when (provider) {
        AiProvider.OPENAI -> KEY_API_KEY_OPENAI
        AiProvider.ANTHROPIC -> KEY_API_KEY_ANTHROPIC
        AiProvider.GOOGLE_VERTEX_AI -> KEY_API_KEY_GOOGLE_VERTEX_AI
        AiProvider.MISTRAL -> KEY_API_KEY_MISTRAL
        AiProvider.GROQ -> KEY_API_KEY_GROQ
        AiProvider.OPENROUTER -> KEY_API_KEY_OPENROUTER
        AiProvider.LITELLM_LOCAL -> KEY_API_KEY_LITELLM_LOCAL
        AiProvider.OPENAI_COMPATIBLE -> KEY_API_KEY_OPENAI_COMPATIBLE
    }

    fun getUserApiKey(context: Context, provider: AiProvider): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(apiKeyPrefKey(provider), "")
            ?.trim()
            .orEmpty()
    }

    fun getApiKey(context: Context, provider: AiProvider): String {
        val userKey = getUserApiKey(context, provider)
        if (userKey.isNotBlank()) return userKey

        // Backward compatibility with old Gemini-only key storage
        if (provider == AiProvider.GOOGLE_VERTEX_AI) {
            val legacyGeminiKey = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_GEMINI_API_KEY, "")
                ?.trim()
                .orEmpty()
            if (legacyGeminiKey.isNotBlank()) return legacyGeminiKey
            if (BuildConfig.DEFAULT_GEMINI_API_KEY.isNotBlank()) return BuildConfig.DEFAULT_GEMINI_API_KEY
        }
        return ""
    }

    fun setApiKey(context: Context, provider: AiProvider, key: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(apiKeyPrefKey(provider), key.trim()).apply()
    }

    fun getDefaultAspectRatio(context: Context): AspectRatioMode {
        val saved = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DEFAULT_ASPECT_RATIO, "4:3") ?: "4:3"
        return AspectRatioMode.entries.find { it.label == saved } ?: AspectRatioMode.RATIO_4_3
    }

    fun setDefaultAspectRatio(context: Context, ratio: AspectRatioMode) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_DEFAULT_ASPECT_RATIO, ratio.label).apply()
    }

    fun getImageQuality(context: Context): Int {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_IMAGE_QUALITY, 85)
    }

    fun setImageQuality(context: Context, quality: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_IMAGE_QUALITY, quality.coerceIn(50, 100)).apply()
    }

    fun getShowPreview(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SHOW_PREVIEW, true)
    }

    fun setShowPreview(context: Context, show: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SHOW_PREVIEW, show).apply()
    }

    fun getAutoSuggestFilename(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTO_SUGGEST_FILENAME, false)
    }

    fun setAutoSuggestFilename(context: Context, auto: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTO_SUGGEST_FILENAME, auto).apply()
    }

    fun getGeminiImageEditModel(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_GEMINI_IMAGE_EDIT_MODEL, DEFAULT_IMAGE_EDIT_MODEL) ?: DEFAULT_IMAGE_EDIT_MODEL
    }

    fun setGeminiImageEditModel(context: Context, model: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_GEMINI_IMAGE_EDIT_MODEL, model).apply()
    }

    fun getGeminiNamingModel(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_GEMINI_NAMING_MODEL, DEFAULT_NAMING_MODEL) ?: DEFAULT_NAMING_MODEL
    }

    fun setGeminiNamingModel(context: Context, model: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_GEMINI_NAMING_MODEL, model).apply()
    }

    fun getUseSameModel(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_USE_SAME_MODEL, true)
    }

    fun setUseSameModel(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_USE_SAME_MODEL, enabled).apply()
    }

    fun getSaveLocation(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SAVE_LOCATION, "") ?: ""
    }

    fun getAiProvider(context: Context): AiProvider {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_AI_PROVIDER, AiProvider.GOOGLE_VERTEX_AI.name)
        if (raw == "GEMINI") return AiProvider.GOOGLE_VERTEX_AI
        return AiProvider.entries.find { it.name == raw } ?: AiProvider.GOOGLE_VERTEX_AI
    }

    fun setAiProvider(context: Context, provider: AiProvider) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_AI_PROVIDER, provider.name).apply()
    }

    fun getAiBaseEndpoint(context: Context, provider: AiProvider = getAiProvider(context)): String {
        return when (provider) {
            AiProvider.LITELLM_LOCAL -> context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_AI_CUSTOM_ENDPOINT_LITELLM, "") ?: ""
            AiProvider.OPENAI_COMPATIBLE -> context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_AI_CUSTOM_ENDPOINT_OPENAI_COMPAT, "") ?: ""
            else -> PROVIDER_DEFAULT_ENDPOINTS[provider] ?: ""
        }
    }

    fun setAiBaseEndpoint(context: Context, provider: AiProvider, endpoint: String) {
        when (provider) {
            AiProvider.LITELLM_LOCAL -> context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_AI_CUSTOM_ENDPOINT_LITELLM, endpoint.trim()).apply()
            AiProvider.OPENAI_COMPATIBLE -> context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_AI_CUSTOM_ENDPOINT_OPENAI_COMPAT, endpoint.trim()).apply()
            else -> Unit // fixed providers use hard-coded defaults
        }
    }

    fun setSaveLocation(context: Context, path: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_SAVE_LOCATION, path).apply()
    }

    /** Allow remote AI for complex filters (e.g. Lifestyle) when in local mode */
    fun getAllowRemoteForComplexAi(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_ALLOW_REMOTE_FOR_COMPLEX_AI, false)
    }

    fun setAllowRemoteForComplexAi(context: Context, allow: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ALLOW_REMOTE_FOR_COMPLEX_AI, allow).apply()
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    var providerApiKey by remember { mutableStateOf(AppSettings.getUserApiKey(context, AppSettings.getAiProvider(context))) }
    var showApiKey by remember { mutableStateOf(false) }
    var defaultAspectRatio by remember { mutableStateOf(AppSettings.getDefaultAspectRatio(context)) }
    var imageQuality by remember { mutableStateOf(AppSettings.getImageQuality(context)) }
    var showPreview by remember { mutableStateOf(AppSettings.getShowPreview(context)) }
    var autoSuggestFilename by remember { mutableStateOf(AppSettings.getAutoSuggestFilename(context)) }
    var aiProvider by remember { mutableStateOf(AppSettings.getAiProvider(context)) }
    var aiProviderEndpoint by remember { mutableStateOf(AppSettings.getAiBaseEndpoint(context, aiProvider)) }
    var imageEditModel by remember { mutableStateOf(AppSettings.getGeminiImageEditModel(context)) }
    var namingModel by remember { mutableStateOf(AppSettings.getGeminiNamingModel(context)) }
    var useSameModel by remember { mutableStateOf(AppSettings.getUseSameModel(context)) }
    var availableModels by remember { mutableStateOf(listOf(DEFAULT_IMAGE_EDIT_MODEL, DEFAULT_NAMING_MODEL).distinct()) }
    var isLoadingModels by remember { mutableStateOf(false) }
    var modelStatus by remember { mutableStateOf<String?>(null) }

    fun refreshModels(apiKeyOverride: String? = null) {
        val effectiveKey = (apiKeyOverride ?: providerApiKey).trim()
        if (effectiveKey.isBlank()) {
            availableModels = providerFallbackModels(aiProvider)
            modelStatus = "Enter API key to load models"
            return
        }
        scope.launch {
            isLoadingModels = true
            modelStatus = null
            try {
                val models = fetchModelsForProvider(
                    provider = aiProvider,
                    apiKey = effectiveKey,
                    baseEndpoint = aiProviderEndpoint
                )
                val fallbackModels = providerFallbackModels(aiProvider)
                availableModels = (models + fallbackModels).distinct()
                if (availableModels.isEmpty()) {
                    modelStatus = "No models returned for ${aiProvider.displayName}"
                    return@launch
                }
                if (!availableModels.contains(imageEditModel)) {
                    imageEditModel = availableModels.first()
                    AppSettings.setGeminiImageEditModel(context, imageEditModel)
                }
                if (!availableModels.contains(namingModel)) {
                    namingModel = imageEditModel
                    AppSettings.setGeminiNamingModel(context, namingModel)
                }
                if (useSameModel && namingModel != imageEditModel) {
                    namingModel = imageEditModel
                    AppSettings.setGeminiNamingModel(context, namingModel)
                }
                modelStatus = "${availableModels.size} model(s) available"
            } catch (e: Exception) {
                modelStatus = "Model refresh failed: ${e.message}"
            } finally {
                isLoadingModels = false
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshModels(providerApiKey)
    }

    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 48.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = colorScheme.onBackground
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "Settings",
                    color = colorScheme.onBackground,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            var aiAnalysisMode by remember { mutableStateOf(ImageAnalysisFactory.getAnalysisMode(context)) }

            // AI Analysis Mode section
            SettingsSection(title = "AI Configuration") {
                Text(
                    "AI Analysis Mode",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Choose how photos are analyzed for naming and descriptions.",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(12.dp))

                AiAnalysisMode.entries.forEach { mode ->
                    val selected = aiAnalysisMode == mode
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clickable {
                                if (mode == AiAnalysisMode.LOCAL && !ImageAnalysisFactory.isLocalAiAvailable()) {
                                    // Hardware doesn't support local — fallback with notice
                                    aiAnalysisMode = AiAnalysisMode.CLOUD
                                    ImageAnalysisFactory.setAnalysisMode(context, AiAnalysisMode.CLOUD)
                                    modelStatus = "On-device AI not available on this device. Using Cloud AI."
                                } else {
                                    aiAnalysisMode = mode
                                    ImageAnalysisFactory.setAnalysisMode(context, mode)
                                }
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected) Primary.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.03f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (selected) Primary else Color.White.copy(alpha = 0.1f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = {
                                    if (mode == AiAnalysisMode.LOCAL && !ImageAnalysisFactory.isLocalAiAvailable()) {
                                        aiAnalysisMode = AiAnalysisMode.CLOUD
                                        ImageAnalysisFactory.setAnalysisMode(context, AiAnalysisMode.CLOUD)
                                        modelStatus = "On-device AI not available on this device. Using Cloud AI."
                                    } else {
                                        aiAnalysisMode = mode
                                        ImageAnalysisFactory.setAnalysisMode(context, mode)
                                    }
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = Primary)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    mode.displayName,
                                    color = if (selected) Primary else Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    mode.description,
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }

                if (aiAnalysisMode == AiAnalysisMode.LOCAL) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "⚡ On-device: No API key needed. Labels are generated locally via ML Kit.",
                        color = Color(0xFF4ADE80).copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(Modifier.height(12.dp))

                    // Allow remote AI for complex filters toggle
                    var allowRemoteForComplex by remember { mutableStateOf(AppSettings.getAllowRemoteForComplexAi(context)) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Allow Cloud AI for Complex Filters",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "Enable to use cloud AI for filters that can’t run on-device (e.g. Lifestyle). Requires a valid API key.",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Switch(
                            checked = allowRemoteForComplex,
                            onCheckedChange = { enabled ->
                                allowRemoteForComplex = enabled
                                AppSettings.setAllowRemoteForComplexAi(context, enabled)
                            },
                            colors = SwitchDefaults.colors(checkedTrackColor = Primary)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Auto-Generate Filename",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Automatically suggest an item name from the photo",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Switch(
                        checked = autoSuggestFilename,
                        onCheckedChange = { enabled ->
                            autoSuggestFilename = enabled
                            AppSettings.setAutoSuggestFilename(context, enabled)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Primary)
                    )
                }
            }

            // Gemini API Key section
            SettingsSection(title = "Remote AI Configuration") {
                Text("AI Provider", color = colorScheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(6.dp))
                AiProviderDropdown(
                    selected = aiProvider,
                    onSelected = { selected ->
                        aiProvider = selected
                        AppSettings.setAiProvider(context, selected)
                        aiProviderEndpoint = AppSettings.getAiBaseEndpoint(context, selected)
                        providerApiKey = AppSettings.getUserApiKey(context, selected)
                        // Clear old provider models immediately, then auto-query new provider if key exists.
                        availableModels = emptyList()
                        modelStatus = null
                        if (providerApiKey.isNotBlank()) {
                            refreshModels(providerApiKey)
                        } else {
                            modelStatus = "Enter API key to load models"
                        }
                    }
                )

                Spacer(Modifier.height(8.dp))
                Text("Base Endpoint", color = colorScheme.onSurfaceVariant, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(6.dp))

                val isCustomEndpointProvider = aiProvider == AiProvider.LITELLM_LOCAL || aiProvider == AiProvider.OPENAI_COMPATIBLE
                if (isCustomEndpointProvider) {
                    OutlinedTextField(
                        value = aiProviderEndpoint,
                        onValueChange = { newValue ->
                            aiProviderEndpoint = newValue
                            AppSettings.setAiBaseEndpoint(context, aiProvider, newValue)
                        },
                        label = { Text("Custom Base Endpoint", fontFamily = FontFamily.Monospace) },
                        placeholder = {
                            Text(
                                "https://your-host/api/v1",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = colorScheme.outlineVariant,
                            focusedLabelColor = Primary,
                            unfocusedLabelColor = colorScheme.onSurfaceVariant,
                            cursorColor = Primary,
                            focusedTextColor = colorScheme.onSurface,
                            unfocusedTextColor = colorScheme.onSurface
                        ),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    )
                } else {
                    Text(
                        aiProviderEndpoint,
                        color = colorScheme.onSurface,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                            .border(1.dp, colorScheme.outlineVariant.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 10.dp)
                    )
                }

                Spacer(Modifier.height(6.dp))
                Text(
                    providerNotes(aiProvider),
                    color = colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = providerApiKey,
                    onValueChange = { newKey ->
                        providerApiKey = newKey
                        AppSettings.setApiKey(context, aiProvider, newKey)
                        refreshModels(newKey)
                    },
                    label = { Text(apiKeyLabel(aiProvider), fontFamily = FontFamily.Monospace) },
                    placeholder = { Text(apiKeyPlaceholder(aiProvider), fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (providerApiKey.isNotBlank()) {
                                IconButton(onClick = {
                                    providerApiKey = ""
                                    AppSettings.setApiKey(context, aiProvider, "")
                                    refreshModels("")
                                }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear key",
                                        tint = Color.White.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            TextButton(onClick = { showApiKey = !showApiKey }) {
                                Text(if (showApiKey) "Hide" else "Show", color = Primary, fontSize = 11.sp)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = colorScheme.outlineVariant,
                        focusedLabelColor = Primary,
                        unfocusedLabelColor = colorScheme.onSurfaceVariant,
                        cursorColor = Primary,
                        focusedTextColor = colorScheme.onSurface,
                        unfocusedTextColor = colorScheme.onSurface
                    ),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 14.sp)
                )

                if (providerApiKey.isBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        providerApiHelp(aiProvider),
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else if (aiProvider == AiProvider.GOOGLE_VERTEX_AI && !AppSettings.hasUserApiKey(context) && BuildConfig.DEFAULT_GEMINI_API_KEY.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Using built-in dev key. Set your own key for production use.",
                        color = Color(0xFF60A5FA).copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }


                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Provider Models", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    IconButton(onClick = { refreshModels() }, enabled = !isLoadingModels) {
                        if (isLoadingModels) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Primary)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh models", tint = Primary)
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Use Same Model", color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.height(2.dp))
                        Text("Use one model for image edits and filename naming", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    Switch(
                        checked = useSameModel,
                        onCheckedChange = { enabled ->
                            useSameModel = enabled
                            AppSettings.setUseSameModel(context, enabled)
                            if (enabled) {
                                namingModel = imageEditModel
                                AppSettings.setGeminiNamingModel(context, namingModel)
                            }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Primary)
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text("Image Edit Model", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Spacer(Modifier.height(6.dp))
                ModelDropdown(
                    models = availableModels,
                    selected = imageEditModel,
                    onSelected = { selected ->
                        imageEditModel = selected
                        AppSettings.setGeminiImageEditModel(context, selected)
                        if (useSameModel) {
                            namingModel = selected
                            AppSettings.setGeminiNamingModel(context, selected)
                        }
                    }
                )

                if (!useSameModel) {
                    Spacer(Modifier.height(10.dp))
                    Text("Filename Model", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(6.dp))
                    ModelDropdown(
                        models = availableModels,
                        selected = namingModel,
                        onSelected = { selected ->
                            namingModel = selected
                            AppSettings.setGeminiNamingModel(context, selected)
                        }
                    )
                }

                modelStatus?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Color.White.copy(alpha = 0.45f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }
            }

            // Camera section
            SettingsSection(title = "Camera") {
                // Default Aspect Ratio
                Text(
                    "Default Aspect Ratio",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AspectRatioMode.entries.forEach { ratio ->
                        val selected = defaultAspectRatio == ratio
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (selected) Primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                                    RoundedCornerShape(8.dp)
                                )
                                .border(
                                    1.dp,
                                    if (selected) Primary else Color.White.copy(alpha = 0.1f),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    defaultAspectRatio = ratio
                                    AppSettings.setDefaultAspectRatio(context, ratio)
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                ratio.label,
                                color = if (selected) Primary else Color.White.copy(alpha = 0.5f),
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Image Quality
                Text(
                    "Image Quality: $imageQuality%",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    "Est. file size (${defaultAspectRatio.label}): ~${estimatedFileSize(imageQuality, defaultAspectRatio)}",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(8.dp))
                // Quality preset buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("Low" to 60, "Med" to 80, "High" to 90, "Max" to 100).forEach { (label, q) ->
                        val selected = imageQuality == q
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (selected) Primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f),
                                    RoundedCornerShape(6.dp)
                                )
                                .border(
                                    1.dp,
                                    if (selected) Primary else Color.White.copy(alpha = 0.1f),
                                    RoundedCornerShape(6.dp)
                                )
                                .clickable {
                                    imageQuality = q
                                    AppSettings.setImageQuality(context, q)
                                }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                color = if (selected) Primary else Color.White.copy(alpha = 0.5f),
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("50", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Slider(
                        value = imageQuality.toFloat(),
                        onValueChange = { quality ->
                            imageQuality = quality.toInt()
                            AppSettings.setImageQuality(context, imageQuality)
                        },
                        valueRange = 50f..100f,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(thumbColor = Primary, activeTrackColor = Primary)
                    )
                    Text("100", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                }

                Spacer(Modifier.height(20.dp))

                // Show Preview After Capture
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Show Preview After Capture",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "Review each photo before saving",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Switch(
                        checked = showPreview,
                        onCheckedChange = { show ->
                            showPreview = show
                            AppSettings.setShowPreview(context, show)
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Primary)
                    )
                }
            }

            // Storage section
            SettingsSection(title = "Storage") {
                var saveLocation by remember { mutableStateOf(AppSettings.getSaveLocation(context)) }
                var isOpening by remember { mutableStateOf(false) }

                val folderPicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocumentTree()
                ) { uri: Uri? ->
                    isOpening = false
                    uri?.let {
                        // Take persistent URI permission
                        context.contentResolver.takePersistableUriPermission(
                            it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                        // Convert tree URI to file path for the native save
                        val filePath = resolveUriToPath(context, it)
                        if (filePath != null) {
                            saveLocation = filePath
                            AppSettings.setSaveLocation(context, filePath)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            isOpening = true
                            folderPicker.launch(Uri.parse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toURI().toString()))
                        }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Folder,
                            contentDescription = "Save Location",
                            tint = Primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "Save Location",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            if (saveLocation.isNotBlank()) {
                                Text(
                                    saveLocation,
                                    color = Color.White.copy(alpha = 0.3f),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Text(
                                    "Pictures/SnapSell (default)",
                                    color = Color.White.copy(alpha = 0.3f),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isOpening) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Primary
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Opening...", color = Primary, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        } else {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "Browse",
                                tint = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // About section
            SettingsSection(title = "About") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Version", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                    Text("1.0.0-native", color = Color.White.copy(alpha = 0.3f), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "SnapSell Native Camera — Built with CameraX + Jetpack Compose",
                    color = Color.White.copy(alpha = 0.3f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun providerNotes(provider: AiProvider): String {
    return when (provider) {
        AiProvider.OPENAI -> "Standard OpenAI API base URL."
        AiProvider.ANTHROPIC -> "Primary Claude Messages endpoint is POST /v1/messages (base host shown)."
        AiProvider.GOOGLE_VERTEX_AI -> "Vertex uses resource-style endpoints: /projects/{project}/locations/{location}/publishers/google/models/{model}:generateContent"
        AiProvider.MISTRAL -> "OpenAI-style integrations commonly target this base."
        AiProvider.GROQ -> "Groq OpenAI-compatible base path uses /openai/v1."
        AiProvider.OPENROUTER -> "OpenAI-compatible gateway endpoint."
        AiProvider.LITELLM_LOCAL -> "User-hosted endpoint: provide your LiteLLM base URL and API key in your deployment."
        AiProvider.OPENAI_COMPATIBLE -> "Bring-your-own endpoint (cloud or self-hosted); API key may be optional depending on server."
    }
}

private fun apiKeyLabel(provider: AiProvider): String = when (provider) {
    AiProvider.OPENAI -> "OpenAI API Key"
    AiProvider.ANTHROPIC -> "Anthropic API Key"
    AiProvider.GOOGLE_VERTEX_AI -> "Google API Key"
    AiProvider.MISTRAL -> "Mistral API Key"
    AiProvider.GROQ -> "Groq API Key"
    AiProvider.OPENROUTER -> "OpenRouter API Key"
    AiProvider.LITELLM_LOCAL -> "LiteLLM API Key"
    AiProvider.OPENAI_COMPATIBLE -> "API Key (optional)"
}

private fun apiKeyPlaceholder(provider: AiProvider): String = when (provider) {
    AiProvider.OPENAI -> "Enter your OpenAI key..."
    AiProvider.ANTHROPIC -> "Enter your Anthropic key..."
    AiProvider.GOOGLE_VERTEX_AI -> "Enter your Google/Vertex key..."
    AiProvider.MISTRAL -> "Enter your Mistral key..."
    AiProvider.GROQ -> "Enter your Groq key..."
    AiProvider.OPENROUTER -> "Enter your OpenRouter key..."
    AiProvider.LITELLM_LOCAL -> "Enter your LiteLLM key (if required)..."
    AiProvider.OPENAI_COMPATIBLE -> "Enter API key if your endpoint requires it..."
}

private fun providerApiHelp(provider: AiProvider): String = when (provider) {
    AiProvider.GOOGLE_VERTEX_AI -> "Required for Google Vertex/Gemini calls."
    AiProvider.OPENAI -> "Required for OpenAI model queries and API calls."
    AiProvider.ANTHROPIC -> "Required for Anthropic model queries and API calls."
    AiProvider.MISTRAL -> "Required for Mistral model queries and API calls."
    AiProvider.GROQ -> "Required for Groq model queries and API calls."
    AiProvider.OPENROUTER -> "Required for OpenRouter model queries and API calls."
    AiProvider.LITELLM_LOCAL -> "Depends on your LiteLLM deployment (often required)."
    AiProvider.OPENAI_COMPATIBLE -> "Optional/required depending on your endpoint."
}

@Composable
private fun AiProviderDropdown(
    selected: AiProvider,
    onSelected: (AiProvider) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Text(
                selected.displayName,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            AiProvider.entries.forEach { provider ->
                DropdownMenuItem(
                    text = {
                        Text(
                            provider.displayName,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    onClick = {
                        onSelected(provider)
                        expanded = false
                    }
                )
            }
        }
    }
}

private suspend fun fetchModelsForProvider(
    provider: AiProvider,
    apiKey: String,
    baseEndpoint: String,
): List<String> = withContext(Dispatchers.IO) {
    val endpoint = when (provider) {
        AiProvider.OPENAI,
        AiProvider.GROQ,
        AiProvider.OPENROUTER,
        AiProvider.OPENAI_COMPATIBLE,
        AiProvider.LITELLM_LOCAL,
        AiProvider.MISTRAL -> "${baseEndpoint.trimEnd('/')}/models"
        AiProvider.ANTHROPIC -> "${baseEndpoint.trimEnd('/')}/v1/models"
        AiProvider.GOOGLE_VERTEX_AI -> "https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey"
    }

    val url = URL(endpoint)
    val connection = url.openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = 15000
    connection.readTimeout = 20000

    when (provider) {
        AiProvider.OPENAI,
        AiProvider.GROQ,
        AiProvider.OPENROUTER,
        AiProvider.OPENAI_COMPATIBLE,
        AiProvider.LITELLM_LOCAL,
        AiProvider.MISTRAL -> {
            if (apiKey.isNotBlank()) connection.setRequestProperty("Authorization", "Bearer $apiKey")
            if (provider == AiProvider.OPENROUTER) {
                connection.setRequestProperty("HTTP-Referer", "https://snapsell.local")
                connection.setRequestProperty("X-Title", "SnapSell")
            }
        }
        AiProvider.ANTHROPIC -> {
            connection.setRequestProperty("x-api-key", apiKey)
            connection.setRequestProperty("anthropic-version", "2023-06-01")
        }
        AiProvider.GOOGLE_VERTEX_AI -> Unit
    }

    try {
        val responseCode = connection.responseCode
        if (responseCode != 200) {
            val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: ""
            throw Exception("$responseCode ${errorBody.take(120)}")
        }

        val responseBody = connection.inputStream.bufferedReader().readText()
        val json = JSONObject(responseBody)
        val models = json.optJSONArray("models") ?: return@withContext emptyList<String>()

        val result = mutableListOf<String>()
        for (i in 0 until models.length()) {
            val model = models.optJSONObject(i) ?: continue
            val rawName = when {
                model.has("id") -> model.optString("id")
                model.has("name") -> model.optString("name")
                else -> ""
            }

            // Google endpoint includes lots of non-generate models; keep its existing filtering.
            if (provider == AiProvider.GOOGLE_VERTEX_AI) {
                val methods = model.optJSONArray("supportedGenerationMethods")
                val supportsGenerateContent = (0 until (methods?.length() ?: 0)).any { idx ->
                    methods?.optString(idx) == "generateContent"
                }
                if (!supportsGenerateContent) continue
            }

            val cleanName = rawName.removePrefix("models/")
            if (cleanName.isNotBlank()) result.add(cleanName)
        }

        result.distinct().sorted()
    } finally {
        connection.disconnect()
    }
}

@Composable
private fun ModelDropdown(
    models: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) {
            Text(
                selected,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.White.copy(alpha = 0.7f))
        }

        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Text(
                            model,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    onClick = {
                        onSelected(model)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun resolveUriToPath(context: Context, treeUri: Uri): String? {
    val docId = DocumentsContract.getTreeDocumentId(treeUri)
    if (docId.startsWith("primary:")) {
        val relativePath = docId.substringAfter("primary:")
        return "${Environment.getExternalStorageDirectory()}/$relativePath"
    }
    // For external storage, try to resolve
    val split = docId.split(":")
    if (split.size >= 2) {
        return "${Environment.getExternalStorageDirectory()}/${split[1]}"
    }
    return null
}

private fun estimatedFileSize(quality: Int, ratio: AspectRatioMode = AspectRatioMode.RATIO_4_3): String {
    // Sensor is natively 4:3 (~4032x3024 = 12.2M pixels).
    // 16:9 crops top/bottom → fewer pixels (but wider frame). 1:1 crops sides → fewer pixels.
    // 4:3 has the most pixels = largest file. 16:9 and 1:1 are smaller because fewer pixels.
    val multiplier = when (ratio) {
        AspectRatioMode.RATIO_4_3 -> 1.0
        AspectRatioMode.RATIO_1_1 -> 0.75   // square crop = 75% of pixels
        AspectRatioMode.RATIO_16_9 -> 0.75  // wide crop = 75% of pixels
    }
    val baseKB = when {
        quality >= 95 -> 4500
        quality >= 90 -> 3500
        quality >= 85 -> 2800
        quality >= 80 -> 2200
        quality >= 75 -> 1800
        quality >= 70 -> 1500
        quality >= 60 -> 1200
        else -> 900
    }
    val estimatedKB = (baseKB * multiplier).toInt()
    return if (estimatedKB >= 1000) {
        String.format("%.1f MB", estimatedKB / 1000.0)
    } else {
        "$estimatedKB KB"
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surface
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            colorScheme.outlineVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                title.uppercase(),
                color = Primary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = colorScheme.outlineVariant, thickness = 1.dp)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}