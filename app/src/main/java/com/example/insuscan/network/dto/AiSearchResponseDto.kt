package com.example.insuscan.network.dto

data class AiSearchResponseDto(
    val results: List<ScoredFoodResultDto>?,
    val processingInfo: SearchOptimizationDto?,
    val aiEnabled: Boolean?
)
