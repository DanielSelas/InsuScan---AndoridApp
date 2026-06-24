package com.example.insuscan.scan.coach

import com.example.insuscan.camera.model.ImageQualityResult
import com.example.insuscan.camera.model.QualityLevel
import com.example.insuscan.utils.ReferenceObjectHelper

class CameraCoachEvaluator {

    private var plateInFrameStartTime = 0L
    private var isForceCaptureAllowed = false

    fun evaluate(
        quality: ImageQualityResult?,
        isDeviceLevel: Boolean,
        selectedRefType: String?
    ): CameraCoachState {
        if (quality == null) return CameraCoachState.FindPlate()

        val report = quality.report
        updateForceCapture(report.isPlateFound)

        if (!isDeviceLevel) return CameraCoachState.LevelPhone()

        if (!report.isPlateFound) return CameraCoachState.FindPlate()

        val refExpected = isRefExpected(selectedRefType)
        val reference = report.referenceObject
        if (refExpected && reference != null && reference.level == QualityLevel.FAILED) {
            return CameraCoachState.PlaceRefObject(getRefDisplayName(selectedRefType))
        }

        val failed = report.checks.firstOrNull { it.level == QualityLevel.FAILED }
        if (failed != null) {
            if (isForceCaptureAllowed) return CameraCoachState.ForceCapture()
            return CameraCoachState.ImproveQuality(failed.message)
        }

        val borderline = report.checks.firstOrNull { it.level == QualityLevel.BORDERLINE }
        if (borderline != null) {
            return CameraCoachState.CaptureWithWarning(borderline.message)
        }

        return CameraCoachState.Ready()
    }

    fun reset() {
        plateInFrameStartTime = 0L
        isForceCaptureAllowed = false
    }

    fun evaluateSidePhoto(isSideAngle: Boolean): CameraCoachState =
        if (isSideAngle) CameraCoachState.SidePhotoReady() else CameraCoachState.SidePhotoNeedTilt()

    private fun updateForceCapture(isPlateFound: Boolean) {
        if (isPlateFound) {
            if (plateInFrameStartTime == 0L) {
                plateInFrameStartTime = System.currentTimeMillis()
            } else if (System.currentTimeMillis() - plateInFrameStartTime > 5000) {
                isForceCaptureAllowed = true
            }
        } else {
            plateInFrameStartTime = 0L
            isForceCaptureAllowed = false
        }
    }

    private fun isRefExpected(selectedRefType: String?): Boolean {
        val refType = ReferenceObjectHelper.fromServerValue(selectedRefType)
        return refType != null && refType != ReferenceObjectHelper.ReferenceObjectType.NONE
    }

    private fun getRefDisplayName(selectedRefType: String?): String {
        val refType = ReferenceObjectHelper.fromServerValue(selectedRefType)
        return when (refType) {
            ReferenceObjectHelper.ReferenceObjectType.CARD -> "credit card 💳"
            ReferenceObjectHelper.ReferenceObjectType.INSULIN_SYRINGE -> "insulin pen 🖊️"
            else -> "reference object"
        }
    }
}