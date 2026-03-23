package com.example.insuscan.analysis.detection.strategy

import com.example.insuscan.analysis.model.PlateDetectionResult
import org.opencv.core.Mat

/**
 * Interface for different algorithms that attempt to detect a plate in an image.
 */
interface PlateDetectionStrategy {
    /**
     * Attempts to find a plate in the pre-processed image.
     * @param grayOrEnhanced The grayscale or CLAHE-enhanced image Mat.
     * @param imageArea The total size of the image in pixels.
     * @return PlateDetectionResult if found, or null if this strategy failed.
     */
    fun detect(grayOrEnhanced: Mat, imageArea: Double): PlateDetectionResult?
}
