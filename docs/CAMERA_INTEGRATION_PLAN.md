# USB Camera Integration Plan
## Viture HUD Android App - Camera Implementation Strategy

**Date:** 2026-01-14
**Status:** Ready for Implementation
**Reviewers:** Claude (Architecture), Gemini (Code Review), Codex (Lint/Quality)

---

## Executive Summary

This document outlines the implementation plan for integrating USB camera functionality into the Viture HUD Android app. The Viture Luma Pro glasses contain a USB 2.0 camera (1920x1080 @ 5fps) that needs to be accessed via Android's USB Host API.

**Primary Approach:** Clone and build `saki4510t/UVCCamera` library locally as project modules
**Estimated Effort:** 7-14 hours (1-2 days)
**Risk Level:** Low (proven technology, CameraFi app demonstrates feasibility)

---

## 1. Problem Statement

### Current Situation
- Android HUD app is functional with text editor and UI layout ✓
- Camera mode toggle exists but is disabled ✓
- Attempted to use UVCCamera via JitPack dependency - **FAILED**
- All JitPack versions fail to resolve (v3.1.0, 2.3.2, master-SNAPSHOT)

### Root Cause Analysis
**JitPack Artifact Upload Failure:**
- POM metadata exists but JAR/AAR artifacts are missing
- Not a configuration issue on our end
- Affects both `saki4510t/UVCCamera` and `jiangdongguo/AndroidUSBCamera`

### Proof of Concept
**CameraFi app successfully accesses Viture camera**, proving:
- USB Host API can communicate with Viture USB camera
- No root access required
- Works in Samsung DeX mode
- UVC protocol implementation is viable

---

## 2. Technical Requirements

### Hardware Specifications
| Component | Value |
|-----------|-------|
| Device | Viture Luma Pro AR Glasses |
| Camera Resolution | 1920x1080 |
| Frame Rate | 5 fps |
| USB Vendor ID | 0x35ca |
| USB Product ID | 0x1101 |
| Protocol | UVC (USB Video Class) |
| Connection | USB-C via phone |

### Software Environment
| Component | Version |
|-----------|---------|
| Phone | Samsung Galaxy S22 Ultra |
| Android Version | 13+ |
| Target SDK | 34 |
| Min SDK | 26 |
| Kotlin | 1.9.22 |
| Gradle | 8.13.2 |
| NDK | 25.x (required for UVCCamera) |

### Permissions Required
```xml
<!-- Already configured in AndroidManifest.xml -->
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.usb.host" android:required="true" />
```

### USB Device Filter
```xml
<!-- Already configured in res/xml/device_filter.xml -->
<usb-device vendor-id="13770" product-id="4353" /> <!-- 0x35ca = 13770, 0x1101 = 4353 -->
```

---

## 3. Recommended Solution: UVCCamera Local Build

### Why UVCCamera?
- **Battle-tested:** 3,200+ GitHub stars, used in production apps
- **Proven compatibility:** CameraFi likely uses this or similar approach
- **Feature-rich:** Supports frame callbacks, MediaCodec, camera controls
- **Stable:** Last updated 2017 but still works perfectly
- **No external dependencies** (besides serenegiant:common)

### Architecture Overview

```
VitureHUD Project
├── app/                          # Our app module
│   ├── src/main/kotlin/com/viture/hud/
│   │   ├── MainActivity.kt       # Existing (to be updated)
│   │   └── VitureCamera.kt       # NEW: Camera helper class
│   └── build.gradle.kts          # Update dependencies
│
├── libuvccamera/                 # UVCCamera module (cloned)
│   ├── src/                      # Native + Java code
│   └── build.gradle              # Pre-configured
│
├── usbCameraCommon/              # UVCCamera common module (cloned)
│   ├── src/                      # Shared utilities
│   └── build.gradle              # Pre-configured
│
└── settings.gradle.kts           # UPDATE: Include new modules
```

---

## 4. Implementation Plan

### Phase 1: Setup & Integration (2-3 hours)

#### Step 1.1: Clone UVCCamera Repository
```bash
cd /home/bluekitty/Documents/Git/viture-utilities/android-app
git clone https://github.com/saki4510t/UVCCamera.git
```

**Expected Output:**
```
Cloning into 'UVCCamera'...
remote: Enumerating objects: 3245, done.
remote: Total 3245 (delta 0), reused 0 (delta 0)
Receiving objects: 100% (3245/3245), 8.42 MiB | 5.23 MiB/s, done.
```

