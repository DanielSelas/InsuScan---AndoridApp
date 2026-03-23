package com.example.insuscan.analysis.detection.strategy

import com.example.insuscan.analysis.model.PlateDetectionResult
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Strategy 1: Adaptive threshold — fast, good for uniform lighting.
 */
class AdaptiveThresholdStrategy : BasePlateStrategy() {
    override fun detect(grayOrEnhanced: Mat, imageArea: Double): PlateDetectionResult? {
        val blurred = Mat()
        Imgproc.GaussianBlur(grayOrEnhanced, blurred, Size(9.0, 9.0), 2.0, 2.0)

        val thresh = Mat()
        Imgproc.adaptiveThreshold(blurred, thresh, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 11, 2.0)

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(thresh, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        val result = findWithFallback(contours, imageArea,
            PRIMARY_CIRCULARITY_THRESHOLD to "adaptive",
            FALLBACK_CIRCULARITY_THRESHOLD to "adaptive-fallback"
        )

        releaseMats(blurred, thresh, hierarchy)
        contours.forEach { it.release() }
        return result
    }
}
