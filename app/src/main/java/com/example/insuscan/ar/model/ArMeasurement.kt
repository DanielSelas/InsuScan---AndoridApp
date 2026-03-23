package com.example.insuscan.ar.model

import com.example.insuscan.analysis.model.ContainerType

data class ArMeasurement(
    val depthCm: Float,
    val plateDiameterCm: Float,
    val surfaceDistanceCm: Float,
    val containerType: ContainerType,
    val confidence: Float,
    val isRealDepth: Boolean
)
