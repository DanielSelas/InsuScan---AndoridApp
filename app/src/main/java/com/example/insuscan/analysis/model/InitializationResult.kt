package com.example.insuscan.analysis.model

/**
 * Result of [PortionEstimator] initialization.
 */
data class InitializationResult(
    val isReady: Boolean,
    val openCvReady: Boolean
)
