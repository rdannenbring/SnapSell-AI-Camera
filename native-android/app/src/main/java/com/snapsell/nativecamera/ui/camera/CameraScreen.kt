package com.snapsell.nativecamera.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import coil.compose.AsyncImage
import com.snapsell.nativecamera.camera.AspectRatioMode
import com.snapsell.nativecamera.camera.CameraManager
import com.snapsell.nativecamera.camera.CaptureResult
import com.snapsell.nativecamera.ui.settings.AppSettings
import com.snapsell.nativecamera.ui.theme.Primary
import com.snapsell.nativecamera.ui.theme.PrimaryContainer
import com.snapsell.nativecamera.ui.theme.OnPrimary
import com.snapsell.nativecamera.ui.theme.SurfaceHigh
import com.snapsell.nativecamera.ui.theme.SurfaceLowest
import com.snapsell.nativecamera.ui.theme.OnSurfaceVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File

private fun triggerHaptic(context: Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    vibrator?.let {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            it.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            it.vibrate(30)
        }
    }
}

@Composable
fun CameraScreen(
    retainedPhotoPaths: List<String> = emptyList(),
    onOpenSettings: () -> Unit,
    onOpenEditor: (List<String>) -> Unit,
    onClear: () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var cameraManager by remember { mutableStateOf<CameraManager?>(null) }
    var isCameraReady by remember { mutableStateOf(false) }
    var aspectRatio by remember { mutableStateOf(AppSettings.getDefaultAspectRatio(context)) }
    var zoom by remember { mutableStateOf(1f) }
    var isProcessing by remember { mutableStateOf(false) }
    var previewResult by remember { mutableStateOf<CaptureResult?>(null) }
    var captureFlash by remember { mutableStateOf(false) }
    var frozenFrame by remember { mutableStateOf<Bitmap?>(null) }
    val capturedPaths = remember { mutableStateListOf<String>() }
    // Restore retained photos when returning from editor
    LaunchedEffect(Unit) {
        if (capturedPaths.isEmpty() && retainedPhotoPaths.isNotEmpty()) {
            capturedPaths.addAll(retainedPhotoPaths)
        }
    }
    val photosCount = capturedPaths.size

    // Controls toggle: tap the viewfinder to show/hide controls (all modes)
    var showControls by remember { mutableStateOf(true) }
    var showClearDialog by remember { mutableStateOf(false) }
    var isProcessingPhotos by remember { mutableStateOf(false) }

    // Track capture metadata per photo for deferred processing
    data class CaptureMeta(val isFront: Boolean, val ratio: AspectRatioMode)
    val captureMetaMap = remember { mutableStateMapOf<String, CaptureMeta>() }

    // Back handler: dismiss preview if showing
    BackHandler(enabled = previewResult != null) {
        previewResult = null
    }

    if (!hasCameraPermission) {
        Box(
            modifier = Modifier.fillMaxSize().background(colorScheme.background),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera access required", color = colorScheme.onBackground, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Please grant camera permission to use SnapSell", color = colorScheme.onBackground.copy(alpha = 0.7f), fontSize = 13.sp)
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("Grant Permission", color = colorScheme.onPrimary)
                }
            }
        }
        return
    }

    // Camera preview aspect ratio (width/height in portrait)
    val previewRatio = when (aspectRatio) {
        AspectRatioMode.RATIO_1_1 -> 1f / 1f
        AspectRatioMode.RATIO_4_3 -> 3f / 4f
        AspectRatioMode.RATIO_16_9 -> 9f / 16f
    }
    val isFullBleed = aspectRatio == AspectRatioMode.RATIO_16_9

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .background(colorScheme.background)
    ) {
        // ── Camera preview — full-screen for ALL modes ──
        if (previewResult != null) {
            // Load preview bitmap reliably ( Coil can fail silently on large raw JPEGs)
            var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(previewResult!!.file) {
                withContext(Dispatchers.IO) {
                    try {
                        // Load efficiently — sample down if very large
                        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(previewResult!!.file.absolutePath, opts)
                        val maxSize = 2048
                        val sampleSize = maxOf(
                            opts.outWidth / maxSize,
                            opts.outHeight / maxSize,
                            1
                        )
                        val decodeOpts = BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                            inPreferredConfig = Bitmap.Config.ARGB_8888
                        }
                        previewBitmap = BitmapFactory.decodeFile(
                            previewResult!!.file.absolutePath, decodeOpts
                        )
                    } catch (_: Exception) {}
                }
            }
            if (previewBitmap != null) {
                Image(
                    bitmap = previewBitmap!!.asImageBitmap(),
                    contentDescription = "Captured photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            } else {
                // Fallback: show dark background while loading
                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        val manager = CameraManager(ctx, lifecycleOwner, this)
                        cameraManager = manager
                        scope.launch {
                            try {
                                manager.startCamera()
                                manager.setAspectRatio(aspectRatio)
                                isCameraReady = true
                            } catch (_: Exception) {}
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, zoomChange, _ ->
                            val newZoom = (zoom * zoomChange).coerceIn(0.5f, 5f)
                            zoom = newZoom
                            cameraManager?.setZoom(newZoom)
                        }
                    }
            )
        }

        // ── Frame overlay mask — darkens areas outside capture zone (live camera only) ──
        if (previewResult == null) {
            FrameGuideOverlay(aspectRatio = aspectRatio, modifier = Modifier.fillMaxSize())
        }

        // Tap to toggle controls + pinch to zoom — only over the actual viewfinder image
        // (the capture-frame rectangle), not on letterboxed/dimmed areas.
        ViewfinderTapArea(
            aspectRatio = aspectRatio,
            onTap = { showControls = !showControls },
            onPinch = { zoomChange ->
                val newZoom = (zoom * zoomChange).coerceIn(0.5f, 5f)
                zoom = newZoom
                cameraManager?.setZoom(newZoom)
            },
            modifier = Modifier.fillMaxSize()
        )

        // ── Floating top bar — overlays the preview for all modes ──
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopBar(
                onOpenSettings = onOpenSettings,
                onSwitchCamera = { cameraManager?.switchCamera() },
                showCameraControls = previewResult == null,
                aspectRatio = aspectRatio,
                onAspectRatioChange = {
                    aspectRatio = it
                    cameraManager?.setAspectRatio(it)
                }
            )
        }

        // Frozen viewfinder frame during capture
        if (frozenFrame != null) {
            Image(
                bitmap = frozenFrame!!.asImageBitmap(),
                contentDescription = "Frozen frame",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // Capture flash animation (both modes)
        AnimatedVisibility(
            visible = captureFlash,
            enter = fadeIn(),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.9f)))
        }

        // Zoom controls — float at bottom, hidden when controls toggled off, not during preview
        AnimatedVisibility(
            visible = previewResult == null && showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 140.dp)
        ) {
            ZoomControls(
                zoom = zoom,
                onZoomChange = { newZoom ->
                    zoom = newZoom
                    cameraManager?.setZoom(newZoom)
                }
            )
        }

        // Bottom action bar (all modes)
        BottomActionBar(
            isCameraReady = isCameraReady,
            isProcessing = isProcessing,
            photosCount = photosCount,
            previewResult = previewResult,
            showControls = showControls,
            modifier = Modifier.align(Alignment.BottomCenter),
            onCapture = {
                triggerHaptic(context)
                // Freeze the viewfinder immediately
                try {
                    cameraManager?.previewView?.bitmap?.let { frozenFrame = it }
                } catch (_: Exception) {}
                scope.launch {
                    isProcessing = true
                    captureFlash = true
                    try {
                        val outputDir = File(context.cacheDir, "captures").apply { mkdirs() }
                        val result = cameraManager?.captureRaw(outputDir)
                        if (result != null) {
                            // Store metadata for deferred processing
                            captureMetaMap[result.file.absolutePath] = CaptureMeta(
                                cameraManager?.isFrontCamera == true,
                                cameraManager?.currentAspectRatio() ?: AspectRatioMode.RATIO_4_3
                            )
                            if (!AppSettings.getShowPreview(context)) {
                                capturedPaths.add(result.file.absolutePath)
                                captureFlash = false
                                frozenFrame = null
                            } else {
                                previewResult = result
                                captureFlash = false
                                frozenFrame = null
                            }
                        } else {
                            captureFlash = false
                            frozenFrame = null
                        }
                    } catch (e: Exception) {
                        captureFlash = false
                        frozenFrame = null
                    } finally {
                        isProcessing = false
                    }
                }
            },
            onKeep = {
                previewResult?.let { result ->
                    capturedPaths.add(result.file.absolutePath)
                    previewResult = null
                }
            },
            onRetake = {
                previewResult?.let { it.file.delete() }
                previewResult = null
            },
            onDelete = {
                previewResult?.let { it.file.delete() }
                previewResult = null
            },
            onClear = {
                if (photosCount > 0) {
                    showClearDialog = true
                }
            },
            onProcess = {
                val allPaths = capturedPaths.toList()
                val pendingPath = previewResult?.file?.absolutePath
                val pathsToSend = if (pendingPath != null) {
                    allPaths + pendingPath
                } else if (allPaths.isNotEmpty()) {
                    allPaths
                } else {
                    emptyList()
                }
                if (pathsToSend.isNotEmpty()) {
                    // Process all raw captures in background, then navigate
                    scope.launch(Dispatchers.Default) {
                        isProcessingPhotos = true
                        pathsToSend.forEach { path ->
                            val meta = captureMetaMap[path]
                            if (meta != null) {
                                try {
                                    cameraManager?.processPhoto(
                                        File(path),
                                        meta.isFront,
                                        meta.ratio
                                    )
                                } catch (_: Exception) {}
                            }
                        }
                        captureMetaMap.clear()
                        withContext(Dispatchers.Main) {
                            isProcessingPhotos = false
                            onOpenEditor(pathsToSend)
                        }
                    }
                }
            }
        )
    }

    // Processing overlay — shown while post-processing photos before editor
    if (isProcessingPhotos) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable(enabled = false) {},
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CircularProgressIndicator(
                    color = Primary,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )
                Text(
                    "Processing...",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // Clear confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    "Clear Photos",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    "You have ${capturedPaths.size} photo(s). Are you sure you want to clear everything?",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        capturedPaths.clear()
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

    DisposableEffect(Unit) {
        onDispose {
            cameraManager?.shutdown()
        }
    }
}

