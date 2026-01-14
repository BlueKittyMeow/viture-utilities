package com.viture.hud

import android.app.Activity
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

class MainActivity : Activity() {

    companion object {
        private const val TAG = "VitureHUD"
    }

    private lateinit var textEditor: EditText
    private lateinit var captureButton: Button
    private lateinit var settingsButton: Button
    private lateinit var modeTextButton: Button
    private lateinit var modeCameraButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Target the glasses display (HDMI) in DeX mode
        targetGlassesDisplay()

        setContentView(R.layout.activity_main)

        // Initialize views
        textEditor = findViewById(R.id.textEditor)
        captureButton = findViewById(R.id.captureButton)
        settingsButton = findViewById(R.id.settingsButton)
        modeTextButton = findViewById(R.id.modeTextButton)
        modeCameraButton = findViewById(R.id.modeCameraButton)

        // Mode toggle buttons
        modeTextButton.setOnClickListener {
            switchToTextMode()
        }

        modeCameraButton.setOnClickListener {
            // Future: switch to camera mode
            Toast.makeText(this, "Camera mode coming soon!", Toast.LENGTH_SHORT).show()
        }

        // Set up capture button (disabled for now)
        captureButton.setOnClickListener {
            Toast.makeText(this, "Camera not enabled in this build - coming soon!", Toast.LENGTH_SHORT).show()
        }

        // Settings button
        settingsButton.setOnClickListener {
            Toast.makeText(this, "Settings coming soon!", Toast.LENGTH_SHORT).show()
        }

        // Start in text mode
        switchToTextMode()

        Log.d(TAG, "Viture HUD started - text editor ready")
    }

    /**
     * Switch to text editor mode
     */
    private fun switchToTextMode() {
        textEditor.visibility = android.view.View.VISIBLE
        modeTextButton.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00AA00.toInt())
        modeCameraButton.backgroundTintList = null
        Log.d(TAG, "Switched to text mode")
    }

    /**
     * Check for secondary display and make fullscreen
     *
     * Note: Auto-launching on secondary display requires special setup.
     * For now, in DeX mode: drag this window to your glasses display.
     */
    private fun targetGlassesDisplay() {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displays = displayManager.displays

        Log.d(TAG, "Found ${displays.size} displays")

        // Make fullscreen
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        // Check for secondary display
        var secondaryDisplayFound = false
        for (display in displays) {
            Log.d(TAG, "Display ${display.displayId}: ${display.name}")
            if (display.displayId > 0) {
                secondaryDisplayFound = true
                Log.d(TAG, "Secondary display detected: ${display.name}")
            }
        }

        if (secondaryDisplayFound) {
            Toast.makeText(
                this,
                "Glasses detected! In DeX: drag window to glasses display",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
