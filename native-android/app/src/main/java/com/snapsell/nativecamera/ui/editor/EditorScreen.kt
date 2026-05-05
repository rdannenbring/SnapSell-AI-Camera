package com.snapsell.nativecamera.ui.editor

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.net.Uri
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.activity.compose.BackHandler
import androidx.core.content.FileProvider
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import com.snapsell.nativecamera.data.AiAnalysisMode
import com.snapsell.nativecamera.data.ImageAnalysisFactory
import com.snapsell.nativecamera.ui.settings.AppSettings
import com.snapsell.nativecamera.ui.settings.AppSettings.getSaveLocation
import com.snapsell.nativecamera.ui.theme.Primary
import com.snapsell.nativecamera.ui.theme.SurfaceDark
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.model.AspectRatio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

private const val NAME_DEBUG_TAG = "SnapSellNameDebug"

// Per-photo edit state
data class CropRect(
    val x: Float = 0f,
    val y: Float = 0f,
    val w: Float = 1f,
    val h: Float = 1f
)

data class PhotoAdjustments(
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val scale: Float = 1f,
    val cropRect: CropRect = CropRect(),
    val appliedFilter: AiFilter? = null
)

enum class AiFilter(val label: String, val prompt: String, val color: Color) {
    ENHANCE("Enhance", "Enhance this product photo for an online store. Improve lighting, clarity, and colors while keeping it natural.", Color(0xFF60A5FA)),
    WHITE_BG("White BG", "Remove the background and replace it with a clean, professional studio white background. Keep the product (clothing/accessory) perfectly intact.", Color(0xFF34D399)),
    LIFESTYLE("Lifestyle", "Place this item in a professional lifestyle setting suitable for an online fashion store. Ensure the lighting matches.", Color(0xFFC084FC))
}

enum class EditorTab(val label: String) {
    AI_FILTERS("AI Filters"),
    EDIT("Edit"),
    ADJUST("Adjust")
}

// Toast state
data class ToastState(
    val message: String,
    val isVisible: Boolean = false,
    val isWarning: Boolean = false
)

private fun playVoiceReadyCue() {
    try {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 85).apply {
            startTone(ToneGenerator.TONE_PROP_BEEP, 180)
            release()
        }
    } catch (_: Exception) {
        // Best-effort cue
    }
}