/**
 * Accurate frame overlay mask for 1:1 and 4:3 modes.
 *
 * Calculates the exact intersection of:
 * - What the PreviewView shows on screen (FILL_CENTER center-crops the sensor to fill the view)
 * - What the software crop captures (center-crops the sensor to the target ratio)
 *
 * The mask dims areas that are visible but won't be captured.
 */
/**
 * Tap-handling area limited to the actual viewfinder image (the capture-frame rectangle).
 * Tapping outside this rectangle (in dimmed/letterbox areas) does nothing,
 * so the user can interact with controls or empty space without toggling controls.
 */
@Composable
private fun ViewfinderTapArea(
    aspectRatio: AspectRatioMode,
    onTap: () -> Unit,
    onPinch: (zoomChange: Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val canvasW: Float = with(density) { maxWidth.toPx() }
        val canvasH: Float = with(density) { maxHeight.toPx() }
        val screenRatio = canvasW / canvasH

        // Sensor (portrait): width/height = 3/4
        val sensorRatio = 3f / 4f

        val targetRatio = when (aspectRatio) {
            AspectRatioMode.RATIO_1_1 -> 1f / 1f
            AspectRatioMode.RATIO_4_3 -> 3f / 4f
            AspectRatioMode.RATIO_16_9 -> 9f / 16f
        }

        // Visible area in normalized sensor coordinates
        val normVisW: Float; val normVisH: Float
        val normVisX: Float; val normVisY: Float
        if (sensorRatio > screenRatio) {
            normVisW = screenRatio / sensorRatio
            normVisH = 1f
            normVisX = (1f - normVisW) / 2f
            normVisY = 0f
        } else {
            normVisW = 1f
            normVisH = sensorRatio / screenRatio
            normVisX = 0f
            normVisY = (1f - normVisH) / 2f
        }

        // Capture area in normalized sensor coordinates
        val normCapW: Float; val normCapH: Float
        val normCapX: Float; val normCapY: Float
        if (targetRatio > sensorRatio) {
            normCapW = 1f
            normCapH = sensorRatio / targetRatio
            normCapX = 0f
            normCapY = (1f - normCapH) / 2f
        } else {
            normCapH = 1f
            normCapW = targetRatio / sensorRatio
            normCapX = (1f - normCapW) / 2f
            normCapY = 0f
        }

        // Intersection
        val interX = maxOf(normVisX, normCapX)
        val interY = maxOf(normVisY, normCapY)
        val interRight = minOf(normVisX + normVisW, normCapX + normCapW)
        val interBottom = minOf(normVisY + normVisH, normCapY + normCapH)

        // Map to screen coordinates
        val rectXPx = (interX - normVisX) / normVisW * canvasW
        val rectYPx = (interY - normVisY) / normVisH * canvasH
        val rectWPx = (interRight - interX) / normVisW * canvasW
        val rectHPx = (interBottom - interY) / normVisH * canvasH

        val rectXDp = with(density) { rectXPx.toDp() }
        val rectYDp = with(density) { rectYPx.toDp() }
        val rectWDp = with(density) { rectWPx.coerceAtLeast(0f).toDp() }
        val rectHDp = with(density) { rectHPx.coerceAtLeast(0f).toDp() }

        Box(
            modifier = Modifier
                .offset(x = rectXDp, y = rectYDp)
                .size(width = rectWDp, height = rectHDp)
                .pointerInput(Unit) {
                    detectTapGestures { onTap() }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoomChange, _ ->
                        if (zoomChange != 1f) onPinch(zoomChange)
                    }
                }
        )
    }
}