#### Step 1.2: Verify NDK Installation
```bash
ls ~/Android/Sdk/ndk/
```

**If empty, install NDK:**
1. Open Android Studio
2. Tools → SDK Manager
3. SDK Tools tab
4. Check "NDK (Side by side)"
5. Click "Apply"
6. Recommended version: 25.2.9519653

#### Step 1.3: Configure UVCCamera Build
Create `/home/bluekitty/Documents/Git/viture-utilities/android-app/UVCCamera/local.properties`:
```properties
sdk.dir=/home/bluekitty/Android/Sdk
ndk.dir=/home/bluekitty/Android/Sdk/ndk/25.2.9519653
```

**⚠️ IMPORTANT:** Adjust paths to match actual SDK/NDK locations!

#### Step 1.4: Update Project Settings
Edit `/home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD/settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Add serenegiant's repository for common library
        maven { url = uri("https://raw.github.com/saki4510t/libcommon/master/repository/") }
    }
}

rootProject.name = "VitureHUD"
include(":app")

// Include UVCCamera modules
include(":libuvccamera")
include(":usbCameraCommon")

// Point to cloned UVCCamera modules
project(":libuvccamera").projectDir = file("../UVCCamera/libuvccamera")
project(":usbCameraCommon").projectDir = file("../UVCCamera/usbCameraCommon")
```

#### Step 1.5: Update App Dependencies
Edit `/home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD/app/build.gradle.kts`:

```kotlin
dependencies {
    // Existing dependencies
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.databinding:databinding-runtime:8.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // UVC Camera modules (local)
    implementation(project(":libuvccamera"))
    implementation(project(":usbCameraCommon"))

    // Required dependency for UVCCamera
    implementation("com.serenegiant:common:1.5.20") {
        exclude(module = "support-v4")
    }
}
```

#### Step 1.6: Sync and Test Build
```bash
cd /home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD
./gradlew clean
./gradlew build
```

**Expected Success Output:**
```
BUILD SUCCESSFUL in 45s
127 actionable tasks: 127 executed
```

**If build fails:**
- Check NDK path in local.properties
- Verify serenegiant repository is accessible
- Check Gradle sync messages for specific errors

---

### Phase 2: Camera Helper Class (2-3 hours)

#### Step 2.1: Create VitureCamera.kt
Location: `/home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD/app/src/main/kotlin/com/viture/hud/VitureCamera.kt`

