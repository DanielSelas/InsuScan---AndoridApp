package com.example.insuscan.manualentry

data class FoodSearchResult(
    val fdcId: String,
    val name: String,
    val carbsPer100g: Float,
    val servingSize: Float? = null,  // optional serving size in grams
    val servingUnit: String? = null  // e.g. "cup", "slice"
)