@Composable
private fun FrameGuideOverlay(
    aspectRatio: AspectRatioMode,
    modifier: Modifier = Modifier
) {
    // Most Android camera sensors are 4:3 in portrait
    val sensorRatio = 3f / 4f  // width/height in portrait orientation

    val targetRatio = when (aspectRatio) {
        AspectRatioMode.RATIO_1_1 -> 1f / 1f
        AspectRatioMode.RATIO_4_3 -> 3f / 4f
        AspectRatioMode.RATIO_16_9 -> 9f / 16f
    }

    // If target ratio matches sensor ratio, everything visible is captured — no mask needed
    if (kotlin.math.abs(targetRatio - sensorRatio) < 0.01f) return

    Canvas(modifier = modifier) {
        val canvasW = size.width
        val canvasH = size.height
        val screenRatio = canvasW / canvasH
        val dimColor = Color.Black.copy(alpha = 0.5f)

        // Step 1: Visible area in normalized sensor coordinates
        // PreviewView FILL_CENTER center-crops the sensor to match screen ratio
        val normVisW: Float
        val normVisH: Float
        val normVisX: Float
        val normVisY: Float

        if (sensorRatio > screenRatio) {
            // Sensor wider than screen → fills height, crops sides
            normVisW = screenRatio / sensorRatio
            normVisH = 1f
            normVisX = (1f - normVisW) / 2f
            normVisY = 0f
        } else {
            // Sensor taller than screen → fills width, crops top/bottom
            normVisW = 1f
            normVisH = sensorRatio / screenRatio
            normVisX = 0f
            normVisY = (1f - normVisH) / 2f
        }

        // Step 2: Capture area in normalized sensor coordinates
        // Software center-crops the sensor to the target ratio
        val normCapW: Float
        val normCapH: Float
        val normCapX: Float
        val normCapY: Float

        if (targetRatio > sensorRatio) {
            // Target wider than sensor → crop height
            normCapW = 1f
            normCapH = sensorRatio / targetRatio
            normCapX = 0f
            normCapY = (1f - normCapH) / 2f
        } else {
            // Target narrower than sensor → crop width
            normCapH = 1f
            normCapW = targetRatio / sensorRatio
            normCapX = (1f - normCapW) / 2f
            normCapY = 0f
        }

        // Step 3: Intersection of visible and capture areas
        val interX = maxOf(normVisX, normCapX)
        val interY = maxOf(normVisY, normCapY)
        val interRight = minOf(normVisX + normVisW, normCapX + normCapW)
        val interBottom = minOf(normVisY + normVisH, normCapY + normCapH)

        // Step 4: Map intersection to screen coordinates
        // Visible area (normVisX..normVisX+normVisW, normVisY..normVisY+normVisH) → (0..canvasW, 0..canvasH)
        val maskX = (interX - normVisX) / normVisW * canvasW
        val maskY = (interY - normVisY) / normVisH * canvasH
        val maskW = (interRight - interX) / normVisW * canvasW
        val maskH = (interBottom - interY) / normVisH * canvasH

        // Step 5: Draw dimming overlays outside the mask
        // Top bar
        if (maskY > 0f) {
            drawRect(dimColor, topLeft = Offset(0f, 0f),
                size = androidx.compose.ui.geometry.Size(canvasW, maskY))
        }
        // Bottom bar
        val maskBottom = maskY + maskH
        if (maskBottom < canvasH) {
            drawRect(dimColor, topLeft = Offset(0f, maskBottom),
                size = androidx.compose.ui.geometry.Size(canvasW, canvasH - maskBottom))
        }
        // Left bar
        if (maskX > 0f) {
            drawRect(dimColor, topLeft = Offset(0f, maskY),
                size = androidx.compose.ui.geometry.Size(maskX, maskH))
        }
        // Right bar
        val maskRight = maskX + maskW
        if (maskRight < canvasW) {
            drawRect(dimColor, topLeft = Offset(maskRight, maskY),
                size = androidx.compose.ui.geometry.Size(canvasW - maskRight, maskH))
        }
    }
}

