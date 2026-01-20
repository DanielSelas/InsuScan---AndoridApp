package com.example.insuscan.manualentry

data class EditableFoodItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    var name: String,
    var weightGrams: Float,
    var carbsPer100g: Float,  // carbs per 100g from USDA
    var usdaFdcId: String? = null
) {
    // Calculate actual carbs based on weight
    val totalCarbs: Float
        get() = (weightGrams * carbsPer100g) / 100f
}