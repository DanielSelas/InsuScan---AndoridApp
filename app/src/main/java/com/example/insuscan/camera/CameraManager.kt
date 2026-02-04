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
    
    private val referenceObjectDetector = com.example.insuscan.analysis.ReferenceObjectDetector(context)
    private val plateDetector = com.example.insuscan.analysis.PlateDetector()

    // Stability counters (Debounce)
    private var framesRefFound = 0
    private var framesPlateFound = 0

    // Callback for real-time image quality updates (UI uses this to show status)
    var onImageQualityUpdate: ((ImageQualityResult) -> Unit)? = null

    companion object {
        private const val TAG = "CameraManager"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val BRIGHTNESS_MIN = 50f
        private const val BRIGHTNESS_MAX = 200f
        private const val SHARPNESS_THRESHOLD = 500f
        // Lowered from 1080p to VGA (640x480) to ensure it works on all devices.
        // The *Capture* will still be max resolution, this is just for the preview/analysis check.
        private const val MIN_RESOLUTION = 640 * 480
    }


    // Starts the camera and attaches it to the given PreviewView.
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onCameraReady: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        // Initialize OpenCV/Detector
        if (!referenceObjectDetector.initialize()) {
            Log.e(TAG, "Failed to initialize ReferenceObjectDetector")
            // We might want to notify error, but for now we proceed, detection will just fail/return not found
        }
        
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
                // We request 1280x720 (HD) to have enough detail for the pen detector, 
                // but CameraX might choose slightly different based on device.
                imageAnalyzer = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(1280, 720))
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


    // Analysis loop
    private fun analyzeImageQuality(imageProxy: ImageProxy) {
        try {
            // existing quality checks...
            val buffer = imageProxy.planes[0].buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            buffer.rewind() // Rewind buffer for other readers if any

            // Average brightness
            val brightness = calculateBrightness(data)

            // Sharpness estimation (variance-based)
            val sharpness = estimateSharpness(data, imageProxy.width)

            // Resolution check
            val resolution = imageProxy.width * imageProxy.height
            val isResolutionOk = resolution >= MIN_RESOLUTION

            // Convert to Bitmap for detectors (heavy operation)
            val bitmap = imageProxy.toBitmap() 
            
            // 1. Reference Object Detection
            val detectionResult = referenceObjectDetector.detectReferenceObject(bitmap)
            val isRefFoundNow = detectionResult is com.example.insuscan.analysis.DetectionResult.Found
            
            // 2. Plate Detection
            val isPlateFoundNow = plateDetector.detectPlate(bitmap).isFound

            // 3. Stability Logic (Hysteresis / Debounce)
            // "Fast Attack, Slow Decay": Easier to find, harder to lose.
            // Helps preventing the UI from flickering if detection misses 1 frame.
            val MAX_SCORE = 10
            val THRESHOLD = 3 // Threshold to consider "Found"

            if (isRefFoundNow) {
                 // Found: Boost score significantly (+3) so it locks in quickly (1-2 frames)
                 framesRefFound = (framesRefFound + 3).coerceAtMost(MAX_SCORE)
            } else {
                 // Lost: Decay slowly (-1) so 1 missed frame doesn't drop status immediately
                 framesRefFound = (framesRefFound - 1).coerceAtLeast(0)
            }
            
            if (isPlateFoundNow) {
                framesPlateFound = (framesPlateFound + 3).coerceAtMost(MAX_SCORE)
            } else {
                framesPlateFound = (framesPlateFound - 1).coerceAtLeast(0)
            }

            // We consider it "Found" if counter >= THRESHOLD
            val isReferenceObjectStable = framesRefFound >= THRESHOLD
            val isPlateStable = framesPlateFound >= THRESHOLD

            val qualityResult = ImageQualityResult(
                brightness = brightness,
                isBrightnessOk = brightness in BRIGHTNESS_MIN..BRIGHTNESS_MAX,
                sharpness = sharpness,
                isSharpnessOk = sharpness >= SHARPNESS_THRESHOLD,
                resolution = resolution,
                isResolutionOk = isResolutionOk,
                isReferenceObjectFound = isReferenceObjectStable,
                isPlateFound = isPlateStable
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
    val isResolutionOk: Boolean,
    val isReferenceObjectFound: Boolean,
    val isPlateFound: Boolean
) {

    // Helper to determine the "State" for UI logic
    val isValid: Boolean get() = isBrightnessOk && isSharpnessOk && isResolutionOk && isPlateFound && isReferenceObjectFound

    // Returns a user-facing message based on the current quality status.
    fun getValidationMessage(): String {
        return when {
            !isPlateFound -> "Plate not detected. Center the food."
            !isReferenceObjectFound -> "Insulin Pen not detected."
            !isBrightnessOk && brightness < 50f -> "Image is too dark. Add light."
            !isBrightnessOk && brightness > 200f -> "Image is too bright. Reduce light."
            !isSharpnessOk -> "Image is blurry. Hold steady."
            !isResolutionOk -> "Resolution too low."
            else -> "Perfect! Ready to capture."
        }
    }
}