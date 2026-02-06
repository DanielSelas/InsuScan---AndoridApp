package com.example.insuscan.network.dto

data class SearchOptimizationDto(
    val translatedQuery: String?,
    val searchVariations: List<String>?,
    val excludeTerms: List<String>?,
    val intent: String?
)
