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

        // The actual camera is a SEPARATE USB device from the Viture control interface
        // Viture control: 35ca:1101 (vendor-specific, class 00) - NOT the camera
        // Actual camera:  0c45:636b (Sonix UVC camera chip, class 0E)
        private const val CAMERA_VENDOR_ID = 0x0c45    // Sonix camera chip (3141 decimal)
        private const val CAMERA_PRODUCT_ID = 0x636b   // Camera product ID (25451 decimal)

        // Keep Viture control IDs for reference/future control features
        private const val VITURE_CONTROL_VID = 0x35ca  // Viture control interface
        private const val VITURE_CONTROL_PID = 0x1101  // Not the camera!

        // Camera specifications
        private const val CAMERA_WIDTH = 1920
        private const val CAMERA_HEIGHT = 1080
        private const val CAMERA_FPS_MIN = 1
        private const val CAMERA_FPS_MAX = 5
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
                if (it.vendorId == CAMERA_VENDOR_ID && it.productId == CAMERA_PRODUCT_ID) {
                    Log.d(TAG, "Sonix camera attached: VID=0x${it.vendorId.toString(16)}, PID=0x${it.productId.toString(16)}")

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
            Log.d(TAG, "Camera2 helper initialized (standby - will use if UVC fails)")

            // NOTE: Do NOT open camera eagerly here!
            // Camera2Helper.openCamera() can create zombie processes if called
            // before the Activity is fully ready or if it fails to initialize.
            // Let USBMonitor detect the camera and use UVC first.
            // Camera2 is only used as fallback if UVC returns error -50.
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
     * Now targeting the actual Sonix UVC camera (0c45:636b) instead of the
     * Viture control interface (35ca:1101).
     */
    private fun openCamera(ctrlBlock: USBMonitor.UsbControlBlock) {
        try {
            Log.d(TAG, "Opening camera with control block: ${ctrlBlock.deviceName}")

            // Log device info for debugging
            val device = ctrlBlock.device
            Log.d(TAG, "USB Device: VID=0x${device.vendorId.toString(16)}, PID=0x${device.productId.toString(16)}")
            Log.d(TAG, "  Interface count: ${device.interfaceCount}")
            for (i in 0 until device.interfaceCount) {
                val intf = device.getInterface(i)
                Log.d(TAG, "  Interface $i: class=${intf.interfaceClass}, subclass=${intf.interfaceSubclass}")
            }

            // Create UVCCamera instance and open
            uvcCamera = UVCCamera()
            uvcCamera?.open(ctrlBlock)

            Log.d(TAG, "Camera opened successfully, querying formats...")

            // Query supported sizes
            val supportedSizes = uvcCamera?.supportedSizeList
            if (supportedSizes.isNullOrEmpty()) {
                Log.w(TAG, "Camera reports NO supported sizes")
            } else {
                Log.d(TAG, "Camera reports ${supportedSizes.size} supported size/format combinations:")
                supportedSizes.take(10).forEach { size ->
                    Log.d(TAG, "  ${size.width}x${size.height} type=${size.type}")
                }
                if (supportedSizes.size > 10) {
                    Log.d(TAG, "  ... and ${supportedSizes.size - 10} more")
                }
            }

            // Configure preview format
            val format = detectBestFormat()
            try {
                uvcCamera?.setPreviewSize(
                    CAMERA_WIDTH,
                    CAMERA_HEIGHT,
                    CAMERA_FPS_MIN,
                    CAMERA_FPS_MAX,
                    format,
                    UVCCamera.DEFAULT_BANDWIDTH
                )
                Log.d(TAG, "Preview configured: ${CAMERA_WIDTH}x${CAMERA_HEIGHT} format=$format")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to set ${CAMERA_WIDTH}x${CAMERA_HEIGHT}, trying fallback formats: ${e.message}")

                // Try other common resolutions
                val fallbackSizes = listOf(
                    1280 to 720,
                    640 to 480,
                    320 to 240
                )

                var configured = false
                for ((width, height) in fallbackSizes) {
                    try {
                        uvcCamera?.setPreviewSize(
                            width, height,
                            CAMERA_FPS_MIN, CAMERA_FPS_MAX,
                            UVCCamera.FRAME_FORMAT_MJPEG,
                            UVCCamera.DEFAULT_BANDWIDTH
                        )
                        Log.d(TAG, "Preview configured with fallback: ${width}x${height} MJPEG")
                        configured = true
                        break
                    } catch (e2: Exception) {
                        Log.w(TAG, "  Failed ${width}x${height}: ${e2.message}")
                    }
                }

                if (!configured) {
                    throw Exception("Could not configure any preview format")
                }
            }

            isConnected = true
            useCamera2 = false
            onCameraConnected?.invoke()

            Log.d(TAG, "Camera ready (UVC)")

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
     * Converts raw NV21 frame data to JPEG.
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
                // Use UVC API with NV21 format for JPEG conversion
                Log.d(TAG, "Capturing image (UVC)")
                var captured = false
                uvcCamera?.setFrameCallback({ frame ->
                    if (!captured) {
                        captured = true
                        // Convert ByteBuffer to ByteArray
                        val nv21Data = ByteArray(frame.remaining())
                        frame.get(nv21Data)
                        Log.d(TAG, "Frame captured (UVC): ${nv21Data.size} bytes NV21")

                        // Clear callback after capturing
                        uvcCamera?.setFrameCallback(null, UVCCamera.PIXEL_FORMAT_NV21)

                        // Convert NV21 to JPEG using Android's YuvImage
                        try {
                            val yuvImage = android.graphics.YuvImage(
                                nv21Data,
                                android.graphics.ImageFormat.NV21,
                                CAMERA_WIDTH,
                                CAMERA_HEIGHT,
                                null
                            )
                            val jpegStream = java.io.ByteArrayOutputStream()
                            yuvImage.compressToJpeg(
                                android.graphics.Rect(0, 0, CAMERA_WIDTH, CAMERA_HEIGHT),
                                90,  // JPEG quality
                                jpegStream
                            )
                            val jpegData = jpegStream.toByteArray()
                            Log.d(TAG, "Image converted to JPEG: ${jpegData.size} bytes")
                            callback(jpegData)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to convert to JPEG", e)
                            onCameraError?.invoke("JPEG conversion failed: ${e.message}")
                        }
                    }
                }, UVCCamera.PIXEL_FORMAT_NV21)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture image", e)
            onCameraError?.invoke("Capture failed: ${e.message}")
        }
    }

    /**
     * Capture a single frame as a still image (coroutine version).
     * Converts raw NV21 frame data to JPEG.
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
                // Use UVC API with NV21 format for JPEG conversion
                Log.d(TAG, "Capturing image suspend (UVC)")
                var captured = false
                uvcCamera?.setFrameCallback({ frame ->
                    if (!captured) {
                        captured = true
                        val nv21Data = ByteArray(frame.remaining())
                        frame.get(nv21Data)
                        Log.d(TAG, "Frame captured suspend (UVC): ${nv21Data.size} bytes NV21")

                        // Clear callback after capturing
                        uvcCamera?.setFrameCallback(null, UVCCamera.PIXEL_FORMAT_NV21)

                        // Convert NV21 to JPEG
                        try {
                            val yuvImage = android.graphics.YuvImage(
                                nv21Data,
                                android.graphics.ImageFormat.NV21,
                                CAMERA_WIDTH,
                                CAMERA_HEIGHT,
                                null
                            )
                            val jpegStream = java.io.ByteArrayOutputStream()
                            yuvImage.compressToJpeg(
                                android.graphics.Rect(0, 0, CAMERA_WIDTH, CAMERA_HEIGHT),
                                90,
                                jpegStream
                            )
                            val jpegData = jpegStream.toByteArray()
                            Log.d(TAG, "Image converted to JPEG: ${jpegData.size} bytes")
                            continuation.resume(jpegData)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to convert to JPEG", e)
                            continuation.resumeWithException(e)
                        }
                    }
                }, UVCCamera.PIXEL_FORMAT_NV21)
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
