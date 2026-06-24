package com.example.insuscan.camera.quality.check

import com.example.insuscan.camera.model.CheckOutcome
import com.example.insuscan.camera.quality.QualityThresholds

class LightingCheck {

    fun evaluate(lux: Float): CheckOutcome {
        if (lux < 0f) {
            return CheckOutcome.ok(lux, "Lighting sensor unavailable")
        }
        return when {
            lux < QualityThresholds.LIGHTING_FAILED_LUX ->
                CheckOutcome.failed(lux, "Too dark - add more light")
            lux < QualityThresholds.LIGHTING_OK_MIN_LUX ->
                CheckOutcome.borderline(lux, "Lighting is a bit low")
            lux <= QualityThresholds.LIGHTING_OK_MAX_LUX ->
                CheckOutcome.ok(lux, "Lighting is good")
            else ->
                CheckOutcome.borderline(lux, "Too bright - reduce light")
        }
    }
}