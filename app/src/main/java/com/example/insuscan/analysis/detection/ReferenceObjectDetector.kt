package com.example.insuscan.analysis.detection

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.insuscan.analysis.detection.strategy.*
import com.example.insuscan.analysis.detection.util.ReferenceDebugStats
import com.example.insuscan.analysis.model.DetectionResult
import com.example.insuscan.analysis.model.FallbackDetectionResult
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

/**
 * Detects a reference object (insulin syringe, cutlery, credit card) in the image using OpenCV.
 * Uses the reference object to calculate real-world scale of the food plate.
 */
class ReferenceObjectDetector(private val context: Context) {

    companion object {
        private const val TAG = "RefObjectDetector"

        // Known object dimensions (can be configured)
        const val DEFAULT_SYRINGE_LENGTH_CM = 12.0f
        const val CARD_WIDTH_CM = 8.56f
        const val CARD_HEIGHT_CM = 5.398f
        const val CARD_RATIO = CARD_WIDTH_CM / CARD_HEIGHT_CM

        // Detection area thresholds
        private const val MIN_CONTOUR_AREA = 1000.0
        private const val MAX_CONTOUR_AREA = 500000.0

        // OpenCV Magic Numbers extracted
        private const val BLUR_KERNEL_SIZE = 5.0
        private const val BLUR_SIGMA = 0.0
        private const val CANNY_THRESHOLD_LOW = 50.0
        private const val CANNY_THRESHOLD_HIGH = 150.0
        
        // Relative sizing logic
        private const val REF_RESOLUTION_PIXELS = 2_000_000.0
    }

    private var isOpenCvInitialized = false
    private var expectedObjectLengthCm = DEFAULT_SYRINGE_LENGTH_CM
    private var expectedObjectWidthCm = 1.0f
    private var expectedObjectHeightCm = 1.0f

    /** Detection mode determines which aspect ratio and solidity thresholds to use. */
    enum class DetectionMode {
        STRICT,   // Pen/Syringe (High aspect ratio)
        FLEXIBLE, // Cutlery (Medium/High ratio)
        CARD      // Credit Card (Specific ratio ~1.58)
    }

    private val strategies = mapOf(
        DetectionMode.STRICT to StrictReferenceStrategy(),
        DetectionMode.FLEXIBLE to FlexibleReferenceStrategy(),
        DetectionMode.CARD to CardReferenceStrategy()
    )

    fun initialize(): Boolean {
        isOpenCvInitialized = OpenCVLoader.initLocal()
        Log.d(TAG, "OpenCV initialized: $isOpenCvInitialized")
        return isOpenCvInitialized
    }

    fun setExpectedObjectDimensions(lengthCm: Float, widthCm: Float, heightCm: Float) {
        expectedObjectLengthCm = lengthCm
        expectedObjectWidthCm = widthCm
        expectedObjectHeightCm = heightCm
        Log.d(TAG, "Expected Object dimensions set to ${lengthCm}x${widthCm}x${heightCm} cm")
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

        val blurSize = Size(BLUR_KERNEL_SIZE, BLUR_KERNEL_SIZE)
        Imgproc.GaussianBlur(gray, gray, blurSize, BLUR_SIGMA)

        val edges = Mat()
        Imgproc.Canny(gray, edges, CANNY_THRESHOLD_LOW, CANNY_THRESHOLD_HIGH)

        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            edges, contours, hierarchy,
            Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
        )

        val debugStats = ReferenceDebugStats()
        val referenceObject = findBestReferenceObject(contours, gray, plateBounds, mode, debugStats)

        mat.release()
        gray.release()
        edges.release()
        hierarchy.release()

        if (referenceObject != null) {
            return referenceObject
        }

        val debugInfo = "Contours: ${debugStats.totalContours}, Small: ${debugStats.tooSmall}, " +
                "Large: ${debugStats.tooLarge}, BadRatio: ${debugStats.badRatio}, LowConf: ${debugStats.lowConfidence}"

        return DetectionResult.NotFound("No valid reference object found", debugInfo)
    }

    private fun findBestReferenceObject(
        contours: List<MatOfPoint>,
        originalImage: Mat,
        plateBounds: android.graphics.Rect?,
        mode: DetectionMode,
        stats: ReferenceDebugStats
    ): DetectionResult.Found? {

        val candidates = mutableListOf<DetectionResult.Found>()
        stats.totalContours = contours.size

        val imgWidth = originalImage.cols().toDouble()
        val imgHeight = originalImage.rows().toDouble()
        val totalPixels = imgWidth * imgHeight

        val scaleFactor = totalPixels / REF_RESOLUTION_PIXELS
        val minArea = MIN_CONTOUR_AREA * scaleFactor
        val maxArea = MAX_CONTOUR_AREA * scaleFactor

        val strategy = strategies[mode] ?: return null

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area < minArea) { stats.tooSmall++; continue }
            if (area > maxArea) { stats.tooLarge++; continue }

            val contour2f = MatOfPoint2f(*contour.toArray())
            val rotatedRect = Imgproc.minAreaRect(contour2f)

            val candidate = strategy.evaluateContour(
                contour = contour,
                rotatedRect = rotatedRect,
                area = area,
                imgWidth = imgWidth,
                expectedLengthCm = expectedObjectLengthCm,
                expectedWidthCm = expectedObjectWidthCm,
                stats = stats
            )

            if (candidate != null) {
                candidates.add(candidate)
            }
        }

        stats.candidates = candidates.size
        if (candidates.isEmpty()) return null

        return candidates.maxByOrNull { it.confidence }?.copy(debugInfo = stats.toString())
    }

    /**
     * Smart detection with fallback: tries the selected mode first, then all other modes.
     * Returns which mode actually succeeded, so the UI can inform the user.
     */
    fun detectWithFallback(
        bitmap: Bitmap,
        plateBounds: android.graphics.Rect?,
        selectedMode: DetectionMode
    ): FallbackDetectionResult {
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

        val allModes = DetectionMode.entries.filter { it != selectedMode }
        for (fallbackMode in allModes) {
            Log.d(TAG, "Smart detect: trying fallback mode $fallbackMode")

            val originalLength = expectedObjectLengthCm
            val originalWidth = expectedObjectWidthCm
            val originalHeight = expectedObjectHeightCm

            when (fallbackMode) {
                DetectionMode.CARD -> setExpectedObjectDimensions(8.5f, 5.5f, 0f)
                DetectionMode.STRICT -> setExpectedObjectDimensions(DEFAULT_SYRINGE_LENGTH_CM, 1.25f, 1.25f)
                DetectionMode.FLEXIBLE -> setExpectedObjectDimensions(DEFAULT_SYRINGE_LENGTH_CM, 1.5f, 1.5f)
            }

            val fallbackResult = detectReferenceObject(bitmap, plateBounds, fallbackMode)

            setExpectedObjectDimensions(originalLength, originalWidth, originalHeight)

            if (fallbackResult is DetectionResult.Found) {
                Log.d(TAG, "Smart detect: found ALTERNATIVE with mode $fallbackMode (instead of $selectedMode)")
                return FallbackDetectionResult(
                    result = fallbackResult,
                    detectedMode = fallbackMode,
                    isAlternative = true
                )
            }
        }

        Log.d(TAG, "Smart detect: no reference object found in any mode")
        return FallbackDetectionResult(
            result = DetectionResult.NotFound("No reference object found in any mode", ""),
            detectedMode = null,
            isAlternative = false
        )
    }

    fun isReady(): Boolean = isOpenCvInitialized
}
