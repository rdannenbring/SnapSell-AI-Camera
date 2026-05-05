package com.snapsell.nativecamera.ui.editor

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color as AColor

import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import com.snapsell.nativecamera.MainActivity
import com.snapsell.nativecamera.ui.theme.Primary
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.UCropFragment
import com.yalantis.ucrop.UCropFragmentCallback
import com.yalantis.ucrop.model.AspectRatio
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Inline crop panel that hosts uCrop's [UCropFragment] directly in the editor
 * Compose tree (above the bottom tab bar) instead of launching a separate
 * Activity. The host Activity must be a [FragmentActivity] (MainActivity is).
 *
 * On apply, the cropped JPEG replaces the source file at [photoPath] and
 * [onApplied] is invoked with the bumped image-version counter so the editor
 * preview reloads.
 */
@Composable
fun InlineCropPanel(
    photoPath: String,
    onApplied: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
        ?: run {
            // Defensive — should never happen.
            Text("Crop unavailable: host is not a FragmentActivity", color = Color.Red)
            return
        }

    // Stable per-mount destination cache file.
    val destinationFile = remember(photoPath) {
        File(context.cacheDir, "crop_${UUID.randomUUID()}.jpg")
    }

    val containerId = remember { View.generateViewId() }
    val fragmentTag = remember(photoPath) { "ucrop_inline_${photoPath.hashCode()}" }

    // Loading state (driven by UCropFragmentCallback.loadingProgress).
    var isLoading by remember { mutableStateOf(false) }
    var fragmentRef by remember { mutableStateOf<UCropFragment?>(null) }

    val onAppliedState = rememberUpdatedState(onApplied)
    val onCancelState = rememberUpdatedState(onCancel)

    val callback = remember(photoPath) {
        object : UCropFragmentCallback {
            override fun loadingProgress(loading: Boolean) {
                isLoading = loading
            }

            override fun onCropFinish(result: UCropFragment.UCropResult) {
                if (result.mResultCode == FragmentActivity.RESULT_OK) {
                    val output = result.mResultData?.let { UCrop.getOutput(it) }
                    if (output != null) {
                        try {
                            val target = File(photoPath)
                            context.contentResolver.openInputStream(output)?.use { input ->
                                FileOutputStream(target).use { out -> input.copyTo(out) }
                            }
                            onAppliedState.value()
                        } catch (e: Exception) {
                            // Surface as cancel — caller will keep original.
                            onCancelState.value()
                        }
                    } else {
                        onCancelState.value()
                    }
                } else {
                    onCancelState.value()
                }
            }
        }
    }

    // Mount the fragment when this composable enters / photoPath changes,
    // remove it when leaving. We attach to the activity's FragmentManager so
    // the fragment survives Compose recompositions but we tear it down on dispose.
    DisposableEffect(photoPath, containerId) {
        val sourceUri = createCropSourceUri(context, File(photoPath))
        val destinationUri = createCropSourceUri(context, destinationFile)

        val options = UCrop.Options().apply {
            setHideBottomControls(false)
            // Enable freestyle so the user can resize the crop frame by
            // dragging corners/edges. We then re-impose the chosen aspect
            // ratio in an OverlayViewChangeListener (see installAspectLock).
            setFreeStyleCropEnabled(true)
            setShowCropGrid(true)
            setShowCropFrame(true)
            setCompressionFormat(Bitmap.CompressFormat.JPEG)
            setCompressionQuality(95)
            setRootViewBackgroundColor(AColor.BLACK)
            setToolbarColor(AColor.parseColor("#FF12161C"))
            setStatusBarColor(AColor.BLACK)
            setToolbarWidgetColor(AColor.WHITE)
            setActiveControlsWidgetColor(AColor.parseColor("#FF34D399"))
            setAspectRatioOptions(
                0,
                AspectRatio("Original", 0f, 0f),
                AspectRatio("1:1", 1f, 1f),
                AspectRatio("4:3", 4f, 3f),
                AspectRatio("3:2", 3f, 2f),
                AspectRatio("16:9", 16f, 9f),
            )
        }

        val intent = UCrop.of(sourceUri, destinationUri)
            .withOptions(options)
            .getIntent(context)
        val args = intent.extras ?: Bundle()

        val fragment = UCropFragment.newInstance(args)
        fragment.setCallback(callback)
        fragmentRef = fragment

        // uCrop's UCropFragment.onAttach() requires the host Activity itself
        // implement UCropFragmentCallback. MainActivity does — register our
        // local callback as a delegate that the activity forwards to.
        (activity as? MainActivity)?.uCropCallbackDelegate = callback

        activity.supportFragmentManager
            .beginTransaction()
            .replace(containerId, fragment, fragmentTag)
            .commitNowAllowingStateLoss()

        // After uCrop lays out, install our aspect-ratio lock listener so
        // resizing snaps to the currently selected ratio.
        fragment.view?.post { installAspectLock(fragment.requireView()) }

        onDispose {
            (activity as? MainActivity)?.uCropCallbackDelegate = null
            // Remove fragment cleanly; ignore if activity is finishing.
            try {
                val fm = activity.supportFragmentManager
                val existing = fm.findFragmentByTag(fragmentTag)
                if (existing != null && !activity.isFinishing && !fm.isStateSaved) {
                    fm.beginTransaction().remove(existing).commitNowAllowingStateLoss()
                }
            } catch (_: Exception) { /* no-op */ }
            // Cleanup cache file.
            try { destinationFile.delete() } catch (_: Exception) {}
            fragmentRef = null
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Crop area — fragment view fills available space above action row.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    FragmentContainerView(ctx).apply {
                        id = containerId
                    }
                },
                update = { container ->
                    // Apply uCrop runtime tweaks (recolor tabs, dark control panes,
                    // hide auto-shown logo) once the fragment view is laid out.
                    container.post { tweakUCropFragmentViews(container); installAspectLock(container) }
                    container.postDelayed({ tweakUCropFragmentViews(container); installAspectLock(container) }, 300)
                    container.postDelayed({ tweakUCropFragmentViews(container); installAspectLock(container) }, 1000)
                    // Re-apply on every global-layout pass, since uCrop swaps
                    // selection state and may re-inflate controls_wrapper async.
                    val tag = LAYOUT_LISTENER_TAG_KEY
                    if (container.getTag(tag) == null) {
                        val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
                            tweakUCropFragmentViews(container)
                        }
                        container.viewTreeObserver.addOnGlobalLayoutListener(listener)
                        container.setTag(tag, listener)
                    }
                }
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Primary, strokeWidth = 3.dp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Action row.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { onCancelState.value() },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50),
            ) {
                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Cancel",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Button(
                onClick = {
                    fragmentRef?.cropAndSaveImage()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Primary,
                    contentColor = Color(0xFF005A3C),
                ),
                enabled = !isLoading && fragmentRef != null,
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "Apply",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

/**
 * Walks up the Compose host context to locate the underlying [FragmentActivity].
 */
private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is android.content.ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

private fun createCropSourceUri(context: Context, file: File) = run {
    file.parentFile?.mkdirs()
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/**
 * Mirrors the SnapSellCropActivity tweaks: dark control-pane background and
 * recolor of inactive tab/chip text. Hides the auto-shown logo placeholder
 * uCrop draws under the photo until ready.
 */
private const val LAYOUT_LISTENER_TAG_KEY = 0x7F000001

private fun resolveUCropId(res: android.content.res.Resources, ctx: Context, name: String): Int {
    // uCrop resources are merged into the app package by AGP, not kept under
    // com.yalantis.ucrop. Try the app package first, then fall back.
    val appId = res.getIdentifier(name, "id", ctx.packageName)
    if (appId != 0) return appId
    return res.getIdentifier(name, "id", "com.yalantis.ucrop")
}

private fun tweakUCropFragmentViews(root: View) {
    val res = root.resources
    val ctx = root.context

    // Hide the placeholder logo if uCrop hasn't already (looks weird inline).
    val logoId = resolveUCropId(res, ctx, "image_view_logo")
    if (logoId != 0) {
        root.findViewById<View>(logoId)?.visibility = View.GONE
    }

    val paneBg = AColor.parseColor("#FF12161C")

    // Force-paint the entire fragment root + every ViewGroup descendant dark.
    // This eliminates any leftover white containers (toolbar wrapper, control
    // wrapper, chip parents) regardless of whether they have R.id values.
    root.setBackgroundColor(paneBg)
    if (root is ViewGroup) paintAllContainers(root, paneBg)

    // Dark-paint specific named wrappers / panes for safety.
    val containerIds = listOf(
        "ucrop_photobox", "wrapper_controls", "wrapper_states",
        "layout_aspect_ratio", "layout_rotate_wheel", "layout_scale_wheel",
    )
    for (name in containerIds) {
        val id = resolveUCropId(res, ctx, name)
        if (id != 0) {
            root.findViewById<View>(id)?.setBackgroundColor(paneBg)
        }
    }

    // Walk the whole tree once and recolor every TextView/ImageView for
    // legibility on the dark background.
    if (root is ViewGroup) recolorAllText(root)

    // Make sure ALL tab wrappers + icons + labels stay visible (so users
    // can see and tap inactive tabs, not just the currently selected one).
    // The actual uCrop IDs are `state_aspect_ratio`, `state_rotate`,
    // `state_scale` (the LinearLayout tab buttons inside `wrapper_states`).
    val tabWrappers = listOf(
        "wrapper_states",
        "state_aspect_ratio",
        "state_rotate",
        "state_scale",
    )
    for (name in tabWrappers) {
        val id = resolveUCropId(res, ctx, name)
        if (id == 0) continue
        root.findViewById<View>(id)?.apply {
            visibility = View.VISIBLE
            alpha = 1f
        }
    }


    val activeColor = AColor.parseColor("#FF34D399")

    // Build a ColorStateList that maps selected/activated → mint, otherwise → white.
    // This overrides uCrop's default tint which blends with the dark background.
    val tabIconTint = ColorStateList(
        arrayOf(
            intArrayOf(android.R.attr.state_selected),
            intArrayOf(android.R.attr.state_activated),
            intArrayOf(),
        ),
        intArrayOf(activeColor, activeColor, AColor.WHITE),
    )
    // Tab labels: keep all three white regardless of selection state. The
    // selected icon already turns mint to indicate selection; mismatched
    // text colors looked inconsistent.
    val tabLabelTint = ColorStateList(
        arrayOf(intArrayOf()),
        intArrayOf(AColor.WHITE),
    )

    // Map each tab icon ID name to its uCrop drawable selector. We
    // explicitly (re-)assign the drawable because the AppCompat
    // `app:srcCompat` reference on these ImageViews sometimes inflates as
    // a 1x1 placeholder when the AAR's vector resources don't get
    // resolved through the host's resource path. Force the bitmap.
    val tabIconSpecs = listOf(
        "image_view_state_aspect_ratio" to "ucrop_crop",
        "image_view_state_rotate" to "ucrop_rotate",
        "image_view_state_scale" to "ucrop_scale",
    )
    val iconPx = (24f * res.displayMetrics.density).toInt() // 24dp like uCrop
    for ((idName, drawableName) in tabIconSpecs) {
        val id = resolveUCropId(res, ctx, idName)
        if (id == 0) continue
        val iv = root.findViewById<View>(id) as? ImageView ?: continue
        iv.visibility = View.VISIBLE
        iv.alpha = 1f
        iv.imageAlpha = 255
        // Force the drawable resource (resolves through app package, where
        // uCrop assets live after AGP merge).
        val drawId = res.getIdentifier(drawableName, "drawable", ctx.packageName)
            .takeIf { it != 0 }
            ?: res.getIdentifier(drawableName, "drawable", "com.yalantis.ucrop")
        if (drawId != 0) {
            try {
                val d = androidx.appcompat.content.res.AppCompatResources.getDrawable(ctx, drawId)
                if (d != null) iv.setImageDrawable(d) else iv.setImageResource(drawId)
            } catch (_: Exception) {
                try { iv.setImageResource(drawId) } catch (_: Exception) {}
            }
        }
        // Force a real on-screen size — the ImageViews were rendering at
        // 1x1 even though the parent LinearLayout had room.
        val lp = iv.layoutParams
        if (lp != null) {
            lp.width = iconPx
            lp.height = iconPx
            iv.layoutParams = lp
        }
        iv.minimumWidth = iconPx
        iv.minimumHeight = iconPx
        iv.scaleType = ImageView.ScaleType.FIT_CENTER
        iv.clearColorFilter()
        iv.imageTintList = tabIconTint
    }

    val tabLabels = listOf(
        "text_view_crop",
        "text_view_rotate",
        "text_view_scale",
    )
    for (name in tabLabels) {
        val id = resolveUCropId(res, ctx, name)
        if (id == 0) continue
        (root.findViewById<View>(id) as? TextView)?.apply {
            visibility = View.VISIBLE
            alpha = 1f
            setTextColor(tabLabelTint)
        }
    }

    // Force each tab wrapper FrameLayout to be visible AND give it a
    // minimum width so neighbouring tabs cannot be squashed to zero. uCrop
    // uses layout_weight=1 for these, but if the parent's measure pass runs
    // before the icons have intrinsic size we sometimes see them collapse.
    val tabWrapperNames = listOf(
        "wrapper_state_aspect_ratio",
        "wrapper_state_rotate",
        "wrapper_state_scale",
    )
    for (name in tabWrapperNames) {
        val id = resolveUCropId(res, ctx, name)
        if (id == 0) continue
        val v = root.findViewById<View>(id) ?: continue
        v.visibility = View.VISIBLE
        v.alpha = 1f
        // Ensure children (icon + label) are visible too in case uCrop set
        // an inactive state's drawable to a transparent placeholder.
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                v.getChildAt(i).apply {
                    visibility = View.VISIBLE
                    alpha = 1f
                }
            }
        }
    }



    // Photo-box centerpiece (the actual crop area) should stay black, not
    // dark-grey, so the image renders cleanly on top.
    val photoBoxId = resolveUCropId(res, ctx, "ucrop")
    if (photoBoxId != 0) {
        root.findViewById<View>(photoBoxId)?.setBackgroundColor(AColor.BLACK)
    }
}

