package com.example.insuscan.camera.quality.check

import com.example.insuscan.camera.model.CheckOutcome
import com.example.insuscan.camera.quality.QualityThresholds

class SharpnessCheck {

    fun evaluate(sharpness: Float): CheckOutcome {
        return when {
            sharpness >= QualityThresholds.SHARPNESS_OK ->
                CheckOutcome.ok(sharpness, "Image is sharp")
            sharpness >= QualityThresholds.SHARPNESS_MIN ->
                CheckOutcome.borderline(sharpness, "Image is slightly soft")
            else ->
                CheckOutcome.failed(sharpness, "Image is blurry — hold steady")
        }
    }
}