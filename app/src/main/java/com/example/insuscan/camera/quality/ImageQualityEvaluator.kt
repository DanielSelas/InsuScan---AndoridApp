package com.example.insuscan.camera.quality

import com.example.insuscan.analysis.model.DetectionResult
import com.example.insuscan.camera.model.CheckOutcome
import com.example.insuscan.camera.model.QualityLevel
import com.example.insuscan.camera.quality.check.LightingCheck
import com.example.insuscan.camera.quality.check.ReferenceObjectCheck
import com.example.insuscan.camera.quality.check.ResolutionCheck
import com.example.insuscan.camera.quality.check.SharpnessCheck

class ImageQualityEvaluator(
    private val lightingCheck: LightingCheck = LightingCheck(),
    private val sharpnessCheck: SharpnessCheck = SharpnessCheck(),
    private val resolutionCheck: ResolutionCheck = ResolutionCheck(),
    private val referenceObjectCheck: ReferenceObjectCheck = ReferenceObjectCheck()
) {

    fun evaluateLiveFrame(
        lux: Float,
        sharpness: Float,
        plateFound: Boolean,
        referenceExpected: Boolean,
        referenceDetection: DetectionResult?,
        frameWidth: Int,
        frameHeight: Int
    ): ImageQualityReport {
        return ImageQualityReport(
            lighting = lightingCheck.evaluate(lux),
            sharpness = sharpnessCheck.evaluate(sharpness),
            resolution = null,
            referenceObject = referenceOutcome(referenceExpected, referenceDetection, frameWidth, frameHeight),
            isPlateFound = plateFound
        )
    }

    fun evaluateCapturedImage(
        lux: Float,
        sharpness: Float,
        width: Int,
        height: Int,
        plateFound: Boolean,
        referenceExpected: Boolean,
        referenceDetection: DetectionResult?
    ): ImageQualityReport {
        return ImageQualityReport(
            lighting = lightingCheck.evaluate(lux),
            sharpness = sharpnessCheck.evaluate(sharpness),
            resolution = resolutionCheck.evaluate(width, height),
            referenceObject = referenceOutcome(referenceExpected, referenceDetection, width, height),
            isPlateFound = plateFound
        )
    }

    private fun referenceOutcome(
        expected: Boolean,
        detection: DetectionResult?,
        frameWidth: Int,
        frameHeight: Int
    ): CheckOutcome? {
        if (!expected || detection == null) return null
        return referenceObjectCheck.evaluate(detection, frameWidth, frameHeight)
    }
}

data class ImageQualityReport(
    val lighting: CheckOutcome?,
    val sharpness: CheckOutcome?,
    val resolution: CheckOutcome?,
    val referenceObject: CheckOutcome?,
    val isPlateFound: Boolean
) {
    val checks: List<CheckOutcome>
        get() = listOfNotNull(lighting, sharpness, resolution, referenceObject)

    val overall: QualityLevel
        get() = QualityLevel.worst(checks.map { it.level })

    val canProceed: Boolean
        get() = overall != QualityLevel.FAILED

    val message: String
        get() {
            val failed = checks.firstOrNull { it.level == QualityLevel.FAILED }
            if (failed != null) return failed.message
            val borderline = checks.firstOrNull { it.level == QualityLevel.BORDERLINE }
            if (borderline != null) return borderline.message
            return "Quality is good"
        }
}