/**
 * Install an OverlayViewChangeListener on uCrop's OverlayView that snaps the
 * crop frame back to the currently-selected aspect ratio after every user
 * resize gesture. uCrop natively only offers "freestyle resize OR locked
 * frame size", not "freestyle resize WITH locked aspect ratio" — so we
 * intercept the rect change and constrain it ourselves.
 *
 * Idempotent: tagged on the OverlayView so we only register once per layout.
 */
private const val ASPECT_LOCK_TAG_KEY = 0x7F000002

private fun installAspectLock(root: View) {
    val res = root.resources
    val ctx = root.context
    val overlayId = resolveUCropId(res, ctx, "view_overlay")
    val byId: View? = if (overlayId != 0) root.findViewById<View>(overlayId) else null
    val target: View? = byId ?: findViewByClassName(root, "com.yalantis.ucrop.view.OverlayView")
    if (target == null) {
        Log.d("SnapSell", "installAspectLock: overlay not found yet")
        return
    }
    if (target.getTag(ASPECT_LOCK_TAG_KEY) == true) return

    try {
        val overlayCls = target.javaClass
        // Find the GestureCropImageView so we can read its current
        // targetAspectRatio AND push the snapped rect back into it (so the
        // image-pan/zoom logic still runs as if uCrop's own listener fired).
        val cropImageId = resolveUCropId(res, ctx, "image_view_crop")
        val cropImageView: View? = (if (cropImageId != 0) root.findViewById<View>(cropImageId) else null)
            ?: findViewByClassName(root, "com.yalantis.ucrop.view.GestureCropImageView")
            ?: findViewByClassName(root, "com.yalantis.ucrop.view.CropImageView")

        // Reflective handles into uCrop internals.
        val mCropViewRectField = overlayCls.getDeclaredField("mCropViewRect").apply { isAccessible = true }
        val updateGridPointsMethod = try {
            overlayCls.getDeclaredMethod("updateGridPoints").apply { isAccessible = true }
        } catch (_: NoSuchMethodException) { null }

        // Capture uCrop's previously-installed listener so we can chain to
        // it (it's the one that calls GestureCropImageView.setCropRect()).
        val mCallbackField = try {
            overlayCls.getDeclaredField("mCallback").apply { isAccessible = true }
        } catch (_: NoSuchFieldException) { null }
        val previousListener: Any? = try { mCallbackField?.get(target) } catch (_: Throwable) { null }

        val listenerCls = Class.forName("com.yalantis.ucrop.callback.OverlayViewChangeListener")
        val setListener = overlayCls.getMethod("setOverlayViewChangeListener", listenerCls)

        val reentry = booleanArrayOf(false)

        val handler = java.lang.reflect.Proxy.newProxyInstance(
            listenerCls.classLoader,
            arrayOf(listenerCls),
        ) { _, method, args ->
            if (method.name == "onCropRectUpdated" && args != null && args.isNotEmpty()) {
                val rect = args[0] as android.graphics.RectF
                if (reentry[0]) {
                    return@newProxyInstance null
                }

                val ratio = try {
                    cropImageView?.javaClass?.getMethod("getTargetAspectRatio")
                        ?.invoke(cropImageView) as? Float
                } catch (_: Exception) { null }
                Log.d("SnapSell", "aspectLock fired ratio=$ratio rect=$rect")

                if (ratio == null || ratio <= 0f || !ratio.isFinite()) {
                    // No ratio constraint (Original): forward original rect.
                    forwardListener(previousListener, listenerCls, rect)
                    return@newProxyInstance null
                }

                val current = if (rect.height() > 0f) rect.width() / rect.height() else ratio
                if (kotlin.math.abs(current - ratio) < 0.001f) {
                    forwardListener(previousListener, listenerCls, rect)
                    return@newProxyInstance null
                }

                // Largest target-ratio rect centered inside user's drag.
                val cx = rect.centerX()
                val cy = rect.centerY()
                var newW = rect.width()
                var newH = newW / ratio
                if (newH > rect.height()) {
                    newH = rect.height()
                    newW = newH * ratio
                }
                val newRect = android.graphics.RectF(
                    cx - newW / 2f,
                    cy - newH / 2f,
                    cx + newW / 2f,
                    cy + newH / 2f,
                )

                reentry[0] = true
                try {
                    // Mutate the SAME RectF instance that uCrop's OverlayView
                    // owns; setting args[0] alone wouldn't help because the
                    // callback is invoked with `mCropViewRect` as argument
                    // (so they ARE the same instance, but we double up to be
                    // foolproof).
                    val live = try { mCropViewRectField.get(target) as android.graphics.RectF } catch (_: Throwable) { rect }
                    live.set(newRect)
                    rect.set(newRect)
                    try { updateGridPointsMethod?.invoke(target) } catch (_: Throwable) {}
                    target.invalidate()
                    // Forward to uCrop's own listener so GestureCropImageView
                    // re-fits the photo to the new (snapped) crop rect.
                    forwardListener(previousListener, listenerCls, newRect)
                } finally {
                    reentry[0] = false
                }
            }
            null
        }

        setListener.invoke(target, handler)

        // The above only fires on ACTION_UP. To snap DURING drag, attach an
        // OnTouchListener that, on every ACTION_MOVE, posts a snap of the
        // live rect after uCrop's own onTouchEvent has updated it. We return
        // false so uCrop still receives + handles the touch normally.
        val snapDuringDrag = Runnable {
            try {
                val ratio = (cropImageView?.javaClass?.getMethod("getTargetAspectRatio")
                    ?.invoke(cropImageView) as? Float) ?: return@Runnable
                if (ratio <= 0f || !ratio.isFinite()) return@Runnable
                val live = mCropViewRectField.get(target) as android.graphics.RectF
                val current = if (live.height() > 0f) live.width() / live.height() else ratio
                if (kotlin.math.abs(current - ratio) < 0.001f) return@Runnable
                val cx = live.centerX(); val cy = live.centerY()
                var newW = live.width(); var newH = newW / ratio
                if (newH > live.height()) { newH = live.height(); newW = newH * ratio }
                live.set(cx - newW / 2f, cy - newH / 2f, cx + newW / 2f, cy + newH / 2f)
                try { updateGridPointsMethod?.invoke(target) } catch (_: Throwable) {}
                target.invalidate()
            } catch (_: Throwable) {}
        }
        target.setOnTouchListener { v, ev ->
            if (ev.actionMasked == android.view.MotionEvent.ACTION_MOVE) {
                v.post(snapDuringDrag)
            }
            false // let uCrop handle the touch
        }

        target.setTag(ASPECT_LOCK_TAG_KEY, true)
        Log.d("SnapSell", "installAspectLock: installed on ${overlayCls.name} (prev=${previousListener?.javaClass?.name})")
    } catch (e: Throwable) {
        Log.w("SnapSell", "installAspectLock failed: ${e.message}", e)
    }
}

