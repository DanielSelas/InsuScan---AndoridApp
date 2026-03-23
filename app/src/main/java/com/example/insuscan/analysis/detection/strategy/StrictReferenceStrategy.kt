package com.example.insuscan.analysis.detection.strategy

import com.example.insuscan.analysis.detection.ReferenceObjectDetector
import com.example.insuscan.analysis.detection.util.ReferenceDebugStats
import com.example.insuscan.analysis.model.DetectionResult
import org.opencv.core.MatOfPoint
import org.opencv.core.RotatedRect

class StrictReferenceStrategy : ReferenceObjectStrategy {
    companion object {
        private const val MIN_ASPECT_RATIO = 4.0
        private const val MAX_ASPECT_RATIO = 25.0
        private const val MIN_RECTANGULARITY = 0.7
        private const val CONFIDENCE = 0.9f
        private const val MIN_LENGTH_THRESHOLD_RATIO = 0.07
    }

    override val mode = ReferenceObjectDetector.DetectionMode.STRICT

    override fun evaluateContour(
        contour: MatOfPoint,
        rotatedRect: RotatedRect,
        area: Double,
        imgWidth: Double,
        expectedLengthCm: Float,
        expectedWidthCm: Float,
        stats: ReferenceDebugStats
    ): DetectionResult.Found? {
        val width = rotatedRect.size.width
        val height = rotatedRect.size.height
        val length = maxOf(width, height)
        val thickness = minOf(width, height)

        if (thickness == 0.0) return null
        val aspectRatio = length / thickness

        if (aspectRatio < MIN_ASPECT_RATIO || aspectRatio > MAX_ASPECT_RATIO) {
            stats.badRatio++
            return null
        }

        val rectArea = width * height
        val rectangularity = area / rectArea
        if (rectangularity <= MIN_RECTANGULARITY) {
            stats.badSolidity++
            return null
        }

        val minLengthThreshold = imgWidth * MIN_LENGTH_THRESHOLD_RATIO
        if (length < minLengthThreshold) {
            stats.tooSmall++
            return null
        }

        val pixelToCmRatio = (expectedLengthCm / length).toFloat()

        return DetectionResult.Found(
            boundingBox = rotatedRect.boundingRect(),
            center = rotatedRect.center,
            angle = rotatedRect.angle,
            lengthPixels = length,
            pixelToCmRatio = pixelToCmRatio,
            confidence = CONFIDENCE,
            debugInfo = ""
        )
    }
}
