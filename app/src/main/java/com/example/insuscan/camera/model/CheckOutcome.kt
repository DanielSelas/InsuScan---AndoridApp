package com.example.insuscan.camera.model

data class CheckOutcome(
    val level: QualityLevel,
    val measuredValue: Float,
    val message: String
) {
    val isOk: Boolean get() = level == QualityLevel.OK
    val isBlocking: Boolean get() = level == QualityLevel.FAILED

    companion object {
        fun ok(measuredValue: Float, message: String) =
            CheckOutcome(QualityLevel.OK, measuredValue, message)

        fun borderline(measuredValue: Float, message: String) =
            CheckOutcome(QualityLevel.BORDERLINE, measuredValue, message)

        fun failed(measuredValue: Float, message: String) =
            CheckOutcome(QualityLevel.FAILED, measuredValue, message)
    }
}