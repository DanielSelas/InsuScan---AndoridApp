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

        // Known object dimensions (can be configured)
        const val DEFAULT_SYRINGE_LENGTH_CM = 12.0f
        const val CARD_WIDTH_CM = 8.56f // ID-1 Standard Width
        const val CARD_HEIGHT_CM = 5.398f // ID-1 Standard Height
        const val CARD_RATIO = CARD_WIDTH_CM / CARD_HEIGHT_CM // ~1.585

        // Detection thresholds
        private const val MIN_CONTOUR_AREA = 500.0 
        private const val MAX_CONTOUR_AREA = 300000.0
        private const val MIN_ASPECT_RATIO = 5.0 // For Pens
        private const val MAX_ASPECT_RATIO = 50.0 
    }

    private var isOpenCvInitialized = false
    private var expectedObjectLengthCm = DEFAULT_SYRINGE_LENGTH_CM

    fun initialize(): Boolean {
        isOpenCvInitialized = OpenCVLoader.initLocal()
        Log.d(TAG, "OpenCV initialized: $isOpenCvInitialized")
        return isOpenCvInitialized
    }

    // ... (initialize same)

    fun setExpectedObjectLength(lengthCm: Float) {
        expectedObjectLengthCm = lengthCm
        Log.d(TAG, "Expected Object length set to $lengthCm cm")
    }

    enum class DetectionMode {
        STRICT,   // Pen/Syringe (High aspect ratio)
        FLEXIBLE, // Cutlery (Medium/High ratio)
        CARD      // Credit Card (Specific ratio ~1.58)
    }

    // Helper class for debugging detection logic
    private class DebugStats {
        var totalContours = 0
        var tooSmall = 0
        var tooLarge = 0
        var badRatio = 0
        var badSolidity = 0
        var lowConfidence = 0
        var candidates = 0
        
        override fun toString(): String {
            return "T:$totalContours S:$tooSmall L:$tooLarge R:$badRatio Sol:$badSolidity Conf:$lowConfidence Can:$candidates"
        }
    }

    fun detectReferenceObject(
        bitmap: Bitmap, 
        plateBounds: android.graphics.Rect? = null,
        mode: DetectionMode = DetectionMode.STRICT
    ): DetectionResult {
        if (!isOpenCvInitialized) {
            Log.e(TAG, "OpenCV not initialized")
            return DetectionResult.NotFound("OpenCV not initialized", "")
        }

        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGB2GRAY)

        // Gaussian Blur
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

        // Canny Edge Detection
        val edges = Mat()
        Imgproc.Canny(gray, edges, 50.0, 150.0)

        // Find Contours
        val contours = ArrayList<MatOfPoint>()
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
        val referenceObject = findBestReferenceObject(contours, gray, plateBounds, mode, debugStats)

        // Cleanup
        mat.release()
        gray.release()
        edges.release()
        hierarchy.release()
        // Contours list elements released by GC, but clearer to not release

        if (referenceObject != null) {
            return referenceObject
        }
        
        // Format debug stats
        val debugInfo = "Contours: ${debugStats.totalContours}, Small: ${debugStats.tooSmall}, Large: ${debugStats.tooLarge}, BadRatio: ${debugStats.badRatio}, LowConf: ${debugStats.lowConfidence}"

        return DetectionResult.NotFound("No valid reference object found", debugInfo)
    }

    // ... (DebugStats same)

    private fun findBestReferenceObject(
        contours: List<MatOfPoint>, 
        originalImage: Mat, 
        plateBounds: android.graphics.Rect?,
        mode: DetectionMode,
        stats: DebugStats
    ): DetectionResult.Found? {
        
        val candidates = mutableListOf<DetectionResult.Found>()

        // ... (plateCenter logic same)
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

            // --- Mode Specific Logic ---
            var confidence = 0.0f
            var isCandidate = false

            when (mode) {
                DetectionMode.STRICT -> {
                    // Pens must be thin (High Ratio > 4.0)
                    if (aspectRatio >= 4.0 && aspectRatio <= MAX_ASPECT_RATIO) {
                         // Rectangularity check
                         val rectArea = width * height
                         val rectangularity = area / rectArea
                         if (rectangularity > 0.8) {
                             confidence = 0.9f 
                             isCandidate = true
                         } else { stats.badSolidity++ }
                    } else { stats.badRatio++ }
                }
                
                DetectionMode.FLEXIBLE -> {
                    // Cutlery allow ratio > 2.0
                    if (aspectRatio >= 2.0 && aspectRatio <= MAX_ASPECT_RATIO) {
                         confidence = 0.7f
                         isCandidate = true
                    }
                }
                
                DetectionMode.CARD -> {
                    // Card Ratio ~1.58. Allow range [1.4, 1.8]
                    // Must be very rectangular
                    if (aspectRatio >= 1.4 && aspectRatio <= 1.8) {
                         val rectArea = width * height
                         val rectangularity = area / rectArea
                         
                         // Cards are very rectangular (no rounded cutlery handles)
                         if (rectangularity > 0.85) {
                             confidence = 0.95f // Cards are easier to detect confidently
                             isCandidate = true
                         } else { stats.badSolidity++ }
                    } else { stats.badRatio++ }
                }
            }

            if (!isCandidate) continue

            // Success! Create candidate
            // Calculate scale based on object type
            // For CARD, length is 8.56cm (or height 5.4cm). 
            // rotRect gives width/height relative to angle. 
            // length = max dimension = 8.56cm
            val realWorldLength = if (mode == DetectionMode.CARD) CARD_WIDTH_CM else expectedObjectLengthCm
            val pixelToCmRatio = (realWorldLength / length).toFloat()
            
            candidates.add(DetectionResult.Found(
                boundingBox = rotatedRect.boundingRect(),
                center = rotatedRect.center,
                angle = rotatedRect.angle,
                lengthPixels = length,
                pixelToCmRatio = pixelToCmRatio,
                confidence = confidence,
                debugInfo = "" 
            ))
        }
        
        stats.candidates = candidates.size
        if (candidates.isEmpty()) return null
        
        // Selection Logic (Simplified)
        return candidates.maxByOrNull { it.confidence }?.copy(debugInfo = stats.toString())
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