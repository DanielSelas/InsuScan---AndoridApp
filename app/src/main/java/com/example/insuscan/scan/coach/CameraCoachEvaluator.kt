package com.example.insuscan.scan.coach

import com.example.insuscan.camera.ImageQualityResult
import com.example.insuscan.utils.ReferenceObjectHelper

class CameraCoachEvaluator {

    private var plateInFrameStartTime = 0L
    private var isForceCaptureAllowed = false

    fun evaluate(
        quality: ImageQualityResult?,
        isDeviceLevel: Boolean,
        selectedRefType: String?,
        isSidePhotoCaptureMode: Boolean
    ): CameraCoachState {
        if (quality == null) return CameraCoachState.FindPlate()

        updateForceCapture(quality.isPlateFound)

        if (!isDeviceLevel) return CameraCoachState.LevelPhone()

        if (!quality.isPlateFound) return CameraCoachState.FindPlate()

        val refExpected = isRefExpected(selectedRefType, isSidePhotoCaptureMode)
        if (refExpected && !quality.isReferenceObjectFound) {
            val refName = getRefDisplayName(selectedRefType)
            return CameraCoachState.PlaceRefObject(refName)
        }

        if (!quality.isValid) {
            if (isForceCaptureAllowed) return CameraCoachState.ForceCapture()
            val issue = when {
                !quality.isBrightnessOk && quality.brightness < 50f -> "Too dark — add more light 🌑"
                !quality.isBrightnessOk && quality.brightness > 200f -> "Too bright — reduce light ☀️"
                !quality.isSharpnessOk -> "Hold steady — image is blurry 📷"
                else -> "Improving quality..."
            }
            return CameraCoachState.ImproveQuality(issue)
        }

        return CameraCoachState.Ready()
    }

    fun reset() {
        plateInFrameStartTime = 0L
        isForceCaptureAllowed = false
    }

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

    private fun isRefExpected(selectedRefType: String?, isSidePhotoCaptureMode: Boolean): Boolean {
        if (isSidePhotoCaptureMode) return false
        val refType = ReferenceObjectHelper.fromServerValue(selectedRefType)
        return refType != null && refType != ReferenceObjectHelper.ReferenceObjectType.NONE
    }

    private fun getRefDisplayName(selectedRefType: String?): String {
        val refType = ReferenceObjectHelper.fromServerValue(selectedRefType)
        return when (refType) {
            ReferenceObjectHelper.ReferenceObjectType.CARD -> "credit card 💳"
            ReferenceObjectHelper.ReferenceObjectType.INSULIN_SYRINGE -> "insulin pen 🖊️"
            ReferenceObjectHelper.ReferenceObjectType.SYRINGE_KNIFE -> "fork/knife 🍴"
            else -> "reference object"
        }
    }

    fun evaluateSidePhoto(pitchDegrees: Float): CameraCoachState {
        val pitchAbs = Math.abs(pitchDegrees)
        return when {
            pitchAbs < 15.0 -> CameraCoachState.SidePhotoReady()
            pitchAbs < 30.0 -> CameraCoachState.SidePhotoAlmost()
            else -> CameraCoachState.SidePhotoTilt()
        }
    }
}