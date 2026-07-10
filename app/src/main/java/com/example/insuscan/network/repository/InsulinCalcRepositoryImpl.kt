package com.example.insuscan.network.repository

import com.example.insuscan.network.ApiConfig
import com.example.insuscan.network.RetrofitClient
import com.example.insuscan.network.dto.InsulinCalcRequestDto
import com.example.insuscan.network.dto.InsulinCalcResponseDto
import com.example.insuscan.network.dto.UserIdDto
import com.example.insuscan.network.repository.base.BaseRepository

/**
 * Repository implementation for the server-side insulin dose calculation endpoint.
 */
class InsulinCalcRepositoryImpl : BaseRepository(), InsulinCalcRepository {

    private val api = RetrofitClient.api

    override suspend fun calculate(
        totalCarbs: Float,
        currentGlucose: Int?,
        email: String,
        planIcr: Float?,
        planIsf: Float?,
        planTargetGlucose: Int?
    ): Result<InsulinCalcResponseDto> = safeApiCall {
        val request = InsulinCalcRequestDto(
            totalCarbs = totalCarbs,
            currentGlucose = currentGlucose,
            userId = UserIdDto(systemId = ApiConfig.SYSTEM_ID, email = email),
            planIcr = planIcr,
            planIsf = planIsf,
            planTargetGlucose = planTargetGlucose
        )
        api.calculateInsulin(request)
    }
}