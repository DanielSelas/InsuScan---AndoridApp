package com.example.insuscan.network.dto

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
    val status: String?,
    val scannedTimestamp: String?,
    val confirmedTimestamp: String?,
    val completedTimestamp: String?
)

data class MealIdDto(
    val systemId: String,
    val id: String
)

data class FoodItemDto(
    val name: String,
    val nameHebrew: String?,
    val estimatedWeightGrams: Float?,
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
