package com.example.insuscan.analysis.detection.util

import android.util.Log
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.imgproc.Imgproc

object CardHomographyCalculator {
    private const val TAG = "CardHomography"
    private const val PX_PER_CM = 100.0
    private const val MIN_SOURCE_WIDTH_PX = 10.0
    private const val APPROX_POLY_EPSILON_MULTIPLIER = 0.02

    /**
     * Computes a perspective-corrected pixelToCmRatio for credit card using homography.
     * Finds 4 corners of the card contour, applies getPerspectiveTransform with known
     * card dimensions (8.56 x 5.398 cm), and derives an accurate ratio.
     * Returns null if homography cannot be computed (e.g., < 4 corners found).
     */
    fun computeRatio(
        contour: MatOfPoint,
        expectedLengthCm: Float,
        expectedWidthCm: Float
    ): Float? {
        return try {
            val contour2f = MatOfPoint2f(*contour.toArray())
            val peri = Imgproc.arcLength(contour2f, true)
            val approxCurve = MatOfPoint2f()
            Imgproc.approxPolyDP(contour2f, approxCurve, APPROX_POLY_EPSILON_MULTIPLIER * peri, true)

            if (approxCurve.rows() != 4) {
                Log.d(TAG, "Expected 4 corners, got ${approxCurve.rows()}")
                return null
            }

            // Sort corners: TL, TR, BR, BL
            val points = approxCurve.toList()
            val sortedByY = points.sortedBy { it.y }
            val topTwo = sortedByY.take(2).sortedBy { it.x }
            val bottomTwo = sortedByY.drop(2).sortedBy { it.x }

            val srcPoints = MatOfPoint2f(
                topTwo[0],    // TL
                topTwo[1],    // TR
                bottomTwo[1], // BR
                bottomTwo[0]  // BL
            )

            // Known card dimensions in a pixel-scale destination
            val dstW = expectedLengthCm * PX_PER_CM
            val dstH = expectedWidthCm * PX_PER_CM

            val dstPoints = MatOfPoint2f(
                org.opencv.core.Point(0.0, 0.0),
                org.opencv.core.Point(dstW, 0.0),
                org.opencv.core.Point(dstW, dstH),
                org.opencv.core.Point(0.0, dstH)
            )

            val perspectiveMatrix = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)

            if (perspectiveMatrix.empty()) {
                Log.w(TAG, "getPerspectiveTransform returned empty matrix")
                return null
            }

            val srcWidthPx = Math.sqrt(
                Math.pow(topTwo[1].x - topTwo[0].x, 2.0) +
                Math.pow(topTwo[1].y - topTwo[0].y, 2.0)
            )

            if (srcWidthPx < MIN_SOURCE_WIDTH_PX) {
                Log.w(TAG, "Source card width too small ($srcWidthPx px)")
                return null
            }

            val correctedRatio = (expectedLengthCm / srcWidthPx).toFloat()
            Log.d(TAG, "srcWidthPx=$srcWidthPx → correctedRatio=$correctedRatio cm/px")

            perspectiveMatrix.release()
            correctedRatio
        } catch (e: Exception) {
            Log.w(TAG, "Computation failed: ${e.message}")
            null
        }
    }
}