```kotlin
package com.viture.hud

import android.content.Context
import android.hardware.usb.UsbDevice
import android.util.Log
import android.view.Surface
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera

/**
 * Helper class to manage Viture camera connection and capture.
 * Handles USB device detection, camera initialization, and frame capture.
 */
class VitureCamera(private val context: Context) {

    companion object {
        private const val TAG = "VitureCamera"

        // Viture Luma Pro camera identifiers
        private const val VITURE_VENDOR_ID = 0x35ca   // 13770 decimal
        private const val VITURE_PRODUCT_ID = 0x1101   // 4353 decimal

        // Camera specifications
        private const val CAMERA_WIDTH = 1920
        private const val CAMERA_HEIGHT = 1080
        private const val CAMERA_FPS_MIN = 1
        private const val CAMERA_FPS_MAX = 5
    }

    // USB monitoring and camera objects
    private var usbMonitor: USBMonitor? = null
    private var uvcCamera: UVCCamera? = null

    // Connection state
    private var isConnected = false

    // Callback for camera events
    var onCameraConnected: (() -> Unit)? = null
    var onCameraDisconnected: (() -> Unit)? = null
    var onCameraError: ((String) -> Unit)? = null

    /**
     * USB device connection listener.
     * Handles device attach/detach and permission requests.
     */
    private val deviceListener = object : USBMonitor.OnDeviceConnectListener {

        override fun onAttach(device: UsbDevice?) {
            device?.let {
                if (it.vendorId == VITURE_VENDOR_ID && it.productId == VITURE_PRODUCT_ID) {
                    Log.d(TAG, "Viture camera attached: VID=${it.vendorId}, PID=${it.productId}")
                    usbMonitor?.requestPermission(it)
                }
            }
        }

        override fun onConnect(
            device: UsbDevice?,
            ctrlBlock: USBMonitor.UsbControlBlock?,
            createNew: Boolean
        ) {
            Log.d(TAG, "Viture camera connected")
            ctrlBlock?.let {
                openCamera(it)
            }
        }

        override fun onDisconnect(device: UsbDevice?, ctrlBlock: USBMonitor.UsbControlBlock?) {
            Log.d(TAG, "Viture camera disconnected")
            closeCamera()
            isConnected = false
            onCameraDisconnected?.invoke()
        }

        override fun onDettach(device: UsbDevice?) {
            Log.d(TAG, "Viture camera detached")
        }

        override fun onCancel(device: UsbDevice?) {
            Log.w(TAG, "USB permission denied for Viture camera")
            onCameraError?.invoke("Camera permission denied")
        }
    }

    /**
     * Initialize USB monitoring.
     * Call this in Activity onCreate().
     */
    fun initialize() {
        if (usbMonitor == null) {
            usbMonitor = USBMonitor(context, deviceListener)
            usbMonitor?.register()
            Log.d(TAG, "USB monitor initialized")
        }
    }

    /**
     * Open and configure the camera.
     */
    private fun openCamera(ctrlBlock: USBMonitor.UsbControlBlock) {
        try {
            // Create UVCCamera instance
            uvcCamera = UVCCamera()
            uvcCamera?.open(ctrlBlock)

            Log.d(TAG, "Camera opened, configuring preview...")

            // Query supported sizes (optional - for debugging)
            uvcCamera?.supportedSizeList?.forEach { size ->
                Log.d(TAG, "Supported: ${size.width}x${size.height} type=${size.type}")
            }

            // Configure preview size and frame rate
            uvcCamera?.setPreviewSize(
                CAMERA_WIDTH,
                CAMERA_HEIGHT,
                CAMERA_FPS_MIN,
                CAMERA_FPS_MAX,
                UVCCamera.FRAME_FORMAT_MJPEG,  // Try MJPEG first
                UVCCamera.DEFAULT_BANDWIDTH
            )

            isConnected = true
            onCameraConnected?.invoke()

            Log.d(TAG, "Camera configured: ${CAMERA_WIDTH}x${CAMERA_HEIGHT} @ ${CAMERA_FPS_MAX}fps")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera", e)
            onCameraError?.invoke("Failed to open camera: ${e.message}")
            closeCamera()
        }
    }

    /**
     * Start camera preview on given surface.
     * @param surface Surface to render preview (from SurfaceView or TextureView)
     */
    fun startPreview(surface: Surface) {
        try {
            if (!isConnected) {
                Log.w(TAG, "Cannot start preview: camera not connected")
                return
            }

            uvcCamera?.setPreviewDisplay(surface)
            uvcCamera?.startPreview()
            Log.d(TAG, "Preview started")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start preview", e)
            onCameraError?.invoke("Failed to start preview: ${e.message}")
        }
    }

    /**
     * Stop camera preview.
     */
    fun stopPreview() {
        try {
            uvcCamera?.stopPreview()
            Log.d(TAG, "Preview stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping preview", e)
        }
    }

    /**
     * Capture a still image.
     * @param callback Receives JPEG image data
     */
    fun captureStillImage(callback: (ByteArray) -> Unit) {
        try {
            if (!isConnected) {
                Log.w(TAG, "Cannot capture: camera not connected")
                onCameraError?.invoke("Camera not connected")
                return
            }

            uvcCamera?.captureStillImage { data ->
                Log.d(TAG, "Image captured: ${data.size} bytes")
                callback(data)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture image", e)
            onCameraError?.invoke("Capture failed: ${e.message}")
        }
    }

    /**
     * Close camera and release resources.
     * Call this in Activity onDestroy().
     */
    private fun closeCamera() {
        try {
            uvcCamera?.stopPreview()
            uvcCamera?.close()
            uvcCamera?.destroy()
            uvcCamera = null
            Log.d(TAG, "Camera closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing camera", e)
        }
    }

    /**
     * Release all resources.
     * Call this in Activity onDestroy().
     */
    fun release() {
        closeCamera()
        usbMonitor?.unregister()
        usbMonitor?.destroy()
        usbMonitor = null
        isConnected = false
        Log.d(TAG, "VitureCamera released")
    }

    /**
     * Check if camera is currently connected.
     */
    fun isConnected(): Boolean = isConnected
}
```

**Key Design Decisions:**
1. **Singleton-like pattern** - One VitureCamera instance per Activity
2. **Callback-based** - Non-blocking, UI-friendly
3. **Error handling** - All exceptions caught and reported
4. **Logging** - Extensive logging for debugging
5. **Resource cleanup** - Proper release in destroy

---

### Phase 3: MainActivity Integration (2-3 hours)

