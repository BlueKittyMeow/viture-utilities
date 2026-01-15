package com.viture.hud

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream

/**
 * Camera2 API implementation for Viture camera access.
 *
 * This approach uses Android's native Camera2 API instead of the UVCCamera library.
 * Theory: CameraFi works because Android's HAL/V4L2 drivers support vendor-specific
 * cameras through the standard Camera2 API, even when they don't advertise as UVC.
 *
 * Benefits:
 * - Uses official Android APIs (stable, supported)
 * - Works with any camera Android supports (including vendor-specific)
 * - Future-proof across Android versions
 * - Simpler than UVC protocol implementation
 */
class Camera2Helper(private val context: Context) {

    companion object {
        private const val TAG = "Camera2Helper"
        private const val IMAGE_WIDTH = 1920
        private const val IMAGE_HEIGHT = 1080
        private const val MAX_IMAGES = 2
    }

    // Camera2 components
    private var cameraManager: CameraManager? = null
    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null

    // Background thread for camera operations
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    // External USB camera ID (discovered during enumeration)
    private var externalCameraId: String? = null

    // Connection state
    private var isConnected = false
    private var isPreviewActive = false

    // Callbacks
    var onCameraConnected: (() -> Unit)? = null
    var onCameraDisconnected: (() -> Unit)? = null
    var onCameraError: ((String) -> Unit)? = null

