package com.example.insuscan.analysis.detection

import android.graphics.Bitmap
import android.util.Log
import com.example.insuscan.analysis.detection.strategy.*
import com.example.insuscan.analysis.model.PlateDetectionResult
import com.example.insuscan.analysis.model.ShapeType
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Detects food plate (circular/elliptical/rectangular shape) in the image.
 *
 * Uses 4 strategies in sequence:
 *   1. Adaptive threshold (fast, uniform lighting)
 *   2. Canny edge detection + morphological closing (varied lighting)
 *   3. Otsu threshold with heavy morphology (global, contrast-dependent)
 *   4. Gradient magnitude (Sobel-based, catches faint edges)
 *
 * CLAHE histogram equalization is applied before processing
 * to boost contrast in poorly-lit or low-contrast images.
 */
class PlateDetector {

    companion object {
        private const val TAG = "PlateDetector"
        private const val TARGET_PROCESSING_WIDTH = 640
        private const val TEMPORAL_HISTORY_SIZE = 5
    }

    private val smoother = PlateDetectionSmoother(TEMPORAL_HISTORY_SIZE)

    // Order of execution determines priority
    private val strategies: List<PlateDetectionStrategy> = listOf(
        AdaptiveThresholdStrategy(),
        CannyEdgeStrategy(),
        OtsuThresholdStrategy(),
        GradientMagnitudeStrategy()
    )

    /**
     * Detects if a plate is present in the image and returns its bounds + shape.
     * Downscales to ~640px width for consistent detection regardless of capture resolution.
     */
    fun detectPlate(bitmap: Bitmap): PlateDetectionResult {
        return try {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)

            val origWidth = mat.width()
            val origHeight = mat.height()

            // Downscale high-res images to normalize processing
            val scaleFactor: Double
            val processingMat: Mat

            if (origWidth > TARGET_PROCESSING_WIDTH) {
                scaleFactor = origWidth.toDouble() / TARGET_PROCESSING_WIDTH.toDouble()
                val targetHeight = (origHeight / scaleFactor).toInt()
                processingMat = Mat()
                Imgproc.resize(mat, processingMat, Size(TARGET_PROCESSING_WIDTH.toDouble(), targetHeight.toDouble()))
                Log.d(TAG, "Downscaled ${origWidth}x${origHeight} → ${TARGET_PROCESSING_WIDTH}x${targetHeight} (scale=${"%.2f".format(scaleFactor)})")
            } else {
                scaleFactor = 1.0
                processingMat = mat
                Log.d(TAG, "Processing at original size: ${origWidth}x${origHeight}")
            }

            val gray = Mat()
            Imgproc.cvtColor(processingMat, gray, Imgproc.COLOR_RGBA2GRAY)

            // Apply CLAHE (Contrast Limited Adaptive Histogram Equalization)
            val clahe = Imgproc.createCLAHE(4.0, Size(6.0, 6.0))
            val enhanced = Mat()
            clahe.apply(gray, enhanced)

            val imageArea = (processingMat.width() * processingMat.height()).toDouble()

            var bestResult: PlateDetectionResult? = null

            // Iterate through strategies until one succeeds
            for (strategy in strategies) {
                bestResult = strategy.detect(enhanced, imageArea)
                if (bestResult != null) {
                    Log.d(TAG, "${strategy::class.simpleName} succeeded")
                    break
                }
                Log.d(TAG, "${strategy::class.simpleName} failed, trying next")
            }

            // Scale bounding box back to original image coordinates
            if (bestResult != null && scaleFactor > 1.0 && bestResult.bounds != null) {
                val b = bestResult.bounds
                val scaledRect = android.graphics.Rect(
                    (b.left * scaleFactor).toInt(),
                    (b.top * scaleFactor).toInt(),
                    (b.right * scaleFactor).toInt(),
                    (b.bottom * scaleFactor).toInt()
                )
                bestResult = bestResult.copy(bounds = scaledRect)
                Log.d(TAG, "Scaled bounds back: ${b.width()}x${b.height()} → ${scaledRect.width()}x${scaledRect.height()}")
            }

            // Clean up
            mat.release()
            if (processingMat !== mat) processingMat.release()
            gray.release()
            enhanced.release()

            bestResult ?: PlateDetectionResult(false, null, 0f, ShapeType.UNKNOWN)

        } catch (e: Exception) {
            Log.e(TAG, "Error detecting plate", e)
            PlateDetectionResult(false, null, 0f, ShapeType.UNKNOWN)
        }
    }

    /**
     * Detect plate with temporal smoothing for live preview.
     * Averages the last N bounding boxes to produce a stable, non-flickering result.
     * Use [detectPlate] for single-shot capture analysis (no smoothing needed).
     */
    fun detectPlateSmoothed(bitmap: Bitmap): PlateDetectionResult {
        return smoother.smooth(detectPlate(bitmap))
    }

    /** Clear temporal smoothing history (call when camera restarts or fragment pauses). */
    fun resetSmoothing() {
        smoother.reset()
    }
}
