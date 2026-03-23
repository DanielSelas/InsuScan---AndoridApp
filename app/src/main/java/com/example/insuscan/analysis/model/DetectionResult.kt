package com.example.insuscan.analysis.model

/**
 * Result of reference object detection.
 */
sealed class DetectionResult {
    data class Found(
        val boundingBox: org.opencv.core.Rect,
        val center: org.opencv.core.Point,
        val angle: Double,
        val lengthPixels: Double,
        val pixelToCmRatio: Float,
        val confidence: Float,
        val debugInfo: String
    ) : DetectionResult()

    data class NotFound(
        val reason: String,
        val debugInfo: String
    ) : DetectionResult()
}
