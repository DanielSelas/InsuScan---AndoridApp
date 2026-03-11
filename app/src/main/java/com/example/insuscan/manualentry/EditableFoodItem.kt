package com.example.insuscan.manualentry

data class EditableFoodItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    var name: String,
    var weightGrams: Float,
    var carbsPer100g: Float?,
    var usdaFdcId: String? = null,
    var isLoading: Boolean = false,
    val originalWeightGrams: Float = weightGrams,
    val originalCarbsPer100g: Float? = carbsPer100g
) {
    // Calculate actual carbs based on weight (only if we have carbs data)
    val totalCarbs: Float
        get() = if (carbsPer100g != null) {
            (weightGrams * carbsPer100g!!) / 100f
        } else {
            0f  // Return 0 if data not yet loaded
        }
    val isModified: Boolean
        get() = weightGrams != originalWeightGrams || carbsPer100g != originalCarbsPer100g

    fun resetToOriginal() {
        weightGrams = originalWeightGrams
        carbsPer100g = originalCarbsPer100g
    }
}