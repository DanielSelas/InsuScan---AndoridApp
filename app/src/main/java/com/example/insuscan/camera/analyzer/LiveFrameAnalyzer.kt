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
import com.example.insuscan.camera.quality.ImageQualityEvaluator
import com.example.insuscan.utils.ReferenceObjectHelper

class LiveFrameAnalyzer(
    private val context: Context,
    private val plateDetector: PlateDetector,
    private val referenceObjectDetector: ReferenceObjectDetector,
    private val luxProvider: () -> Float,
    private val onResult: (ImageQualityResult) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        private const val TAG = "LiveFrameAnalyzer"
        private const val BRIGHTNESS_TO_PSEUDO_LUX = 2.5f
    }

    var selectedReferenceType: String? = null
    private val stabilizer = DetectionStabilizer()
    private val evaluator = ImageQualityEvaluator()
    private var lastRefFound: DetectionResult.Found? = null

    override fun analyze(imageProxy: ImageProxy) {
        try {
            val buffer = imageProxy.planes[0].buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)
            buffer.rewind()

            val brightness = FrameMathHelper.calculateBrightness(data)
            val sharpness = FrameMathHelper.estimateSharpness(data, imageProxy.width)

            val realLux = luxProvider()
            val lux = if (realLux >= 0f) realLux else brightness * BRIGHTNESS_TO_PSEUDO_LUX
            Log.d(TAG, "LUX | real=$realLux | used=$lux | brightness=$brightness")

            val bitmap = imageProxy.toBitmap()

            val refType = ReferenceObjectHelper.fromServerValue(selectedReferenceType)
            val mode = when (refType) {
                ReferenceObjectHelper.ReferenceObjectType.CARD -> ReferenceObjectDetector.DetectionMode.CARD
                ReferenceObjectHelper.ReferenceObjectType.INSULIN_SYRINGE -> ReferenceObjectDetector.DetectionMode.STRICT
                else -> null
            }

            val fallbackResult = if (mode != null) referenceObjectDetector.detectWithFallback(bitmap, null, mode) else null
            val currentFound = fallbackResult?.result as? DetectionResult.Found
            if (currentFound != null) lastRefFound = currentFound

            val isRefFoundNow = currentFound != null
            val isPlateFoundNow = plateDetector.detectPlateSmoothed(bitmap).isFound

            val isReferenceObjectStable = stabilizer.updateReferenceObjectFound(isRefFoundNow)
            val isPlateStable = stabilizer.updatePlateFound(isPlateFoundNow)

            val referenceExpected = mode != null
            val referenceDetection: DetectionResult? = if (!referenceExpected) {
                null
            } else if (!isReferenceObjectStable) {
                DetectionResult.NotFound("Unstable detection", "")
            } else {
                currentFound ?: lastRefFound ?: DetectionResult.NotFound("No detection", "")
            }

            val report = evaluator.evaluateLiveFrame(
                lux = lux,
                sharpness = sharpness,
                plateFound = isPlateStable,
                referenceExpected = referenceExpected,
                referenceDetection = referenceDetection,
                frameWidth = bitmap.width,
                frameHeight = bitmap.height
            )
            Log.d(TAG, "REPORT | overall=${report.overall} | lighting=${report.lighting?.level} | sharpness=${report.sharpness?.level} | reference=${report.referenceObject?.level} | msg=${report.message}")

            val debugInfoStr = when (val result = fallbackResult?.result) {
                is DetectionResult.Found -> result.debugInfo
                is DetectionResult.NotFound -> result.debugInfo
                null -> ""
            }

            val qualityResult = ImageQualityResult(report = report, debugInfo = debugInfoStr)

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