#### Step 3.1: Update MainActivity.kt
Location: `/home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD/app/src/main/kotlin/com/viture/hud/MainActivity.kt`

Add imports:
```kotlin
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
```

Add class properties:
```kotlin
class MainActivity : Activity() {

    // Existing properties...

    // Camera components
    private var vitureCamera: VitureCamera? = null
    private var surfaceView: SurfaceView? = null
    private var isCameraMode = false

    // ... rest of code
}
```

Update onCreate():
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    targetGlassesDisplay()
    setContentView(R.layout.activity_main)

    // Initialize UI references
    textEditor = findViewById(R.id.textEditor)
    modeTextButton = findViewById(R.id.modeTextButton)
    modeCameraButton = findViewById(R.id.modeCameraButton)
    captureButton = findViewById(R.id.captureButton)
    settingsButton = findViewById(R.id.settingsButton)

    // Initialize camera
    vitureCamera = VitureCamera(this)
    vitureCamera?.apply {
        onCameraConnected = {
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Camera connected", Toast.LENGTH_SHORT).show()
                modeCameraButton.isEnabled = true
            }
        }
        onCameraDisconnected = {
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Camera disconnected", Toast.LENGTH_SHORT).show()
                modeCameraButton.isEnabled = false
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
    settingsButton.setOnClickListener { openSettings() }

    // Start in text mode
    switchToTextMode()
}
```

Add camera mode switching:
```kotlin
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
    captureButton.text = "Capture"
    captureButton.isEnabled = false
}

private fun switchToCameraMode() {
    if (!vitureCamera?.isConnected() == true) {
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
    captureButton.text = "Capture"
    captureButton.isEnabled = true
}

private fun createCameraSurface() {
    // Get content area
    val contentArea = findViewById<android.widget.FrameLayout>(R.id.contentArea)

    // Create SurfaceView for camera preview
    surfaceView = SurfaceView(this)
    surfaceView?.holder?.addCallback(object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {
            // Start camera preview when surface is ready
            vitureCamera?.startPreview(holder.surface)
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            // Handle surface changes if needed
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            // Stop preview when surface is destroyed
            vitureCamera?.stopPreview()
        }
    })

    // Add to content area
    contentArea.addView(surfaceView, android.widget.FrameLayout.LayoutParams(
        android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
        android.widget.FrameLayout.LayoutParams.MATCH_PARENT
    ))
}
```

Add capture handler:
```kotlin
private fun handleCapture() {
    if (!isCameraMode) {
        // In text mode - save text
        val text = textEditor.text.toString()
        if (text.isNotEmpty()) {
            saveTextNote(text)
        } else {
            Toast.makeText(this, "Nothing to save", Toast.LENGTH_SHORT).show()
        }
    } else {
        // In camera mode - capture image
        capturePhoto()
    }
}

