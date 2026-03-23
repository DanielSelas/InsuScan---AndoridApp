package com.example.insuscan.camera.model

/**
 * Image quality check result.
 */
data class ImageQualityResult(
    val brightness: Float,
    val isBrightnessOk: Boolean,
    val sharpness: Float,
    val isSharpnessOk: Boolean,
    val resolution: Int,
    val isResolutionOk: Boolean,
    val isReferenceObjectFound: Boolean,
    val isPlateFound: Boolean,
    val debugInfo: String = "" // Added debug info
) {

    // Helper to determine the "State" for UI logic
    val isValid: Boolean get() = isBrightnessOk && isSharpnessOk && isResolutionOk && isPlateFound

    // Returns a user-facing message based on the current quality status.
    fun getValidationMessage(): String {
        return when {
            !isPlateFound -> "Plate not detected. Center the food."
            !isReferenceObjectFound -> "Reference Object not detected."
            !isBrightnessOk && brightness < 50f -> "Image is too dark. Add light."
            !isBrightnessOk && brightness > 200f -> "Image is too bright. Reduce light."
            !isSharpnessOk -> "Image is blurry. Hold steady."
            !isResolutionOk -> "Resolution too low."
            else -> "Perfect! Ready to capture."
        }
    }
}