@Composable
fun EditorScreen(
    photoPaths: List<String>,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onClear: () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Multi-photo state
    var selectedIndex by remember { mutableIntStateOf(0) }
    val adjustments = remember { mutableStateMapOf<Int, PhotoAdjustments>() }
    var applyToAll by remember { mutableStateOf(false) }

    // Tab state
    var activeTab by remember { mutableStateOf(EditorTab.AI_FILTERS) }

    // Confirmation dialog state
    var showClearDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }

    // AI listing generation
    var generatedTitle by remember { mutableStateOf("") }
    var generatedDescription by remember { mutableStateOf("") }
    var generatedPrice by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var hasGenerated by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Item name
    var itemName by rememberSaveable { mutableStateOf("") }
    var isSuggestingName by remember { mutableStateOf(false) }

    // AI filter processing
    var isFilterProcessing by remember { mutableStateOf(false) }
    var isCropProcessing by remember { mutableStateOf(false) }
    var imageVersion by remember { mutableIntStateOf(0) } // cache-bust after filter

    // Custom AI prompt
    var customPrompt by remember { mutableStateOf("") }

    // Voice input launcher
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) {
                customPrompt = text
            }
        }
    }

    // Save state
    var isSaving by remember { mutableStateOf(false) }

    // Toast
    var toastState by remember { mutableStateOf(ToastState("")) }

    // Auto-dismiss toast
    LaunchedEffect(toastState.isVisible) {
        if (toastState.isVisible) {
            val duration = if (toastState.isWarning) 5000L else 3500L
            delay(duration)
            toastState = toastState.copy(isVisible = false)
        }
    }

    // Backup originals so the "Reset" button can restore them. Snapshot each
    // photoPath the first time we see it; further edits replace the source.
    val originalsDir = remember { File(context.cacheDir, "originals").apply { mkdirs() } }
    LaunchedEffect(photoPaths) {
        withContext(Dispatchers.IO) {
            photoPaths.forEach { p ->
                try {
                    val src = File(p)
                    if (!src.exists() || src.length() == 0L) return@forEach
                    val backup = File(originalsDir, "${p.hashCode()}.bak")
                    if (!backup.exists()) {
                        src.inputStream().use { input ->
                            backup.outputStream().use { out -> input.copyTo(out) }
                        }
                    }
                } catch (_: Exception) { /* best-effort */ }
            }
        }
    }

    fun resetCurrentPhoto() {
        val idx = selectedIndex
        val path = photoPaths.getOrNull(idx) ?: return
        scope.launch(Dispatchers.IO) {
            try {
                val backup = File(originalsDir, "${path.hashCode()}.bak")
                if (backup.exists()) {
                    backup.inputStream().use { input ->
                        File(path).outputStream().use { out -> input.copyTo(out) }
                    }
                }
            } catch (_: Exception) { /* best-effort */ }
            withContext(Dispatchers.Main) {
                adjustments[idx] = PhotoAdjustments()
                imageVersion++
                toastState = ToastState("Reset to original.", isVisible = true)
            }
        }
    }

    // Current photo adjustments
    val currentAdjustments = adjustments[selectedIndex] ?: PhotoAdjustments()

    fun updateAdjustments(newAdj: PhotoAdjustments) {
        adjustments[selectedIndex] = newAdj
        if (applyToAll) {
            photoPaths.indices.forEach { i ->
                if (i != selectedIndex) adjustments[i] = newAdj
            }
        }
    }


    // Determine current AI analysis mode
    val analysisMode = remember { ImageAnalysisFactory.getAnalysisMode(context) }
    val isLocalMode = analysisMode == AiAnalysisMode.LOCAL

    // Unified name suggestion that routes through the factory
    suspend fun suggestNameViaFactory(photoPath: String): String {
        return if (isLocalMode) {
            val provider = ImageAnalysisFactory.getProvider(context)
            val bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(photoPath) }
                ?: throw Exception("Cannot read photo")
            provider.suggestName(bitmap)
        } else {
            val apiKey = AppSettings.getGeminiApiKey(context)
            if (apiKey.isBlank()) throw Exception("No Gemini API key set.")
            suggestItemName(apiKey, photoPath, resolveNamingModel(context))
        }
    }

    // Auto-suggest filename when first photo/key become available if setting is enabled
    LaunchedEffect(photoPaths.firstOrNull(), AppSettings.getGeminiApiKey(context)) {
        android.util.Log.d(
            NAME_DEBUG_TAG,
            "Auto-suggest effect fired. photoPresent=${photoPaths.isNotEmpty()}, itemNameBlank=${itemName.isBlank()}, mode=$analysisMode"
        )
        if (itemName.isBlank() && photoPaths.isNotEmpty()) {
            val autoSuggest = AppSettings.getAutoSuggestFilename(context)
            android.util.Log.d(NAME_DEBUG_TAG, "Auto-suggest setting enabled=$autoSuggest")
            if (autoSuggest) {
                isSuggestingName = true
                try {
                    // LOCAL mode doesn't need an API key
                    if (isLocalMode || AppSettings.getGeminiApiKey(context).isNotBlank()) {
                        val name = suggestNameViaFactory(photoPaths.first())
                        android.util.Log.d(NAME_DEBUG_TAG, "Auto-suggest raw name='$name'")
                        val cleaned = sanitizeFilenameBase(name)
                        android.util.Log.d(NAME_DEBUG_TAG, "Auto-suggest cleaned name='$cleaned'")
                        if (cleaned.isNotBlank()) {
                            itemName = cleaned
                            android.util.Log.d(NAME_DEBUG_TAG, "Auto-suggest applied to text field")
                        } else {
                            android.util.Log.w(NAME_DEBUG_TAG, "Auto-suggest cleaned name was blank; not applying")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w(NAME_DEBUG_TAG, "Auto-suggest failed: ${e.message}", e)
                } finally {
                    isSuggestingName = false
                }
            }
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = {
                Text(
                    "Reset to Original",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "This will undo all changes to the current photo and restore the original. Continue?",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetDialog = false
                        resetCurrentPhoto()
                    }
                ) {
                    Text(
                        "Reset",
                        color = MaterialTheme.colorScheme.error,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel", fontFamily = FontFamily.Monospace)
                }
            },
            containerColor = colorScheme.surface,
            titleContentColor = colorScheme.onSurface,
            textContentColor = colorScheme.onSurfaceVariant
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    "Unsaved Photos",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "You have unsaved photos. Are you sure you want to clear everything?",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        onClear()
                    }
                ) {
                    Text(
                        "Clear",
                        color = MaterialTheme.colorScheme.error,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(
                        "Cancel",
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            containerColor = colorScheme.surface,
            titleContentColor = colorScheme.onSurface,
            textContentColor = colorScheme.onSurfaceVariant
        )
    }

    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header bar (always visible)
            android.util.Log.d(NAME_DEBUG_TAG, "Before HeaderBar render: itemName='$itemName'")
            HeaderBar(
                    itemName = itemName,
                    onItemNameChange = {
                        android.util.Log.d(NAME_DEBUG_TAG, "TextField onValueChange -> '$it'")
                        itemName = it
                    },
                    onSuggestName = {
                        if (photoPaths.isNotEmpty()) {
                            scope.launch {
                                isSuggestingName = true
                                try {
                                    // Route through factory (local ML Kit or cloud Gemini)
                                    if (!isLocalMode) {
                                        val apiKey = AppSettings.getGeminiApiKey(context)
                                        if (apiKey.isBlank()) {
                                            errorMessage = "No Gemini API key set."
                                            return@launch
                                        }
                                    }
                                    val name = suggestNameViaFactory(photoPaths.first())
                                    android.util.Log.d(NAME_DEBUG_TAG, "Manual suggest raw name='$name'")
                                    val cleaned = sanitizeFilenameBase(name)
                                    android.util.Log.d(NAME_DEBUG_TAG, "Manual suggest cleaned name='$cleaned'")
                                    if (cleaned.isBlank()) {
                                        throw Exception("Generated filename was empty.")
                                    }
                                    itemName = cleaned
                                    android.util.Log.d(NAME_DEBUG_TAG, "Manual suggest applied to text field")
                                } catch (e: Exception) {
                                    android.util.Log.w(NAME_DEBUG_TAG, "Manual suggest failed: ${e.message}", e)
                                    errorMessage = "Failed to generate name: ${e.message}"
                                } finally {
                                    isSuggestingName = false
                                }
                            }
                        }
                    },
                    isSuggestingName = isSuggestingName,
                    isSaving = isSaving,
                    onResetRequest = { showResetDialog = true },
                    onSave = {
                        scope.launch {
                            isSaving = true
                            try {
                                val savedCount = saveAllPhotos(context, photoPaths, adjustments.toMap(), itemName)
                                toastState = ToastState("Saved $savedCount photo(s).")
                            } catch (e: Exception) {
                                toastState = ToastState("Saved to fallback. Check permissions.", isWarning = true)
                            } finally {
                                isSaving = false
                            }
                            onClear()
                        }
                    },
                onBack = onBack,
                onOpenSettings = onOpenSettings,
                onClearRequest = { showClearDialog = true }
            )

            if (activeTab != EditorTab.EDIT) {
                // Preview area — explicit black background to prevent white flash
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    // Main image
                    val photoFile = File(photoPaths[selectedIndex])
                    val adj = currentAdjustments

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Load bitmap reliably via BitmapFactory instead of Coil
                        val currentPhotoPath = photoPaths[selectedIndex]
                        var editorBitmap by remember { mutableStateOf<Bitmap?>(null) }
                        var loadError by remember { mutableStateOf<String?>(null) }
                        LaunchedEffect(currentPhotoPath, imageVersion) {
                            // Reset state when switching photos
                            editorBitmap = null
                            loadError = null
                            withContext(Dispatchers.IO) {
                                try {
                                    if (!photoFile.exists()) {
                                        loadError = "File not found"
                                        return@withContext
                                    }
                                    if (photoFile.length() == 0L) {
                                        loadError = "File is empty"
                                        return@withContext
                                    }
                                    // Decode with sampling for large images
                                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                                    BitmapFactory.decodeFile(photoFile.absolutePath, opts)
                                    if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                                        loadError = "Invalid image: ${opts.outWidth}x${opts.outHeight}"
                                        return@withContext
                                    }
                                    val maxSize = 2048
                                    val sample = maxOf(opts.outWidth / maxSize, opts.outHeight / maxSize, 1)
                                    val decodeOpts = BitmapFactory.Options().apply {
                                        inSampleSize = sample
                                        inPreferredConfig = Bitmap.Config.ARGB_8888
                                    }
                                    editorBitmap = BitmapFactory.decodeFile(photoFile.absolutePath, decodeOpts)
                                    if (editorBitmap == null) {
                                        loadError = "Decode returned null (${opts.outWidth}x${opts.outHeight}, sample=$sample, size=${photoFile.length()})"
                                    }
                                } catch (e: Exception) {
                                    loadError = "Error: ${e.message}"
                                }
                            }
                        }
                        if (loadError != null) {
                            Text(loadError!!, color = Color.Red, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(16.dp))
                        } else if (editorBitmap == null) {
                            // Loading state
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = Primary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Image(
                                bitmap = editorBitmap!!.asImageBitmap(),
                                contentDescription = "Photo ${selectedIndex + 1}",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        scaleX = adj.scale.coerceIn(0.1f, 2f)
                                        scaleY = adj.scale.coerceIn(0.1f, 2f)
                                    },
                                contentScale = ContentScale.Fit,
                                colorFilter = if (adj.exposure != 0f || adj.contrast != 0f) {
                                    val brightness = adj.exposure / 2f
                                    val contrast = 1f + (adj.contrast / 100f)
                                    androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                                        androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                                            contrast, 0f, 0f, 0f, brightness,
                                            0f, contrast, 0f, 0f, brightness,
                                            0f, 0f, contrast, 0f, brightness,
                                            0f, 0f, 0f, 1f, 0f
                                        ))
                                    )
                                } else null
                            )
                        }

                        // Processing overlay
                        if (isFilterProcessing || isCropProcessing) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(48.dp),
                                        color = Primary,
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    Text(
                                        if (isFilterProcessing) "AI Processing..." else "Applying crop...",
                                        color = Color.White,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // Photo counter
                    if (photoPaths.size > 1) {
                        Text(
                            "${selectedIndex + 1} / ${photoPaths.size}",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Thumbnail strip
                if (photoPaths.isNotEmpty()) {
                    ThumbnailStrip(
                        photoPaths = photoPaths,
                        selectedIndex = selectedIndex,
                        onSelect = { selectedIndex = it },
                        onAddMore = onBack,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

                // Bottom control panel (tabs + content only)
                BottomControlPanel(
                    modifier = if (activeTab == EditorTab.EDIT) Modifier
                        .fillMaxWidth()
                        .weight(1f)
                    else Modifier.fillMaxWidth(),
                    activeTab = activeTab,
                    applyToAll = applyToAll,
                    onApplyToAllChange = { applyToAll = it },
                    onTabChange = { tab -> activeTab = tab },
                    photoPath = photoPaths.getOrNull(selectedIndex) ?: "",
                    onCropApplied = {
                        imageVersion++
                        activeTab = EditorTab.ADJUST
                    },
                    onCropCancel = {
                        activeTab = EditorTab.ADJUST
                    },
                    adjustments = currentAdjustments,
                    onAdjustmentsChange = { newAdj -> updateAdjustments(newAdj) },
                    onApplyFilter = { filter ->
                        scope.launch {
                            isFilterProcessing = true
                            try {
                                val apiKey = AppSettings.getGeminiApiKey(context)
                                if (apiKey.isBlank()) {
                                    errorMessage = "No Gemini API key set."
                                    return@launch
                                }
                                val result = applyAiFilter(
                                    context,
                                    apiKey,
                                    photoPaths[selectedIndex],
                                    filter,
                                    model = AppSettings.getGeminiImageEditModel(context)
                                )
                                // Replace the file with the processed result
                                val outputFile = File(photoPaths[selectedIndex])
                                FileOutputStream(outputFile).use { fos ->
                                    result.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                                }
                                imageVersion++ // bust Coil cache to reload filtered image
                                adjustments[selectedIndex] = currentAdjustments.copy(appliedFilter = filter)
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "AI filter failed"
                            } finally {
                                isFilterProcessing = false
                            }
                        }
                    },
                    isFilterProcessing = isFilterProcessing,
                    customPrompt = customPrompt,
                    onCustomPromptChange = { customPrompt = it },
                    onVoiceInput = {
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe how you want AI to modify this photo")
                        }
                        try {
                            voiceLauncher.launch(intent)
                            // Give an audible cue shortly after launch so users know
                            // when they can start speaking.
                            scope.launch {
                                delay(350)
                                playVoiceReadyCue()
                            }
                        } catch (_: Exception) {
                            // Speech recognition not available
                        }
                    },
                    onApplyCustomFilter = { prompt ->
                        scope.launch {
                            isFilterProcessing = true
                            try {
                                val apiKey = AppSettings.getGeminiApiKey(context)
                                if (apiKey.isBlank()) {
                                    errorMessage = "No Gemini API key set."
                                    return@launch
                                }
                                val result = applyAiFilter(
                                    context,
                                    apiKey,
                                    photoPaths[selectedIndex],
                                    AiFilter.ENHANCE,
                                    prompt,
                                    AppSettings.getGeminiImageEditModel(context)
                                )
                                val outputFile = File(photoPaths[selectedIndex])
                                FileOutputStream(outputFile).use { fos ->
                                    result.compress(Bitmap.CompressFormat.JPEG, 95, fos)
                                }
                                imageVersion++
                            } catch (e: Exception) {
                                errorMessage = e.message ?: "Custom AI filter failed"
                            } finally {
                                isFilterProcessing = false
                            }
                        }
                    }
                )
        }

        // Generated listing overlay
        if (hasGenerated) {
            ListingOverlay(
                title = generatedTitle,
                onTitleChange = { generatedTitle = it },
                price = generatedPrice,
                onPriceChange = { generatedPrice = it },
                description = generatedDescription,
                onDescriptionChange = { generatedDescription = it },
                errorMessage = errorMessage,
                isGenerating = isGenerating,
                onRegenerate = {
                    scope.launch {
                        isGenerating = true
                        errorMessage = null
                        try {
                            val apiKey = AppSettings.getGeminiApiKey(context)
                            if (apiKey.isBlank()) {
                                errorMessage = "No Gemini API key set."
                                return@launch
                            }
                            val result = generateListingDescription(apiKey, photoPaths.first())
                            generatedTitle = result.title
                            generatedDescription = result.description
                            generatedPrice = result.suggestedPrice
                        } catch (e: Exception) {
                            errorMessage = e.message ?: "Failed to generate listing"
                        } finally {
                            isGenerating = false
                        }
                    }
                },
                onDismiss = { hasGenerated = false },
                context = context
            )
        }

        // Error toast
        errorMessage?.let { error ->
            LaunchedEffect(error) {
                delay(5000)
                errorMessage = null
            }
        }

        // Error bar
        errorMessage?.let { error ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
                    .padding(horizontal = 32.dp)
            ) {
                Text(
                    error,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .background(Color(0xFF1A0000), RoundedCornerShape(8.dp))
                        .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                )
            }
        }

        // Toast notification
        AnimatedVisibility(
            visible = toastState.isVisible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
        ) {
            Text(
                toastState.message,
                color = Color.White,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .background(
                        if (toastState.isWarning) Color(0xFF4A3000).copy(alpha = 0.95f)
                        else SurfaceDark.copy(alpha = 0.95f),
                        RoundedCornerShape(50)
                    )
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun HeaderBar(
    itemName: String,
    onItemNameChange: (String) -> Unit,
    onSuggestName: () -> Unit,
    isSuggestingName: Boolean,
    isSaving: Boolean,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onClearRequest: () -> Unit = {},
    onResetRequest: () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    SideEffect {
        android.util.Log.d(NAME_DEBUG_TAG, "HeaderBar received itemName='$itemName'")
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorScheme.surface.copy(alpha = 0.92f))
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Close",
                    tint = colorScheme.onSurface.copy(alpha = 0.85f)
                )
            }

            // Item name input (BasicTextField used to force explicit visible text rendering)
            val nameFieldShape = RoundedCornerShape(50)
            BasicTextField(
                value = itemName,
                onValueChange = onItemNameChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Primary),
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(nameFieldShape)
                            .background(colorScheme.surfaceVariant.copy(alpha = 0.65f), nameFieldShape)
                            .border(
                                width = 1.dp,
                                color = colorScheme.outlineVariant,
                                shape = nameFieldShape
                            )
                            .padding(start = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (itemName.isBlank()) {
                                Text(
                                    "File Name...",
                                    color = colorScheme.onSurfaceVariant,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            innerTextField()
                        }

                        if (isSuggestingName) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF60A5FA)
                            )
                        } else {
                            IconButton(onClick = onSuggestName) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "AI Suggest",
                                    tint = Color(0xFF60A5FA),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            )

        Spacer(Modifier.width(4.dp))

        // Reset button
        IconButton(onClick = onResetRequest) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "Reset to original",
                tint = colorScheme.onSurface.copy(alpha = 0.85f)
            )
        }

        Spacer(Modifier.width(4.dp))

        // Finish/Save button
        Button(
            onClick = onSave,
            enabled = !isSaving,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = Primary,
                contentColor = colorScheme.onPrimary,
                disabledContainerColor = Primary.copy(alpha = 0.5f)
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = colorScheme.onPrimary
                )
                Spacer(Modifier.width(4.dp))
                Text("Saving...", fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Save", fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    }
}

