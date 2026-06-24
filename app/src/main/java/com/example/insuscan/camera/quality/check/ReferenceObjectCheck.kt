package com.example.insuscan.camera.quality.check

import com.example.insuscan.analysis.model.DetectionResult
import com.example.insuscan.camera.model.CheckOutcome
import com.example.insuscan.camera.quality.QualityThresholds

class ReferenceObjectCheck {

    fun evaluate(detection: DetectionResult, frameWidth: Int, frameHeight: Int): CheckOutcome {
        return when (detection) {
            is DetectionResult.NotFound ->
                CheckOutcome.failed(0f, "Reference object not found")
            is DetectionResult.Found -> {
                if (isTouchingEdge(detection, frameWidth, frameHeight)) {
                    CheckOutcome.borderline(detection.confidence, "Reference object near the edge")
                } else {
                    CheckOutcome.ok(detection.confidence, "Reference object detected")
                }
            }
        }
    }

    private fun isTouchingEdge(found: DetectionResult.Found, frameWidth: Int, frameHeight: Int): Boolean {
        val marginX = frameWidth * QualityThresholds.REFERENCE_EDGE_MARGIN_RATIO
        val marginY = frameHeight * QualityThresholds.REFERENCE_EDGE_MARGIN_RATIO
        val box = found.boundingBox
        val left = box.x
        val top = box.y
        val right = box.x + box.width
        val bottom = box.y + box.height
        return left <= marginX ||
                top <= marginY ||
                right >= frameWidth - marginX ||
                bottom >= frameHeight - marginY
    }
}