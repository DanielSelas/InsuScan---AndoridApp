package com.example.insuscan.meal

// Represents a scanned meal with nutritional and analysis data
data class Meal(
    val title: String,
    val carbs: Float,
    val insulinDose: Float? = null,
    val timestamp: Long = System.currentTimeMillis(),

    // Portion analysis data (from ARCore + OpenCV)
    val portionWeightGrams: Float? = null,
    val portionVolumeCm3: Float? = null,
    val plateDiameterCm: Float? = null,
    val plateDepthCm: Float? = null,
    val analysisConfidence: Float? = null,
    val referenceObjectDetected: Boolean? = null,

    // Food items from server
    val foodItems: List<FoodItem>? = null,

    // Server IDs for sync
    val serverId: String? = null,

    // Context flags (existing)
    val wasSickMode: Boolean = false,
    val wasStressMode: Boolean = false,

    // Glucose and activity data
    val glucoseLevel: Int? = null,
    val glucoseUnits: String? = null,  // "mg/dL" or "mmol/L"
    val activityLevel: String? = null,  // "normal", "light", "intense"

    // Calculation breakdown
    val carbDose: Float? = null,
    val correctionDose: Float? = null,
    val exerciseAdjustment: Float? = null,

    val profileComplete: Boolean = false,
    val missingProfileFields: List<String> = emptyList(),
    val insulinMessage: String? = null,
)

data class FoodItem(
    val name: String,
    val nameHebrew: String? = null,
    val carbsGrams: Float? = null,
    val weightGrams: Float? = null,
    val confidence: Float? = null
)