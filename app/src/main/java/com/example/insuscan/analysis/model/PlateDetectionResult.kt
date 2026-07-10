package com.example.insuscan.analysis.model

import android.graphics.Rect

/**
 * Outcome of a single plate-detection pass: whether a plate was found, its bounds,
 * how confident the match is, and the detected shape.
 */
data class PlateDetectionResult(
    val isFound: Boolean,
    val bounds: Rect?,
    val confidence: Float,
    val shapeType: ShapeType = ShapeType.UNKNOWN
)
