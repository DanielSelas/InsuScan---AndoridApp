package com.example.insuscan.analysis

import android.content.Context
import android.graphics.Bitmap
import android.util.Log


//  Combines depth estimation and reference object detection
//  to calculate real portion size in grams

class PortionEstimator(private val context: Context) {

    companion object {
        private const val TAG = "PortionEstimator"

        // Average food density (g/cm³) - Adjusted for Cooked Rice/Carbs (was 0.8)
        private const val DEFAULT_FOOD_DENSITY = 0.65f

        // Plate fill percentage - Removed (set to 1.0) in favor of geometric accuracy
        private const val DEFAULT_FILL_PERCENTAGE = 1.0f
    }

    private val depthEstimator = DepthEstimator(context)
    private val referenceDetector = ReferenceObjectDetector(context)
    // Add PlateDetector to get accurate size
    private val plateDetector = PlateDetector()

    private var isInitialized = false

    // Expose AR status for UI
    var isArCoreSupported: Boolean = false
        private set

    // Initialize both estimators
    fun initialize(): InitializationResult {
        val arCoreStatus = depthEstimator.checkArCoreAvailability()
        
        isArCoreSupported = (arCoreStatus == ArCoreStatus.SUPPORTED)

        // Initialize AR Session if supported
        if (isArCoreSupported) {
            val arInitialized = depthEstimator.initializeSession()
            Log.d(TAG, "AR Session initialized: $arInitialized")
        }
        
        val openCvReady = referenceDetector.initialize()
        isInitialized = openCvReady // OpenCV is required, ARCore is optional

        Log.d(TAG, "Initialization - ARCore: $arCoreStatus, OpenCV: $openCvReady")

        return InitializationResult(
            isReady = isInitialized,
            arCoreStatus = arCoreStatus,
            openCvReady = openCvReady
        )
    }

    // Configure Syringe
    fun configureSyringe(lengthCm: Float) {
        referenceDetector.setExpectedObjectLength(lengthCm)
    }

    // Refresh settings from profile before scanning
    private fun refreshSettings() {
        // Get custom length from profile (e.g. 15cm fork)
        val customLength = com.example.insuscan.profile.UserProfileManager.getCustomSyringeLength(context)
        configureSyringe(customLength)
        configureSyringe(customLength)
    }

    // Start ARCore scanning for surfaces
    fun startArScan(callback: (Boolean) -> Unit) {
        depthEstimator.startSurfaceScan(callback)
    }

    // Stop ARCore scanning
    fun stopArScan() {
        depthEstimator.stopSurfaceScan()
    }

