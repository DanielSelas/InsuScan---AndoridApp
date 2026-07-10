package com.example.insuscan.scan.coach

/**
 * Describes the measurement strategy available for the current scan session.
 *
 * The strategy is determined by which inputs are present (ARCore depth and/or
 * a reference object), and it drives the accuracy label shown to the user.
 */
sealed class MeasurementStrategy(
    val label: String,
    val accuracy: Accuracy
) {
    object Best    : MeasurementStrategy("High accuracy — 3D depth + reference object", Accuracy.HIGH)
    object ArOnly  : MeasurementStrategy("Good accuracy — 3D depth measurement", Accuracy.GOOD)
    object RefOnly : MeasurementStrategy("Good accuracy — reference object detected", Accuracy.GOOD)
    object Basic   : MeasurementStrategy("Moderate — estimated from image analysis", Accuracy.MODERATE)

    enum class Accuracy { HIGH, GOOD, MODERATE }

    companion object {
        /**
         * Selects the best available strategy from the current scan inputs.
         *
         * @param hasRefObject Whether a reference object was detected.
         * @param hasAr        Whether ARCore depth data is available.
         */
        fun decide(hasRefObject: Boolean, hasAr: Boolean): MeasurementStrategy {
            return when {
                hasRefObject && hasAr -> Best
                hasAr -> ArOnly
                hasRefObject -> RefOnly
                else -> Basic
            }
        }
    }
}