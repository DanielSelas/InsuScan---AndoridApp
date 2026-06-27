package com.example.insuscan.network.dto

data class InsulinCalcRequestDto(
    val totalCarbs: Float,
    val currentGlucose: Int?,
    val userId: UserIdDto?,
    val planIcr: Float?,
    val planIsf: Float?,
    val planTargetGlucose: Int?
)

data class InsulinCalcResponseDto(
    val totalRecommendedDose: Float?,
    val carbDose: Float?,
    val correctionDose: Float?,
    val insulinCarbRatioUsed: String?,
    val correctionFactorUsed: Float?,
    val targetGlucoseUsed: Int?,
    val warning: String?
)