    fun estimatePortion(bitmap: Bitmap): PortionResult {
        if (!isInitialized) {
            Log.w(TAG, "PortionEstimator not initialized")
            return PortionResult.Error("System not initialized")
        }
        
        // 0. Update settings
        refreshSettings()

        // Step 1: Detect Plate (We do this FIRST now, to help find the reference object)
        val plateResult = plateDetector.detectPlate(bitmap)
        val plateBounds = if (plateResult.isFound) plateResult.bounds else null
        
        if (plateBounds != null) {
             Log.d(TAG, "Plate detected at: ${plateBounds.toShortString()}")
        }

        // Determine Detection Mode based on User Profile
        // "Syringe" or "Pen" -> Strict Mode (Physics-based rejection)
        // "Other", "Fork", "Knife" -> Flexible Mode (Proximity & Shape)
        val syringeType = com.example.insuscan.profile.UserProfileManager.getSyringeSize(context).lowercase()
        val detectionMode = if (syringeType.contains("syringe") || syringeType.contains("pen")) {
            com.example.insuscan.analysis.ReferenceObjectDetector.DetectionMode.STRICT
        } else {
            com.example.insuscan.analysis.ReferenceObjectDetector.DetectionMode.FLEXIBLE
        }
        
        Log.d(TAG, "Using Detection Mode: $detectionMode (Type: $syringeType)")

        // Step 2: Detect reference object (Using Plate location as bias)
        // "Smart Cutlery": Finds object closest to plate
        val detectionResult = referenceDetector.detectReferenceObject(bitmap, plateBounds, detectionMode)

        val pixelToCmRatio = when (detectionResult) {
            is DetectionResult.Found -> {
                Log.d(TAG, "Reference object found with ratio: ${detectionResult.pixelToCmRatio}")
                detectionResult.pixelToCmRatio
            }
            is DetectionResult.NotFound -> {
                Log.w(TAG, "Reference object not found: ${detectionResult.reason}")
                estimateFallbackRatio(bitmap)
            }
        }

        // Step 3: Detect container type and estimate depth
        val containerType = depthEstimator.detectContainerType(bitmap)
        val depthResult = depthEstimator.estimateDepth(bitmap, containerType)

        // Step 4: Calculate accurate plate dimensions (using now-known ratio)
        val plateDimensions = calculatePlateDimensions(bitmap, plateResult, pixelToCmRatio)

        // Step 5: Calculate volume using SPHERICAL CAP (more accurate for piles of food)
        val volumeCm3 = calculateVolume(plateDimensions, depthResult.depthCm)

        // Step 6: Estimate weight
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
    private fun calculatePlateDimensions(bitmap: Bitmap, plateResult: PlateDetectionResult, pixelToCmRatio: Float): PlateDimensions {
        
        val plateWidthPixels = if (plateResult.isFound && plateResult.bounds != null) {
            Log.d(TAG, "Using actual detected plate width: ${plateResult.bounds.width()}px")
            plateResult.bounds.width().toFloat()
        } else {
            // Fallback: Assume plate takes up ~45% of image width (safer average than 60%)
            // Reduced to 45% because we advised user to zoom out
            Log.d(TAG, "Plate not found for sizing, using fallback 45%")
            bitmap.width * 0.45f
        }
        
        val diameterCm = plateWidthPixels * pixelToCmRatio
        
        // ... rest of logic
        val adjustedDiameter = diameterCm.coerceIn(12f, 32f)

         return PlateDimensions(
            diameterCm = adjustedDiameter,
            radiusCm = adjustedDiameter / 2
        )
    }
    
    // Calculate food volume based on plate dimensions and depth
    private fun calculateVolume(dimensions: PlateDimensions, depthCm: Float): Float {
        val radius = dimensions.radiusCm
        
        // Single Portion Limit:
        // Even if the plate is Huge (e.g. 32cm), a single serving of food rarely exceeds 12cm width (Radius 6cm).
        // This is critical for small portions (like 70g) on large plates.
        val effectiveRadius = radius.coerceAtMost(6.0f)

        // Cone/Mound Formula for food piles (V = ~1/3 * Base * Height)
        val cylinderVolume = Math.PI * effectiveRadius * effectiveRadius * depthCm
        val adjustedVolume = cylinderVolume * 0.35 
        
        Log.d(TAG, "CALC_DEBUG: RawRadius=${radius}cm, EffRadius=${effectiveRadius}cm, Depth=${depthCm}cm") 
        Log.d(TAG, "CALC_DEBUG: Vol=${adjustedVolume.toInt()}cm3 (Mass ~${adjustedVolume * DEFAULT_FOOD_DENSITY}g)")
        
        return adjustedVolume.toFloat()
    }

    private fun estimateFallbackRatio(bitmap: Bitmap): Float {
        val baseRatio = 0.02f
        val resolutionFactor = 1920f / bitmap.width
        return baseRatio * resolutionFactor
    }

    private fun calculateOverallConfidence(
        detection: DetectionResult,
        depth: DepthResult
    ): Float {
        val detectionConfidence = when (detection) {
            is DetectionResult.Found -> detection.confidence
            is DetectionResult.NotFound -> 0.3f 
        }
        val depthConfidence = depth.confidence
        return (detectionConfidence * 0.6f + depthConfidence * 0.4f)
    }

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