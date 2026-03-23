package com.example.insuscan.analysis.model

enum class ContainerType(val fallbackDepthCm: Float, val fallbackConfidence: Float) {
    FLAT_PLATE(2.0f, 0.15f),
    REGULAR_BOWL(5.0f, 0.10f),
    DEEP_BOWL(8.0f, 0.10f),
    UNKNOWN(3.0f, 0.05f)
}