@Composable
private fun TopBar(
    onOpenSettings: () -> Unit,
    onSwitchCamera: () -> Unit,
    showCameraControls: Boolean,
    aspectRatio: AspectRatioMode,
    onAspectRatioChange: (AspectRatioMode) -> Unit,
    isImmersive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Black.copy(alpha = 0.3f), Color.Transparent)
                )
            )
    ) {

        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 8.dp)
        ) {
            Icon(
                Icons.Default.Settings,
                contentDescription = "Settings",
                tint = colorScheme.onBackground.copy(alpha = 0.9f)
            )
        }

        if (showCameraControls) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(50))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                AspectRatioMode.entries.forEach { ratio ->
                    TextButton(
                        onClick = { onAspectRatioChange(ratio) },
                        modifier = Modifier
                            .background(
                                if (aspectRatio == ratio) Color.White.copy(alpha = 0.15f)
                                else Color.Transparent,
                                CircleShape
                            )
                            .padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = ratio.label,
                            color = if (aspectRatio == ratio) Primary else colorScheme.onBackground.copy(alpha = 0.75f),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = if (aspectRatio == ratio) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }

            IconButton(
                onClick = onSwitchCamera,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Switch camera",
                    tint = colorScheme.onBackground.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Composable
private fun ZoomControls(
    zoom: Float,
    onZoomChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("0.5x", color = colorScheme.onBackground.copy(alpha = 0.7f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            Text(
                String.format("%.1fx", zoom),
                color = colorScheme.onBackground,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Text("5x", color = colorScheme.onBackground.copy(alpha = 0.7f), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
        }

        Slider(
            value = zoom,
            onValueChange = onZoomChange,
            valueRange = 0.5f..5f,
            steps = 44,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Primary,
                activeTrackColor = Primary
            )
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            listOf(0.5f, 1f, 2f, 5f).forEach { preset ->
                TextButton(
                    onClick = { onZoomChange(preset) },
                    modifier = Modifier
                        .size(40.dp)
                        .then(
                            if (zoom == preset) Modifier.background(Color.White.copy(alpha = 0.15f), CircleShape)
                            else Modifier
                        ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = if (preset == 0.5f) ".5" else if (zoom == preset) "${preset.toInt()}x" else "${preset.toInt()}",
                        color = if (zoom == preset) Primary else colorScheme.onBackground.copy(alpha = 0.75f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (zoom == preset) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun BottomActionBar(
    isCameraReady: Boolean,
    isProcessing: Boolean,
    photosCount: Int,
    previewResult: CaptureResult?,
    showControls: Boolean,
    onCapture: () -> Unit,
    onKeep: () -> Unit,
    onRetake: () -> Unit,
    onDelete: () -> Unit,
    onProcess: () -> Unit,
    onClear: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxWidth()
    ) {
        if (showControls) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
            )
        }

        if (previewResult == null) {
            // Camera mode
            if (!showControls) {
                // Controls hidden: show only shutter button
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ShutterButton(
                        isCameraReady = isCameraReady,
                        isProcessing = isProcessing,
                        onCapture = onCapture
                    )
                }
            } else {
                // Full controls row — redesigned per design spec
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp, vertical = 32.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Clear/reset button (left)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(SurfaceHigh, CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape)
                            .then(if (photosCount > 0) Modifier.clickable { onClear() } else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Replay,
                            contentDescription = "Clear",
                            tint = if (photosCount > 0) colorScheme.onSurfaceVariant else colorScheme.onSurface.copy(alpha = 0.35f),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Shutter button with photo count badge
                    Box(contentAlignment = Alignment.Center) {
                        ShutterButton(
                            isCameraReady = isCameraReady,
                            isProcessing = isProcessing,
                            onCapture = onCapture
                        )
                        // Photo count badge (top-right) — dynamic width
                        if (photosCount > 0) {
                            val badgeText = "$photosCount"
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 2.dp, y = (-6).dp)
                                    .defaultMinSize(minWidth = 24.dp, minHeight = 24.dp)
                                    .background(Color(0xFF10B981), CircleShape)
                                    .border(2.dp, SurfaceLowest, CircleShape)
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    badgeText,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }

                    // Process/forward button with gradient (right)
                    FloatingActionButton(
                        onClick = onProcess,
                        containerColor = Color.Transparent,
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Primary, PrimaryContainer),
                                    start = Offset(0f, 0f),
                                    end = Offset(44f, 44f)
                                ),
                                CircleShape
                            ),
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 0.dp
                        )
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Process",
                            tint = OnPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        } else {
            // Preview mode — always show action buttons
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
                    .padding(horizontal = 32.dp, vertical = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onProcess() }
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Primary.copy(alpha = 0.2f), CircleShape)
                            .border(1.dp, Primary.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Finish", tint = Primary)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("FINISH", color = Primary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onRetake() }
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Refresh, "Retake", tint = Color.White.copy(alpha = 0.8f))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("RE-TAKE", color = colorScheme.onBackground.copy(alpha = 0.9f), fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onKeep() }
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .background(Primary.copy(alpha = 0.2f), CircleShape)
                            .border(1.dp, Primary.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, "Keep", tint = Primary)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("KEEP", color = Primary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ShutterButton(
    isCameraReady: Boolean,
    isProcessing: Boolean,
    onCapture: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(contentAlignment = Alignment.Center) {
        if (isProcessing) {
            val infiniteTransition = rememberInfiniteTransition()
            val scale by infiniteTransition.animateFloat(
                initialValue = 1f,
                targetValue = 1.15f,
                animationSpec = infiniteRepeatable(
                    animation = tween(500),
                    repeatMode = RepeatMode.Reverse
                )
            )
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale }
                    .border(3.dp, Primary.copy(alpha = 0.6f), CircleShape)
            )
        }

        OutlinedButton(
            onClick = onCapture,
            enabled = isCameraReady && !isProcessing,
            shape = CircleShape,
            border = ButtonDefaults.outlinedButtonBorder.copy(
                brush = Brush.linearGradient(
                    if (isProcessing) listOf(Primary.copy(alpha = 0.6f), Primary.copy(alpha = 0.6f))
                    else listOf(colorScheme.onBackground.copy(alpha = 0.8f), colorScheme.onBackground.copy(alpha = 0.8f))
                )
            ),
            modifier = Modifier
                .size(72.dp)
                .then(if (isProcessing) Modifier.graphicsLayer { scaleX = 0.9f; scaleY = 0.9f } else Modifier),
            contentPadding = PaddingValues(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        if (isProcessing) Primary.copy(alpha = 0.2f) else colorScheme.surface,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .border(
                            1.dp,
                            if (isProcessing) Primary.copy(alpha = 0.4f) else colorScheme.onSurface.copy(alpha = 0.35f),
                            CircleShape
                        )
                )
            }
        }
    }
}