private fun capturePhoto() {
    vitureCamera?.captureStillImage { imageData ->
        // Save on background thread
        Thread {
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val fileName = "viture_$timestamp.jpg"

                // Save to app-specific storage
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
                }

            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Failed to save photo: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
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
            }

        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Failed to save note: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }.start()
}
```

Add cleanup:
```kotlin
override fun onDestroy() {
    vitureCamera?.release()
    super.onDestroy()
}
```

---

### Phase 4: Testing & Validation (2-4 hours)

#### Test Plan

**4.1 Build Verification**
```bash
cd /home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD
./gradlew clean build
```
- [ ] Build completes without errors
- [ ] No lint warnings related to camera code
- [ ] APK generated successfully

**4.2 USB Permission Testing**
- [ ] Connect Viture glasses to phone
- [ ] Launch app
- [ ] USB permission dialog appears
- [ ] Grant permission with "Always allow" checked
- [ ] "Camera connected" toast appears

**4.3 Camera Preview Testing**
- [ ] Switch to Camera mode
- [ ] Camera preview displays in content area
- [ ] Preview is NOT blurry (1920x1080 resolution)
- [ ] Frame rate is acceptable (~5fps)
- [ ] No black screen or freeze

**4.4 Still Image Capture**
- [ ] In camera mode, tap "Capture" button
- [ ] "Photo saved" toast appears
- [ ] Image file exists in /Android/data/com.viture.hud/files/captures/
- [ ] Image is viewable and not corrupted
- [ ] Image is 1920x1080 resolution

**4.5 Mode Switching**
- [ ] Switch from text to camera mode - works
- [ ] Switch from camera to text mode - works
- [ ] Multiple switches - no crashes
- [ ] Camera preview stops when switching to text

**4.6 Edge Cases**
- [ ] Disconnect glasses during preview - no crash, graceful error
- [ ] Reconnect glasses - camera re-initializes
- [ ] Background app - camera releases properly
- [ ] Foreground app - camera resumes
- [ ] Multiple launch/close cycles - no memory leaks

**4.7 DeX Mode Testing**
- [ ] Launch app in DeX mode
- [ ] App appears on glasses display (drag if needed)
- [ ] Camera preview works in DeX
- [ ] Capture works in DeX
- [ ] No performance issues

#### Validation Checklist

**Code Quality:**
- [ ] No compiler warnings
- [ ] All exceptions caught and handled
- [ ] Proper resource cleanup (no leaks)
- [ ] Logging statements for debugging
- [ ] Comments on complex logic

**Performance:**
- [ ] Preview latency < 200ms
- [ ] Capture time < 2 seconds
- [ ] Memory usage stable (no leaks)
- [ ] CPU usage reasonable (<30% sustained)

**User Experience:**
- [ ] Clear error messages
- [ ] Responsive UI (no freezes)
- [ ] Visual feedback for actions
- [ ] Graceful degradation on errors

---

## 5. Fallback Plans

### Fallback #1: AndroidUSBCamera via Liferay

**If UVCCamera build fails**, try modern alternative:

Update `settings.gradle.kts`:
```kotlin
repositories {
    google()
    mavenCentral()
    maven { url = uri("https://repository.liferay.com/nexus/content/repositories/public/") }
    maven { url = uri("https://jitpack.io") }
}
```

Update `app/build.gradle.kts`:
```kotlin
implementation("com.github.jiangdongguo.AndroidUSBCamera:libausbc:3.3.3")
```

**Pros:**
- Simpler dependency management
- More modern (Kotlin-based)
- Recently updated (Feb 2025)

**Cons:**
- Relies on third-party repository
- Less community support than UVCCamera

**Estimated effort:** 4-6 hours

### Fallback #2: Direct AAR Integration

**If module inclusion fails**, use pre-built AARs:

```bash
# Build UVCCamera AARs
cd /home/bluekitty/Documents/Git/viture-utilities/android-app/UVCCamera
./gradlew :libuvccamera:assembleRelease
./gradlew :usbCameraCommon:assembleRelease