@Composable
private fun ThumbnailStrip(
    photoPaths: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onAddMore: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        photoPaths.forEachIndexed { index, path ->
            val isSelected = index == selectedIndex
            // Thumbnail via BitmapFactory
            var thumbBitmap by remember { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(path) {
                withContext(Dispatchers.IO) {
                    try {
                        val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                        thumbBitmap = BitmapFactory.decodeFile(path, opts)
                    } catch (_: Exception) {}
                }
            }
            if (thumbBitmap != null) {
                Image(
                    bitmap = thumbBitmap!!.asImageBitmap(),
                    contentDescription = "Thumb ${index + 1}",
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (isSelected) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp))
                            else Modifier.border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        )
                        .then(if (!isSelected) Modifier.graphicsLayer { alpha = 0.5f } else Modifier)
                        .clickable { onSelect(index) },
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .then(
                            if (isSelected) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp))
                            else Modifier.border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        )
                        .then(if (!isSelected) Modifier.graphicsLayer { alpha = 0.5f } else Modifier)
                        .clickable { onSelect(index) }
                )
            }
        }
        // Add more photos button
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                .clickable { onAddMore() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add more photos",
                tint = Color.White.copy(alpha = 0.4f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun BottomControlPanel(
    activeTab: EditorTab,
    applyToAll: Boolean,
    onApplyToAllChange: (Boolean) -> Unit,
    onTabChange: (EditorTab) -> Unit,
    photoPath: String,
    onCropApplied: () -> Unit,
    onCropCancel: () -> Unit,
    adjustments: PhotoAdjustments,
    onAdjustmentsChange: (PhotoAdjustments) -> Unit,
    onApplyFilter: (AiFilter) -> Unit,
    isFilterProcessing: Boolean,
    customPrompt: String = "",
    onCustomPromptChange: (String) -> Unit = {},
    onVoiceInput: () -> Unit = {},
    onApplyCustomFilter: (String) -> Unit = {},
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    Column(
        modifier = modifier
            .background(SurfaceDark)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
    ) {
        // Tab content panel
        Column(
            modifier = if (activeTab == EditorTab.EDIT)
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            else Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = (if (activeTab == EditorTab.EDIT) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                    .padding(
                        horizontal = if (activeTab == EditorTab.EDIT) 0.dp else 24.dp,
                        vertical = if (activeTab == EditorTab.EDIT) 0.dp else 20.dp
                    )
            ) {
                when (activeTab) {
                    EditorTab.ADJUST -> AdjustTab(
                        adjustments = adjustments,
                        onAdjustmentsChange = onAdjustmentsChange,
                        applyToAll = applyToAll,
                        onApplyToAllChange = onApplyToAllChange
                    )
                    EditorTab.AI_FILTERS -> AiFiltersTab(
                        onApplyFilter = onApplyFilter,
                        isProcessing = isFilterProcessing,
                        customPrompt = customPrompt,
                        onCustomPromptChange = onCustomPromptChange,
                        onVoiceInput = onVoiceInput,
                        onApplyCustomFilter = onApplyCustomFilter
                    )
                    EditorTab.EDIT -> {
                        if (photoPath.isNotBlank()) {
                            InlineCropPanel(
                                photoPath = photoPath,
                                onApplied = onCropApplied,
                                onCancel = onCropCancel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }

        // Bottom Navigation Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF000000).copy(alpha = 0.92f))
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            EditorTab.entries.forEach { tab ->
                val isActive = activeTab == tab
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isActive) Color.White.copy(alpha = 0.1f) else Color.Transparent)
                        .clickable { onTabChange(tab) }
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                ) {
                    Icon(
                        when (tab) {
                            EditorTab.ADJUST -> Icons.Default.Tune
                            EditorTab.AI_FILTERS -> Icons.Default.AutoAwesome
                            EditorTab.EDIT -> Icons.Default.EditNote
                        },
                        contentDescription = tab.label,
                        tint = if (isActive) Color.White else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        tab.label,
                        color = if (isActive) Color.White else Color.White.copy(alpha = 0.4f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun AdjustTab(
    adjustments: PhotoAdjustments,
    onAdjustmentsChange: (PhotoAdjustments) -> Unit,
    applyToAll: Boolean,
    onApplyToAllChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Exposure slider — design spec style
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "EXPOSURE",
                    color = Color(0xFFABABAB), // on-surface-variant
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.1.sp
                )
                Text(
                    if (adjustments.exposure >= 0) "+${"%.1f".format(adjustments.exposure / 100f * 2)} eV" else "${"%.1f".format(adjustments.exposure / 100f * 2)} eV",
                    color = Primary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(8.dp))
            Slider(
                value = adjustments.exposure,
                onValueChange = { onAdjustmentsChange(adjustments.copy(exposure = it)) },
                valueRange = -100f..100f,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Primary,
                    inactiveTrackColor = Color(0xFF262626)
                )
            )
        }

        // Contrast slider
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "CONTRAST",
                    color = Color(0xFFABABAB),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.1.sp
                )
                Text(
                    if (adjustments.contrast >= 0) "+${adjustments.contrast.toInt()}%" else "${adjustments.contrast.toInt()}%",
                    color = Primary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(8.dp))
            Slider(
                value = adjustments.contrast,
                onValueChange = { onAdjustmentsChange(adjustments.copy(contrast = it)) },
                valueRange = -100f..100f,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Primary,
                    inactiveTrackColor = Color(0xFF262626)
                )
            )
        }
    }
}

@Composable
private fun AiFiltersTab(
    onApplyFilter: (AiFilter) -> Unit,
    isProcessing: Boolean,
    customPrompt: String,
    onCustomPromptChange: (String) -> Unit,
    onVoiceInput: () -> Unit,
    onApplyCustomFilter: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Custom AI instruction text field with mic icon
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = customPrompt,
                onValueChange = onCustomPromptChange,
                placeholder = {
                    Text(
                        "Describe how AI should modify this photo...",
                        color = Color.White.copy(alpha = 0.3f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    cursorColor = Primary,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                ),
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        if (customPrompt.isNotBlank()) {
                            IconButton(
                                onClick = { onCustomPromptChange("") },
                                enabled = !isProcessing
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            IconButton(
                                onClick = { onApplyCustomFilter(customPrompt) },
                                enabled = !isProcessing
                            ) {
                                if (isProcessing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 2.dp,
                                        color = Primary
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = "Apply",
                                        tint = Primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        IconButton(onClick = onVoiceInput) {
                            Icon(
                                Icons.Default.Mic,
                                contentDescription = "Voice input",
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            )
        }

        // Filter cards
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AiFilter.entries.forEach { filter ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .clickable(enabled = !isProcessing) { onApplyFilter(filter) }
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            when (filter) {
                                AiFilter.ENHANCE -> Icons.Default.AutoFixHigh
                                AiFilter.WHITE_BG -> Icons.Default.Layers
                                AiFilter.LIFESTYLE -> Icons.Default.Image
                            },
                            contentDescription = filter.label,
                            tint = filter.color,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            filter.label,
                            color = filter.color,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CropTab(
    photoPath: String,
    cropRect: CropRect,
    onCropRectChange: (CropRect) -> Unit,
    onApplyCrop: (CropRect) -> Unit,
    onCancel: () -> Unit,
    isProcessing: Boolean,
    modifier: Modifier = Modifier
) {
    val imageAspect = remember(photoPath) { getImageAspectRatio(photoPath) }
    var ratioPreset by remember(photoPath) { mutableStateOf("photo") }
    var lockedAspect by remember(photoPath) { mutableStateOf<Float?>(imageAspect) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var dragStartRect by remember { mutableStateOf<CropRect?>(null) }
    var dragTotal by remember { mutableStateOf(Offset.Zero) }

    fun setPreset(preset: String) {
        ratioPreset = preset
        val ratio = when (preset) {
            "photo" -> imageAspect
            "1:1" -> 1f
            "4:3" -> 4f / 3f
            "16:9" -> 16f / 9f
            else -> null
        }
        lockedAspect = ratio
        if (ratio == null) {
            onCropRectChange(CropRect())
            return
        }

        val cRatio = if (containerSize.height > 0) {
            containerSize.width.toFloat() / containerSize.height.toFloat()
        } else {
            imageAspect
        }
        val normTarget = ratio / cRatio
        var w = 0.9f
        var h = w / normTarget
        if (h > 0.9f) {
            h = 0.9f
            w = h * normTarget
        }
        onCropRectChange(
            CropRect(
                x = ((1f - w) / 2f).coerceIn(0f, 1f),
                y = ((1f - h) / 2f).coerceIn(0f, 1f),
                w = w.coerceIn(0.05f, 1f),
                h = h.coerceIn(0.05f, 1f)
            )
        )
    }

    fun dragCrop(handle: String, totalDxPx: Float, totalDyPx: Float) {
        val start = dragStartRect ?: cropRect
        if (containerSize.width <= 0 || containerSize.height <= 0) return

        val dx = totalDxPx / containerSize.width.toFloat()
        val dy = totalDyPx / containerSize.height.toFloat()
        val minSize = 0.1f
        val right = start.x + start.w
        val bottom = start.y + start.h

        var x = start.x
        var y = start.y
        var w = start.w
        var h = start.h

        if (handle == "move") {
            onCropRectChange(
                CropRect(
                    x = (start.x + dx).coerceIn(0f, 1f - start.w),
                    y = (start.y + dy).coerceIn(0f, 1f - start.h),
                    w = start.w,
                    h = start.h
                )
            )
            return
        }

        when (handle) {
            "tl" -> {
                val newW = (start.w - dx).coerceAtLeast(minSize)
                val newH = (start.h - dy).coerceAtLeast(minSize)
                x = right - newW
                y = bottom - newH
                w = newW
                h = newH
            }
            "tr" -> {
                w = (start.w + dx).coerceAtLeast(minSize)
                val newH = (start.h - dy).coerceAtLeast(minSize)
                y = bottom - newH
                h = newH
            }
            "bl" -> {
                val newW = (start.w - dx).coerceAtLeast(minSize)
                x = right - newW
                w = newW
                h = (start.h + dy).coerceAtLeast(minSize)
            }
            "br" -> {
                w = (start.w + dx).coerceAtLeast(minSize)
                h = (start.h + dy).coerceAtLeast(minSize)
            }
            "top" -> {
                val newH = (start.h - dy).coerceAtLeast(minSize)
                y = bottom - newH
                h = newH
            }
            "bottom" -> h = (start.h + dy).coerceAtLeast(minSize)
            "left" -> {
                val newW = (start.w - dx).coerceAtLeast(minSize)
                x = right - newW
                w = newW
            }
            "right" -> w = (start.w + dx).coerceAtLeast(minSize)
        }

        lockedAspect?.let { targetPhysicalRatio ->
            val containerRatio = containerSize.width.toFloat() / containerSize.height.toFloat()
            val normTarget = targetPhysicalRatio / containerRatio
            if (handle == "top" || handle == "bottom") {
                val newW = h * normTarget
                val cx = x + w / 2f
                w = newW.coerceAtMost(1f)
                x = (cx - w / 2f).coerceIn(0f, 1f - w)
            } else if (handle == "left" || handle == "right") {
                val newH = w / normTarget
                val cy = y + h / 2f
                h = newH.coerceAtMost(1f)
                y = (cy - h / 2f).coerceIn(0f, 1f - h)
            } else {
                val currentNorm = w / h
                if (currentNorm > normTarget) {
                    val newW = h * normTarget
                    if (handle == "tl" || handle == "bl") x = (x + w) - newW
                    w = newW
                } else {
                    val newH = w / normTarget
                    if (handle == "tl" || handle == "tr") y = (y + h) - newH
                    h = newH
                }
            }
        }

        val clamped = CropRect(
            x = x.coerceIn(0f, 1f - minSize),
            y = y.coerceIn(0f, 1f - minSize),
            w = w.coerceIn(minSize, 1f),
            h = h.coerceIn(minSize, 1f)
        ).let {
            val maxW = (1f - it.x).coerceAtLeast(minSize)
            val maxH = (1f - it.y).coerceAtLeast(minSize)
            it.copy(w = it.w.coerceIn(minSize, maxW), h = it.h.coerceIn(minSize, maxH))
        }

        onCropRectChange(clamped)
    }

    @Composable
    fun DragRegion(handle: String, modifier: Modifier) {
        Box(
            modifier = modifier.pointerInput(handle, isProcessing, cropRect) {
                detectDragGestures(
                    onDragStart = {
                        if (!isProcessing) {
                            dragStartRect = cropRect
                            dragTotal = Offset.Zero
                        }
                    },
                    onDragEnd = {
                        dragStartRect = null
                        dragTotal = Offset.Zero
                    },
                    onDragCancel = {
                        dragStartRect = null
                        dragTotal = Offset.Zero
                    }
                ) { change, dragAmount ->
                    if (isProcessing) return@detectDragGestures
                    dragTotal += dragAmount
                    dragCrop(handle, dragTotal.x, dragTotal.y)
                }
            }
        )
    }

    LaunchedEffect(photoPath) {
        setPreset("photo")
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(imageAspect)
                    .clip(RoundedCornerShape(12.dp))
                    .onSizeChanged { containerSize = it }
            ) {
            // Crop preview via BitmapFactory
            var cropBitmap by remember { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(photoPath) {
                withContext(Dispatchers.IO) {
                    try {
                        cropBitmap = BitmapFactory.decodeFile(photoPath)
                    } catch (_: Exception) {}
                }
            }
            if (cropBitmap != null) {
                Image(
                    bitmap = cropBitmap!!.asImageBitmap(),
                    contentDescription = "Crop preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }

            // Dimmed outside overlay
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(cropRect.y.coerceIn(0f, 1f))
                        .background(Color.Black.copy(alpha = 0.55f))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .fillMaxHeight((1f - cropRect.y - cropRect.h).coerceIn(0f, 1f))
                        .background(Color.Black.copy(alpha = 0.55f))
                )
                Box(
                    modifier = Modifier
                        .offset(x = 0.dp, y = (210f * cropRect.y).dp)
                        .fillMaxHeight(cropRect.h.coerceIn(0f, 1f))
                        .fillMaxWidth(cropRect.x.coerceIn(0f, 1f))
                        .background(Color.Black.copy(alpha = 0.55f))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = (210f * cropRect.y).dp)
                        .fillMaxHeight(cropRect.h.coerceIn(0f, 1f))
                        .fillMaxWidth((1f - cropRect.x - cropRect.w).coerceIn(0f, 1f))
                        .background(Color.Black.copy(alpha = 0.55f))
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = (containerSize.width * cropRect.x).dp,
                        y = (containerSize.height * cropRect.y).dp
                    )
                    .size(
                        (containerSize.width * cropRect.w).dp,
                        (containerSize.height * cropRect.h).dp
                    )
                    .border(2.dp, Color.White, RoundedCornerShape(4.dp))
            ) {
                // Rule-of-thirds grid
                Box(
                    Modifier
                        .fillMaxSize()
                        .border(0.dp, Color.Transparent)
                ) {
                    Box(Modifier.fillMaxHeight().width(1.dp).offset(x = (containerSize.width * cropRect.w / 3f).dp).background(Color.White.copy(alpha = 0.3f)))
                    Box(Modifier.fillMaxHeight().width(1.dp).offset(x = (containerSize.width * cropRect.w * 2f / 3f).dp).background(Color.White.copy(alpha = 0.3f)))
                    Box(Modifier.fillMaxWidth().height(1.dp).offset(y = (containerSize.height * cropRect.h / 3f).dp).background(Color.White.copy(alpha = 0.3f)))
                    Box(Modifier.fillMaxWidth().height(1.dp).offset(y = (containerSize.height * cropRect.h * 2f / 3f).dp).background(Color.White.copy(alpha = 0.3f)))
                }

                // Move area
                DragRegion("move", Modifier.fillMaxSize().padding(16.dp))

                // Edge drag regions
                DragRegion("top", Modifier.align(Alignment.TopCenter).fillMaxWidth().height(18.dp))
                DragRegion("bottom", Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(18.dp))
                DragRegion("left", Modifier.align(Alignment.CenterStart).fillMaxHeight().width(18.dp))
                DragRegion("right", Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(18.dp))

                // Corner anchors
                val anchorSize = 18.dp
                @Composable
                fun anchor(handle: String, alignment: Alignment) {
                    DragRegion(
                        handle,
                        Modifier
                            .align(alignment)
                            .size(34.dp)
                    )
                    Box(
                        Modifier
                            .align(alignment)
                            .size(anchorSize)
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(2.dp, Primary, CircleShape)
                    )
                }
                anchor("tl", Alignment.TopStart)
                anchor("tr", Alignment.TopEnd)
                anchor("bl", Alignment.BottomStart)
                anchor("br", Alignment.BottomEnd)
            }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("photo", "free", "1:1", "4:3", "16:9").forEach { preset ->
                val active = ratioPreset == preset
                Text(
                    text = if (preset == "photo") "PHOTO" else preset.uppercase(),
                    color = if (active) Primary else Color.White.copy(alpha = 0.5f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (active) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.06f))
                        .clickable(enabled = !isProcessing) { setPreset(preset) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                enabled = !isProcessing,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50)
            ) {
                Text("Cancel", fontFamily = FontFamily.Monospace, fontSize = 11.sp)
            }
            Button(
                onClick = { onApplyCrop(cropRect) },
                enabled = !isProcessing,
                modifier = Modifier.weight(1.2f),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Color(0xFF005A3C))
            ) {
                Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Apply", fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun getImageAspectRatio(photoPath: String): Float {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(photoPath, bounds)
    val w = bounds.outWidth.takeIf { it > 0 } ?: 1
    val h = bounds.outHeight.takeIf { it > 0 } ?: 1
    return w.toFloat() / h.toFloat()
}

@Composable
private fun ListingOverlay(
    title: String,
    onTitleChange: (String) -> Unit,
    price: String,
    onPriceChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    errorMessage: String?,
    isGenerating: Boolean,
    onRegenerate: () -> Unit,
    onDismiss: () -> Unit,
    context: Context
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .background(SurfaceDark, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .clickable(enabled = false) {} // prevent dismiss when clicking inside
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "AI Listing",
                    color = Primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White.copy(alpha = 0.5f))
                }
            }

            Spacer(Modifier.height(12.dp))

            // Title
            Text("TITLE", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = title,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = textFieldColors(),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            )

            Spacer(Modifier.height(12.dp))

            // Price
            Text("SUGGESTED PRICE", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = price,
                onValueChange = onPriceChange,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    cursorColor = Primary,
                    focusedTextColor = Primary,
                    unfocusedTextColor = Primary
                ),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            )

            Spacer(Modifier.height(12.dp))

            // Description
            Text("DESCRIPTION", color = Primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = description,
                onValueChange = onDescriptionChange,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                shape = RoundedCornerShape(8.dp),
                colors = textFieldColors(),
                textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp)
            )

            Spacer(Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Copy
                OutlinedButton(
                    onClick = {
                        val text = buildString {
                            append(title); append("\n\n")
                            if (price.isNotBlank()) append("Price: $price\n\n")
                            append(description)
                        }
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Listing", text))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary)
                ) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }

                // Share
                Button(
                    onClick = {
                        val text = buildString {
                            append(title); append("\n\n")
                            if (price.isNotBlank()) append("Price: $price\n\n")
                            append(description)
                        }
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Share Listing"))
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary, contentColor = Color(0xFF005A3C))
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Share", fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(8.dp))

            // Regenerate
            OutlinedButton(
                onClick = onRegenerate,
                enabled = !isGenerating,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF60A5FA))
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color(0xFF60A5FA))
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (isGenerating) "Generating..." else "Regenerate",
                    fontFamily = FontFamily.Monospace, fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun textFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Primary,
    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
    cursorColor = Primary,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White
)

// ==================== DATA / API HELPERS ====================

private data class ListingResult(
    val title: String,
    val description: String,
    val suggestedPrice: String
)

private fun resolveNamingModel(context: Context): String {
    val useSameModel = AppSettings.getUseSameModel(context)
    return if (useSameModel) {
        AppSettings.getGeminiImageEditModel(context)
    } else {
        AppSettings.getGeminiNamingModel(context)
    }
}

private suspend fun suggestItemName(apiKey: String, photoPath: String, model: String): String =
    withContext(Dispatchers.IO) {
        val base64Image = compressImageForApi(photoPath)

        suspend fun requestName(modelName: String): String {
            val normalizedModel = modelName.removePrefix("models/").trim()
            android.util.Log.d(NAME_DEBUG_TAG, "Requesting filename with model='$normalizedModel'")
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
                    put("responseModalities", JSONArray().apply {
                        put("TEXT")
                    })
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
                val responseBody = if (responseCode == 200) {
                    connection.inputStream.bufferedReader().readText()
                } else {
                    val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: ""
                    android.util.Log.w(NAME_DEBUG_TAG, "Filename API non-200 ($responseCode): $errorBody")
                    throw Exception(friendlyApiError(responseCode, errorBody))
                }
                val responseJson = JSONObject(responseBody)
                android.util.Log.d(NAME_DEBUG_TAG, "Filename API response received, length=${responseBody.length}")
                val candidates = responseJson.optJSONArray("candidates")
                    ?: throw Exception("Gemini returned no candidates.")
                if (candidates.length() == 0) throw Exception("Gemini returned no candidates.")
                android.util.Log.d(NAME_DEBUG_TAG, "Filename API candidates=${candidates.length()}")

                val parts = candidates.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?: throw Exception("Gemini returned no content parts.")

                var extractedText: String? = null
                for (i in 0 until parts.length()) {
                    val part = parts.optJSONObject(i) ?: continue
                    val text = part.optString("text").takeIf { it.isNotBlank() }
                    if (text != null) {
                        extractedText = text
                        break
                    }
                }

                val finalText = extractedText
                    ?.trim()
                    ?.removeSurrounding("\"")
                    ?.removeSurrounding("*")
                    ?.takeIf { it.isNotBlank() }
                    ?: throw Exception("Gemini did not return text for filename.")

                android.util.Log.d(NAME_DEBUG_TAG, "Filename extracted text='$finalText'")

                return finalText
            } finally {
                connection.disconnect()
            }
        }

        try {
            requestName(model)
        } catch (e: Exception) {
            val fallbackModel = "gemini-2.0-flash"
            val normalizedRequested = model.removePrefix("models/").trim()
            if (normalizedRequested == fallbackModel) throw e

            android.util.Log.w(
                "SnapSell",
                "Filename model '$normalizedRequested' failed (${e.message}); retrying with '$fallbackModel'"
            )
            try {
                requestName(fallbackModel)
            } catch (_: Exception) {
                throw e
            }
        }
    }

private fun sanitizeFilenameBase(input: String): String {
    return input
        .trim()
        .replace("\n", " ")
        .replace("\r", " ")
        .replace(Regex("[`\"']"), "")
        .replace(Regex("[^a-zA-Z0-9 _-]"), "")
        .replace(Regex("\\s+"), "_")
        .replace(Regex("_+"), "_")
        .trim('_')
        .take(40)
}

/**
 * Translate Gemini API error responses into a short, user-friendly message
 * suitable for a toast/inline error. Falls back to the raw status code.
 */
private fun friendlyApiError(code: Int, body: String): String {
    // Try to pull the API's own short message out of the JSON payload.
    val apiMessage = try {
        JSONObject(body).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
    } catch (_: Exception) { null }

    return when (code) {
        429 -> "Gemini API rate limit reached. Please try again in a minute."
        401, 403 -> "Gemini API key invalid or unauthorized. Check Settings."
        400 -> "Bad request to Gemini API." + (apiMessage?.let { " ($it)" } ?: "")
        500, 502, 503, 504 -> "Gemini service is temporarily unavailable. Try again shortly."
        else -> apiMessage?.let { "Gemini error: $it" } ?: "Gemini API error ($code)."
    }
}

private suspend fun generateListingDescription(apiKey: String, photoPath: String): ListingResult =

    withContext(Dispatchers.IO) {
        val base64Image = compressImageForApi(photoPath)

        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$apiKey")

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
                            put("text", """You are a professional e-commerce listing creator. Analyze this product photo and create a compelling marketplace listing.

Return your response as valid JSON with exactly these fields:
{
  "title": "A clear, searchable product title (under 80 chars)",
  "description": "A compelling product description with key features, condition notes, and selling points. Use bullet points with • for readability.",
  "suggestedPrice": "A reasonable price estimate with $ symbol, or blank if impossible to estimate"
}

Be specific about the item. If it's not a product, describe what you see as if creating a listing anyway.""")
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 1024)
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
            val responseBody = if (responseCode == 200) {
                connection.inputStream.bufferedReader().readText()
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                throw Exception("API error ($responseCode): $errorBody")
            }

            parseGeminiResponse(responseBody)
        } finally {
            connection.disconnect()
        }
    }

private fun compressImageForApi(imagePath: String, maxDim: Int = 1024): String {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(imagePath, bounds)
    var sampleSize = 1
    while (bounds.outWidth / (sampleSize * 2) >= maxDim && bounds.outHeight / (sampleSize * 2) >= maxDim) {
        sampleSize *= 2
    }
    val loadOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val bitmap = BitmapFactory.decodeFile(imagePath, loadOpts) ?: throw Exception("Cannot read photo")
    val scale = minOf(1f, minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height))
    val scaled = if (scale < 1f) {
        Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            .also { if (it !== bitmap) bitmap.recycle() }
    } else bitmap
    val baos = java.io.ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
    scaled.recycle()
    return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
}

private suspend fun applyAiFilter(
    context: Context,
    apiKey: String,
    photoPath: String,
    filter: AiFilter,
    promptOverride: String? = null,
    model: String = "gemini-2.5-flash-image"
): Bitmap =
    withContext(Dispatchers.IO) {
        val base64Image = compressImageForApi(photoPath)

        // Try Gemini image generation model first, then fall back to software filter
        var usedAiGeneration = false
        try {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey")

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
                                put("text", promptOverride ?: filter.prompt)
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().apply {
                        put("IMAGE")
                        put("TEXT")
                    })
                    put("temperature", 0.4)
                })
            }

            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 30000
            connection.readTimeout = 90000

            try {
                connection.outputStream.use { os ->
                    os.write(jsonBody.toString().toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                if (responseCode != 200) {
                    val errorBody = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                    android.util.Log.w("SnapSell", "AI filter API error ($responseCode): $errorBody")
                    connection.disconnect()
                } else {
                    val responseBody = connection.inputStream.bufferedReader().readText()
                    val responseJson = JSONObject(responseBody)

                    // Check for valid candidates
                    if (responseJson.has("candidates") && responseJson.getJSONArray("candidates").length() > 0) {
                        val parts = responseJson.getJSONArray("candidates")
                            .getJSONObject(0).getJSONObject("content")
                            .getJSONArray("parts")

                        // Look for image data in response — check both "inline_data" and "inlineData" keys
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            val inlineData = if (part.has("inline_data")) {
                                part.getJSONObject("inline_data")
                            } else if (part.has("inlineData")) {
                                part.getJSONObject("inlineData")
                            } else {
                                null
                            }
                            if (inlineData != null && inlineData.has("data")) {
                                val imageData = inlineData.getString("data")
                                val imageBytesResult = Base64.decode(imageData, Base64.DEFAULT)
                                val bitmap = BitmapFactory.decodeByteArray(imageBytesResult, 0, imageBytesResult.size)
                                if (bitmap != null) {
                                    connection.disconnect()
                                    usedAiGeneration = true
                                    return@withContext bitmap
                                }
                            }
                        }
                        android.util.Log.w("SnapSell", "AI filter: no image data in response, using software fallback")
                    }
                    connection.disconnect()
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            android.util.Log.w("SnapSell", "AI filter exception: ${e.message}, using software fallback")
        }

        // Software-based fallback with strong visual effects
        val original = BitmapFactory.decodeFile(photoPath) ?: throw Exception("Cannot read photo")
        return@withContext applySoftwareFilter(original, filter)
    }

private fun applySoftwareFilter(bitmap: Bitmap, filter: AiFilter): Bitmap {
    val result = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint()

    when (filter) {
        AiFilter.ENHANCE -> {
            // Strong contrast + saturation boost
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
        }
        AiFilter.WHITE_BG -> {
            // Push light areas to white (high-key effect)
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
        }
        AiFilter.LIFESTYLE -> {
            // Warm golden tones + vignette
            val cm = ColorMatrix(floatArrayOf(
                1.2f, 0.1f, 0.05f, 0f, 15f,
                0.05f, 1.1f, 0.0f, 0f, 8f,
                0.0f, 0.0f, 0.9f, 0f, -5f,
                0f, 0f, 0f, 1f, 0f
            ))
            paint.colorFilter = ColorMatrixColorFilter(cm)
            canvas.drawBitmap(bitmap, 0f, 0f, paint)

            // Add vignette overlay
            val vignettePaint = Paint().apply {
                shader = android.graphics.RadialGradient(
                    result.width / 2f, result.height / 2f,
                    maxOf(result.width, result.height) * 0.6f,
                    android.graphics.Color.TRANSPARENT,
                    android.graphics.Color.argb(80, 0, 0, 0),
                    android.graphics.Shader.TileMode.CLAMP
                )
            }
            canvas.drawRect(0f, 0f, result.width.toFloat(), result.height.toFloat(), vignettePaint)
        }
    }

    return result
}

private fun parseGeminiResponse(responseBody: String): ListingResult {
    val responseJson = JSONObject(responseBody)
    val candidates = responseJson.getJSONArray("candidates")
    val content = candidates.getJSONObject(0).getJSONObject("content")
    val parts = content.getJSONArray("parts")
    val text = parts.getJSONObject(0).getString("text")

    val jsonStr = extractJson(text)
    val listing = JSONObject(jsonStr)

    return ListingResult(
        title = listing.optString("title", "Untitled"),
        description = listing.optString("description", ""),
        suggestedPrice = listing.optString("suggestedPrice", "")
    )
}

private fun extractJson(text: String): String {
    val jsonBlockRegex = Regex("```(?:json)?\\s*\\n?([\\s\\S]*?)\\n?```")
    val match = jsonBlockRegex.find(text)
    if (match != null) {
        return match.groupValues[1].trim()
    }

    val braceStart = text.indexOf('{')
    val braceEnd = text.lastIndexOf('}')
    if (braceStart >= 0 && braceEnd > braceStart) {
        return text.substring(braceStart, braceEnd + 1)
    }

    return text.trim()
}

/**
 * Determine a writable save folder. Tries the user-selected location first,
 * then falls back to Pictures/SnapSell, then to app-specific storage.
 */
private fun determineSaveFolder(context: Context, saveDir: String): File {
    // Try user-selected directory
    if (saveDir.isNotBlank()) {
        val userDir = File(saveDir)
        if (userDir.isDirectory && userDir.canWrite()) {
            return userDir
        }
        // Try to create it
        try {
            if (userDir.mkdirs() && userDir.canWrite()) {
                return userDir
            }
        } catch (_: Exception) {}
    }

    // Fallback 1: Pictures/SnapSell
    try {
        val picturesDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "SnapSell")
        if (picturesDir.exists() || picturesDir.mkdirs()) {
            if (picturesDir.canWrite()) {
                return picturesDir
            }
        }
    } catch (_: Exception) {}

    // Fallback 2: App-specific Pictures/SnapSell (always writable)
    val appDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "SnapSell")
    appDir.mkdirs()
    return appDir
}

private suspend fun saveAllPhotos(
    context: Context,
    photoPaths: List<String>,
    adjustments: Map<Int, PhotoAdjustments>,
    itemName: String
): Int = withContext(Dispatchers.IO) {
    var savedCount = 0
    val saveDir = getSaveLocation(context)
    val folder = determineSaveFolder(context, saveDir)

    photoPaths.forEachIndexed { index, path ->
        try {
            val sourceFile = File(path)
            if (!sourceFile.exists()) return@forEachIndexed

            val adj = adjustments[index] ?: PhotoAdjustments()

            // Apply adjustments to bitmap
            val original = BitmapFactory.decodeFile(path) ?: return@forEachIndexed
            val processed = applyAdjustmentsToBitmap(original, adj)

            // Generate filename
            val timestamp = System.currentTimeMillis()
            val sanitizedName = itemName.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30)
            val filename = if (sanitizedName.isNotBlank()) {
                "${sanitizedName}_${index + 1}.jpg"
            } else {
                "SnapSell_${timestamp}_${index + 1}.jpg"
            }

            val outputFile = File(folder, filename)
            FileOutputStream(outputFile).use { fos ->
                processed.compress(Bitmap.CompressFormat.JPEG, 95, fos)
            }

            // Also add to MediaStore for gallery visibility
            addImageToMediaStore(context, outputFile, filename)

            savedCount++
        } catch (e: Exception) {
            // Try fallback save
            try {
                val sourceFile = File(path)
                if (sourceFile.exists()) {
                    val fallbackDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "SnapSell")
                    fallbackDir.mkdirs()
                    val fallbackFile = File(fallbackDir, "SnapSell_fallback_${index + 1}.jpg")
                    sourceFile.copyTo(fallbackFile, overwrite = true)
                    savedCount++
                }
            } catch (_: Exception) {}
        }
    }

    savedCount
}