private fun forwardListener(previous: Any?, listenerCls: Class<*>, rect: android.graphics.RectF) {
    if (previous == null) return
    try {
        val m = listenerCls.getMethod("onCropRectUpdated", android.graphics.RectF::class.java)
        m.invoke(previous, rect)
    } catch (_: Throwable) {}
}

private fun findViewByClassName(root: View, className: String): View? {
    if (root.javaClass.name == className) return root
    if (root is ViewGroup) {
        for (i in 0 until root.childCount) {
            val r = findViewByClassName(root.getChildAt(i), className)
            if (r != null) return r
        }
    }
    return null
}

/** Recursively repaints every ViewGroup in the tree (except the photo
 *  surface itself) with the given color so no white parent leaks through. */
private fun paintAllContainers(group: ViewGroup, color: Int) {
    for (i in 0 until group.childCount) {
        val child = group.getChildAt(i)
        // Don't paint over the actual image-cropping surface.
        val idName = try {
            if (child.id != View.NO_ID) child.resources.getResourceEntryName(child.id) else ""
        } catch (_: Exception) { "" }
        if (idName != "ucrop" && idName != "image_view_crop" && child is ViewGroup) {
            child.setBackgroundColor(color)
        }
        if (child is ViewGroup) paintAllContainers(child, color)
    }
}

/** Recursively recolors every TextView in the tree:
 *  - selected/activated → mint green
 *  - inactive → white. */
private fun recolorAllText(group: ViewGroup) {
    val activeColor = AColor.parseColor("#FF34D399")
    for (i in 0 until group.childCount) {
        val child = group.getChildAt(i)
        if (child is TextView) {
            if (child.isSelected || child.isActivated) {
                child.setTextColor(activeColor)
            } else {
                child.setTextColor(AColor.WHITE)
            }
        }
        if (child is ImageView) {
            // Only recolor known tab/state icons — never the actual crop
            // image (`image_view_crop`) or the photobox surface, otherwise
            // we tint the user's photo white.
            val idName = try {
                if (child.id != View.NO_ID) child.resources.getResourceEntryName(child.id) else ""
            } catch (_: Exception) { "" }
            val tintableIcons = setOf(
                "image_view_state_aspect_ratio",
                "image_view_state_rotate",
                "image_view_state_scale",
            )
            if (idName in tintableIcons) {
                if (child.isSelected || child.isActivated) {
                    child.setColorFilter(activeColor)
                } else {
                    child.setColorFilter(AColor.WHITE)
                }
            }
        }
        if (child is ViewGroup) recolorAllText(child)
    }
}
