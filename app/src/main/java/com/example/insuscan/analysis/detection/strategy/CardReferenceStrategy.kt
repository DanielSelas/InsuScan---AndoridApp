package com.example.insuscan.analysis.detection.strategy

import android.util.Log
import com.example.insuscan.analysis.detection.ReferenceObjectDetector
import com.example.insuscan.analysis.detection.util.CardHomographyCalculator
import com.example.insuscan.analysis.detection.util.ReferenceDebugStats
import com.example.insuscan.analysis.model.DetectionResult
import org.opencv.core.MatOfPoint
import org.opencv.core.RotatedRect

class CardReferenceStrategy : ReferenceObjectStrategy {
    companion object {
        private const val TAG = "CardStrategy"
        private const val MIN_ASPECT_RATIO = 1.4
        private const val MAX_ASPECT_RATIO = 1.8
        private const val MIN_RECTANGULARITY = 0.85
        private const val MIN_THICKNESS_RATIO = 0.05
        private const val CONFIDENCE = 0.95f
        private const val HOMOGRAPHY_CONFIDENCE = 0.97f
        private const val MIN_LENGTH_THRESHOLD_RATIO = 0.07
    }

    override val mode = ReferenceObjectDetector.DetectionMode.CARD

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

        val minThickness = imgWidth * MIN_THICKNESS_RATIO
        if (thickness < minThickness) {
            stats.badSolidity++
            Log.d(TAG, "Rejected: thickness=${thickness.toInt()}px < min=${minThickness.toInt()}px")
            return null
        }

        val minLengthThreshold = imgWidth * MIN_LENGTH_THRESHOLD_RATIO
        if (length < minLengthThreshold) {
            stats.tooSmall++
            return null
        }

        val realWorldArea = expectedLengthCm * expectedWidthCm
        var pixelToCmRatio = if (area > 0) {
            kotlin.math.sqrt(realWorldArea / area).toFloat()
        } else {
            (expectedLengthCm / length).toFloat()
        }

        var finalConfidence = CONFIDENCE

        val homographyRatio = CardHomographyCalculator.computeRatio(
            contour = contour,
            expectedLengthCm = expectedLengthCm,
            expectedWidthCm = expectedWidthCm
        )

        if (homographyRatio != null) {
            pixelToCmRatio = (pixelToCmRatio + homographyRatio) / 2.0f
            finalConfidence = HOMOGRAPHY_CONFIDENCE
            Log.d(TAG, "Homography combined ratio: $pixelToCmRatio")
        }

        return DetectionResult.Found(
            boundingBox = rotatedRect.boundingRect(),
            center = rotatedRect.center,
            angle = rotatedRect.angle,
            lengthPixels = length,
            pixelToCmRatio = pixelToCmRatio,
            confidence = finalConfidence,
            debugInfo = ""
        )
    }
}
