package com.viture.hud

import android.content.Context
import android.hardware.usb.UsbDevice
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import com.serenegiant.usb.IFrameCallback
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.min

/**
 * Helper class to manage Viture camera connection and capture.
 * Handles USB device detection, camera initialization, and frame capture.
 *
 * Enhanced with:
 * - USB permission persistence
 * - Automatic frame format detection
 * - DeX mode retry logic
 * - Both callback and coroutine APIs
 * - Camera2 API fallback for non-UVC devices
 *
 * Strategy:
 * 1. Try UVC approach first (for standard UVC cameras)
 * 2. If UVC fails with error -50 (non-UVC device), automatically fall back to Camera2 API
 * 3. Camera2 API uses Android's native HAL/V4L2 drivers for vendor-specific cameras
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

        /**
         * Convert byte array to hex string for debugging
         */
        private fun ByteArray.toHexString(): String {
            return joinToString(" ") { byte -> "%02X".format(byte) }
        }
    }

    // USB monitoring and camera objects (UVC approach)
    private var usbMonitor: USBMonitor? = null
    private var uvcCamera: UVCCamera? = null

    // Camera2 helper (fallback for non-UVC devices)
    private var camera2Helper: Camera2Helper? = null

    // Connection state
    private var isConnected = false
    private var useCamera2 = false  // Track which API is being used

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

                    // Request permission (will trigger onConnect immediately if already granted)
                    Log.d(TAG, "Requesting USB permission...")
                    usbMonitor?.requestPermission(it)
                }
            }
        }

        override fun onConnect(
            device: UsbDevice?,
            ctrlBlock: USBMonitor.UsbControlBlock?,
            createNew: Boolean
        ) {
            device?.let { dev ->
                Log.d(TAG, "Viture camera connected: VID=${dev.vendorId}, PID=${dev.productId}, Device=${dev.deviceName}")
            }
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
     * Initialize USB monitoring and Camera2 API.
     * Call this in Activity onCreate().
     */
    fun initialize() {
        // Initialize UVC approach (try this first for standard UVC cameras)
        if (usbMonitor == null) {
            usbMonitor = USBMonitor(context, deviceListener)
            usbMonitor?.register()
            Log.d(TAG, "USB monitor initialized")
        }

        // Initialize Camera2 fallback (for vendor-specific cameras like Viture)
        if (camera2Helper == null) {
            camera2Helper = Camera2Helper(context).apply {
                onCameraConnected = {
                    isConnected = true
                    this@VitureCamera.onCameraConnected?.invoke()
                }
                onCameraDisconnected = {
                    isConnected = false
                    this@VitureCamera.onCameraDisconnected?.invoke()
                }
                onCameraError = { error ->
                    this@VitureCamera.onCameraError?.invoke(error)
                }
                initialize()
            }
            Log.d(TAG, "Camera2 helper initialized")

            // If external camera is available via Camera2, use that approach
            if (camera2Helper?.isExternalCameraAvailable() == true) {
                Log.d(TAG, "External USB camera detected via Camera2 API - using Camera2 approach")
                useCamera2 = true
                camera2Helper?.openCamera()
            }
        }
    }

    /**
     * Detect best video format for the camera.
     * Tries MJPEG first (preferred), falls back to YUYV.
     */
    private fun detectBestFormat(): Int {
        val supportedFormats = uvcCamera?.supportedSizeList
            ?.filter { it.width == CAMERA_WIDTH && it.height == CAMERA_HEIGHT }
            ?.map { it.type }
            ?.distinct()

        Log.d(TAG, "Supported formats at ${CAMERA_WIDTH}x${CAMERA_HEIGHT}: $supportedFormats")

        return when {
            supportedFormats?.contains(UVCCamera.FRAME_FORMAT_MJPEG) == true -> {
                Log.d(TAG, "Using MJPEG format (preferred)")
                UVCCamera.FRAME_FORMAT_MJPEG
            }
            supportedFormats?.contains(UVCCamera.FRAME_FORMAT_YUYV) == true -> {
                Log.d(TAG, "Using YUYV format (fallback)")
                UVCCamera.FRAME_FORMAT_YUYV
            }
            else -> {
                Log.w(TAG, "No preferred format found, using MJPEG as default")
                UVCCamera.FRAME_FORMAT_MJPEG
            }
        }
    }

    /**
     * Open and configure the camera (UVC approach).
     */
    private fun openCamera(ctrlBlock: USBMonitor.UsbControlBlock) {
        try {
            Log.d(TAG, "Opening camera with control block: ${ctrlBlock.deviceName}")

            // Create UVCCamera instance
            uvcCamera = UVCCamera()
            uvcCamera?.open(ctrlBlock)

            Log.d(TAG, "Camera opened successfully, configuring preview...")

            // Query ALL supported sizes (for debugging)
            val supportedSizes = uvcCamera?.supportedSizeList
            if (supportedSizes.isNullOrEmpty()) {
                Log.w(TAG, "Camera reports NO supported sizes - this is unusual but may still work")
            } else {
                Log.d(TAG, "Camera reports ${supportedSizes.size} supported size/format combinations:")
                supportedSizes.forEach { size ->
                    Log.d(TAG, "  ${size.width}x${size.height} type=${size.type}")
                }
            }

            // PHASE 1 TEST: Try direct streaming without format negotiation
            // Hypothesis: Camera might work with default format, no setPreviewSize() needed
            Log.d(TAG, "=== PHASE 1: Attempting direct streaming without format negotiation ===")

            var directStreamingSuccess = false
            var frameCount = 0
            val maxTestFrames = 5  // Capture up to 5 frames for testing

            try {
                // Set frame callback to capture any incoming data
                uvcCamera?.setFrameCallback({ frame ->
                    frameCount++
                    val size = frame.remaining()

                    // Capture first 16 bytes for analysis
                    val headerSize = min(16, size)
                    val header = ByteArray(headerSize)
                    frame.get(header)
                    frame.rewind()  // Reset position for other uses

                    Log.d(TAG, "PHASE 1 FRAME #$frameCount: size=${size} bytes, header=${header.toHexString()}")

                    // Check for MJPEG signature (FF D8 FF)
                    if (size > 3 && header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() && header[2] == 0xFF.toByte()) {
                        Log.d(TAG, "  ✅ MJPEG frame detected! (FF D8 FF signature)")
                    }

                    // Check for YUYV pattern (alternating luma/chroma)
                    if (size >= 4) {
                        Log.d(TAG, "  First 4 bytes (potential YUYV): Y=${header[0].toInt() and 0xFF} Cb=${header[1].toInt() and 0xFF} Y=${header[2].toInt() and 0xFF} Cr=${header[3].toInt() and 0xFF}")
                    }

                    // Stop capturing after test frames
                    if (frameCount >= maxTestFrames) {
                        Log.d(TAG, "PHASE 1: Captured $maxTestFrames test frames, stopping callback")
                        uvcCamera?.setFrameCallback(null, UVCCamera.PIXEL_FORMAT_RAW)
                    }

                }, UVCCamera.PIXEL_FORMAT_RAW)

                Log.d(TAG, "PHASE 1: Frame callback set, attempting to start preview...")

                // Try to start preview without calling setPreviewSize()
                // This might work if camera auto-negotiates format
                uvcCamera?.startPreview()

                // Wait a moment to see if frames arrive
                Thread.sleep(1000)

                if (frameCount > 0) {
                    directStreamingSuccess = true
                    Log.d(TAG, "✅ PHASE 1 SUCCESS! Camera streaming without format negotiation!")
                    Log.d(TAG, "   Received $frameCount frames in test period")
                } else {
                    Log.w(TAG, "❌ PHASE 1 FAILED: No frames received after startPreview()")
                }

            } catch (e: Exception) {
                Log.w(TAG, "❌ PHASE 1 FAILED: Exception during direct streaming test: ${e.message}")
                directStreamingSuccess = false
            }

            // If Phase 1 failed, try traditional format negotiation
            if (!directStreamingSuccess) {
                Log.d(TAG, "=== Falling back to traditional format negotiation ===")

                try {
                    // Try 1920x1080 MJPEG
                    uvcCamera?.setPreviewSize(
                        CAMERA_WIDTH,
                        CAMERA_HEIGHT,
                        CAMERA_FPS_MIN,
                        CAMERA_FPS_MAX,
                        UVCCamera.FRAME_FORMAT_MJPEG,
                        UVCCamera.DEFAULT_BANDWIDTH
                    )
                    Log.d(TAG, "Preview configured: ${CAMERA_WIDTH}x${CAMERA_HEIGHT} MJPEG")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to set 1920x1080 MJPEG, trying YUYV: ${e.message}")
                    try {
                        // Try 1920x1080 YUYV
                        uvcCamera?.setPreviewSize(
                            CAMERA_WIDTH,
                            CAMERA_HEIGHT,
                            CAMERA_FPS_MIN,
                            CAMERA_FPS_MAX,
                            UVCCamera.FRAME_FORMAT_YUYV,
                            UVCCamera.DEFAULT_BANDWIDTH
                        )
                        Log.d(TAG, "Preview configured: ${CAMERA_WIDTH}x${CAMERA_HEIGHT} YUYV")
                    } catch (e2: Exception) {
                        Log.e(TAG, "Failed to set any preview format")
                        Log.e(TAG, "=== PHASE 1 RESULT: Direct streaming did not work ===")
                        Log.e(TAG, "=== NEXT STEP: Proceed to Phase 2 (USB traffic sniffing) ===")
                        throw e2
                    }
                }
            }

            isConnected = true
            useCamera2 = false
            onCameraConnected?.invoke()

            Log.d(TAG, "Camera configured (UVC): ${CAMERA_WIDTH}x${CAMERA_HEIGHT} @ ${CAMERA_FPS_MAX}fps")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera via UVC", e)

            // Check if this is error -50 (INVALID_DEVICE - non-UVC camera)
            if (e.message?.contains("result=-50") == true) {
                Log.d(TAG, "UVC error -50 detected (non-UVC device), falling back to Camera2 API")

                // Fall back to Camera2 API
                if (camera2Helper?.isExternalCameraAvailable() == true) {
                    Log.d(TAG, "Attempting Camera2 API fallback...")
                    useCamera2 = true
                    camera2Helper?.openCamera()
                } else {
                    onCameraError?.invoke("Camera not compatible with UVC and not detected via Camera2")
                    closeCamera()
                }
            } else {
                onCameraError?.invoke("Failed to open camera: ${e.message}")
                closeCamera()
            }
        }
    }

    /**
     * Start camera preview on given surface.
     * Uses retry logic for DeX mode compatibility.
     * @param surface Surface to render preview (from SurfaceView or TextureView)
     */
    fun startPreview(surface: Surface) {
        if (useCamera2) {
            // Use Camera2 API
            Log.d(TAG, "Starting preview (Camera2)")
            camera2Helper?.startPreview(surface)
        } else {
            // Use UVC API with retry logic
            Log.d(TAG, "Starting preview (UVC)")
            startPreviewWithRetry(surface, attempts = 3)
        }
    }

    /**
     * Start preview with retry logic for DeX mode timing issues (UVC only).
     */
    private fun startPreviewWithRetry(surface: Surface, attempts: Int) {
        try {
            if (!isConnected) {
                Log.w(TAG, "Cannot start preview: camera not connected")
                return
            }

            uvcCamera?.setPreviewDisplay(surface)
            uvcCamera?.startPreview()
            Log.d(TAG, "Preview started successfully (UVC)")

        } catch (e: Exception) {
            if (attempts > 0) {
                Log.w(TAG, "Preview start failed, retrying... ($attempts attempts left)")
                // Retry after delay (DeX mode may need time)
                Handler(Looper.getMainLooper()).postDelayed({
                    startPreviewWithRetry(surface, attempts - 1)
                }, 500) // 500ms delay
            } else {
                Log.e(TAG, "Failed to start preview after all retries", e)
                onCameraError?.invoke("Failed to start preview: ${e.message}")
            }
        }
    }

    /**
     * Stop camera preview.
     */
    fun stopPreview() {
        try {
            if (useCamera2) {
                camera2Helper?.stopPreview()
                Log.d(TAG, "Preview stopped (Camera2)")
            } else {
                uvcCamera?.stopPreview()
                Log.d(TAG, "Preview stopped (UVC)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping preview", e)
        }
    }

    /**
     * Capture a single frame as a still image (callback version).
     * Uses setFrameCallback to capture one frame in JPEG format.
     * @param callback Receives JPEG image data
     */
    fun captureStillImage(callback: (ByteArray) -> Unit) {
        try {
            if (!isConnected) {
                Log.w(TAG, "Cannot capture: camera not connected")
                onCameraError?.invoke("Camera not connected")
                return
            }

            if (useCamera2) {
                // Use Camera2 API
                Log.d(TAG, "Capturing image (Camera2)")
                camera2Helper?.captureStillImage(callback)
            } else {
                // Use UVC API
                Log.d(TAG, "Capturing image (UVC)")
                // Set one-shot frame callback to capture a single frame
                // Use PIXEL_FORMAT_RAW to get raw MJPEG data without conversion
                var captured = false
                uvcCamera?.setFrameCallback({ frame ->
                    if (!captured) {
                        captured = true
                        // Convert ByteBuffer to ByteArray
                        val data = ByteArray(frame.remaining())
                        frame.get(data)
                        Log.d(TAG, "Image captured (UVC): ${data.size} bytes")

                        // Clear callback after capturing
                        uvcCamera?.setFrameCallback(null, UVCCamera.PIXEL_FORMAT_RAW)

                        callback(data)
                    }
                }, UVCCamera.PIXEL_FORMAT_RAW)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture image", e)
            onCameraError?.invoke("Capture failed: ${e.message}")
        }
    }

    /**
     * Capture a single frame as a still image (coroutine version).
     * Uses setFrameCallback to capture one frame in JPEG format.
     * @return JPEG image data
     * @throws IllegalStateException if camera not connected
     */
    suspend fun captureStillImageSuspend(): ByteArray = suspendCancellableCoroutine { continuation ->
        try {
            if (!isConnected) {
                continuation.resumeWithException(IllegalStateException("Camera not connected"))
                return@suspendCancellableCoroutine
            }

            if (useCamera2) {
                // Use Camera2 API - delegate to callback version
                Log.d(TAG, "Capturing image suspend (Camera2)")
                camera2Helper?.captureStillImage { data ->
                    continuation.resume(data)
                }
            } else {
                // Use UVC API
                Log.d(TAG, "Capturing image suspend (UVC)")
                // Set one-shot frame callback
                // Use PIXEL_FORMAT_RAW to get raw MJPEG data without conversion
                var captured = false
                uvcCamera?.setFrameCallback({ frame ->
                    if (!captured) {
                        captured = true
                        // Convert ByteBuffer to ByteArray
                        val data = ByteArray(frame.remaining())
                        frame.get(data)
                        Log.d(TAG, "Image captured suspend (UVC): ${data.size} bytes")

                        // Clear callback after capturing
                        uvcCamera?.setFrameCallback(null, UVCCamera.PIXEL_FORMAT_RAW)

                        continuation.resume(data)
                    }
                }, UVCCamera.PIXEL_FORMAT_RAW)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture image (suspend)", e)
            continuation.resumeWithException(e)
        }
    }

    /**
     * Close camera and release resources.
     */
    private fun closeCamera() {
        try {
            if (useCamera2) {
                // Camera2Helper has its own close logic in release()
                Log.d(TAG, "Camera closed (Camera2)")
            } else {
                uvcCamera?.stopPreview()
                uvcCamera?.close()
                uvcCamera?.destroy()
                uvcCamera = null
                Log.d(TAG, "Camera closed (UVC)")
            }
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
        camera2Helper?.release()
        camera2Helper = null
        isConnected = false
        useCamera2 = false
        Log.d(TAG, "VitureCamera released")
    }

    /**
     * Check if camera is currently connected.
     */
    fun isConnected(): Boolean = isConnected
}
