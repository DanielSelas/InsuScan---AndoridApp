package com.example.insuscan.camera

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.insuscan.analysis.detection.PlateDetector
import com.example.insuscan.analysis.detection.ReferenceObjectDetector
import com.example.insuscan.ar.ArCoreManager
import com.example.insuscan.camera.analyzer.LiveFrameAnalyzer
import com.example.insuscan.camera.model.ImageQualityResult
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
 * - Delegates analysis to [LiveFrameAnalyzer]
 */
class CameraManager(private val context: Context) {

    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    
    private val referenceObjectDetector = ReferenceObjectDetector(context)
    private val plateDetector = PlateDetector()

    // Callback for real-time image quality updates (UI uses this to show status)
    var onImageQualityUpdate: ((ImageQualityResult) -> Unit)? = null
    
    // Store last result for UI actions
    var lastQualityResult: ImageQualityResult? = null

    private val liveFrameAnalyzer = LiveFrameAnalyzer(
        context = context,
        plateDetector = plateDetector,
        referenceObjectDetector = referenceObjectDetector
    ) { result ->
        lastQualityResult = result
        onImageQualityUpdate?.invoke(result)
    }

    // Reference type selected from the dialog — set by ScanFragment
    var selectedReferenceType: String?
        get() = liveFrameAnalyzer.selectedReferenceType
        set(value) {
            liveFrameAnalyzer.selectedReferenceType = value
        }

    // ARCore manager for continuous depth frame updates during live preview
    var arCoreManager: ArCoreManager? = null

    companion object {
        private const val TAG = "CameraManager"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
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

                imageAnalyzer = ImageAnalysis.Builder()
                    .setTargetResolution(android.util.Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor, liveFrameAnalyzer)
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

        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + ".jpg"
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

    fun stopPreview() {
        cameraProvider?.unbindAll()
    }

    // Stops camera and releases resources.
    fun shutdown() {
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
    }
}