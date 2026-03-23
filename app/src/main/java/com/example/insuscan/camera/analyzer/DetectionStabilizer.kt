package com.example.insuscan.camera.analyzer

/**
 * Handles hysteresis and debounce logic for real-time detections.
 * Prevents UI flickering by requiring multiple consecutive 'Found' frames.
 */
class DetectionStabilizer {

    companion object {
        private const val MAX_SCORE = 10
        private const val THRESHOLD = 3
    }

    private var framesRefFound = 0
    private var framesPlateFound = 0

    fun updateReferenceObjectFound(isFound: Boolean): Boolean {
        if (isFound) {
            framesRefFound = (framesRefFound + 3).coerceAtMost(MAX_SCORE)
        } else {
            framesRefFound = (framesRefFound - 1).coerceAtLeast(0)
        }
        return framesRefFound >= THRESHOLD
    }

    fun updatePlateFound(isFound: Boolean): Boolean {
        if (isFound) {
            framesPlateFound = (framesPlateFound + 3).coerceAtMost(MAX_SCORE)
        } else {
            framesPlateFound = (framesPlateFound - 1).coerceAtLeast(0)
        }
        return framesPlateFound >= THRESHOLD
    }

    fun reset() {
        framesRefFound = 0
        framesPlateFound = 0
    }
}
