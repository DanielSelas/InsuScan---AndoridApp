package com.example.insuscan.analysis.detection.strategy

import com.example.insuscan.analysis.detection.ReferenceObjectDetector
import com.example.insuscan.analysis.detection.util.ReferenceDebugStats
import com.example.insuscan.analysis.model.DetectionResult
import org.opencv.core.MatOfPoint
import org.opencv.core.RotatedRect

/**
 * Strategy interface for evaluating a single contour as a potential reference object
 * based on the selected DetectionMode (e.g. STRICT for syringes, CARD for credit cards).
 */
interface ReferenceObjectStrategy {
    val mode: ReferenceObjectDetector.DetectionMode

    /**
     * @return A valid Found candidate if the contour matches the mode's criteria, or null if rejected.
     */
    fun evaluateContour(
        contour: MatOfPoint,
        rotatedRect: RotatedRect,
        area: Double,
        imgWidth: Double,
        expectedLengthCm: Float,
        expectedWidthCm: Float,
        stats: ReferenceDebugStats
    ): DetectionResult.Found?
}
