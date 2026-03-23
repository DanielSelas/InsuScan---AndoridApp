package com.example.insuscan.analysis.detection.strategy

import android.util.Log
import com.example.insuscan.analysis.model.PlateDetectionResult
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Strategy 4: Gradient magnitude (Sobel) — catches plate rims as sharp intensity transitions.
 */
class GradientMagnitudeStrategy : BasePlateStrategy() {
    override fun detect(grayOrEnhanced: Mat, imageArea: Double): PlateDetectionResult? {
        val blurred = Mat()
        Imgproc.GaussianBlur(grayOrEnhanced, blurred, Size(7.0, 7.0), 1.5, 1.5)

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

        val thresh = Mat()
        Imgproc.threshold(gradMag, thresh, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)

        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(19.0, 19.0))
        val closed = Mat()
        Imgproc.morphologyEx(thresh, closed, Imgproc.MORPH_CLOSE, kernel)

        val fillKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(9.0, 9.0))
        val filled = Mat()
        Imgproc.dilate(closed, filled, fillKernel)
        Imgproc.erode(filled, filled, fillKernel)

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(filled, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

        Log.d(TAG, "Gradient found ${contours.size} contours")

        val result = findWithFallback(contours, imageArea,
            FALLBACK_CIRCULARITY_THRESHOLD to "gradient",
            LAST_RESORT_CIRCULARITY_THRESHOLD to "gradient-lastresort"
        )

        if (result != null) Log.d(TAG, "Gradient strategy found plate!")

        releaseMats(blurred, gradX, gradY, absGradX, absGradY, gradMag, thresh, kernel, closed, fillKernel, filled, hierarchy)
        contours.forEach { it.release() }
        return result
    }
}
