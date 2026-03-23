package com.example.insuscan.analysis.model

import com.example.insuscan.analysis.detection.ReferenceObjectDetector

/**
 * Result of smart fallback detection, includes which mode actually detected the object.
 */
data class FallbackDetectionResult(
    val result: DetectionResult,
    val detectedMode: ReferenceObjectDetector.DetectionMode?,
    val isAlternative: Boolean
)
