package com.example.insuscan.analysis

import android.graphics.Bitmap
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

/**
 * Shape types for detected plates/bowls
 */
enum class ShapeType {
    CIRCULAR,      // major/minor axis ratio < 1.2
    OVAL,          // major/minor axis ratio 1.2 - 2.0
    RECTANGULAR,   // approxPolyDP gives 4 vertices
    UNKNOWN
}

/**
 * Detects food plate (circular/elliptical/rectangular shape) in the image.
 * Returns shape classification via fitEllipse and approxPolyDP.
 *
 * Uses 5 strategies in sequence:
 *   1. Adaptive threshold (fast, uniform lighting)
 *   2. Canny edge detection + morphological closing (varied lighting)
 *   3. HoughCircles (specifically for circular plates)
 *   4. Otsu threshold with heavy morphology (global, contrast-dependent)
 *   5. Gradient magnitude (Sobel-based, catches faint edges)
 *
 * CLAHE histogram equalization is applied before processing
 * to boost contrast in poorly-lit or low-contrast images.
 */
class PlateDetector {

    companion object {
        private const val TAG = "PlateDetector"
        private const val MIN_PLATE_AREA_RATIO = 0.04 // 4% — allow wider shots
        private const val MAX_PLATE_AREA_RATIO = 0.90 // Plate usually won't cover >90%
        private const val PRIMARY_CIRCULARITY_THRESHOLD = 0.55
        private const val FALLBACK_CIRCULARITY_THRESHOLD = 0.35 // For ovals/bowls seen from angle
        private const val LAST_RESORT_CIRCULARITY_THRESHOLD = 0.25 // Very relaxed for difficult images
        private const val TARGET_PROCESSING_WIDTH = 640 // Normalize to this width before processing
    }

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
            val clahe = Imgproc.createCLAHE(3.0, Size(8.0, 8.0))
            val enhanced = Mat()
            clahe.apply(gray, enhanced)

            val imageArea = (processingMat.width() * processingMat.height()).toDouble()
            val imgWidth = processingMat.width()
            val imgHeight = processingMat.height()

            // Strategy 1: Adaptive threshold (fast, works well with uniform lighting)
            var bestResult = tryAdaptiveThreshold(enhanced, imageArea)

            // Strategy 2: Canny edge detection (better with varied lighting/textures)
            if (bestResult == null) {
                Log.d(TAG, "Strategy 1 (adaptive) failed, trying Canny edges")
                bestResult = tryCanny(enhanced, imageArea)
            }

            // Strategy 3: HoughCircles (specifically designed for circular plates)
            if (bestResult == null) {
                Log.d(TAG, "Strategy 2 (Canny) failed, trying HoughCircles")
                bestResult = tryHoughCircles(enhanced, imgWidth, imgHeight)
            }

            // Strategy 4: Otsu threshold with heavy morphology
            if (bestResult == null) {
                Log.d(TAG, "Strategy 3 (HoughCircles) failed, trying Otsu threshold")
                bestResult = tryOtsu(enhanced, imageArea)
            }

