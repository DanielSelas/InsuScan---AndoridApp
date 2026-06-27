package com.example.insuscan.network.repository

import com.example.insuscan.network.dto.InsulinCalcResponseDto

interface InsulinCalcRepository {
    suspend fun calculate(
        totalCarbs: Float,
        currentGlucose: Int?,
        email: String,
        planIcr: Float?,
        planIsf: Float?,
        planTargetGlucose: Int?
    ): Result<InsulinCalcResponseDto>
}