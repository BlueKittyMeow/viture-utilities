package com.viture.hud

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    companion object {
        private const val TAG = "VitureHUD"
        private const val CAMERA_PERMISSION_REQUEST = 100
    }

    private lateinit var textEditor: EditText
    private lateinit var captureButton: Button
    private lateinit var settingsButton: Button
    private lateinit var modeTextButton: Button
    private lateinit var modeCameraButton: Button

    // Camera components
    private var vitureCamera: VitureCamera? = null
    private var surfaceView: SurfaceView? = null
    private var isCameraMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Target the glasses display (HDMI) in DeX mode
        targetGlassesDisplay()

        setContentView(R.layout.activity_main)

        // Initialize UI references
        textEditor = findViewById(R.id.textEditor)
        captureButton = findViewById(R.id.captureButton)
        settingsButton = findViewById(R.id.settingsButton)
        modeTextButton = findViewById(R.id.modeTextButton)
        modeCameraButton = findViewById(R.id.modeCameraButton)

        // Request CAMERA permission (required for USB cameras on Android 13+)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Requesting CAMERA permission...")
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
        }

        // Initialize camera
        vitureCamera = VitureCamera(this)
        vitureCamera?.apply {
            onCameraConnected = {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Camera connected", Toast.LENGTH_SHORT).show()
                    modeCameraButton.isEnabled = true
                    modeCameraButton.alpha = 1.0f
                }
            }
            onCameraDisconnected = {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Camera disconnected", Toast.LENGTH_SHORT).show()
                    modeCameraButton.isEnabled = false
                    modeCameraButton.alpha = 0.5f
                    if (isCameraMode) {
                        switchToTextMode()
                    }
                }
            }
            onCameraError = { error ->
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Camera error: $error", Toast.LENGTH_LONG).show()
                }
            }
            initialize()
        }

        // Set up mode toggle buttons
        modeTextButton.setOnClickListener { switchToTextMode() }
        modeCameraButton.setOnClickListener { switchToCameraMode() }

        // Set up action buttons
        captureButton.setOnClickListener { handleCapture() }
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // Start in text mode
        switchToTextMode()

        Log.d(TAG, "Viture HUD started - camera initialized")
    }

    /**
     * Switch to text editor mode
     */
    private fun switchToTextMode() {
        isCameraMode = false

        // Stop camera preview if running
        vitureCamera?.stopPreview()

        // Hide camera surface
        surfaceView?.visibility = View.GONE

        // Show text editor
        textEditor.visibility = View.VISIBLE

        // Update button colors
        modeTextButton.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00AA00.toInt())
        modeCameraButton.backgroundTintList = null

        // Update capture button
        captureButton.text = "Save Note"
        captureButton.isEnabled = false

        Log.d(TAG, "Switched to text mode")
    }

    /**
     * Switch to camera preview mode
     */
    private fun switchToCameraMode() {
        if (vitureCamera?.isConnected() != true) {
            Toast.makeText(this, "Camera not connected", Toast.LENGTH_SHORT).show()
            return
        }

        isCameraMode = true

        // Hide text editor
        textEditor.visibility = View.GONE

        // Create or show camera surface
        if (surfaceView == null) {
            createCameraSurface()
        }
        surfaceView?.visibility = View.VISIBLE

        // Update button colors
        modeCameraButton.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFF00AA00.toInt())
        modeTextButton.backgroundTintList = null

        // Update capture button
        captureButton.text = "Capture Photo"
        captureButton.isEnabled = true

        Log.d(TAG, "Switched to camera mode")
    }

    /**
     * Create camera preview surface
     */
    private fun createCameraSurface() {
        // Get content area
        val contentArea = findViewById<FrameLayout>(R.id.contentArea)

        // Create SurfaceView for camera preview
        surfaceView = SurfaceView(this)
        surfaceView?.holder?.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                // Start camera preview when surface is ready
                Log.d(TAG, "Surface created, starting preview")
                vitureCamera?.startPreview(holder.surface)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                Log.d(TAG, "Surface changed: ${width}x${height}")
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                // Stop preview when surface is destroyed
                Log.d(TAG, "Surface destroyed, stopping preview")
                vitureCamera?.stopPreview()
            }
        })

        // Add to content area
        contentArea.addView(surfaceView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    /**
     * Handle capture button press (mode-dependent)
     */
    private fun handleCapture() {
        if (!isCameraMode) {
            // In text mode - save text note
            val text = textEditor.text.toString()
            if (text.isNotEmpty()) {
                saveTextNote(text)
            } else {
                Toast.makeText(this, "Nothing to save", Toast.LENGTH_SHORT).show()
            }
        } else {
            // In camera mode - capture photo
            capturePhoto()
        }
    }

    /**
     * Capture photo from camera
     */
    private fun capturePhoto() {
        vitureCamera?.captureStillImage { imageData ->
            // Save on background thread
            Thread {
                try {
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val fileName = "viture_$timestamp.jpg"

                    val saveLocation = AppPreferences.getSaveLocation(this)

                    when (saveLocation) {
                        AppPreferences.SaveLocation.CAMERA_ROLL -> {
                            // Save to Pictures/VitureHUD using MediaStore (visible in gallery)
                            saveToMediaStore(imageData, fileName)
                        }
                        AppPreferences.SaveLocation.APP_STORAGE -> {
                            // Save to app-specific storage (private)
                            saveToAppStorage(imageData, fileName)
                        }
                    }

                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this, "Failed to save photo: ${e.message}", Toast.LENGTH_LONG).show()
                        Log.e(TAG, "Failed to save photo", e)
                    }
                }
            }.start()
        }
    }

    /**
     * Save photo to MediaStore (Pictures/VitureHUD) - visible in gallery
     */
    private fun saveToMediaStore(imageData: ByteArray, fileName: String) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/VitureHUD")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        if (uri != null) {
            contentResolver.openOutputStream(uri)?.use { output ->
                output.write(imageData)
            }

            // Mark as complete (for Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, contentValues, null, null)
            }

            runOnUiThread {
                Toast.makeText(this, "Photo saved to gallery: $fileName", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "Photo saved to MediaStore: $uri")
            }
        } else {
            throw Exception("Failed to create MediaStore entry")
        }
    }

    /**
     * Save photo to app-specific storage (private)
     */
    private fun saveToAppStorage(imageData: ByteArray, fileName: String) {
        val dir = File(getExternalFilesDir(null), "captures")
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val file = File(dir, fileName)
        FileOutputStream(file).use { output ->
            output.write(imageData)
        }

        runOnUiThread {
            Toast.makeText(this, "Photo saved: $fileName", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "Photo saved: ${file.absolutePath}")
        }
    }

    /**
     * Save text note to file
     */
    private fun saveTextNote(text: String) {
        Thread {
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "note_$timestamp.txt"

                val dir = File(getExternalFilesDir(null), "notes")
                if (!dir.exists()) {
                    dir.mkdirs()
                }

                val file = File(dir, fileName)
                file.writeText(text)

                runOnUiThread {
                    Toast.makeText(this, "Note saved: $fileName", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "Note saved: ${file.absolutePath}")
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Failed to save note: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "Failed to save note", e)
                }
            }
        }.start()
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

    /**
     * Clean up camera resources when activity is destroyed
     */
    override fun onDestroy() {
        vitureCamera?.release()
        super.onDestroy()
        Log.d(TAG, "Viture HUD destroyed - camera released")
    }
}
