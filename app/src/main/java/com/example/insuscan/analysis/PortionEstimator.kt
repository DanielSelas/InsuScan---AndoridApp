package com.example.insuscan.analysis

import android.content.Context
import android.graphics.Bitmap
import android.util.Log


//  Combines depth estimation and reference object detection
//  to calculate real portion size in grams

class PortionEstimator(private val context: Context) {

    companion object {
        private const val TAG = "PortionEstimator"

        // Average food density (g/cm³) - varies by food type
        private const val DEFAULT_FOOD_DENSITY = 0.8f

        // Plate fill percentage (how full is the plate typically)
        private const val DEFAULT_FILL_PERCENTAGE = 0.7f
    }

    private val depthEstimator = DepthEstimator(context)
    private val referenceDetector = ReferenceObjectDetector(context)

    private var isInitialized = false

    // Initialize both estimators
    fun initialize(): InitializationResult {
        val arCoreStatus = depthEstimator.checkArCoreAvailability()
        val openCvReady = referenceDetector.initialize()

        isInitialized = openCvReady // OpenCV is required, ARCore is optional

        Log.d(TAG, "Initialization - ARCore: $arCoreStatus, OpenCV: $openCvReady")

        return InitializationResult(
            isReady = isInitialized,
            arCoreStatus = arCoreStatus,
            openCvReady = openCvReady
        )
    }


    //  Set syringe length from user profile
    fun configureSyringe(lengthCm: Float) {
        referenceDetector.setSyringeLength(lengthCm)
    }


    // Estimate portion size from image
    // Returns estimated weight in grams

    fun estimatePortion(bitmap: Bitmap): PortionResult {
        if (!isInitialized) {
            Log.w(TAG, "PortionEstimator not initialized")
            return PortionResult.Error("System not initialized")
        }

        // Step 1: Detect reference object (syringe)
        val detectionResult = referenceDetector.detectReferenceObject(bitmap)

        val pixelToCmRatio = when (detectionResult) {
            is DetectionResult.Found -> {
                Log.d(TAG, "Reference object found with ratio: ${detectionResult.pixelToCmRatio}")
                detectionResult.pixelToCmRatio
            }
            is DetectionResult.NotFound -> {
                Log.w(TAG, "Reference object not found: ${detectionResult.reason}")
                // Use fallback ratio based on typical phone camera
                estimateFallbackRatio(bitmap)
            }
        }

        // Step 2: Detect container type and estimate depth
        val containerType = depthEstimator.detectContainerType(bitmap)
        val depthResult = depthEstimator.estimateDepth(bitmap, containerType)

        // Step 3: Calculate plate dimensions
        val plateDimensions = calculatePlateDimensions(bitmap, pixelToCmRatio)

        // Step 4: Calculate volume
        val volumeCm3 = calculateVolume(plateDimensions, depthResult.depthCm)

        // Step 5: Estimate weight
        val weightGrams = volumeCm3 * DEFAULT_FOOD_DENSITY * DEFAULT_FILL_PERCENTAGE

        Log.d(TAG, "Portion estimate: ${weightGrams}g (volume: ${volumeCm3}cm³)")

        return PortionResult.Success(
            estimatedWeightGrams = weightGrams,
            volumeCm3 = volumeCm3,
            plateDiameterCm = plateDimensions.diameterCm,
            depthCm = depthResult.depthCm,
            containerType = containerType,
            confidence = calculateOverallConfidence(detectionResult, depthResult),
            referenceObjectDetected = detectionResult is DetectionResult.Found
        )
    }

    // Calculate plate dimensions from image
    private fun calculatePlateDimensions(bitmap: Bitmap, pixelToCmRatio: Float): PlateDimensions {
        // Estimate plate takes up ~60% of image width typically
        val plateWidthPixels = bitmap.width * 0.6f
        val diameterCm = plateWidthPixels * pixelToCmRatio

        // Typical plate diameter range: 20-30 cm
        val adjustedDiameter = diameterCm.coerceIn(15f, 35f)

        return PlateDimensions(
            diameterCm = adjustedDiameter,
            radiusCm = adjustedDiameter / 2
        )
    }


    // Calculate food volume based on plate dimensions and depth
    private fun calculateVolume(dimensions: PlateDimensions, depthCm: Float): Float {
        // Simplified: treat as cylinder
        // V = π * r² * h
        val radius = dimensions.radiusCm
        val volume = Math.PI.toFloat() * radius * radius * depthCm

        return volume
    }

    // Fallback ratio when syringe not detected
    // Based on typical smartphone camera and shooting distance
    private fun estimateFallbackRatio(bitmap: Bitmap): Float {
        // Assume typical shooting distance of 30cm and phone camera FOV
        // This gives roughly 0.02 cm per pixel for 1080p image
        val baseRatio = 0.02f

        // Adjust for image resolution
        val resolutionFactor = 1920f / bitmap.width

        return baseRatio * resolutionFactor
    }

    // Calculate overall confidence based on detection results
    private fun calculateOverallConfidence(
        detection: DetectionResult,
        depth: DepthResult
    ): Float {
        val detectionConfidence = when (detection) {
            is DetectionResult.Found -> detection.confidence
            is DetectionResult.NotFound -> 0.3f // low confidence without reference
        }

        val depthConfidence = depth.confidence

        // Weighted average
        return (detectionConfidence * 0.6f + depthConfidence * 0.4f)
    }

    // Release resources
    fun release() {
        depthEstimator.release()
        Log.d(TAG, "PortionEstimator released")
    }
}

// Initialization result
data class InitializationResult(
    val isReady: Boolean,
    val arCoreStatus: ArCoreStatus,
    val openCvReady: Boolean
)

// Plate dimensions
data class PlateDimensions(
    val diameterCm: Float,
    val radiusCm: Float
)

// Result of portion estimation
sealed class PortionResult {
    data class Success(
        val estimatedWeightGrams: Float,
        val volumeCm3: Float,
        val plateDiameterCm: Float,
        val depthCm: Float,
        val containerType: ContainerType,
        val confidence: Float,
        val referenceObjectDetected: Boolean
    ) : PortionResult()

    data class Error(
        val message: String
    ) : PortionResult()
}