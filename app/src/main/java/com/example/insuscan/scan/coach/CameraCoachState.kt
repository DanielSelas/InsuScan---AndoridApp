package com.example.insuscan.scan.coach

sealed class CameraCoachState(
    val step: Int,
    val totalSteps: Int,
    val message: String,
    val severity: CoachSeverity,
    val canCapture: Boolean
) {
    class LevelPhone : CameraCoachState(
        1, 3, "Hold phone flat above the plate 📐", CoachSeverity.BLOCKING, false
    )

    class FindPlate : CameraCoachState(
        2, 3, "Center the plate in the frame 🍽️", CoachSeverity.BLOCKING, false
    )

    class PlaceRefObject(refName: String) : CameraCoachState(
        2, 3, "Place $refName next to the plate 💡", CoachSeverity.TIP, true
    )

    class ImproveQuality(issue: String) : CameraCoachState(
        3, 3, issue, CoachSeverity.WARNING, false
    )

    class ForceCapture : CameraCoachState(
        3, 3, "Quality is OK — tap to capture ⚠️", CoachSeverity.ACCEPTABLE, true
    )

    class Ready : CameraCoachState(
        3, 3, "Perfect! Tap to capture ✅", CoachSeverity.GOOD, true
    )

    class SidePhotoReady : CameraCoachState(
        1, 1, "Perfect angle — tap to capture ✅", CoachSeverity.GOOD, true
    )

    class SidePhotoAlmost : CameraCoachState(
        1, 1, "Good enough — tap to capture ⚠️", CoachSeverity.ACCEPTABLE, true
    )

    class SidePhotoTilt : CameraCoachState(
        1, 1, "Hold phone upright at table level 📐", CoachSeverity.BLOCKING, false
    )
}

enum class CoachSeverity {
    BLOCKING,
    WARNING,
    TIP,
    ACCEPTABLE,
    GOOD
}