    /**
     * Camera device state callback.
     */
    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            Log.d(TAG, "Camera device opened: ${camera.id}")
            cameraDevice = camera
            isConnected = true
            onCameraConnected?.invoke()
        }

        override fun onDisconnected(camera: CameraDevice) {
            Log.d(TAG, "Camera device disconnected: ${camera.id}")
            closeCamera()
            isConnected = false
            onCameraDisconnected?.invoke()
        }

        override fun onError(camera: CameraDevice, error: Int) {
            val errorMsg = when (error) {
                ERROR_CAMERA_IN_USE -> "Camera in use"
                ERROR_MAX_CAMERAS_IN_USE -> "Max cameras in use"
                ERROR_CAMERA_DISABLED -> "Camera disabled"
                ERROR_CAMERA_DEVICE -> "Camera device error"
                ERROR_CAMERA_SERVICE -> "Camera service error"
                else -> "Unknown error: $error"
            }
            Log.e(TAG, "Camera device error: $errorMsg")
            closeCamera()
            isConnected = false
            onCameraError?.invoke(errorMsg)
        }
    }

    /**
     * Initialize Camera2 system and enumerate cameras.
     */
    fun initialize() {
        try {
            // Check camera permission
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Camera permission not granted")
                onCameraError?.invoke("Camera permission not granted")
                return
            }

            // Initialize CameraManager
            cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

            // Start background thread for camera operations
            startBackgroundThread()

            // Enumerate cameras to find external USB camera
            enumerateCameras()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Camera2", e)
            onCameraError?.invoke("Initialization failed: ${e.message}")
        }
    }

    /**
     * Enumerate all available cameras and identify external USB camera.
     */
    private fun enumerateCameras() {
        try {
            val cameraIds = cameraManager?.cameraIdList ?: emptyArray()
            Log.d(TAG, "Found ${cameraIds.size} cameras")

            for (id in cameraIds) {
                val characteristics = cameraManager?.getCameraCharacteristics(id)
                val facing = characteristics?.get(CameraCharacteristics.LENS_FACING)
                val capabilities = characteristics?.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)

                Log.d(TAG, "Camera $id: facing=$facing, capabilities=${capabilities?.joinToString()}")

                // External USB cameras have LENS_FACING_EXTERNAL
                if (facing == CameraCharacteristics.LENS_FACING_EXTERNAL) {
                    Log.d(TAG, "Found external USB camera: $id")
                    externalCameraId = id

                    // Log supported resolutions
                    val streamConfigMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    streamConfigMap?.getOutputSizes(ImageFormat.JPEG)?.forEach { size ->
                        Log.d(TAG, "  Supported JPEG size: ${size.width}x${size.height}")
                    }
                }
            }

            if (externalCameraId == null) {
                Log.w(TAG, "No external USB camera found")
                Log.d(TAG, "Available cameras: ${cameraIds.joinToString()}")
            } else {
                Log.d(TAG, "Ready to open external camera: $externalCameraId")
            }

        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to enumerate cameras", e)
            onCameraError?.invoke("Camera enumeration failed: ${e.message}")
        }
    }

    /**
     * Open the external USB camera.
     */
    fun openCamera() {
        try {
            val cameraId = externalCameraId
            if (cameraId == null) {
                Log.w(TAG, "Cannot open camera: no external camera found")
                onCameraError?.invoke("No external USB camera detected")
                return
            }

            // Check permission again
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Camera permission not granted")
                onCameraError?.invoke("Camera permission required")
                return
            }

            Log.d(TAG, "Opening camera: $cameraId")
            cameraManager?.openCamera(cameraId, stateCallback, backgroundHandler)

        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to open camera", e)
            onCameraError?.invoke("Failed to open camera: ${e.message}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Camera permission denied", e)
            onCameraError?.invoke("Camera permission denied")
        }
    }

    /**
     * Start camera preview on the given surface.
     */
    fun startPreview(surface: Surface) {
        try {
            val camera = cameraDevice
            if (camera == null) {
                Log.w(TAG, "Cannot start preview: camera not opened")
                onCameraError?.invoke("Camera not opened")
                return
            }

            // Create image reader for capture (even though we're just previewing)
            imageReader = ImageReader.newInstance(IMAGE_WIDTH, IMAGE_HEIGHT, ImageFormat.JPEG, MAX_IMAGES)

            // Create capture request for preview
            val captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            captureRequestBuilder.addTarget(surface)

            // Create capture session
            Log.d(TAG, "Creating capture session...")
            camera.createCaptureSession(
                listOf(surface, imageReader?.surface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        Log.d(TAG, "Capture session configured")
                        cameraCaptureSession = session

                        try {
                            // Set auto-focus and auto-exposure
                            captureRequestBuilder.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                            )
                            captureRequestBuilder.set(
                                CaptureRequest.CONTROL_AE_MODE,
                                CaptureRequest.CONTROL_AE_MODE_ON
                            )

                            // Start preview
                            val captureRequest = captureRequestBuilder.build()
                            session.setRepeatingRequest(captureRequest, null, backgroundHandler)
                            isPreviewActive = true
                            Log.d(TAG, "Preview started successfully")

                        } catch (e: CameraAccessException) {
                            Log.e(TAG, "Failed to start preview", e)
                            onCameraError?.invoke("Preview failed: ${e.message}")
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Capture session configuration failed")
                        onCameraError?.invoke("Failed to configure camera session")
                    }
                },
                backgroundHandler
            )

        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to start preview", e)
            onCameraError?.invoke("Preview failed: ${e.message}")
        }
    }

    /**
     * Stop camera preview.
     */
    fun stopPreview() {
        try {
            cameraCaptureSession?.stopRepeating()
            isPreviewActive = false
            Log.d(TAG, "Preview stopped")
        } catch (e: CameraAccessException) {
            Log.e(TAG, "Error stopping preview", e)
        }
    }

    /**
     * Capture a still image.
     */
    fun captureStillImage(callback: (ByteArray) -> Unit) {
        try {
            val camera = cameraDevice
            val session = cameraCaptureSession

            if (camera == null || session == null) {
                Log.w(TAG, "Cannot capture: camera not ready")
                onCameraError?.invoke("Camera not ready")
                return
            }

            // Set up image reader callback
            imageReader?.setOnImageAvailableListener({ reader ->
                val image = reader.acquireLatestImage()
                if (image != null) {
                    try {
                        val buffer = image.planes[0].buffer
                        val bytes = ByteArray(buffer.remaining())
                        buffer.get(bytes)
                        Log.d(TAG, "Image captured: ${bytes.size} bytes")
                        callback(bytes)
                    } finally {
                        image.close()
                    }
                }
            }, backgroundHandler)

            // Create still capture request
            val captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            captureRequestBuilder.addTarget(imageReader!!.surface)

            // Set auto-focus and auto-exposure
            captureRequestBuilder.set(
                CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            )
            captureRequestBuilder.set(
                CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_ON
            )

            // Capture the image
            session.capture(captureRequestBuilder.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult
                ) {
                    Log.d(TAG, "Still image capture completed")
                }

                override fun onCaptureFailed(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    failure: CaptureFailure
                ) {
                    Log.e(TAG, "Still image capture failed: ${failure.reason}")
                    onCameraError?.invoke("Capture failed")
                }
            }, backgroundHandler)

        } catch (e: CameraAccessException) {
            Log.e(TAG, "Failed to capture image", e)
            onCameraError?.invoke("Capture failed: ${e.message}")
        }
    }

    /**
     * Close camera and release resources.
     */
    private fun closeCamera() {
        try {
            cameraCaptureSession?.close()
            cameraCaptureSession = null

            cameraDevice?.close()
            cameraDevice = null

            imageReader?.close()
            imageReader = null

            isConnected = false
            isPreviewActive = false

            Log.d(TAG, "Camera closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing camera", e)
        }
    }

    /**
     * Release all resources including background thread.
     */
    fun release() {
        closeCamera()
        stopBackgroundThread()
        cameraManager = null
        externalCameraId = null
        Log.d(TAG, "Camera2Helper released")
    }

    /**
     * Check if external camera is available.
     */
    fun isExternalCameraAvailable(): Boolean = externalCameraId != null

    /**
     * Check if camera is currently connected.
     */
    fun isConnected(): Boolean = isConnected

    /**
     * Check if preview is active.
     */
    fun isPreviewActive(): Boolean = isPreviewActive

    /**
     * Start background thread for camera operations.
     */
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("Camera2Background").apply {
            start()
            backgroundHandler = Handler(looper)
        }
        Log.d(TAG, "Background thread started")
    }

    /**
     * Stop background thread.
     */
    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
            Log.d(TAG, "Background thread stopped")
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }
}
