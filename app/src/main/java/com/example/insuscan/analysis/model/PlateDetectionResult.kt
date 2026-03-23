package com.example.insuscan.analysis.model

import android.graphics.Rect

data class PlateDetectionResult(
    val isFound: Boolean,
    val bounds: Rect?,
    val confidence: Float,
    val shapeType: ShapeType = ShapeType.UNKNOWN
)
