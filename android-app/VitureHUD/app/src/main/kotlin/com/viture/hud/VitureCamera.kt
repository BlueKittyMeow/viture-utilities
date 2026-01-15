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
     * Open and configure the camera.
     */
    private fun openCamera(ctrlBlock: USBMonitor.UsbControlBlock) {
        try {
            Log.d(TAG, "Opening camera with control block: ${ctrlBlock.deviceName}")

            // Create UVCCamera instance
            uvcCamera = UVCCamera()
            uvcCamera?.open(ctrlBlock)

            Log.d(TAG, "Camera opened successfully, configuring preview...")

            // Query supported sizes (for debugging)
            uvcCamera?.supportedSizeList?.forEach { size ->
                Log.d(TAG, "Supported: ${size.width}x${size.height} type=${size.type}")
            }

            // Detect best format
            val format = detectBestFormat()

            // Configure preview size and frame rate
            uvcCamera?.setPreviewSize(
                CAMERA_WIDTH,
                CAMERA_HEIGHT,
                CAMERA_FPS_MIN,
                CAMERA_FPS_MAX,
                format,
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
     * Uses retry logic for DeX mode compatibility.
     * @param surface Surface to render preview (from SurfaceView or TextureView)
     */
    fun startPreview(surface: Surface) {
        startPreviewWithRetry(surface, attempts = 3)
    }

    /**
     * Start preview with retry logic for DeX mode timing issues.
     */
    private fun startPreviewWithRetry(surface: Surface, attempts: Int) {
        try {
            if (!isConnected) {
                Log.w(TAG, "Cannot start preview: camera not connected")
                return
            }

            uvcCamera?.setPreviewDisplay(surface)
            uvcCamera?.startPreview()
            Log.d(TAG, "Preview started successfully")

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
            uvcCamera?.stopPreview()
            Log.d(TAG, "Preview stopped")
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

            // Set one-shot frame callback to capture a single frame
            // Use PIXEL_FORMAT_RAW to get raw MJPEG data without conversion
            var captured = false
            uvcCamera?.setFrameCallback({ frame ->
                if (!captured) {
                    captured = true
                    // Convert ByteBuffer to ByteArray
                    val data = ByteArray(frame.remaining())
                    frame.get(data)
                    Log.d(TAG, "Image captured: ${data.size} bytes")

                    // Clear callback after capturing
                    uvcCamera?.setFrameCallback(null, UVCCamera.PIXEL_FORMAT_RAW)

                    callback(data)
                }
            }, UVCCamera.PIXEL_FORMAT_RAW)

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

            // Set one-shot frame callback
            // Use PIXEL_FORMAT_RAW to get raw MJPEG data without conversion
            var captured = false
            uvcCamera?.setFrameCallback({ frame ->
                if (!captured) {
                    captured = true
                    // Convert ByteBuffer to ByteArray
                    val data = ByteArray(frame.remaining())
                    frame.get(data)
                    Log.d(TAG, "Image captured (suspend): ${data.size} bytes")

                    // Clear callback after capturing
                    uvcCamera?.setFrameCallback(null, UVCCamera.PIXEL_FORMAT_RAW)

                    continuation.resume(data)
                }
            }, UVCCamera.PIXEL_FORMAT_RAW)

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
