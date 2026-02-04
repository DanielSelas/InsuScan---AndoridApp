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
    /**
     * Detects if a plate is present in the image and returns its bounds.
     */
    fun detectPlate(bitmap: Bitmap): PlateDetectionResult {
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

            var bestResult = PlateDetectionResult(false, null, 0f)
            val imageArea = (mat.width() * mat.height()).toDouble()

            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                
                // Filter by size
                if (area < imageArea * MIN_PLATE_AREA_RATIO || area > imageArea * MAX_PLATE_AREA_RATIO) {
                    continue
                }

                val peri = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
                // Circularity check (4*pi*area / perimeter^2)
                val circularity = (4 * Math.PI * area) / (peri * peri)
                
                // Improve tolerance for ovals (perspective view)
                if (circularity > 0.6) {
                    val rect = Imgproc.boundingRect(contour)
                    // Convert OpenCV Rect to Android Rect
                    val androidRect = android.graphics.Rect(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height)
                    
                    bestResult = PlateDetectionResult(true, androidRect, circularity.toFloat())
                    Log.d(TAG, "Plate detected! Area=${(area/imageArea)*100}%, Circ=$circularity")
                    break
                }
            }

            // Clean up
            mat.release()
            gray.release()
            thresh.release()
            hierarchy.release()
            contours.forEach { it.release() }

            bestResult

        } catch (e: Exception) {
            Log.e(TAG, "Error detecting plate", e)
            PlateDetectionResult(false, null, 0f)
        }
    }
    
    // Helper to calculate hull area (OpenCV java doesn't have easy helper for this) - Unused now but kept safe
    private fun calculateHullArea(contour: MatOfPoint, hullIds: MatOfInt): Double {
        val hierarchy = Mat()
        val contourPoints = contour.toList()
        val hullPoints = hullIds.toList().map { contourPoints[it] }
        val hullContour = MatOfPoint()
        hullContour.fromList(hullPoints)
        return Imgproc.contourArea(hullContour)
    }
}

data class PlateDetectionResult(
    val isFound: Boolean,
    val bounds: android.graphics.Rect?, // The bounding box of the plate
    val confidence: Float
)
