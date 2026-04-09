package com.example.insuscan.camera.exception

/**
 * Sealed exception hierarchy for all camera and scanning failures.
 */
sealed class CameraException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    // ── Image Quality ─────────────────────────────────────────────────────────

    class ImageTooDark(
        val brightness: Float
    ) : CameraException("Image is too dark (brightness=$brightness). Move to a brighter area.")

    class ImageTooBright(
        val brightness: Float
    ) : CameraException("Image is too bright (brightness=$brightness). Reduce the lighting.")

    class ImageBlurry(
        val sharpness: Float
    ) : CameraException("Image is blurry (sharpness=$sharpness). Hold the phone steady.")

    class ResolutionTooLow(
        val actual: String,
        val required: String
    ) : CameraException("Resolution too low ($actual). Minimum required: $required.")

    // ── Plate Detection ───────────────────────────────────────────────────────

    object PlateNotFound : CameraException("No food plate detected in the image. Center the plate and try again.")

    object MultiplePlatesFound : CameraException("Multiple plates detected. Make sure only one plate is visible.")

    class PlateTooSmall(
        val plateAreaPercent: Float
    ) : CameraException("Plate is too far away (covers ${plateAreaPercent.toInt()}% of image). Move the camera closer.")

    // ── Reference Object ─────────────────────────────────────────────────────

    object ReferenceObjectNotFound : CameraException("Reference object not detected. Place the reference object next to the food.")

    class ReferenceObjectTooFar(
        val distancePx: Float
    ) : CameraException("Reference object is too far from the plate (distance=${distancePx.toInt()}px).")

    // ── Portion Estimation ────────────────────────────────────────────────────

    class PortionEstimationFailed(
        reason: String
    ) : CameraException("Could not estimate portion: $reason")

    object NoScaleSource : CameraException("Cannot measure portion — no reference object and no AR available.")

    // ── ARCore ────────────────────────────────────────────────────────────────

    object ARCoreNotSupported : CameraException("ARCore is not supported on this device.")

    class ARCoreSessionFailed(
        cause: Throwable? = null
    ) : CameraException("ARCore session failed to initialize: ${cause?.message ?: "unknown error"}", cause)

    object ARCoreDepthUnavailable : CameraException("ARCore depth data is not yet available. Wait for the camera to warm up.")
}
