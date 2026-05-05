package com.snapsell.nativecamera

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.snapsell.nativecamera.ui.theme.SnapSellTheme
import com.snapsell.nativecamera.ui.camera.CameraScreen
import com.snapsell.nativecamera.ui.settings.SettingsScreen
import com.snapsell.nativecamera.ui.editor.EditorScreen
import com.yalantis.ucrop.UCropFragment
import com.yalantis.ucrop.UCropFragmentCallback
import dagger.hilt.android.AndroidEntryPoint

sealed class Screen {
    data class Camera(val retainedPaths: List<String> = emptyList()) : Screen()
    data class Editor(val photoPaths: List<String>) : Screen()
    data object Settings : Screen()
}

@AndroidEntryPoint
class MainActivity : FragmentActivity(), UCropFragmentCallback {
    /** Delegate set by [com.snapsell.nativecamera.ui.editor.InlineCropPanel] while mounted. */
    var uCropCallbackDelegate: UCropFragmentCallback? = null

    override fun loadingProgress(loading: Boolean) {
        uCropCallbackDelegate?.loadingProgress(loading)
    }

    override fun onCropFinish(result: UCropFragment.UCropResult) {
        uCropCallbackDelegate?.onCropFinish(result)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SnapSellTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent()
                }
            }
        }
    }
}

@Composable
fun AppContent() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Camera()) }
    // Track retained paths across screen transitions (persists through settings navigation)
    var retainedPaths by remember { mutableStateOf<List<String>>(emptyList()) }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            if (targetState is Screen.Editor) {
                // Camera → Editor: fade through black for a premium feel
                fadeIn(tween(400)) togetherWith fadeOut(tween(200))
            } else if (targetState is Screen.Camera && initialState is Screen.Editor) {
                // Editor → Camera: smooth fade
                fadeIn(tween(300)) togetherWith fadeOut(tween(300))
            } else if (targetState is Screen.Settings) {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300)) togetherWith
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(300))
            } else {
                slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300)) togetherWith
                    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(300))
            }
        },
        label = "screen_transition"
    ) { screen ->
        when (screen) {
            is Screen.Camera -> CameraScreen(
                retainedPhotoPaths = screen.retainedPaths,
                onOpenSettings = {
                    retainedPaths = screen.retainedPaths
                    currentScreen = Screen.Settings
                },
                onOpenEditor = { paths ->
                    retainedPaths = emptyList()
                    currentScreen = Screen.Editor(paths)
                }
            )
            is Screen.Editor -> EditorScreen(
                photoPaths = screen.photoPaths,
                onBack = {
                    val paths = screen.photoPaths
                    retainedPaths = paths
                    currentScreen = Screen.Camera(retainedPaths = paths)
                },
                onOpenSettings = {
                    retainedPaths = screen.photoPaths
                    currentScreen = Screen.Settings
                },
                onClear = {
                    retainedPaths = emptyList()
                    currentScreen = Screen.Camera()
                }
            )
            is Screen.Settings -> SettingsScreen(
                onBack = {
                    currentScreen = Screen.Camera(retainedPaths = retainedPaths)
                }
            )
        }
    }
}