package com.example.insuscan.camera.analyzer

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.content.ContextCompat
import com.example.insuscan.analysis.detection.PlateDetector
import com.example.insuscan.analysis.detection.ReferenceObjectDetector
import com.example.insuscan.analysis.model.DetectionResult
import com.example.insuscan.camera.model.ImageQualityResult
import com.example.insuscan.profile.UserProfileManager
import com.example.insuscan.utils.ReferenceObjectHelper

/**
 * Analyzes live frames from the camera to provide real-time feedback.
 * Orchestrates brightness, sharpness, and object detection checks.
 */
class LiveFrameAnalyzer(
    private val context: Context,
    private val plateDetector: PlateDetector,
    private val referenceObjectDetector: ReferenceObjectDetector,
    private val onResult: (ImageQualityResult) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "LiveFrameAnalyzer"
        
        // Quality Constants
        private const val BRIGHTNESS_MIN = 40f
        private const val BRIGHTNESS_MAX = 220f
        private const val SHARPNESS_THRESHOLD = 1000f
        private const val MIN_RESOLUTION = 640 * 480
    }

    var selectedReferenceType: String? = null
    private val stabilizer = DetectionStabilizer()

    override fun analyze(imageProxy: ImageProxy) {
        try {
            val buffer = imageProxy.planes[0].buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            buffer.rewind() 

            val brightness = FrameMathHelper.calculateBrightness(data)
            val sharpness = FrameMathHelper.estimateSharpness(data, imageProxy.width)

            val resolution = imageProxy.width * imageProxy.height
            val isResolutionOk = resolution >= MIN_RESOLUTION

            val bitmap = imageProxy.toBitmap() 
            
            // Reference Object Mode Logic
            val refType = ReferenceObjectHelper.fromServerValue(selectedReferenceType)
            val mode = when (refType) {
                ReferenceObjectHelper.ReferenceObjectType.CARD ->
                    ReferenceObjectDetector.DetectionMode.CARD
                ReferenceObjectHelper.ReferenceObjectType.INSULIN_SYRINGE ->
                    ReferenceObjectDetector.DetectionMode.STRICT
                ReferenceObjectHelper.ReferenceObjectType.SYRINGE_KNIFE ->
                    ReferenceObjectDetector.DetectionMode.FLEXIBLE
                else -> {
                    val syringeType = UserProfileManager.getSyringeSize(context).lowercase()
                    when {
                        syringeType.contains("card") || syringeType.contains("id") ->
                            ReferenceObjectDetector.DetectionMode.CARD
                        syringeType.contains("syringe") || syringeType.contains("pen") ->
                            ReferenceObjectDetector.DetectionMode.STRICT
                        else ->
                            ReferenceObjectDetector.DetectionMode.FLEXIBLE
                    }
                }
            }

            val fallbackResult = referenceObjectDetector.detectWithFallback(bitmap, null, mode)
            val isRefFoundNow = fallbackResult.result is DetectionResult.Found
            
            val debugInfoStr = when (fallbackResult.result) {
                is DetectionResult.Found -> (fallbackResult.result as DetectionResult.Found).debugInfo
                is DetectionResult.NotFound -> (fallbackResult.result as DetectionResult.NotFound).debugInfo
            }
            
            val isPlateFoundNow = plateDetector.detectPlateSmoothed(bitmap).isFound

            val isReferenceObjectStable = stabilizer.updateReferenceObjectFound(isRefFoundNow)
            val isPlateStable = stabilizer.updatePlateFound(isPlateFoundNow)

            val qualityResult = ImageQualityResult(
                brightness = brightness,
                isBrightnessOk = brightness in BRIGHTNESS_MIN..BRIGHTNESS_MAX,
                sharpness = sharpness,
                isSharpnessOk = sharpness >= SHARPNESS_THRESHOLD,
                resolution = resolution,
                isResolutionOk = isResolutionOk,
                isReferenceObjectFound = isReferenceObjectStable,
                isPlateFound = isPlateStable,
                debugInfo = debugInfoStr
            )

            ContextCompat.getMainExecutor(context).execute {
                onResult(qualityResult)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing image", e)
        } finally {
            imageProxy.close()
        }
    }
}
