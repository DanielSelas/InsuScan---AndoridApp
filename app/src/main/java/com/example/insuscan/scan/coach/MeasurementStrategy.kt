package com.example.insuscan.scan.coach

sealed class MeasurementStrategy(
    val label: String,
    val accuracy: Accuracy
) {
    object Best : MeasurementStrategy("High accuracy — 3D depth + reference object", Accuracy.HIGH)
    object ArOnly : MeasurementStrategy("Good accuracy — 3D depth measurement", Accuracy.GOOD)
    object RefOnly : MeasurementStrategy("Good accuracy — reference object detected", Accuracy.GOOD)
    object Basic : MeasurementStrategy("Moderate — estimated from image analysis", Accuracy.MODERATE)

    enum class Accuracy { HIGH, GOOD, MODERATE }

    companion object {
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