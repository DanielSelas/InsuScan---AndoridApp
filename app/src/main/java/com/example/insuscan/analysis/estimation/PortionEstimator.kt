package com.example.insuscan.analysis.estimation

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.insuscan.analysis.detection.PlateDetector
import com.example.insuscan.analysis.detection.ReferenceObjectDetector
import com.example.insuscan.analysis.model.ContainerType
import com.example.insuscan.analysis.model.DetectionResult
import com.example.insuscan.analysis.model.InitializationResult
import com.example.insuscan.analysis.model.PlateDetectionResult
import com.example.insuscan.analysis.model.PlateDimensions
import com.example.insuscan.analysis.model.PortionResult
import com.example.insuscan.analysis.model.ScaleSource
import com.example.insuscan.ar.model.ArMeasurement

/**
 * Combines depth estimation and reference object detection
 * to calculate real portion size in grams.
 *
 * Scale sources (in priority order):
 *  1. Reference object (syringe, card, etc.) → pixelToCmRatio
 *  2. ARCore projection → real plate diameter
 *  3. Neither → returns Error with warning
 */
class PortionEstimator(private val context: Context) {

    companion object {
        private const val TAG = "PortionEstimator"
        private const val DEFAULT_FOOD_DENSITY = 0.65f
    }

    private val depthEstimator = DepthEstimator(context)
    internal val referenceDetector = ReferenceObjectDetector(context)
    private val plateDetector = PlateDetector()

    private var isInitialized = false

    fun initialize(): InitializationResult {
        val openCvReady = referenceDetector.initialize()
        isInitialized = openCvReady

        Log.d(TAG, "Initialization — OpenCV: $openCvReady")

        return InitializationResult(
            isReady = isInitialized,
            openCvReady = openCvReady
        )
    }

    fun configureSyringe(lengthCm: Float) {
        referenceDetector.setExpectedObjectDimensions(lengthCm, 1.0f, 1.0f)
    }

    private fun refreshSettings() {
        val customLength = com.example.insuscan.profile.UserProfileManager.getCustomSyringeLength(context)
        configureSyringe(customLength)
    }

    /**
     * Main estimation pipeline.
     *
     * @param bitmap              The captured food image.
     * @param referenceObjectType Server value of the selected reference type (e.g. "CARD", "INSULIN_SYRINGE").
     * @param arMeasurement       Real AR measurement from ArCoreManager (null if AR unavailable).
     */
    fun estimatePortion(
        bitmap: Bitmap,
        referenceObjectType: String? = null,
        arMeasurement: ArMeasurement? = null,
        precomputedPlateResult: PlateDetectionResult? = null
    ): PortionResult {
        if (!isInitialized) {
            Log.w(TAG, "PortionEstimator not initialized")
            return PortionResult.Error("System not initialized")
        }

        refreshSettings()

        // Step 1: Detect Plate (reuse if already detected by caller)
        val plateResult = precomputedPlateResult ?: plateDetector.detectPlate(bitmap)
        val plateBounds = if (plateResult.isFound) plateResult.bounds else null

        if (plateBounds != null) {
            Log.d(TAG, "Plate detected at: ${plateBounds.toShortString()}")
        }

        // Step 2: Detect Reference Object
        val refType = com.example.insuscan.utils.ReferenceObjectHelper.fromServerValue(referenceObjectType)
        val detectionMode = when (refType) {
            com.example.insuscan.utils.ReferenceObjectHelper.ReferenceObjectType.INSULIN_SYRINGE ->
                ReferenceObjectDetector.DetectionMode.STRICT
            com.example.insuscan.utils.ReferenceObjectHelper.ReferenceObjectType.SYRINGE_KNIFE ->
                ReferenceObjectDetector.DetectionMode.FLEXIBLE
            com.example.insuscan.utils.ReferenceObjectHelper.ReferenceObjectType.CARD ->
                ReferenceObjectDetector.DetectionMode.CARD
            com.example.insuscan.utils.ReferenceObjectHelper.ReferenceObjectType.NONE, null -> null
        }

        if (refType != null && refType != com.example.insuscan.utils.ReferenceObjectHelper.ReferenceObjectType.NONE) {
            referenceDetector.setExpectedObjectDimensions(refType.lengthCm, refType.widthCm, refType.heightCm)
        }

        Log.d(TAG, "Detection Mode: $detectionMode (RefType: $refType)")

        val detectionResult = if (detectionMode != null) {
            referenceDetector.detectReferenceObject(bitmap, plateBounds, detectionMode)
        } else {
            DetectionResult.NotFound("User chose no reference object", "")
        }

        // Step 3: Determine scale (pixelToCmRatio or AR diameter)
        val hasRefObject = detectionResult is DetectionResult.Found
        val hasArMeasurement = arMeasurement != null

        val scaleSource: ScaleSource = when {
            hasRefObject -> {
                val ratio = (detectionResult as DetectionResult.Found).pixelToCmRatio
                Log.d(TAG, "Scale from REFERENCE OBJECT: ratio=$ratio")
                ScaleSource.ReferenceObject(ratio)
            }
            hasArMeasurement -> {
                Log.d(TAG, "Scale from AR: plate diameter=${arMeasurement!!.plateDiameterCm}cm")
                ScaleSource.ArProjection(arMeasurement)
            }
            else -> {
                Log.w(TAG, "NO scale source available (no reference object, no AR)")
                ScaleSource.None
            }
        }

        // Step 4: Depth estimation
        val containerType = depthEstimator.detectContainerType(plateResult, arMeasurement)
        val depthResult = depthEstimator.estimateDepth(arMeasurement, containerType)

        // Step 5: Calculate plate dimensions
        val plateDimensions = when (scaleSource) {
            is ScaleSource.ReferenceObject -> {
                calculatePlateDimensionsFromRatio(bitmap, plateResult, scaleSource.pixelToCmRatio)
            }
            is ScaleSource.ArProjection -> {
                val diameter = scaleSource.arMeasurement.plateDiameterCm
                Log.d(TAG, "Using AR plate diameter: ${diameter}cm")
                PlateDimensions(diameterCm = diameter, radiusCm = diameter / 2)
            }
            is ScaleSource.None -> {
                return PortionResult.Success(
                    estimatedWeightGrams = 0f,
                    volumeCm3 = 0f,
                    plateDiameterCm = 0f,
                    depthCm = depthResult.depthCm,
                    containerType = containerType,
                    confidence = 0.05f,
                    referenceObjectDetected = false,
                    arMeasurementUsed = false,
                    arDepthIsReal = false,
                    warning = "No reference object or AR available. Place a reference object for accurate measurements."
                )
            }
        }

        // Step 6: Calculate raw container volume
        val volumeCm3 = calculateVolume(plateDimensions, depthResult.depthCm)

        Log.d(TAG, "Raw measurements: vol=${volumeCm3}cm³, " +
                "plate=${plateDimensions.diameterCm}cm, depth=${depthResult.depthCm}cm, " +
                "source=${scaleSource::class.simpleName}")

        return PortionResult.Success(
            estimatedWeightGrams = 0f,
            volumeCm3 = volumeCm3,
            plateDiameterCm = plateDimensions.diameterCm,
            depthCm = depthResult.depthCm,
            containerType = containerType,
            confidence = calculateOverallConfidence(detectionResult, depthResult, scaleSource),
            referenceObjectDetected = hasRefObject,
            arMeasurementUsed = hasArMeasurement,
            arDepthIsReal = arMeasurement?.isRealDepth ?: false,
            warning = null
        )
    }