            // Strategy 5: Gradient magnitude (Sobel-based, catches subtle plate rims)
            if (bestResult == null) {
                Log.d(TAG, "Strategy 4 (Otsu) failed, trying gradient magnitude")
                bestResult = tryGradientMagnitude(enhanced, imageArea)
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
     * Strategy 1: Adaptive threshold — fast, good for uniform lighting.
     */
    private fun tryAdaptiveThreshold(gray: Mat, imageArea: Double): PlateDetectionResult? {
        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(9.0, 9.0), 2.0, 2.0)

        val thresh = Mat()
        Imgproc.adaptiveThreshold(blurred, thresh, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 11, 2.0)

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(thresh, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        var result = findBestPlateContour(contours, imageArea, PRIMARY_CIRCULARITY_THRESHOLD, "adaptive")
        if (result == null) {
            result = findBestPlateContour(contours, imageArea, FALLBACK_CIRCULARITY_THRESHOLD, "adaptive-fallback")
        }

        blurred.release()
        thresh.release()
        hierarchy.release()
        contours.forEach { it.release() }
        return result
    }

    /**
     * Strategy 2: Canny edge detection + morphological closing — better with varied lighting.
     * Uses bilateral filter to preserve edges while smoothing texture noise.
     */
    private fun tryCanny(gray: Mat, imageArea: Double): PlateDetectionResult? {
        // Bilateral filter: preserves edges (plate rim) while smoothing textures (food, table)
        val filtered = Mat()
        Imgproc.bilateralFilter(gray, filtered, 9, 75.0, 75.0)

        val edges = Mat()
        Imgproc.Canny(filtered, edges, 15.0, 60.0) // Very low thresholds to catch faint plate rims

        // Close gaps in edges to form complete contours — large kernel
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(15.0, 15.0))
        val closed = Mat()
        Imgproc.morphologyEx(edges, closed, Imgproc.MORPH_CLOSE, kernel)

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(closed, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        Log.d(TAG, "Canny found ${contours.size} contours")

        var result = findBestPlateContour(contours, imageArea, PRIMARY_CIRCULARITY_THRESHOLD, "canny")
        if (result == null) {
            result = findBestPlateContour(contours, imageArea, FALLBACK_CIRCULARITY_THRESHOLD, "canny-fallback")
        }

        if (result != null) {
            Log.d(TAG, "Canny strategy found plate!")
        }

        filtered.release()
        edges.release()
        kernel.release()
        closed.release()
        hierarchy.release()
        contours.forEach { it.release() }
        return result
    }

    /**
     * Strategy 3: HoughCircles — specifically designed for finding circular plates.
     * Tries two passes: strict then relaxed parameters.
     * Uses center-bias check to reject circles at image edges.
     */
    private fun tryHoughCircles(gray: Mat, width: Int, height: Int): PlateDetectionResult? {
        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(9.0, 9.0), 2.0, 2.0)

        // Pass 1: moderate sensitivity
        var result = runHoughCircles(blurred, width, height, 80.0, 25.0, "pass1")

        // Pass 2: high sensitivity (more false positives, but catches faint circles)
        if (result == null) {
            result = runHoughCircles(blurred, width, height, 60.0, 18.0, "pass2")
        }

        blurred.release()
        return result
    }

    private fun runHoughCircles(blurred: Mat, width: Int, height: Int,
                                 param1: Double, param2: Double, passName: String): PlateDetectionResult? {
        val circles = Mat()
        val minRadius = (minOf(width, height) * 0.10).toInt()
        val maxRadius = (minOf(width, height) * 0.48).toInt()

        Imgproc.HoughCircles(
            blurred, circles, Imgproc.HOUGH_GRADIENT,
            1.2, (minOf(width, height) * 0.25), // slightly lower minDist
            param1, param2, minRadius, maxRadius
        )

        val centerX = width / 2.0
        val centerY = height / 2.0
        val maxCenterDist = minOf(width, height) * 0.40

        if (circles.cols() > 0) {
            var bestScore = -1.0
            var bestRadius = 0.0
            var bestX = 0.0
            var bestY = 0.0

            for (i in 0 until circles.cols()) {
                val data = circles.get(0, i)
                val cx = data[0]
                val cy = data[1]
                val r = data[2]

                val distFromCenter = Math.sqrt((cx - centerX) * (cx - centerX) + (cy - centerY) * (cy - centerY))
                if (distFromCenter > maxCenterDist) {
                    Log.d(TAG, "HoughCircles($passName): rejected edge circle center=(${cx.toInt()},${cy.toInt()}) r=${r.toInt()} dist=${distFromCenter.toInt()}")
                    continue
                }

                val centerScore = 1.0 - (distFromCenter / maxCenterDist)
                val score = r * (0.5 + 0.5 * centerScore)

                if (score > bestScore) {
                    bestScore = score
                    bestRadius = r
                    bestX = cx
                    bestY = cy
                }
            }

            if (bestScore > 0) {
                val rect = android.graphics.Rect(
                    (bestX - bestRadius).toInt().coerceAtLeast(0),
                    (bestY - bestRadius).toInt().coerceAtLeast(0),
                    (bestX + bestRadius).toInt().coerceAtMost(width),
                    (bestY + bestRadius).toInt().coerceAtMost(height)
                )

                Log.d(TAG, "HoughCircles($passName) found plate! center=(${bestX.toInt()},${bestY.toInt()}) r=${bestRadius.toInt()} score=${"%.1f".format(bestScore)}")
                circles.release()
                return PlateDetectionResult(true, rect, 0.85f, ShapeType.CIRCULAR)
            }
        }

        Log.d(TAG, "HoughCircles($passName): no valid circles (total=${circles.cols()})")
        circles.release()
        return null
    }

