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
    val referenceObjectDetected: Boolean = false
)