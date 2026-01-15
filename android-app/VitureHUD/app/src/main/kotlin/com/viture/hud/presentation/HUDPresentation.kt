package com.viture.hud.presentation

import android.app.Presentation
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.TextView
import com.viture.hud.R

/**
 * Presentation class for HUD content displayed on Viture glasses.
 *
 * This class manages what appears on the secondary display (glasses).
 * It receives content updates from MainActivity on the phone.
 *
 * Key responsibilities:
 * - Display camera preview surface
 * - Display synchronized text content
 * - Handle mode switching (camera vs text)
 * - Minimal UI - just content, no controls
 */
class HUDPresentation(
    context: Context,
    display: Display,
    private val onSurfaceReady: ((Surface) -> Unit)? = null
) : Presentation(context, display) {

    companion object {
        private const val TAG = "HUDPresentation"
    }

    // UI components
    private lateinit var contentContainer: FrameLayout
    private lateinit var textContainer: ScrollView
    private lateinit var textDisplay: TextView
    private var surfaceView: SurfaceView? = null

    // Current display mode
    private var currentMode: HUDMode = HUDMode.TEXT

    // Surface lifecycle tracking
    private var surfaceReady = false

    enum class HUDMode {
        TEXT,
        CAMERA,
        CLEAR_VIEW
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Request no title BEFORE super.onCreate - critical for fullscreen
        requestWindowFeature(Window.FEATURE_NO_TITLE)

        super.onCreate(savedInstanceState)

        // Configure window for true fullscreen (including DeX taskbar area)
        window?.apply {
            // Keep screen on while HUD is active
            addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            // Fullscreen flags for DeX - these can be set before setContentView
            setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            )
            // Note: insetsController setup happens in applyImmersiveMode() AFTER setContentView
        }

        setContentView(R.layout.layout_hud_presentation)

        // Initialize views
        contentContainer = findViewById(R.id.hudContentContainer)
        textContainer = findViewById(R.id.textContainer)
        textDisplay = findViewById(R.id.hudTextDisplay)

        // Apply immersive fullscreen AFTER setContentView (decorView needs to exist)
        applyImmersiveMode()

        // Start in text mode by default
        showTextMode()

        Log.d(TAG, "HUD Presentation created on display: ${display.name}")
    }

    override fun onStart() {
        super.onStart()
        // Re-apply immersive mode in case system reset it
        applyImmersiveMode()
    }

    /**
     * Apply immersive fullscreen mode to hide system bars.
     * Must be called AFTER setContentView.
     */
    private fun applyImmersiveMode() {
        window?.decorView?.let { decorView ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Android 11+ (API 30+)
                window?.setDecorFitsSystemWindows(false)
                decorView.windowInsetsController?.let { controller ->
                    controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                    controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                // Android 10 and below
                @Suppress("DEPRECATION")
                decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                )
            }
        }
    }

    /**
     * Switch to camera preview mode.
     * Creates SurfaceView if not exists and notifies when surface is ready.
     */
    fun showCameraMode() {
        currentMode = HUDMode.CAMERA

        // Show content container
        contentContainer.visibility = View.VISIBLE

        // Hide text display
        textContainer.visibility = View.GONE

        // Create or show camera surface
        if (surfaceView == null) {
            createCameraSurface()
        }
        surfaceView?.visibility = View.VISIBLE

        Log.d(TAG, "Switched to camera mode")
    }

    /**
     * Switch to text display mode.
     * Hides camera surface to stop preview.
     */
    fun showTextMode() {
        currentMode = HUDMode.TEXT

        // Show content container
        contentContainer.visibility = View.VISIBLE

        // Hide camera surface
        surfaceView?.visibility = View.GONE

        // Show text display
        textContainer.visibility = View.VISIBLE

        Log.d(TAG, "Switched to text mode")
    }

    /**
     * Switch to clear view mode.
     * Hides all content - black background = transparent on Viture glasses.
     */
    fun showClearView() {
        currentMode = HUDMode.CLEAR_VIEW

        // Hide the entire content container - just black screen (transparent on glasses)
        contentContainer.visibility = View.GONE

        Log.d(TAG, "Switched to clear view mode")
    }

    /**
     * Update the text displayed on glasses.
     * Called when user types on phone.
     */
    fun updateText(text: String) {
        textDisplay.text = text
    }

    /**
     * Get the surface for camera preview.
     * Returns null if surface not ready yet.
     */
    fun getCameraSurface(): Surface? {
        return if (surfaceReady) {
            surfaceView?.holder?.surface
        } else {
            null
        }
    }

    /**
     * Check if HUD is in camera mode and surface is ready.
     */
    fun isCameraSurfaceReady(): Boolean {
        return currentMode == HUDMode.CAMERA && surfaceReady
    }

    /**
     * Get current mode.
     */
    fun getCurrentMode(): HUDMode = currentMode

    /**
     * Create camera preview SurfaceView.
     * Sets up surface lifecycle callbacks.
     */
    private fun createCameraSurface() {
        surfaceView = SurfaceView(context).apply {
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    Log.d(TAG, "Camera surface created")
                    surfaceReady = true
                    onSurfaceReady?.invoke(holder.surface)
                }

                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    Log.d(TAG, "Camera surface changed: ${width}x${height}")
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    Log.d(TAG, "Camera surface destroyed")
                    surfaceReady = false
                }
            })
        }

        // Add to content container (fills the container)
        contentContainer.addView(
            surfaceView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    override fun onStop() {
        surfaceReady = false
        super.onStop()
        Log.d(TAG, "HUD Presentation stopped")
    }
}