    /**
     * Strategy 4: Otsu threshold with heavy morphological operations.
     * Uses a large kernel to merge fragmented contours into cohesive plate shapes.
     */
    private fun tryOtsu(gray: Mat, imageArea: Double): PlateDetectionResult? {
        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(11.0, 11.0), 3.0, 3.0)

        val thresh = Mat()
        Imgproc.threshold(blurred, thresh, 0.0, 255.0, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU)

        // Heavy morphological closing with large kernel to merge fragments into plate shape
        val closeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(21.0, 21.0))
        val closed = Mat()
        Imgproc.morphologyEx(thresh, closed, Imgproc.MORPH_CLOSE, closeKernel)

        // Dilate to further merge nearby blobs
        val dilateKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(11.0, 11.0))
        val dilated = Mat()
        Imgproc.dilate(closed, dilated, dilateKernel)

        // Erode back to roughly original size (open = erode+dilate, but we want close+dilate+erode)
        val erodeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(7.0, 7.0))
        val eroded = Mat()
        Imgproc.erode(dilated, eroded, erodeKernel)

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(eroded, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        Log.d(TAG, "Otsu found ${contours.size} contours (after heavy morphology)")

        // Try with progressively relaxed thresholds
        var result = findBestPlateContour(contours, imageArea, PRIMARY_CIRCULARITY_THRESHOLD, "otsu")
        if (result == null) {
            result = findBestPlateContour(contours, imageArea, FALLBACK_CIRCULARITY_THRESHOLD, "otsu-fallback")
        }
        if (result == null) {
            result = findBestPlateContour(contours, imageArea, LAST_RESORT_CIRCULARITY_THRESHOLD, "otsu-lastresort")
        }

        if (result != null) {
            Log.d(TAG, "Otsu strategy found plate!")
        }

        blurred.release()
        thresh.release()
        closeKernel.release()
        closed.release()
        dilateKernel.release()
        dilated.release()
        erodeKernel.release()
        eroded.release()
        hierarchy.release()
        contours.forEach { it.release() }
        return result
    }

    /**
     * Strategy 5: Gradient magnitude (Sobel) — catches plate rims as sharp intensity transitions.
     * Even faint rims create a ring of high gradient values that can be thresholded.
     */
    private fun tryGradientMagnitude(gray: Mat, imageArea: Double): PlateDetectionResult? {
        val blurred = Mat()
        Imgproc.GaussianBlur(gray, blurred, Size(7.0, 7.0), 1.5, 1.5)

        // Compute gradient magnitude using Sobel
        val gradX = Mat()
        val gradY = Mat()
        Imgproc.Sobel(blurred, gradX, CvType.CV_16S, 1, 0, 3)
        Imgproc.Sobel(blurred, gradY, CvType.CV_16S, 0, 1, 3)

        val absGradX = Mat()
        val absGradY = Mat()
        Core.convertScaleAbs(gradX, absGradX)
        Core.convertScaleAbs(gradY, absGradY)

        val gradMag = Mat()
        Core.addWeighted(absGradX, 0.5, absGradY, 0.5, 0.0, gradMag)

        // Threshold gradient magnitude
        val thresh = Mat()
        Imgproc.threshold(gradMag, thresh, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)

        // Heavy morphological closing to connect gradient edges into plate boundary
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(19.0, 19.0))
        val closed = Mat()
        Imgproc.morphologyEx(thresh, closed, Imgproc.MORPH_CLOSE, kernel)

        // Fill holes
        val fillKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(9.0, 9.0))
        val filled = Mat()
        Imgproc.dilate(closed, filled, fillKernel)
        Imgproc.erode(filled, filled, fillKernel)

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(filled, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        Log.d(TAG, "Gradient found ${contours.size} contours")

        var result = findBestPlateContour(contours, imageArea, FALLBACK_CIRCULARITY_THRESHOLD, "gradient")
        if (result == null) {
            result = findBestPlateContour(contours, imageArea, LAST_RESORT_CIRCULARITY_THRESHOLD, "gradient-lastresort")
        }

        if (result != null) {
            Log.d(TAG, "Gradient strategy found plate!")
        }

        blurred.release()
        gradX.release()
        gradY.release()
        absGradX.release()
        absGradY.release()
        gradMag.release()
        thresh.release()
        kernel.release()
        closed.release()
        fillKernel.release()
        filled.release()
        hierarchy.release()
        contours.forEach { it.release() }
        return result
    }

    /**
     * Finds the best plate contour above the given circularity threshold.
     * Picks the largest qualifying contour and classifies its shape.
     * Logs diagnostic info about why top contours were rejected.
     */
    private fun findBestPlateContour(
        contours: List<MatOfPoint>,
        imageArea: Double,
        circularityThreshold: Double,
        strategyName: String
    ): PlateDetectionResult? {
        var bestArea = 0.0
        var bestResult: PlateDetectionResult? = null
        var tooSmall = 0
        var tooBig = 0
        var notCircular = 0
        var topRejectedArea = 0.0
        var topRejectedCirc = 0.0

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)

            if (area < imageArea * MIN_PLATE_AREA_RATIO) {
                tooSmall++
                continue
            }
            if (area > imageArea * MAX_PLATE_AREA_RATIO) {
                tooBig++
                continue
            }

            val peri = Imgproc.arcLength(MatOfPoint2f(*contour.toArray()), true)
            val circularity = (4 * Math.PI * area) / (peri * peri)

            if (circularity <= circularityThreshold) {
                notCircular++
                if (area > topRejectedArea) {
                    topRejectedArea = area
                    topRejectedCirc = circularity
                }
                continue
            }

            if (area > bestArea) {
                val rect = Imgproc.boundingRect(contour)
                val androidRect = android.graphics.Rect(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height)
                val shapeType = classifyShape(contour)

                bestArea = area
                bestResult = PlateDetectionResult(true, androidRect, circularity.toFloat(), shapeType)
                Log.d(TAG, "[$strategyName] Plate candidate: Area=${"%.1f".format((area/imageArea)*100)}%, Circ=${"%.3f".format(circularity)}, Shape=$shapeType")
            }
        }

        if (bestResult == null && contours.isNotEmpty()) {
            Log.d(TAG, "[$strategyName] Rejected: tooSmall=$tooSmall, tooBig=$tooBig, notCircular=$notCircular" +
                    (if (topRejectedArea > 0) ", topRejected: area=${"%.1f".format((topRejectedArea/imageArea)*100)}% circ=${"%.3f".format(topRejectedCirc)}" else ""))
        }

        return bestResult
    }

    /**
     * Classifies contour shape using fitEllipse (axis ratio) and approxPolyDP (vertex count).
     */
    private fun classifyShape(contour: MatOfPoint): ShapeType {
        val contour2f = MatOfPoint2f(*contour.toArray())

        if (contour2f.rows() >= 5) {
            try {
                val ellipse = Imgproc.fitEllipse(contour2f)
                val major = maxOf(ellipse.size.width, ellipse.size.height)
                val minor = minOf(ellipse.size.width, ellipse.size.height)

                if (minor > 0) {
                    val axisRatio = major / minor
                    return when {
                        axisRatio < 1.2 -> {
                            Log.d(TAG, "Shape: CIRCULAR (axis ratio ${"%.2f".format(axisRatio)})")
                            ShapeType.CIRCULAR
                        }
                        axisRatio in 1.2..2.0 -> {
                            Log.d(TAG, "Shape: OVAL (axis ratio ${"%.2f".format(axisRatio)})")
                            ShapeType.OVAL
                        }
                        else -> checkRectangular(contour2f)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "fitEllipse failed, trying approxPolyDP", e)
            }
        }

        return checkRectangular(contour2f)
    }

    /**
     * Checks if contour is rectangular using polygon approximation.
     */
    private fun checkRectangular(contour2f: MatOfPoint2f): ShapeType {
        val peri = Imgproc.arcLength(contour2f, true)
        val approxCurve = MatOfPoint2f()
        Imgproc.approxPolyDP(contour2f, approxCurve, 0.02 * peri, true)

        return if (approxCurve.rows() == 4) {
            Log.d(TAG, "Shape: RECTANGULAR (4 vertices)")
            ShapeType.RECTANGULAR
        } else {
            Log.d(TAG, "Shape: UNKNOWN (${approxCurve.rows()} vertices)")
            ShapeType.UNKNOWN
        }
    }
}

data class PlateDetectionResult(
    val isFound: Boolean,
    val bounds: android.graphics.Rect?,
    val confidence: Float,
    val shapeType: ShapeType = ShapeType.UNKNOWN
)
