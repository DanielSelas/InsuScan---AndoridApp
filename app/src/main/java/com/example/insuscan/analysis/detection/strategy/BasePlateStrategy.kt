package com.example.insuscan.analysis.detection.strategy

import android.util.Log
import com.example.insuscan.analysis.model.PlateDetectionResult
import com.example.insuscan.analysis.model.ShapeType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.imgproc.Imgproc

/**
 * Common logic for all plate detection strategies.
 * Provides contour analysis, circularity/shape verification, and helper functions.
 */
abstract class BasePlateStrategy : PlateDetectionStrategy {

    companion object {
        const val TAG = "PlateStrategy"
        private const val MIN_PLATE_AREA_RATIO = 0.04
        private const val MAX_PLATE_AREA_RATIO = 0.90
        
        const val PRIMARY_CIRCULARITY_THRESHOLD = 0.65
        const val FALLBACK_CIRCULARITY_THRESHOLD = 0.45
        const val LAST_RESORT_CIRCULARITY_THRESHOLD = 0.35

        private const val CIRCULAR_AXIS_RATIO_THRESHOLD = 1.2
        private const val OVAL_AXIS_RATIO_MAX = 2.0
        private const val ELLIPSE_EXTENT_MAX = 0.88
        private const val ELLIPSE_EXTENT_MIN = 0.68
    }

    protected fun findWithFallback(
        contours: List<MatOfPoint>,
        imageArea: Double,
        vararg attempts: Pair<Double, String>
    ): PlateDetectionResult? {
        for ((threshold, label) in attempts) {
            val result = findBestPlateContour(contours, imageArea, threshold, label)
            if (result != null) return result
        }
        return null
    }

    private fun findBestPlateContour(
        contours: List<MatOfPoint>,
        imageArea: Double,
        circularityThreshold: Double,
        strategyName: String
    ): PlateDetectionResult? {
        var bestArea = 0.0
        var bestResult: PlateDetectionResult? = null
        var tooSmall = 0
        var tooBig = 0
        var notCircular = 0
        var topRejectedArea = 0.0
        var topRejectedCirc = 0.0

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)

            if (area < imageArea * MIN_PLATE_AREA_RATIO) {
                tooSmall++
                continue
            }
            if (area > imageArea * MAX_PLATE_AREA_RATIO) {
                tooBig++
                continue
            }

            val peri = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
            val circularity = (4 * Math.PI * area) / (peri * peri)

            if (circularity <= circularityThreshold) {
                notCircular++
                if (area > topRejectedArea) {
                    topRejectedArea = area
                    topRejectedCirc = circularity
                }
                continue
            }

            val shapeType = classifyShape(contour)
            if (shapeType == ShapeType.UNKNOWN) {
                notCircular++
                Log.d(TAG, "[$strategyName] Rejected: Shape is UNKNOWN (not circular, oval, or rectangular)")
                continue
            }

            if (area > bestArea) {
                val rect = Imgproc.boundingRect(contour)
                val androidRect = android.graphics.Rect(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height)

                bestArea = area
                bestResult = PlateDetectionResult(true, androidRect, circularity.toFloat(), shapeType)
                Log.d(TAG, "[$strategyName] Plate candidate: Area=${"%.1f".format((area/imageArea)*100)}%, Circ=${"%.3f".format(circularity)}, Shape=$shapeType")
            }
        }

        if (bestResult == null && contours.isNotEmpty()) {
            Log.d(TAG, "[$strategyName] Rejected: tooSmall=$tooSmall, tooBig=$tooBig, notCircular=$notCircular" +
                    (if (topRejectedArea > 0) ", topRejected: area=${"%.1f".format((topRejectedArea/imageArea)*100)}% circ=${"%.3f".format(topRejectedCirc)}" else ""))
        }

        return bestResult
    }

    private fun classifyShape(contour: MatOfPoint): ShapeType {
        val contour2f = MatOfPoint2f(*contour.toArray())

        // Reject non-elliptical shapes geometrically using extent
        val area = Imgproc.contourArea(contour)
        val minRect = Imgproc.minAreaRect(contour2f)
        val rectArea = minRect.size.width * minRect.size.height

        if (rectArea > 0) {
            val extent = area / rectArea
            if (extent > ELLIPSE_EXTENT_MAX || extent < ELLIPSE_EXTENT_MIN) {
                Log.d(TAG, "Shape: REJECTED (Extent ${"%.3f".format(extent)} doesn't match a plate. A plate must be ~0.785)")
                return ShapeType.UNKNOWN
            }
        }

        // Check if oval/circle via fitEllipse
        if (contour2f.rows() >= 5) {
            try {
                val ellipse = Imgproc.fitEllipse(contour2f)
                val major = maxOf(ellipse.size.width, ellipse.size.height)
                val minor = minOf(ellipse.size.width, ellipse.size.height)

                if (minor > 0) {
                    val axisRatio = major / minor
                    return when {
                        axisRatio < CIRCULAR_AXIS_RATIO_THRESHOLD -> {
                            Log.d(TAG, "Shape: CIRCULAR (axis ratio ${"%.2f".format(axisRatio)})")
                            ShapeType.CIRCULAR
                        }
                        axisRatio in CIRCULAR_AXIS_RATIO_THRESHOLD..OVAL_AXIS_RATIO_MAX -> {
                            Log.d(TAG, "Shape: OVAL (axis ratio ${"%.2f".format(axisRatio)})")
                            ShapeType.OVAL
                        }
                        else -> {
                            Log.d(TAG, "Shape: UNKNOWN (too elongated, axis ratio ${"%.2f".format(axisRatio)})")
                            ShapeType.UNKNOWN
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "fitEllipse failed", e)
            }
        }

        return ShapeType.UNKNOWN
    }

    protected fun releaseMats(vararg mats: Mat) {
        mats.forEach { it.release() }
    }
}
