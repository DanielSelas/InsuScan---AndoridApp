package com.example.insuscan.utils.exception

/**
 * Sealed exception hierarchy for insulin calculation and medical profile failures.
 */
sealed class InsulinException(message: String) : Exception(message) {

    // ── Missing Profile Data ──────────────────────────────────────────────────

    object MissingICR : InsulinException(
        "Insulin-to-Carb Ratio (ICR) is not set. Please complete your medical profile."
    )

    object MissingISF : InsulinException(
        "Insulin Sensitivity Factor (ISF) is not set. Please complete your medical profile."
    )

    object MissingMedicalProfile : InsulinException(
        "Medical profile is incomplete. Please fill in your insulin plan before calculating a dose."
    )

    object NoActivePlan : InsulinException(
        "No active insulin plan selected. Please choose a plan from the home screen."
    )

    // ── Glucose ───────────────────────────────────────────────────────────────

    class InvalidGlucose(
        val value: Int,
        val unit: String,
        val minAllowed: Int,
        val maxAllowed: Int
    ) : InsulinException(
        "Glucose value $value $unit is out of the valid range ($minAllowed–$maxAllowed $unit)."
    )

    object GlucoseNotProvided : InsulinException(
        "Glucose value was not provided. A correction dose cannot be calculated."
    )

    // ── Calculation ───────────────────────────────────────────────────────────

    class CalculationFailed(
        reason: String
    ) : InsulinException("Dose calculation failed: $reason")

    object NegativeCarbInput : InsulinException(
        "Carbohydrate amount cannot be negative."
    )

    object ZeroICR : InsulinException(
        "ICR is set to zero. Division by zero is not allowed in dose calculation."
    )
}
