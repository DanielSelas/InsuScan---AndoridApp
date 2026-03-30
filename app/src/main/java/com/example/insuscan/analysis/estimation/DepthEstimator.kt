package com.example.insuscan.analysis.estimation

import android.content.Context
import android.util.Log
import com.example.insuscan.analysis.model.ContainerType
import com.example.insuscan.analysis.model.DepthResult
import com.example.insuscan.analysis.model.PlateDetectionResult
import com.example.insuscan.ar.model.ArMeasurement

/**
 * Estimates depth of food on a plate/bowl.
 *
 * Primary source: real ARCore measurement from [ArMeasurement].
 * Fallback: conservative heuristic estimate with low confidence.
 *
 * No hardcoded depth values — all real measurements come from ARCore.
 */
class DepthEstimator(private val context: Context) {

    companion object {
        private const val TAG = "DepthEstimator"
        private const val FLAT_PLATE_ASPECT_RATIO_THRESHOLD = 1.3f
        private const val DEEP_BOWL_ASPECT_RATIO_THRESHOLD = 0.8f
    }

    /**
     * Estimate depth using AR measurement when available.
     *
     * @param arMeasurement Real measurement from ArCoreManager (null if AR unavailable).
     * @param containerType Classification of the container (plate/bowl) from image analysis.
     * @return [DepthResult] with depth, confidence, and source.
     */
    fun estimateDepth(arMeasurement: ArMeasurement?, containerType: ContainerType): DepthResult {
        // Primary: Use real AR depth
        if (arMeasurement != null) {
            Log.d(TAG, "Using AR depth: ${arMeasurement.depthCm}cm (confidence=${arMeasurement.confidence})")
            return DepthResult(
                depthCm = arMeasurement.depthCm,
                confidence = arMeasurement.confidence,
                isFromArCore = true,
                containerType = arMeasurement.containerType
            )
        }

        // No AR depth — report unknown and let server AI estimate visually
        Log.w(TAG, "No AR depth available for $containerType — deferring to server AI estimation")

        return DepthResult(
            depthCm = 0f,       // Unknown — server AI will estimate from image
            confidence = 0f,
            isFromArCore = false,
            containerType = containerType
        )
    }

    /**
     * Classify container type from plate detection result.
     * Uses bounding box aspect ratio when AR depth is not available.
     * When AR depth IS available, the container type comes from ArMeasurement.
     */
    fun detectContainerType(plateResult: PlateDetectionResult, arMeasurement: ArMeasurement? = null): ContainerType {
        if (arMeasurement != null) {
            Log.d(TAG, "Container type from AR: ${arMeasurement.containerType}")
            return arMeasurement.containerType
        }

        val bounds = plateResult.bounds ?: return ContainerType.UNKNOWN
        val plateAspectRatio = bounds.width().toFloat() / bounds.height().toFloat()

        val type = when {
            plateAspectRatio > FLAT_PLATE_ASPECT_RATIO_THRESHOLD -> ContainerType.FLAT_PLATE
            plateAspectRatio < DEEP_BOWL_ASPECT_RATIO_THRESHOLD -> ContainerType.DEEP_BOWL
            else -> ContainerType.REGULAR_BOWL
        }

        Log.d(TAG, "Container type from heuristic: $type (aspect ratio=$plateAspectRatio)")
        return type
    }

    fun release() {
        Log.d(TAG, "DepthEstimator released")
    }
}
