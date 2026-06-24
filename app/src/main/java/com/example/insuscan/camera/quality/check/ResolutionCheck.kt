package com.example.insuscan.camera.quality.check

import com.example.insuscan.camera.model.CheckOutcome
import com.example.insuscan.camera.quality.QualityThresholds

class ResolutionCheck {

    fun evaluate(width: Int, height: Int): CheckOutcome {
        val pixels = width.toLong() * height.toLong()
        val measured = pixels.toFloat()
        return when {
            pixels >= QualityThresholds.RESOLUTION_OK_PIXELS ->
                CheckOutcome.ok(measured, "Resolution is good")
            pixels >= QualityThresholds.RESOLUTION_MIN_PIXELS ->
                CheckOutcome.borderline(measured, "Resolution is borderline")
            else ->
                CheckOutcome.failed(measured, "Resolution too low")
        }
    }
}