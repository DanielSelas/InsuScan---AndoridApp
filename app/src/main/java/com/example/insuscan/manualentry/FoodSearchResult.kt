package com.example.insuscan.manualentry

data class FoodSearchResult(
    val fdcId: String,
    val name: String,
    val carbsPer100g: Float,
    val servingSize: Float? = null,  // optional serving size in grams
    val servingUnit: String? = null,  // e.g. "cup", "slice"
    // AI search fields (nullable for backward compatibility)
    val relevanceScore: Int? = null,  // 0-100 relevance score from AI
    val matchReason: String? = null   // AI's explanation of why this result matches
)