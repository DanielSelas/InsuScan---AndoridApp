package com.example.insuscan.analysis

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

/**
 * Detects food plate (circular/elliptical shape) in the image.
 */
class PlateDetector {

    companion object {
        private const val TAG = "PlateDetector"
        private const val MIN_PLATE_AREA_RATIO = 0.05 // Lowered to 5% to allow wider shots with pen
        private const val MAX_PLATE_AREA_RATIO = 0.90 // Plate usually won't cover >90%
    }

    /**
     * Detects if a plate is present in the image.
     */
    fun detectPlate(bitmap: Bitmap): Boolean {
        return try {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)

            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)

            // Blur to reduce noise
            Imgproc.GaussianBlur(gray, gray, Size(9.0, 9.0), 2.0, 2.0)

            // Adaptive threshold to handle different lighting
            val thresh = Mat()
            Imgproc.adaptiveThreshold(gray, thresh, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 11, 2.0)

            // Find contours
            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(thresh, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

            var plateFound = false
            val imageArea = (mat.width() * mat.height()).toDouble()

            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                
                // Filter by size
                if (area < imageArea * MIN_PLATE_AREA_RATIO || area > imageArea * MAX_PLATE_AREA_RATIO) {
                    continue
                }

                // Check convexity / roundness
                val contour2f = MatOfPoint2f(*contour.toArray())
                val approx = MatOfPoint2f()
                val peri = Imgproc.arcLength(contour2f, true)
                Imgproc.approxPolyDP(contour2f, approx, 0.02 * peri, true)

                // Plates are circular/oval
                // 1. Convex Hull check
                val hull = MatOfInt()
                Imgproc.convexHull(contour, hull)
                val hullArea = calculateHullArea(contour, hull)
                val solidity = area / hullArea

                // Solidity > 0.9 means it's a solid convex shape (like circle/oval), not weird jaggy shape
                if (solidity > 0.9) {
                     // 2. Circularity check (4*pi*area / perimeter^2)
                     val circularity = (4 * Math.PI * area) / (peri * peri)
                     
                     // Improve tolerance for ovals (perspective view)
                     if (circularity > 0.6) {
                         plateFound = true
                         Log.d(TAG, "Plate detected! Area=${(area/imageArea)*100}%, Circ=$circularity")
                         break
                     }
                }
            }

            // Clean up
            mat.release()
            gray.release()
            thresh.release()
            hierarchy.release()
            contours.forEach { it.release() }

            plateFound

        } catch (e: Exception) {
            Log.e(TAG, "Error detecting plate", e)
            false
        }
    }
    
    // Helper to calculate hull area (OpenCV java doesn't have easy helper for this)
    private fun calculateHullArea(contour: MatOfPoint, hullIds: MatOfInt): Double {
        val hierarchy = Mat()
        val contourPoints = contour.toList()
        val hullPoints = hullIds.toList().map { contourPoints[it] }
        val hullContour = MatOfPoint()
        hullContour.fromList(hullPoints)
        return Imgproc.contourArea(hullContour)
    }
}
