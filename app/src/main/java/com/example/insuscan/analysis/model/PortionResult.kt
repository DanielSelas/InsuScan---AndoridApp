package com.example.insuscan.analysis.model

/**
 * Result of portion estimation — either a successful measurement or an error.
 */
sealed class PortionResult {
    data class Success(
        val estimatedWeightGrams: Float,
        val volumeCm3: Float,
        val plateDiameterCm: Float,
        val depthCm: Float,
        val containerType: ContainerType,
        val confidence: Float,
        val referenceObjectDetected: Boolean,
        val arMeasurementUsed: Boolean,
        val arDepthIsReal: Boolean,
        val warning: String?
    ) : PortionResult()

    data class Error(
        val message: String
    ) : PortionResult()
}
