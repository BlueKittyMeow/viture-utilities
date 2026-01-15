package com.viture.hud

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.viture.hud.presentation.DisplayStateManager
import com.viture.hud.presentation.HUDPresentation
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Main Activity - Phone-side controls for the HUD.
 *
 * This activity runs on the phone screen and provides:
 * - Text input field (synced to glasses display)
 * - Camera capture button (when in camera mode)
 * - Mode toggle between text and camera
 * - Settings access
 *
 * The actual HUD content appears on the glasses via HUDPresentation,
 * managed by DisplayStateManager.
 */
class MainActivity : Activity() {

    companion object {
        private const val TAG = "VitureHUD"
        private const val CAMERA_PERMISSION_REQUEST = 100
    }

    // Phone UI components
    private lateinit var statusText: TextView
    private lateinit var textInput: EditText
    private lateinit var cameraPlaceholder: LinearLayout
    private lateinit var captureButton: Button
    private lateinit var modeTextButton: Button
    private lateinit var modeCameraButton: Button
    private lateinit var settingsButton: Button

    // Display management
    private lateinit var displayStateManager: DisplayStateManager

    // Camera
    private var vitureCamera: VitureCamera? = null
    private var isCameraMode = false

    // Reference to current presentation
    private var hudPresentation: HUDPresentation? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize phone UI
        initializePhoneUI()

        // Initialize display state manager
        initializeDisplayManager()

        // Initialize camera
        initializeCamera()

        // Start in text mode
        switchToTextMode()

        Log.d(TAG, "MainActivity created - phone controls ready")
    }

    private fun initializePhoneUI() {
        statusText = findViewById(R.id.statusText)
        textInput = findViewById(R.id.textInput)
        cameraPlaceholder = findViewById(R.id.cameraPlaceholder)
        captureButton = findViewById(R.id.captureButton)
        modeTextButton = findViewById(R.id.modeTextButton)
        modeCameraButton = findViewById(R.id.modeCameraButton)
        settingsButton = findViewById(R.id.settingsButton)

        // Text input listener - sync to glasses
        textInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                // Sync text to glasses display
                hudPresentation?.updateText(s?.toString() ?: "")
            }
        })

        // Mode buttons
        modeTextButton.setOnClickListener { switchToTextMode() }
        modeCameraButton.setOnClickListener { switchToCameraMode() }

        // Capture button
        captureButton.setOnClickListener { handleCapture() }

        // Settings button
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun initializeDisplayManager() {
        displayStateManager = DisplayStateManager(this).apply {
            onGlassesConnected = { presentation ->
                runOnUiThread {
                    hudPresentation = presentation
                    updateStatusText()
                    Toast.makeText(this@MainActivity, "HUD active on glasses", Toast.LENGTH_SHORT).show()

                    // If in text mode, sync current text
                    if (!isCameraMode) {
                        presentation.showTextMode()
                        presentation.updateText(textInput.text.toString())
                    }
                }
            }

            onGlassesDisconnected = {
                runOnUiThread {
                    hudPresentation = null
                    updateStatusText()

                    // If in camera mode, stop preview and switch to text
                    if (isCameraMode) {
                        vitureCamera?.stopPreview()
                        switchToTextMode()
                    }

                    Toast.makeText(this@MainActivity, "Glasses disconnected", Toast.LENGTH_SHORT).show()
                }
            }

            onSurfaceReady = { surface ->
                // Camera surface is ready on glasses - start preview if in camera mode
                Log.d(TAG, "Surface ready from presentation")
                if (isCameraMode && vitureCamera?.isConnected() == true) {
                    Log.d(TAG, "Starting camera preview on glasses surface")
                    vitureCamera?.startPreview(surface)
                }
            }
        }

        displayStateManager.start()
    }

    private fun initializeCamera() {
        // Request camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST
            )
        }

        vitureCamera = VitureCamera(this).apply {
            onCameraConnected = {
                runOnUiThread {
                    modeCameraButton.isEnabled = true
                    modeCameraButton.alpha = 1.0f
                    updateStatusText()
                }
            }
            onCameraDisconnected = {
                runOnUiThread {
                    modeCameraButton.isEnabled = false
                    modeCameraButton.alpha = 0.5f
                    if (isCameraMode) {
                        switchToTextMode()
                    }
                    updateStatusText()
                }
            }
            onCameraError = { error ->
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Camera error: $error", Toast.LENGTH_LONG).show()
                }
            }
            initialize()
        }
    }

    private fun switchToTextMode() {
        isCameraMode = false

        // Stop camera preview
        vitureCamera?.stopPreview()

        // Update phone UI
        textInput.visibility = View.VISIBLE
        cameraPlaceholder.visibility = View.GONE
        captureButton.text = "Save Note"
        modeTextButton.backgroundTintList =
            android.content.res.ColorStateList.valueOf(0xFF00AA00.toInt())
        modeCameraButton.backgroundTintList = null

        // Update glasses display
        hudPresentation?.apply {
            showTextMode()
            updateText(textInput.text.toString())
        }

        Log.d(TAG, "Switched to text mode")
    }

    private fun switchToCameraMode() {
        if (vitureCamera?.isConnected() != true) {
            Toast.makeText(this, "Camera not connected", Toast.LENGTH_SHORT).show()
            return
        }

        if (!displayStateManager.isGlassesConnected()) {
            Toast.makeText(this, "Connect glasses to use camera mode", Toast.LENGTH_SHORT).show()
            return
        }

        isCameraMode = true

        // Update phone UI - hide text input, show placeholder
        textInput.visibility = View.GONE
        cameraPlaceholder.visibility = View.VISIBLE
        captureButton.text = "Capture Photo"
        modeCameraButton.backgroundTintList =
            android.content.res.ColorStateList.valueOf(0xFF00AA00.toInt())
        modeTextButton.backgroundTintList = null

        // Update glasses display to camera mode
        hudPresentation?.showCameraMode()

        // Preview will start when surface ready callback fires

        Log.d(TAG, "Switched to camera mode")
    }

    private fun handleCapture() {
        if (isCameraMode) {
            capturePhoto()
        } else {
            val text = textInput.text.toString()
            if (text.isNotEmpty()) {
                saveTextNote(text)
            } else {
                Toast.makeText(this, "Nothing to save", Toast.LENGTH_SHORT).show()
            }
        }
    }

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
                            saveToMediaStore(imageData, fileName)
                        }
                        AppPreferences.SaveLocation.APP_STORAGE -> {
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

    private fun updateStatusText() {
        val glassesStatus = if (displayStateManager.isGlassesConnected()) "Glasses: Connected" else "Glasses: Not connected"
        val cameraStatus = if (vitureCamera?.isConnected() == true) "Camera: Ready" else "Camera: Not connected"
        statusText.text = "$glassesStatus | $cameraStatus"
    }

    override fun onDestroy() {
        displayStateManager.stop()
        vitureCamera?.release()
        super.onDestroy()
        Log.d(TAG, "MainActivity destroyed")
    }
}
