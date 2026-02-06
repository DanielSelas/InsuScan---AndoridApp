package com.example.insuscan.network.dto

data class ScoredFoodResultDto(
    val fdcId: String?,
    val foodName: String?,
    val carbsPer100g: Float?,
    val servingSize: String?,
    val servingSizeGrams: Float?,
    val found: Boolean?,
    // AI scoring fields
    val relevanceScore: Int?,
    val matchReason: String?
)
