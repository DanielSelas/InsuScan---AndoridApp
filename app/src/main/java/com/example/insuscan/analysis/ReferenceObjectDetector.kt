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
        private const val MIN_CONTOUR_AREA = 500.0 
        private const val MAX_CONTOUR_AREA = 300000.0 // Increased to support large cutlery/knives close up
        private const val MIN_ASPECT_RATIO = 5.0
        private const val MAX_ASPECT_RATIO = 50.0 // Increased to support chopsticks
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
    enum class DetectionMode {
        STRICT,   // Expects a Syringe/Pen: High solidity, no gaps, specific ratio
        FLEXIBLE  // Expects Cutlery/Other: Relaxed solidity (allows forks), prioritizes location
    }

    /**
     * Detect reference object (syringe/cutlery) in the image
     * Returns detection result with position and pixel-to-cm ratio
     * @param plateBounds Optional bounding box of the detected plate to prioritize objects near it
     * @param mode Detection mode (Strict/Flexible)
     */
    fun detectReferenceObject(
        bitmap: Bitmap, 
        plateBounds: android.graphics.Rect? = null,
        mode: DetectionMode = DetectionMode.STRICT
    ): DetectionResult {
        if (!isOpenCvInitialized) {
            Log.w(TAG, "OpenCV not initialized, using fallback")
            return DetectionResult.NotFound("OpenCV not initialized", "")
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

            // Find best reference object based on Mode
            val debugStats = DebugStats()
            // Pass 'gray' instead of 'mat' because getRectSubPix only supports 1 or 3 channels, and mat is RGBA (4 channels)
            val referenceObject = findBestReferenceObject(contours, gray, plateBounds, mode, debugStats)

            // Clean up
            mat.release()
            gray.release()
            blurred.release()
            edges.release()
            hierarchy.release()
            contours.forEach { it.release() }

            if (referenceObject != null) {
                // Attach debug info to Found result (requires updating Found data class)
                referenceObject 
            } else {
                DetectionResult.NotFound("Reference object not detected", debugStats.toString())
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error detecting reference object", e)
            DetectionResult.NotFound("Detection error: ${e.message}", "")
        }
    }

    private class DebugStats {
        var totalContours = 0
        var tooSmall = 0
        var tooLarge = 0
        var badRatio = 0
        var badSolidity = 0
        var badParallel = 0
        var wrongSide = 0
        var candidates = 0

        override fun toString(): String {
            return "Total:$totalContours, Small:$tooSmall, Large:$tooLarge, Ratio:$badRatio, Side:$wrongSide, OK:$candidates"
        }
    }

    // Find best candidate for reference object
    private fun findBestReferenceObject(
        contours: List<MatOfPoint>, 
        originalImage: Mat, 
        plateBounds: android.graphics.Rect?,
        mode: DetectionMode,
        stats: DebugStats
    ): DetectionResult.Found? {
        
        val candidates = mutableListOf<DetectionResult.Found>()

        val plateCenter = plateBounds?.let { 
            Point(it.centerX().toDouble(), it.centerY().toDouble()) 
        }
        
        stats.totalContours = contours.size

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)

            if (area < MIN_CONTOUR_AREA) { stats.tooSmall++; continue }
            if (area > MAX_CONTOUR_AREA) { stats.tooLarge++; continue }

            val contour2f = MatOfPoint2f(*contour.toArray())
            val rotatedRect = Imgproc.minAreaRect(contour2f)
            
            val width = rotatedRect.size.width
            val height = rotatedRect.size.height
            val length = maxOf(width, height)
            val thickness = minOf(width, height)

            if (thickness == 0.0) continue
            val aspectRatio = length / thickness

            // --- Mode Specific Thresholds ---
            val minRatio = if (mode == DetectionMode.STRICT) 4.0 else 2.0 
            
            // 1. Aspect Ratio Check
            if (aspectRatio < minRatio || aspectRatio > MAX_ASPECT_RATIO) {
                stats.badRatio++
                continue
            }

            // 2. Structural Checks (Strict Only)
            var confidence = 1.0f
            
            if (mode == DetectionMode.STRICT) {
                // Strict: Must be rectangular and straight (Pen/Syringe)
                val rectArea = width * height
                val rectangularity = area / rectArea
                if (rectangularity < 0.8) { stats.badSolidity++; continue }

                val roi = getRoI(originalImage, rotatedRect)
                val parallelScore = calculateParallelLineScore(roi)
                if (parallelScore < 0.3) { stats.badParallel++; continue }
                
                confidence = calculateConfidence(aspectRatio, rectangularity, parallelScore)
            } else {
                // Flexible: We ACCEPT anything elongated near the plate.
                // We trust the Aspect Ratio check ( > 2.0 ) is enough to filter out food blobs.
                // No rectangularity or parallel check.
                confidence = 0.8f // Default confidence for Flexible
            }

            // Success! Create candidate
            val pixelToCmRatio = (syringeLengthCm / length).toFloat()
            
            candidates.add(DetectionResult.Found(
                boundingBox = rotatedRect.boundingRect(),
                center = rotatedRect.center,
                angle = rotatedRect.angle,
                lengthPixels = length,
                pixelToCmRatio = pixelToCmRatio,
                confidence = confidence,
                debugInfo = "" // Placeholder, filled later
            ))
        }
        
        stats.candidates = candidates.size
        
        if (candidates.isEmpty()) return null
        
        // --- Selection Logic ---
        val bestCandidate = if (mode == DetectionMode.FLEXIBLE) {
            
            if (plateCenter != null) {
                 // 1. Prioritize Valid Candidates on the RIGHT of the plate
                 val rightSideCandidates = candidates.filter { it.center.x > plateCenter.x }
                 
                 if (rightSideCandidates.isNotEmpty()) {
                     // Pick closest to plate among those on the right
                     rightSideCandidates.minByOrNull { candidate ->
                        val dx = candidate.center.x - plateCenter.x
                        val dy = candidate.center.y - plateCenter.y
                        kotlin.math.sqrt(dx * dx + dy * dy)
                     }
                 } else {
                     stats.wrongSide = candidates.size // All candidates were on the wrong side
                     // Fallback: Closest to plate (even if on Left/Top/Bottom) - maybe user didn't follow guide
                     candidates.minByOrNull { candidate ->
                        val dx = candidate.center.x - plateCenter.x
                        val dy = candidate.center.y - plateCenter.y
                        kotlin.math.sqrt(dx * dx + dy * dy)
                     }
                 }
            } else {
                // No Plate detected? Determine "Right" using image width
                val imageWidth = originalImage.cols().toDouble()
                val rightSideCandidates = candidates.filter { it.center.x > (imageWidth * 0.5) }
                
                if (rightSideCandidates.isNotEmpty()) {
                    rightSideCandidates.maxByOrNull { it.confidence }
                } else {
                    stats.wrongSide = candidates.size
                    candidates.maxByOrNull { it.confidence }
                }
            }
        } else {
            // Strict OR Fallback (if no Flexible candidates found on right/anywhere)
            candidates.maxByOrNull { it.confidence }
        }

        return bestCandidate?.copy(debugInfo = stats.toString())
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
        val confidence: Float,       // 0.0 to 1.0
        val debugInfo: String
    ) : DetectionResult()

    data class NotFound(
        val reason: String,
        val debugInfo: String
    ) : DetectionResult()
}