package com.example.insuscan.analysis

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.insuscan.ar.ArMeasurement

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
    }

    /**
     * Estimate depth using AR measurement when available.
     *
     * @param arMeasurement Real measurement from ArCoreManager (null if AR unavailable).
     * @param containerType Classification of the container (plate/bowl) from image analysis.
     * @return [DepthResult] with depth, confidence, and source.
     */
    fun estimateDepth(arMeasurement: ArMeasurement?, containerType: ContainerType): DepthResult {
        // ── Primary: Use real AR depth ──
        if (arMeasurement != null) {
            Log.d(TAG, "Using AR depth: ${arMeasurement.depthCm}cm (confidence=${arMeasurement.confidence})")
            return DepthResult(
                depthCm = arMeasurement.depthCm,
                confidence = arMeasurement.confidence,
                isFromArCore = true,
                containerType = arMeasurement.containerType
            )
        }

        // ── Fallback: Estimate from container type classification ──
        // These are very rough estimates with LOW confidence.
        // The UI should warn the user that these are unreliable.
        val (estimatedDepth, confidence) = when (containerType) {
            ContainerType.FLAT_PLATE -> Pair(2.0f, 0.15f)
            ContainerType.REGULAR_BOWL -> Pair(5.0f, 0.10f)
            ContainerType.DEEP_BOWL -> Pair(8.0f, 0.10f)
            ContainerType.UNKNOWN -> Pair(3.0f, 0.05f)
        }

        Log.w(TAG, "No AR depth available. Fallback estimate: ${estimatedDepth}cm " +
                "for $containerType (confidence=$confidence)")

        return DepthResult(
            depthCm = estimatedDepth,
            confidence = confidence,
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
        // If AR gave us container type, prefer it (based on real depth)
        if (arMeasurement != null) {
            Log.d(TAG, "Container type from AR: ${arMeasurement.containerType}")
            return arMeasurement.containerType
        }

        // Fallback: heuristic from bounding box aspect ratio
        val bounds = plateResult.bounds ?: return ContainerType.UNKNOWN
        val plateAspectRatio = bounds.width().toFloat() / bounds.height().toFloat()

        val type = when {
            plateAspectRatio > 1.3f -> ContainerType.FLAT_PLATE
            plateAspectRatio < 0.8f -> ContainerType.DEEP_BOWL
            else -> ContainerType.REGULAR_BOWL
        }

        Log.d(TAG, "Container type from heuristic: $type (aspect ratio=$plateAspectRatio)")
        return type
    }

    fun release() {
        Log.d(TAG, "DepthEstimator released")
    }
}

// Type of food container
enum class ContainerType {
    FLAT_PLATE,
    REGULAR_BOWL,
    DEEP_BOWL,
    UNKNOWN
}

// Result of depth estimation
data class DepthResult(
    val depthCm: Float,
    val confidence: Float,
    val isFromArCore: Boolean,
    val containerType: ContainerType
)