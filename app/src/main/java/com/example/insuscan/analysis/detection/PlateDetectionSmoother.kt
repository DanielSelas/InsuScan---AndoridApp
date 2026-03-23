package com.example.insuscan.analysis.detection

import android.graphics.Rect
import com.example.insuscan.analysis.model.PlateDetectionResult
import com.example.insuscan.analysis.model.ShapeType

/**
 * Smooths plate detection results over multiple frames to reduce flickering
 * in live preview mode. Uses a moving average of the last N bounding boxes.
 */
class PlateDetectionSmoother(private val historySize: Int = 5) {

    private val boundsHistory = ArrayDeque<Rect>(historySize)
    private var lastShapeType = ShapeType.UNKNOWN
    private var lastConfidence = 0f

    fun smooth(raw: PlateDetectionResult): PlateDetectionResult {
        if (!raw.isFound || raw.bounds == null) {
            boundsHistory.clear()
            return raw
        }

        if (boundsHistory.size >= historySize) boundsHistory.removeFirst()
        boundsHistory.addLast(raw.bounds)
        lastShapeType = raw.shapeType
        lastConfidence = raw.confidence

        if (boundsHistory.size < 2) return raw

        var avgLeft = 0; var avgTop = 0; var avgRight = 0; var avgBottom = 0
        for (r in boundsHistory) {
            avgLeft += r.left; avgTop += r.top; avgRight += r.right; avgBottom += r.bottom
        }
        val n = boundsHistory.size
        val smoothedBounds = Rect(avgLeft / n, avgTop / n, avgRight / n, avgBottom / n)

        return PlateDetectionResult(true, smoothedBounds, lastConfidence, lastShapeType)
    }

    fun reset() {
        boundsHistory.clear()
        lastShapeType = ShapeType.UNKNOWN
        lastConfidence = 0f
    }
}
