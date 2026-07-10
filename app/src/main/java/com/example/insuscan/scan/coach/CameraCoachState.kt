package com.example.insuscan.scan.coach

/**
 * Represents a camera coaching state shown to the user during the scan flow.
 *
 * Each subclass carries a [message] displayed in the coach pill,
 * a [severity] that controls the pill colour, and a [canCapture] flag
 * that enables or disables the capture button.
 */
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

    /** Shown after the plate has been in-frame for > 5 s despite a quality issue. */
    class ForceCapture : CameraCoachState(
        3, 3, "Quality is OK - tap to capture ⚠️", CoachSeverity.ACCEPTABLE, true
    )

    class CaptureWithWarning(message: String) : CameraCoachState(
        3, 3, message, CoachSeverity.ACCEPTABLE, true
    )

    class Ready : CameraCoachState(
        3, 3, "Perfect! Tap to capture ✅", CoachSeverity.GOOD, true
    )

    class SidePhotoNeedTilt : CameraCoachState(
        1, 1, "Tilt phone sideways to show the plate's depth ↔️", CoachSeverity.BLOCKING, false
    )

    class SidePhotoReady : CameraCoachState(
        1, 1, "Hold steady - tap to capture the side view ✅", CoachSeverity.GOOD, true
    )
}

/** Controls the visual urgency of a [CameraCoachState] pill. */
enum class CoachSeverity {
    BLOCKING,
    WARNING,
    TIP,
    ACCEPTABLE,
    GOOD
}