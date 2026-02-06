package com.example.insuscan.manualentry

data class EditableFoodItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    var name: String,
    var weightGrams: Float,
    var carbsPer100g: Float?,  // Nullable - will be filled after USDA lookup
    var usdaFdcId: String? = null,
    var isLoading: Boolean = false  // True while fetching USDA data
) {
    // Calculate actual carbs based on weight (only if we have carbs data)
    val totalCarbs: Float
        get() = if (carbsPer100g != null) {
            (weightGrams * carbsPer100g!!) / 100f
        } else {
            0f  // Return 0 if data not yet loaded
        }
}