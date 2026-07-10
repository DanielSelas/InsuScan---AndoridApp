package com.example.insuscan.network.repository

import com.example.insuscan.network.dto.InsulinCalcResponseDto

/**
 * Requests an insulin dose from the server for a given carb amount and active plan.
 */
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