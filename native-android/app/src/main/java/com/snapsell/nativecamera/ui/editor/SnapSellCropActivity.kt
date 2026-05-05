package com.snapsell.nativecamera.ui.editor

import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.widget.Toolbar
import com.yalantis.ucrop.UCropActivity

class SnapSellCropActivity : UCropActivity() {

    private var layoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        blockSystemBackGesture()
        hookToolbarCloseButton()
        configureBackGestureExclusion()
        fixCropTabIconVisibility()
        addBackGestureInfoBanner()
    }

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val content = findViewById<View>(android.R.id.content)
            layoutListener?.let { listener ->
                content?.viewTreeObserver?.removeOnGlobalLayoutListener(listener)
            }
        }
        layoutListener = null
        super.onDestroy()
    }

    /**
     * Blocks the system back gesture and hardware back button.
     * The toolbar X button is handled separately via [hookToolbarCloseButton].
     */
    private fun blockSystemBackGesture() {
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    // Intentionally swallow — users must use toolbar X or ✓
                }
            }
        )
    }

    /**
     * UCrop's toolbar X button calls onBackPressed() internally,
     * which our [OnBackPressedCallback] would block.
     * We re-hook the toolbar nav click to call finish() directly instead.
     */
    private fun hookToolbarCloseButton() {
        val content = findViewById<View>(android.R.id.content) ?: return
        content.post {
            try {
                var toolbarId = resources.getIdentifier("toolbar", "id", "com.yalantis.ucrop")
                if (toolbarId == 0) toolbarId = resources.getIdentifier("toolbar", "id", packageName)
                val toolbar = if (toolbarId != 0) findViewById<View>(toolbarId) as? Toolbar else null

                if (toolbar != null) {
                    toolbar.setNavigationOnClickListener {
                        setResult(RESULT_CANCELED)
                        finish()
                    }
                }
            } catch (_: Exception) {
                // Toolbar not found — fallback, back will be blocked entirely
            }
        }
    }

    private fun configureBackGestureExclusion() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        val content = findViewById<View>(android.R.id.content) ?: return

        fun updateExclusionRects() {
            val width = content.width
            val height = content.height
            if (width <= 0 || height <= 0) return

            val edgePx = (40f * resources.displayMetrics.density).toInt()
            content.systemGestureExclusionRects = listOf(
                Rect(0, 0, edgePx, height),
                Rect(width - edgePx, 0, width, height)
            )
        }

        content.post { updateExclusionRects() }

        val listener = ViewTreeObserver.OnGlobalLayoutListener { updateExclusionRects() }
        content.viewTreeObserver.addOnGlobalLayoutListener(listener)
        layoutListener = listener
    }

    /**
     * uCrop's bottom controls have 3 tab icons (crop, rotate, scale).
     * On a dark theme some of those icons/text can render invisibly.
     *
     * IMPORTANT: We must NOT touch the photo preview views (image_view_crop,
     * image_view_overlay, ucrop_photobox, ucrop_frame, ucrop_view, etc.).
     * Painting a white color filter onto the preview ImageView is what was
     * causing the entire photo to render as a solid white rectangle.
     *
     * This implementation only recolors a strict allowlist of tab/state
     * widgets identified by their exact uCrop resource ids.
     */
    private fun fixCropTabIconVisibility() {
        val content = findViewById<View>(android.R.id.content) ?: return
        // Run after layout, then again to catch any lazy inflation in uCrop's
        // bottom controls (aspect ratio / rotate / scale wrappers).
        content.post { recolorTabWidgets() }
        content.postDelayed({ recolorTabWidgets() }, 300)
        content.postDelayed({ recolorTabWidgets() }, 1000)
    }

    /**
     * Allowlist of uCrop bottom-control widget ids that are safe to recolor.
     * These are *only* the tab icons and labels — never the photo preview.
     */
    private val tabIconIds = listOf(
        // Tab icons (ImageView)
        "image_view_state_aspect_ratio",
        "image_view_state_rotate",
        "image_view_state_scale",
        // Tab labels (TextView)
        "text_view_crop",
        "text_view_rotate",
        "text_view_scale"
    )

    /**
     * Wrapper FrameLayouts that hold each tab — safe to ensure visibility on,
     * but we never touch their photo-related siblings.
     */
    private val tabWrapperIds = listOf(
        "wrapper_states",
        "wrapper_state_aspect_ratio",
        "wrapper_state_rotate",
        "wrapper_state_scale"
    )

    /**
     * Layout containers for the bottom control panes (above the Crop/Rotate/Scale
     * tab nav). uCrop's defaults paint these with `ucrop_color_widget_background`
     * which on our setup ends up near-white, hiding the aspect ratio chip text
     * (which is also white). Force them dark to match our theme.
     */
    private val controlPaneIds = listOf(
        "layout_aspect_ratio",
        "layout_rotate_wheel",
        "layout_scale_wheel"
    )

    private val controlPaneBackground = Color.parseColor("#FF12161C")

    private fun recolorTabWidgets() {
        val ucropPkg = "com.yalantis.ucrop"

        // Make wrappers visible (no color tinting).
        for (resName in tabWrapperIds) {
            val id = findUcropId(resName, ucropPkg)
            if (id != 0) {
                val v = findViewById<View>(id)
                if (v != null) v.visibility = View.VISIBLE
            }
        }

        // Force each control pane to a dark background and walk its descendants
        // to recolor any non-active (white) chip text so it stays legible.
        for (resName in controlPaneIds) {
            val id = findUcropId(resName, ucropPkg)
            if (id == 0) continue
            val pane = findViewById<View>(id) ?: continue
            pane.setBackgroundColor(controlPaneBackground)
            if (pane is ViewGroup) recolorChipTextDescendants(pane)
        }

        // Recolor only the explicit tab icon ImageViews and label TextViews.
        for (resName in tabIconIds) {
            val id = findUcropId(resName, ucropPkg)
            if (id == 0) continue
            val v = findViewById<View>(id) ?: continue
            v.visibility = View.VISIBLE
            when (v) {
                is ImageView -> {
                    v.setColorFilter(Color.WHITE)
                    v.imageAlpha = 255
                }
                is TextView -> {
                    v.setTextColor(Color.WHITE)
                }
            }
        }
    }

    /**
     * Walk every TextView descendant of a control pane and ensure its color is
     * legible on a dark background. uCrop's AspectRatioTextView swaps colors
     * via setSelected() / setActivated(), but its inactive state inherits the
     * `ucrop_color_widget` color which on our build is white-on-white.
     *
     * We leave the active/selected color alone (it's set via the
     * `setActiveControlsWidgetColor` UCrop.Option) and only enforce a visible
     * inactive color.
     */
    private fun recolorChipTextDescendants(group: ViewGroup) {
        for (i in 0 until group.childCount) {
            val child = group.getChildAt(i)
            if (child is TextView) {
                if (!child.isSelected && !child.isActivated) {
                    child.setTextColor(Color.WHITE)
                }
            }
            if (child is ViewGroup) recolorChipTextDescendants(child)
        }
    }

    private fun findUcropId(name: String, ucropPkg: String): Int {
        var id = resources.getIdentifier(name, "id", ucropPkg)
        if (id == 0) id = resources.getIdentifier(name, "id", packageName)
        return id
    }

    private fun addBackGestureInfoBanner() {
        val rootView = findViewById<View>(android.R.id.content) ?: return

        val banner = TextView(this).apply {
            text = "ⓘ  Back gestures disabled on this screen"
            setTextColor(Color.parseColor("#99FFFFFF"))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(android.graphics.Typeface.MONOSPACE)
            gravity = Gravity.CENTER
            setPadding(24, 8, 24, 8)
            setBackgroundColor(0xB3000000.toInt())
        }

        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = (48 * resources.displayMetrics.density).toInt()
        }

        (rootView as? FrameLayout)?.addView(banner, params)
            ?: (rootView.parent as? FrameLayout)?.addView(banner, params)
    }
}
