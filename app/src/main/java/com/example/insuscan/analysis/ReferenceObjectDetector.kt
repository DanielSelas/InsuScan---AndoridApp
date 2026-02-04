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
        private const val MIN_CONTOUR_AREA = 500.0 // Lowered to allow pen to be smaller in frame
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
            val syringeContour = findSyringeContour(contours, mat)

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

    // Find contour that matches syringe shape using advanced geometric constraints
    private fun findSyringeContour(contours: List<MatOfPoint>, originalImage: Mat): DetectionResult.Found? {
        var bestMatch: DetectionResult.Found? = null
        var maxConfidence = 0f

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)

            // 1. Initial Area Filter
            if (area < MIN_CONTOUR_AREA || area > MAX_CONTOUR_AREA) {
                // Log.v(TAG, "Rejected area: $area") // Too verbose
                continue
            }

            // 2. Geometric Analysis using Rotated Rectangle (MinAreaRect)
            val contour2f = MatOfPoint2f(*contour.toArray())
            val rotatedRect = Imgproc.minAreaRect(contour2f)
            
            val width = rotatedRect.size.width
            val height = rotatedRect.size.height
            
            val length = maxOf(width, height)
            val thickness = minOf(width, height)

            if (thickness == 0.0) continue

            val aspectRatio = length / thickness

            // Location-Based Logic (User Suggestion: "Ref object always on right")
            // We relax thresholds if the object is on the right side of the screen
            val centerX = rotatedRect.center.x
            val imageWidth = originalImage.cols()
            val normalizedX = centerX / imageWidth
            
            val isOnRightSide = normalizedX > 0.5
            
            // Dynamic Thresholds based on location
            val minRatio = if (isOnRightSide) 3.5 else 4.0
            val minRect = if (isOnRightSide) 0.6 else 0.7

            // 3. Aspect Ratio Check
            if (aspectRatio < minRatio || aspectRatio > MAX_ASPECT_RATIO) {
                continue
            }

            // 4. Rectangularity Check
            val rectArea = width * height
            val rectangularity = area / rectArea
            if (rectangularity < minRect) {
                continue
            }

            // 5. Straight Edge Verification
            val roi = getRoI(originalImage, rotatedRect)
            val parallelScore = calculateParallelLineScore(roi)
            
            // Relaxed Logic: If shape is very good OR (It's pretty good AND on right side)
            // If on right side, we accept almost any rectangular shape with correct ratio
            val usesStrongShapeFallback = if (isOnRightSide) {
                // Right side: Just needs to be rectangular-ish
                rectangularity > 0.65 
            } else {
                // Left side: Needs to be VERY rectangular to ignore lines
                aspectRatio > 6.0 && rectangularity > 0.75
            }

            if (parallelScore < 0.2 && !usesStrongShapeFallback) {
                continue
            }

            // Calculate confidence
            val confidence = calculateConfidence(aspectRatio, rectangularity, parallelScore)

            if (confidence > maxConfidence) {
                maxConfidence = confidence
                val pixelToCmRatio = (syringeLengthCm / length).toFloat() // Scale factor based on real length
                
                Log.d(TAG, "Syringe detected: L=${length.toInt()}px, Ratio=$aspectRatio, Conf=$confidence")

                bestMatch = DetectionResult.Found(
                    boundingBox = rotatedRect.boundingRect(), // Convert rotated back to standard rect for UI drawing if needed
                    center = rotatedRect.center,
                    angle = rotatedRect.angle,
                    lengthPixels = length,
                    pixelToCmRatio = pixelToCmRatio,
                    confidence = confidence
                )
            }
        }
        return bestMatch
    }

    /**
     * Extracts a Region of Interest (ROI) from the image based on the rotated rectangle.
     */
    private fun getRoI(image: Mat, rect: RotatedRect): Mat {
        // Create the rotation matrix
        val rotationMatrix = Imgproc.getRotationMatrix2D(rect.center, rect.angle, 1.0)
        
        // Rotate the entire image to align the pen horizontally/vertically
        val rotatedImage = Mat()
        Imgproc.warpAffine(image, rotatedImage, rotationMatrix, image.size(), Imgproc.INTER_LINEAR)
        
        // Extract the crop
        val cropSize = rect.size
        val crop = Mat()
        // Ensure roi is within bounds
        try {
            Imgproc.getRectSubPix(rotatedImage, cropSize, rect.center, crop)
        } catch (e: Exception) {
            // Fallback if crop fails (e.g. edge of image), return empty to skip
            return Mat()
        }
        return crop
    }

    /**
     * Checks if the ROI contains parallel straight lines (characteristic of a pen).
     */
    private fun calculateParallelLineScore(roi: Mat): Float {
        if (roi.empty()) return 0f

        val edges = Mat()
        Imgproc.Canny(roi, edges, 50.0, 150.0)

        val lines = Mat()
        // HoughLinesP to find probabilistic line segments
        Imgproc.HoughLinesP(edges, lines, 1.0, Math.PI / 180, 20, 30.0, 10.0)

        if (lines.rows() < 2) return 0f // Need at least 2 lines for parallel check

        // Check for parallel lines
        // Since we rotated the ROI, the pen lines should be roughly horizontal (angle ~ 0 or 180) 
        // OR vertical (angle ~ 90) depending on how minAreaRect oriented it.
        // minAreaRect usually gives width/height such that we can infer orientation.
        
        var parallelLinesCount = 0
        for (i in 0 until lines.rows()) {
            val l = lines.get(i, 0)
            val dx = l[2] - l[0]
            val dy = l[3] - l[1]
            val angle = kotlin.math.abs(kotlin.math.atan2(dy, dx) * 180 / Math.PI)
            
            // We expect lines to be aligned with the box sides (either ~0 or ~90 degrees relative to crop)
            if (angle < 10 || angle > 170 || (angle > 80 && angle < 100)) {
                parallelLinesCount++
            }
        }
        
        return parallelLinesCount.toFloat() / lines.rows().toFloat()
    }

    private fun calculateConfidence(aspectRatio: Double, rectangularity: Double, parallelScore: Float): Float {
        val idealRatio = 10.0
        val ratioScore = (1.0 - (kotlin.math.abs(aspectRatio - idealRatio) / idealRatio)).coerceIn(0.0, 1.0)
        
        // Weighted sum
        return ((ratioScore * 0.4 + rectangularity * 0.4 + parallelScore * 0.2) * 100).toInt() / 100f
    }

    // Check if OpenCV is ready
    fun isReady(): Boolean = isOpenCvInitialized
}

// Result of reference object detection
sealed class DetectionResult {
    data class Found(
        val boundingBox: org.opencv.core.Rect,
        val center: org.opencv.core.Point,
        val angle: Double,
        val lengthPixels: Double,
        val pixelToCmRatio: Float,  // cm per pixel
        val confidence: Float       // 0.0 to 1.0
    ) : DetectionResult()

    data class NotFound(
        val reason: String
    ) : DetectionResult()
}