# Copy to project
cd /home/bluekitty/Documents/Git/viture-utilities/android-app/VitureHUD
mkdir -p app/libs
cp ../UVCCamera/libuvccamera/build/outputs/aar/libuvccamera-release.aar app/libs/
cp ../UVCCamera/usbCameraCommon/build/outputs/aar/usbCameraCommon-release.aar app/libs/
```

Update `build.gradle.kts`:
```kotlin
dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    implementation("com.serenegiant:common:1.5.20")
}
```

**Estimated effort:** 2-3 hours

### Fallback #3: Native Implementation

**Only if all libraries fail** (not recommended):

- Implement USB Host API directly
- Parse UVC protocol manually
- Handle frame decoding

**Estimated effort:** 1-2 weeks
**Risk:** High (complex, many edge cases)

---

## 6. Known Issues & Gotchas

### Issue 1: NDK Version Compatibility
**Symptom:** Native build errors
**Solution:** Use NDK 21.x - 25.x, avoid NDK 26+ (may have breaking changes)

### Issue 2: Frame Format Mismatch
**Symptom:** Black screen or corrupted preview
**Solution:** Try different formats:
```kotlin
UVCCamera.FRAME_FORMAT_MJPEG  // Try first
UVCCamera.FRAME_FORMAT_YUYV   // Fallback
```

### Issue 3: Bandwidth Settings
**Symptom:** "Buffer overflow" errors in logcat
**Solution:**
```kotlin
uvcCamera?.setPreviewSize(
    width, height, minFps, maxFps,
    format,
    UVCCamera.BANDWIDTH_AUTO  // Instead of DEFAULT_BANDWIDTH
)
```

### Issue 4: Surface Lifecycle
**Symptom:** Preview works first time, fails on re-open
**Solution:** Always wait for `surfaceCreated()` callback before starting preview

### Issue 5: DeX Mode USB Timing
**Symptom:** Camera not detected in DeX mode
**Solution:** Add retry logic with 500ms delay after USB attach

### Issue 6: Memory Leaks
**Symptom:** App slows down after multiple captures
**Solution:** Ensure `destroy()` called on both UVCCamera and USBMonitor

---

## 7. Success Criteria

### Minimum Viable Product (MVP)
- [x] App builds without errors
- [ ] Camera detected and connected
- [ ] Preview displays at 1920x1080
- [ ] Still image capture works
- [ ] No crashes on normal usage

### Production Ready
- [ ] All edge cases handled gracefully
- [ ] Memory leaks fixed
- [ ] Performance optimized (<200ms latency)
- [ ] User-friendly error messages
- [ ] Tested in DeX mode extensively

### Future Enhancements (Phase 2+)
- [ ] GPS location tagging on capture
- [ ] Video recording
- [ ] Camera controls (brightness, contrast)
- [ ] Burst mode capture
- [ ] Cloud backup integration

---

## 8. Timeline & Resources

### Estimated Timeline
| Phase | Duration | Dependencies |
|-------|----------|--------------|
| Setup & Integration | 2-3 hours | NDK installed |
| Camera Helper Class | 2-3 hours | Phase 1 complete |
| MainActivity Updates | 2-3 hours | Phase 2 complete |
| Testing & Validation | 2-4 hours | Phase 3 complete |
| **Total** | **8-13 hours** | ~1-2 days |

### Required Resources
- Android Studio with NDK
- Samsung Galaxy S22 Ultra (testing device)
- Viture Luma Pro glasses
- USB-C cable
- Stable internet (for dependency downloads)

### Team Roles
- **Claude:** Architecture, planning, code generation
- **Gemini:** Code review, best practices, optimization suggestions
- **Codex:** Lint checking, syntax validation, error detection
- **Developer:** Integration, testing, debugging

---

## 9. References

### Documentation
- **UVCCamera GitHub:** https://github.com/saki4510t/UVCCamera
- **Android USB Host API:** https://developer.android.com/develop/connectivity/usb/host
- **UVC Specification:** USB.org USB Video Class 1.5
- **Samsung DeX Guidelines:** https://developer.samsung.com/samsung-dex

### Sample Code
- UVCCamera sample apps: `/UVCCamera/usbCameraTest*/`
- USB Host API examples: Android developer samples

### Community Support
- Stack Overflow: `[android] [usb] [uvc]`
- UVCCamera Issues: https://github.com/saki4510t/UVCCamera/issues
- Viture Developer Discord: (if exists)

---

## 10. Review Notes

### For Gemini (Code Review)
Please review:
- **VitureCamera.kt** - Are there any Kotlin best practices violations?
- **Error handling** - Are all exceptions properly caught?
- **Resource cleanup** - Any potential memory leaks?
- **Thread safety** - Any concurrency issues?
- **API usage** - Are Android APIs used correctly?

### For Codex (Lint/Quality)
Please check:
- **Syntax errors** - Any typos or syntax issues?
- **Unused imports** - Any cleanup needed?
- **Naming conventions** - Follow Kotlin style guide?
- **Deprecated APIs** - Any outdated API usage?
- **Type safety** - Any unsafe casts or nullability issues?

---

## 11. Next Steps

**Immediate (Today):**
1. Get team review feedback on this plan
2. Verify NDK is installed
3. Clone UVCCamera repository
4. Attempt Phase 1 setup

**This Week:**
1. Complete camera integration (Phases 1-3)
2. Comprehensive testing (Phase 4)
3. Fix any issues discovered
4. Deploy to phone for real-world testing

**Next Week:**
1. Add GPS location tagging (if desired)
2. Implement video recording (if desired)
3. Polish UI and error handling
4. Performance optimization

---

## Appendix A: Build Configuration Files

### settings.gradle.kts (Complete)
```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://raw.github.com/saki4510t/libcommon/master/repository/") }
    }
}

rootProject.name = "VitureHUD"
include(":app")
include(":libuvccamera")
include(":usbCameraCommon")

project(":libuvccamera").projectDir = file("../UVCCamera/libuvccamera")
project(":usbCameraCommon").projectDir = file("../UVCCamera/usbCameraCommon")
```

### app/build.gradle.kts (Dependencies Section)
```kotlin
dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.databinding:databinding-runtime:8.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // UVC Camera modules
    implementation(project(":libuvccamera"))
    implementation(project(":usbCameraCommon"))
    implementation("com.serenegiant:common:1.5.20") {
        exclude(module = "support-v4")
    }
}
```

---

**Document Version:** 1.0
**Last Updated:** 2026-01-14
**Status:** Ready for team review and implementation
