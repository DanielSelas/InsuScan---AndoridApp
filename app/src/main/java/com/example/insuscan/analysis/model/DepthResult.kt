package com.example.insuscan.analysis.model

data class DepthResult(
    val depthCm: Float,
    val confidence: Float,
    val isFromArCore: Boolean,
    val containerType: ContainerType
)