    /**
     * Calculate plate dimensions from pixelToCmRatio (reference object based).
     */
    private fun calculatePlateDimensionsFromRatio(
        bitmap: Bitmap,
        plateResult: PlateDetectionResult,
        pixelToCmRatio: Float
    ): PlateDimensions {
        val plateWidthPixels = if (plateResult.isFound && plateResult.bounds != null) {
            Log.d(TAG, "Plate width from detection: ${plateResult.bounds.width()}px")
            plateResult.bounds.width().toFloat()
        } else {
            Log.d(TAG, "Plate not detected, using 45% of image width")
            bitmap.width * 0.45f
        }

        val diameterCm = plateWidthPixels * pixelToCmRatio
        Log.d(TAG, "Plate diameter from ref object: ${diameterCm}cm ($plateWidthPixels px × $pixelToCmRatio)")

        return PlateDimensions(
            diameterCm = diameterCm,
            radiusCm = diameterCm / 2
        )
    }

    /**
     * Calculate raw container volume (full cylinder).
     * No adjustments — server handles fill level via GPT + density via USDA.
     */
    private fun calculateVolume(dimensions: PlateDimensions, depthCm: Float): Float {
        val radius = dimensions.radiusCm
        val volume = (Math.PI * radius * radius * depthCm).toFloat()

        Log.d(TAG, "Raw container volume: r=${radius}cm, d=${depthCm}cm → ${volume.toInt()}cm³")

        return volume
    }

    private fun calculateOverallConfidence(
        detection: DetectionResult,
        depth: com.example.insuscan.analysis.model.DepthResult,
        scaleSource: ScaleSource
    ): Float {
        val scaleConfidence = when (scaleSource) {
            is ScaleSource.ReferenceObject -> {
                when (detection) {
                    is DetectionResult.Found -> detection.confidence
                    is DetectionResult.NotFound -> 0.1f
                }
            }
            is ScaleSource.ArProjection -> scaleSource.arMeasurement.confidence * 0.9f
            is ScaleSource.None -> 0.05f
        }

        val depthConfidence = depth.confidence
        return (scaleConfidence * 0.6f + depthConfidence * 0.4f)
    }

    fun release() {
        depthEstimator.release()
        Log.d(TAG, "PortionEstimator released")
    }
}
