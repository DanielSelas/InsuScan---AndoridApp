package com.example.insuscan.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraManager - handles all camera-related work for InsuScan.
 *
 * Responsibilities:
 * - Initialize and start the camera
 * - Capture high-quality images
 * - Analyze frames in real time for on-screen validation
 */

class CameraManager(private val context: Context) {

    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // Callback for real-time image quality updates (UI uses this to show status)
    var onImageQualityUpdate: ((ImageQualityResult) -> Unit)? = null

    companion object {
        private const val TAG = "CameraManager"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val BRIGHTNESS_MIN = 50f
        private const val BRIGHTNESS_MAX = 200f
        private const val SHARPNESS_THRESHOLD = 500f
        private const val MIN_RESOLUTION = 1920 * 1080
    }


    // Starts the camera and attaches it to the given PreviewView.
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onCameraReady: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                // Preview use case
                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                // Image capture use case (maximize quality)
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .build()

                // Image analysis use case (real-time validation)
                imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            analyzeImageQuality(imageProxy)
                        }
                    }

                // Use back camera
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                // Rebind use cases
                cameraProvider?.unbindAll()

                cameraProvider?.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageCapture,
                    imageAnalyzer
                )

                onCameraReady()
                Log.d(TAG, "Camera started successfully")

            } catch (e: Exception) {
                Log.e(TAG, "Camera initialization failed", e)
                onError("Failed to start camera: ${e.message}")
            }

        }, ContextCompat.getMainExecutor(context))
    }


    // Captures a photo and saves it into the provided directory.
    fun captureImage(
        outputDirectory: File,
        onImageCaptured: (File) -> Unit,
        onError: (String) -> Unit
    ) {
        val imageCapture = imageCapture ?: run {
            onError("Camera is not ready")
            return
        }

        // Create a unique file name for the captured image
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                .format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    Log.d(TAG, "Photo captured: ${photoFile.absolutePath}")
                    onImageCaptured(photoFile)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed", exception)
                    onError("Failed to capture photo: ${exception.message}")
                }
            }
        )
    }


    // Analyzes frame quality in real time.
    // Checks: brightness, sharpness, and resolution.
    private fun analyzeImageQuality(imageProxy: ImageProxy) {
        try {
            val buffer = imageProxy.planes[0].buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)

            // Average brightness
            val brightness = calculateBrightness(data)

            // Sharpness estimation (variance-based)
            val sharpness = estimateSharpness(data, imageProxy.width)

            // Resolution check
            val resolution = imageProxy.width * imageProxy.height
            val isResolutionOk = resolution >= MIN_RESOLUTION

            val qualityResult = ImageQualityResult(
                brightness = brightness,
                isBrightnessOk = brightness in BRIGHTNESS_MIN..BRIGHTNESS_MAX,
                sharpness = sharpness,
                isSharpnessOk = sharpness >= SHARPNESS_THRESHOLD,
                resolution = resolution,
                isResolutionOk = isResolutionOk
            )

            // Notify on main thread (UI updates happen there)
            ContextCompat.getMainExecutor(context).execute {
                onImageQualityUpdate?.invoke(qualityResult)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing image", e)
        } finally {
            imageProxy.close()
        }
    }


    // Computes average brightness from the Y plane.
    private fun calculateBrightness(data: ByteArray): Float {
        var sum = 0L
        for (byte in data) {
            sum += (byte.toInt() and 0xFF)
        }
        return sum.toFloat() / data.size
    }


    // Estimates sharpness using pixel variance.
    private fun estimateSharpness(data: ByteArray, width: Int): Float {
        if (data.size < width * 2) return 0f

        var variance = 0.0
        val mean = data.map { it.toInt() and 0xFF }.average()

        for (byte in data) {
            val value = byte.toInt() and 0xFF
            variance += (value - mean) * (value - mean)
        }

        return (variance / data.size).toFloat()
    }

    // Stops camera and releases resources.
    fun shutdown() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }
}

// Image quality check result.
data class ImageQualityResult(
    val brightness: Float,
    val isBrightnessOk: Boolean,
    val sharpness: Float,
    val isSharpnessOk: Boolean,
    val resolution: Int,
    val isResolutionOk: Boolean
) {

    //True if the frame passes all quality checks.
    val isValid: Boolean
        get() = isBrightnessOk && isSharpnessOk && isResolutionOk

    // Returns a user-facing message based on the current quality status.
    fun getValidationMessage(): String {
        return when {
            !isBrightnessOk && brightness < 50f -> "Image is too dark. Please add more light."
            !isBrightnessOk && brightness > 200f -> "Image is too bright. Please reduce the lighting."
            !isSharpnessOk -> "Image is blurry. Please keep the phone steady."
            !isResolutionOk -> "Image resolution is too low."
            else -> "Image looks good. Ready to capture."
        }
    }
}