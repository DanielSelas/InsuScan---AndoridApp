package com.example.insuscan.network.dto

data class FoodSearchResultDto(
    val fdcId: String?,
    val foodName: String?,
    val carbsPer100g: Float?,
    val servingSize: String?,
    val servingSizeGrams: Float?,
    val found: Boolean?
)