package com.example.insuscan.analysis.detection.strategy

import android.util.Log
import com.example.insuscan.analysis.model.PlateDetectionResult
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Strategy 2: Canny edge detection + morphological closing — better with varied lighting.
 * Uses bilateral filter to preserve edges while smoothing texture noise.
 */
class CannyEdgeStrategy : BasePlateStrategy() {
    override fun detect(grayOrEnhanced: Mat, imageArea: Double): PlateDetectionResult? {
        val filtered = Mat()
        Imgproc.bilateralFilter(grayOrEnhanced, filtered, 9, 75.0, 75.0)

        val edges = Mat()
        Imgproc.Canny(filtered, edges, 15.0, 60.0)

        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(15.0, 15.0))
        val closed = Mat()
        Imgproc.morphologyEx(edges, closed, Imgproc.MORPH_CLOSE, kernel)

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(closed, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        Log.d(TAG, "Canny found ${contours.size} contours")

        val result = findWithFallback(contours, imageArea,
            PRIMARY_CIRCULARITY_THRESHOLD to "canny",
            FALLBACK_CIRCULARITY_THRESHOLD to "canny-fallback"
        )

        if (result != null) Log.d(TAG, "Canny strategy found plate!")

        releaseMats(filtered, edges, kernel, closed, hierarchy)
        contours.forEach { it.release() }
        return result
    }
}