private fun applyAdjustmentsToBitmap(bitmap: Bitmap, adj: PhotoAdjustments): Bitmap {
    if (adj.exposure == 0f && adj.contrast == 0f && adj.scale == 1f) {
        return bitmap
    }

    var result = bitmap

    // Apply brightness and contrast
    if (adj.exposure != 0f || adj.contrast != 0f) {
        val brightness = adj.exposure / 2f // -50 to +50
        val contrast = 1f + (adj.contrast / 100f)
        val cm = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))
        val filtered = Bitmap.createBitmap(result.width, result.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(filtered)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
        canvas.drawBitmap(result, 0f, 0f, paint)
        result = filtered
    }

    // Apply scale
    if (adj.scale != 1f) {
        val matrix = Matrix().apply {
            postScale(adj.scale, adj.scale, result.width / 2f, result.height / 2f)
        }
        result = Bitmap.createBitmap(result, 0, 0, result.width, result.height, matrix, true)
    }

    return result
}

private fun addImageToMediaStore(context: Context, file: File, filename: String) {
    try {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SnapSell")
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        uri?.let {
            context.contentResolver.openOutputStream(it)?.use { os ->
                file.inputStream().use { fis ->
                    fis.copyTo(os)
                }
            }
        }
    } catch (_: Exception) {
        // MediaStore insert is best-effort
    }
}

private fun createUriForCrop(context: Context, file: File): Uri {
    file.parentFile?.mkdirs()
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file
    )
}

private fun applyCropToFile(photoPath: String, cropRect: CropRect) {
    val src = BitmapFactory.decodeFile(photoPath) ?: throw Exception("Cannot read photo")
    val x = (cropRect.x.coerceIn(0f, 1f) * src.width).toInt().coerceIn(0, src.width - 1)
    val y = (cropRect.y.coerceIn(0f, 1f) * src.height).toInt().coerceIn(0, src.height - 1)
    val maxW = src.width - x
    val maxH = src.height - y
    val w = (cropRect.w.coerceIn(0.01f, 1f) * src.width).toInt().coerceIn(1, maxW)
    val h = (cropRect.h.coerceIn(0.01f, 1f) * src.height).toInt().coerceIn(1, maxH)

    val cropped = Bitmap.createBitmap(src, x, y, w, h)
    val outputFile = File(photoPath)
    FileOutputStream(outputFile).use { fos ->
        cropped.compress(Bitmap.CompressFormat.JPEG, 95, fos)
    }
    if (cropped != src) cropped.recycle()
    src.recycle()
}
