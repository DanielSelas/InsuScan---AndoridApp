package com.example.insuscan.analysis

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

/**
 * Detects insulin syringe (reference object) in image using OpenCV
 * Uses the syringe to calculate real-world scale of the food plate
 */
class ReferenceObjectDetector(private val context: Context) {

    companion object {
        private const val TAG = "RefObjectDetector"

        // Known syringe dimensions in cm (can be configured per user)
        const val DEFAULT_SYRINGE_LENGTH_CM = 12.0f
        const val DEFAULT_SYRINGE_WIDTH_CM = 1.2f

        // Detection thresholds
        private const val MIN_CONTOUR_AREA = 1000.0
        private const val MAX_CONTOUR_AREA = 50000.0
        private const val MIN_ASPECT_RATIO = 5.0   // syringe is long and thin
        private const val MAX_ASPECT_RATIO = 15.0
    }

    private var isOpenCvInitialized = false
    private var syringeLengthCm = DEFAULT_SYRINGE_LENGTH_CM

    /**
     * Initialize OpenCV library
     */
    fun initialize(): Boolean {
        return try {
            isOpenCvInitialized = OpenCVLoader.initLocal()
            if (isOpenCvInitialized) {
                Log.d(TAG, "OpenCV initialized successfully")
            } else {
                Log.e(TAG, "OpenCV initialization failed")
            }
            isOpenCvInitialized
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing OpenCV", e)
            false
        }
    }

    /**
     * Set the known syringe length from user profile
     */
    fun setSyringeLength(lengthCm: Float) {
        syringeLengthCm = lengthCm
        Log.d(TAG, "Syringe length set to $lengthCm cm")
    }

    /**
     * Detect reference object (syringe) in the image
     * Returns detection result with position and pixel-to-cm ratio
     */
    fun detectReferenceObject(bitmap: Bitmap): DetectionResult {
        if (!isOpenCvInitialized) {
            Log.w(TAG, "OpenCV not initialized, using fallback")
            return DetectionResult.NotFound("OpenCV not initialized")
        }

        return try {
            // Convert bitmap to OpenCV Mat
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)

            // Convert to grayscale
            val gray = Mat()
            Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)

            // Apply blur to reduce noise
            val blurred = Mat()
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

            // Edge detection
            val edges = Mat()
            Imgproc.Canny(blurred, edges, 50.0, 150.0)

            // Find contours
            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(
                edges,
                contours,
                hierarchy,
                Imgproc.RETR_EXTERNAL,
                Imgproc.CHAIN_APPROX_SIMPLE
            )

            // Find syringe-like contour
            val syringeContour = findSyringeContour(contours)

            // Clean up
            mat.release()
            gray.release()
            blurred.release()
            edges.release()
            hierarchy.release()
            contours.forEach { it.release() }

            if (syringeContour != null) {
                syringeContour
            } else {
                DetectionResult.NotFound("Syringe not detected in image")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error detecting reference object", e)
            DetectionResult.NotFound("Detection error: ${e.message}")
        }
    }

    // Find contour that matches syringe shape
    private fun findSyringeContour(contours: List<MatOfPoint>): DetectionResult.Found? {
        for (contour in contours) {
            val area = Imgproc.contourArea(contour)

            // Filter by area
            if (area < MIN_CONTOUR_AREA || area > MAX_CONTOUR_AREA) {
                continue
            }

            // Get bounding rectangle
            val rect = Imgproc.boundingRect(contour)
            val aspectRatio = rect.width.toDouble() / rect.height.toDouble()

            // Syringe is long and thin - check aspect ratio
            val normalizedRatio = if (aspectRatio < 1) 1 / aspectRatio else aspectRatio

            if (normalizedRatio in MIN_ASPECT_RATIO..MAX_ASPECT_RATIO) {
                // Found potential syringe!
                val lengthPixels = maxOf(rect.width, rect.height)
                val pixelToCmRatio = syringeLengthCm / lengthPixels

                Log.d(TAG, "Syringe detected: ${rect.width}x${rect.height} px, ratio: $pixelToCmRatio")

                return DetectionResult.Found(
                    boundingBox = rect,
                    lengthPixels = lengthPixels,
                    pixelToCmRatio = pixelToCmRatio,
                    confidence = calculateConfidence(normalizedRatio, area)
                )
            }
        }
        return null
    }

    // Calculate detection confidence based on shape match
    private fun calculateConfidence(aspectRatio: Double, area: Double): Float {
        // Higher confidence for ideal aspect ratio (~10:1) and medium area
        val idealRatio = 10.0
        val ratioScore = 1.0 - (kotlin.math.abs(aspectRatio - idealRatio) / idealRatio)

        val idealArea = 15000.0
        val areaScore = 1.0 - (kotlin.math.abs(area - idealArea) / idealArea).coerceAtMost(1.0)

        return ((ratioScore * 0.7 + areaScore * 0.3) * 100).toInt() / 100f
    }

    // Check if OpenCV is ready
    fun isReady(): Boolean = isOpenCvInitialized
}

// Result of reference object detection
sealed class DetectionResult {
    data class Found(
        val boundingBox: org.opencv.core.Rect,
        val lengthPixels: Int,
        val pixelToCmRatio: Float,  // cm per pixel
        val confidence: Float       // 0.0 to 1.0
    ) : DetectionResult()

    data class NotFound(
        val reason: String
    ) : DetectionResult()
}