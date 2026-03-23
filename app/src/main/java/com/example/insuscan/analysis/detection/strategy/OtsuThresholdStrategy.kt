package com.example.insuscan.analysis.detection.strategy

import android.util.Log
import com.example.insuscan.analysis.model.PlateDetectionResult
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Strategy 3: Otsu threshold with heavy morphological operations.
 * Uses a large kernel to merge fragmented contours into cohesive plate shapes.
 */
class OtsuThresholdStrategy : BasePlateStrategy() {
    override fun detect(grayOrEnhanced: Mat, imageArea: Double): PlateDetectionResult? {
        val blurred = Mat()
        Imgproc.GaussianBlur(grayOrEnhanced, blurred, Size(11.0, 11.0), 3.0, 3.0)

        val thresh = Mat()
        Imgproc.threshold(blurred, thresh, 0.0, 255.0, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU)

        val closeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(21.0, 21.0))
        val closed = Mat()
        Imgproc.morphologyEx(thresh, closed, Imgproc.MORPH_CLOSE, closeKernel)

        val dilateKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(11.0, 11.0))
        val dilated = Mat()
        Imgproc.dilate(closed, dilated, dilateKernel)

        val erodeKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(7.0, 7.0))
        val eroded = Mat()
        Imgproc.erode(dilated, eroded, erodeKernel)

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(eroded, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        Log.d(TAG, "Otsu found ${contours.size} contours (after heavy morphology)")

        val result = findWithFallback(contours, imageArea,
            PRIMARY_CIRCULARITY_THRESHOLD to "otsu",
            FALLBACK_CIRCULARITY_THRESHOLD to "otsu-fallback",
            LAST_RESORT_CIRCULARITY_THRESHOLD to "otsu-lastresort"
        )

        if (result != null) Log.d(TAG, "Otsu strategy found plate!")

        releaseMats(blurred, thresh, closeKernel, closed, dilateKernel, dilated, erodeKernel, eroded, hierarchy)
        contours.forEach { it.release() }
        return result
    }
}
