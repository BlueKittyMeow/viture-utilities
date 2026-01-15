package com.viture.hud.presentation

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import android.view.Surface

/**
 * Manages display detection, connection events, and presentation lifecycle.
 *
 * Responsibilities:
 * - Detect secondary displays (glasses)
 * - Monitor display connect/disconnect events
 * - Create and destroy HUDPresentation as needed
 * - Provide callbacks for display state changes
 */
class DisplayStateManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "DisplayStateManager"
    }

    // Display manager reference
    private val displayManager: DisplayManager by lazy {
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    // Current presentation instance
    private var hudPresentation: HUDPresentation? = null

    // Callbacks
    var onGlassesConnected: ((HUDPresentation) -> Unit)? = null
    var onGlassesDisconnected: (() -> Unit)? = null
    var onSurfaceReady: ((Surface) -> Unit)? = null

    // Display listener for connect/disconnect events
    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            Log.d(TAG, "Display added: $displayId")
            if (displayId > 0) {
                // Secondary display connected - likely glasses
                val display = displayManager.getDisplay(displayId)
                if (display != null) {
                    showPresentationOnDisplay(display)
                }
            }
        }

        override fun onDisplayRemoved(displayId: Int) {
            Log.d(TAG, "Display removed: $displayId")
            if (displayId > 0) {
                // Secondary display removed - glasses disconnected
                dismissPresentation()
                onGlassesDisconnected?.invoke()
            }
        }

        override fun onDisplayChanged(displayId: Int) {
            Log.d(TAG, "Display changed: $displayId")
            // Display properties changed - typically not relevant for our use
        }
    }

    /**
     * Start monitoring for display changes and check for existing secondary display.
     */
    fun start() {
        Log.d(TAG, "Starting display monitoring")

        // Register for display events
        displayManager.registerDisplayListener(displayListener, null)

        // Check if glasses are already connected
        checkForSecondaryDisplay()
    }

    /**
     * Stop monitoring and clean up presentation.
     */
    fun stop() {
        Log.d(TAG, "Stopping display monitoring")
        displayManager.unregisterDisplayListener(displayListener)
        dismissPresentation()
    }

    /**
     * Check for existing secondary display (glasses already connected).
     */
    private fun checkForSecondaryDisplay() {
        val displays = displayManager.displays
        Log.d(TAG, "Found ${displays.size} displays")

        for (display in displays) {
            Log.d(TAG, "Display ${display.displayId}: ${display.name}")
            if (display.displayId > 0) {
                // Found secondary display - create presentation
                showPresentationOnDisplay(display)
                return
            }
        }

        Log.d(TAG, "No secondary display found - glasses not connected")
    }

    /**
     * Create and show HUDPresentation on the given display.
     */
    private fun showPresentationOnDisplay(display: Display) {
        // Dismiss any existing presentation first
        dismissPresentation()

        Log.d(TAG, "Creating HUD presentation on display: ${display.name}")

        hudPresentation = HUDPresentation(
            context = context,
            display = display,
            onSurfaceReady = { surface ->
                Log.d(TAG, "Surface ready callback from presentation")
                onSurfaceReady?.invoke(surface)
            }
        ).also { presentation ->
            presentation.show()
            onGlassesConnected?.invoke(presentation)
        }
    }

    /**
     * Dismiss current presentation.
     */
    private fun dismissPresentation() {
        hudPresentation?.let {
            Log.d(TAG, "Dismissing presentation")
            it.dismiss()
        }
        hudPresentation = null
    }

    /**
     * Get the current presentation instance (if showing).
     */
    fun getPresentation(): HUDPresentation? = hudPresentation

    /**
     * Check if glasses are currently connected and presentation is showing.
     */
    fun isGlassesConnected(): Boolean = hudPresentation != null
}
