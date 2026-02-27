package com.example.insuscan.network.dto

import com.google.gson.annotations.SerializedName

// Matches server's MealBoundary
data class MealDto(
    val mealId: MealIdDto?,
    val userId: UserIdDto?,
    val imageUrl: String?,
    val foodItems: List<FoodItemDto>?,
    val totalCarbs: Float?,
    val estimatedWeight: Float?,
    val plateVolumeCm3: Float?,
    val plateDiameterCm: Float?,
    val plateDepthCm: Float?,
    val analysisConfidence: Float?,
    val referenceDetected: Boolean?,
    val referenceObjectType: String?,
    val insulinCalculation: InsulinCalculationDto?,
    val profileComplete: Boolean?,
    val missingProfileFields: List<String>?,
    val insulinMessage: String?,
    val wasSickMode: Boolean?,
    val wasStressMode: Boolean?,

    // added: server stores these at top level too
    val currentGlucose: Int?,
    val activityLevel: String?,
    val glucoseUnits: String?,  // "mg/dL" or "mmol/L"

    // Calculation breakdown (top level)
    val carbDose: Float?,
    val correctionDose: Float?,
    val sickAdjustment: Float?,
    val stressAdjustment: Float?,
    val exerciseAdjustment: Float?,
    val activeInsulin: Float?,

    val recommendedDose: Float?,
    val actualDose: Float?,
    val status: String?,
    @SerializedName(value = "scannedAt", alternate = ["scannedTimestamp"])
    val scannedTimestamp: String?,
    @SerializedName(value = "confirmedAt", alternate = ["confirmedTimestamp"])
    val confirmedTimestamp: String?,
    @SerializedName(value = "completedAt", alternate = ["completedTimestamp"])
    val completedTimestamp: String?
)

data class MealIdDto(
    val systemId: String,
    @SerializedName(value = "mealId", alternate = ["id"])
    val id: String
)

data class FoodItemDto(
    val name: String,
    val nameHebrew: String?,
    @SerializedName(value = "quantity", alternate = ["estimatedWeightGrams"])
    val estimatedWeightGrams: Float?,
    @SerializedName(value = "carbs", alternate = ["carbsGrams"])
    val carbsGrams: Float?,
    val confidence: Float?,
    // bbox from GPT (% of image) for GrabCut segmentation
    val bboxXPct: Float? = null,
    val bboxYPct: Float? = null,
    val bboxWPct: Float? = null,
    val bboxHPct: Float? = null
)

data class InsulinCalculationDto(
    val totalCarbs: Float?,
    val carbDose: Float?,
    val correctionDose: Float?,
    @SerializedName(value = "recommendedDose", alternate = ["totalRecommendedDose"])
    val recommendedDose: Float?,
    @SerializedName(value = "insulinCarbRatio", alternate = ["insulinCarbRatioUsed"])
    val insulinCarbRatio: String?,
    val currentGlucose: Int?,
    @SerializedName(value = "targetGlucose", alternate = ["targetGlucoseUsed"])
    val targetGlucose: Int?,
    @SerializedName(value = "correctionFactor", alternate = ["correctionFactorUsed"])
    val correctionFactor: Float?,

    // added: adjustment values from server
    val sickAdjustment: Float?,
    val stressAdjustment: Float?,
    val exerciseAdjustment: Float?,
    val activityLevel: String?  // "normal", "light", "intense"
)

// For creating new meal
data class CreateMealRequest(
    val userSystemId: String,
    val userEmail: String,
    val imageUrl: String
)
