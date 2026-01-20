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
    val insulinCalculation: InsulinCalculationDto?,
    // Professional health status fields added for accuracy
    val wasSickMode: Boolean?,
    val wasStressMode: Boolean?,

    // Server-side insulin fields (Spring MealBoundary uses top-level recommendedDose/actualDose)
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
    val confidence: Float?
)

data class InsulinCalculationDto(
    val totalCarbs: Float?,
    val carbDose: Float?,
    val correctionDose: Float?,
    val recommendedDose: Float?,
    val insulinCarbRatio: String?,
    val currentGlucose: Int?,
    val targetGlucose: Int?,
    val correctionFactor: Float?
)

// For creating new meal
data class CreateMealRequest(
    val userSystemId: String,
    val userEmail: String,
    val imageUrl: String
)
