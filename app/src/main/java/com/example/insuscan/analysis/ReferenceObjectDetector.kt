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
        const val CARD_RATIO = CARD_WIDTH_CM / CARD_HEIGHT_CM

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
                             // Minimum width check: card's short side must be substantial
                             // A credit card at normal photo distance should have its short edge
                             // be at least ~5% of image width. This rejects thin objects like syringes.
                             val imgWidth = originalImage.cols().toDouble()
                             val minThickness = imgWidth * 0.05
                             if (thickness >= minThickness) {
                                 confidence = 0.95f // Cards are easier to detect confidently
                                 isCandidate = true
                             } else {
                                 stats.badSolidity++ // Reuse counter: too thin for a card
                                 Log.d(TAG, "CARD rejected: thickness=${thickness.toInt()}px < min=${minThickness.toInt()}px (too thin, likely not a card)")
                             }
                         } else { stats.badSolidity++ }
                    } else { stats.badRatio++ }
                }
            }

            if (!isCandidate) continue

            // Success! Create candidate
            // Calculate scale based on object type
            val realWorldLength = if (mode == DetectionMode.CARD) CARD_WIDTH_CM else expectedObjectLengthCm
            var pixelToCmRatio = (realWorldLength / length).toFloat()

            // For CARD mode: attempt homography for perspective correction
            if (mode == DetectionMode.CARD) {
                val correctedRatio = computeCardHomographyRatio(contour)
                if (correctedRatio != null) {
                    pixelToCmRatio = correctedRatio
                    confidence = 0.97f // Higher confidence with homography
                    Log.d(TAG, "Homography corrected ratio: $pixelToCmRatio")
                }
            }
            
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

    /**
     * Computes a perspective-corrected pixelToCmRatio for credit card using homography.
     * Finds 4 corners of the card contour, applies getPerspectiveTransform with known
     * card dimensions (8.56 x 5.398 cm), and derives an accurate ratio.
     * Returns null if homography cannot be computed (e.g., < 4 corners found).
     */
    private fun computeCardHomographyRatio(contour: MatOfPoint): Float? {
        return try {
            val contour2f = MatOfPoint2f(*contour.toArray())
            val peri = Imgproc.arcLength(contour2f, true)
            val approxCurve = MatOfPoint2f()
            Imgproc.approxPolyDP(contour2f, approxCurve, 0.02 * peri, true)

            // Need exactly 4 corners for a card
            if (approxCurve.rows() != 4) {
                Log.d(TAG, "Homography: expected 4 corners, got ${approxCurve.rows()}")
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
            // Use 856 x 540 pixels to represent 8.56 x 5.40 cm (100px per cm)
            val pxPerCm = 100.0
            val dstW = CARD_WIDTH_CM * pxPerCm
            val dstH = CARD_HEIGHT_CM * pxPerCm

            val dstPoints = MatOfPoint2f(
                org.opencv.core.Point(0.0, 0.0),
                org.opencv.core.Point(dstW, 0.0),
                org.opencv.core.Point(dstW, dstH),
                org.opencv.core.Point(0.0, dstH)
            )

            val perspectiveMatrix = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)

            if (perspectiveMatrix.empty()) {
                Log.w(TAG, "Homography: getPerspectiveTransform returned empty matrix")
                return null
            }

            // The ratio is: how many cm per source pixel
            // We mapped source card corners → destination of dstW x dstH pixels
            // at pxPerCm resolution. So the ratio = 1/pxPerCm = 0.01 cm/px
            // But we need to account for the actual source pixel span.
            // Width in source pixels = distance between TL and TR
            val srcWidthPx = Math.sqrt(
                Math.pow(topTwo[1].x - topTwo[0].x, 2.0) +
                Math.pow(topTwo[1].y - topTwo[0].y, 2.0)
            )

            if (srcWidthPx < 10.0) {
                Log.w(TAG, "Homography: source card width too small ($srcWidthPx px)")
                return null
            }

            val correctedRatio = (CARD_WIDTH_CM / srcWidthPx).toFloat()
            Log.d(TAG, "Homography: srcWidthPx=$srcWidthPx → correctedRatio=$correctedRatio cm/px")

            perspectiveMatrix.release()
            correctedRatio
        } catch (e: Exception) {
            Log.w(TAG, "Homography computation failed: ${e.message}")
            null
        }
    }

    /**
     * Smart detection with fallback: tries the selected mode first, then all other modes.
     * Returns which mode actually succeeded, so the UI can inform the user.
     */
    fun detectWithFallback(
        bitmap: android.graphics.Bitmap,
        plateBounds: android.graphics.Rect?,
        selectedMode: DetectionMode
    ): FallbackDetectionResult {
        val mat = org.opencv.core.Mat()
        org.opencv.android.Utils.bitmapToMat(bitmap, mat)
        mat.release()

        // 1. Try the mode the user selected
        Log.d(TAG, "Smart detect: trying selected mode $selectedMode")
        val primaryResult = detectReferenceObject(bitmap, plateBounds, selectedMode)
        if (primaryResult is DetectionResult.Found) {
            Log.d(TAG, "Smart detect: found with selected mode $selectedMode")
            return FallbackDetectionResult(
                result = primaryResult,
                detectedMode = selectedMode,
                isAlternative = false
            )
        }

        // 2. Try all other modes
        val allModes = DetectionMode.entries.filter { it != selectedMode }
        for (fallbackMode in allModes) {
            Log.d(TAG, "Smart detect: trying fallback mode $fallbackMode")

            // Set correct expected length for the fallback mode
            val originalLength = expectedObjectLengthCm
            when (fallbackMode) {
                DetectionMode.CARD -> { /* Card uses fixed constant, no need to set */ }
                DetectionMode.STRICT -> setExpectedObjectLength(DEFAULT_SYRINGE_LENGTH_CM)
                DetectionMode.FLEXIBLE -> setExpectedObjectLength(DEFAULT_SYRINGE_LENGTH_CM)
            }

            val fallbackResult = detectReferenceObject(bitmap, plateBounds, fallbackMode)

            // Restore original length
            expectedObjectLengthCm = originalLength

            if (fallbackResult is DetectionResult.Found) {
                Log.d(TAG, "Smart detect: found ALTERNATIVE with mode $fallbackMode (instead of $selectedMode)")
                return FallbackDetectionResult(
                    result = fallbackResult,
                    detectedMode = fallbackMode,
                    isAlternative = true
                )
            }
        }

        // 3. Nothing found in any mode
        Log.d(TAG, "Smart detect: no reference object found in any mode")
        return FallbackDetectionResult(
            result = DetectionResult.NotFound("No reference object found in any mode", ""),
            detectedMode = null,
            isAlternative = false
        )
    }
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

/**
 * Result of smart fallback detection, includes which mode actually detected the object.
 */
data class FallbackDetectionResult(
    val result: DetectionResult,
    val detectedMode: ReferenceObjectDetector.DetectionMode?,
    val isAlternative: Boolean  // true if a DIFFERENT mode than selected found the object
)