package com.example.insuscan.mapping

import com.example.insuscan.meal.Meal
import com.example.insuscan.network.dto.*
import com.example.insuscan.utils.DateTimeHelper

object MealDtoMapper : Mapper<MealDto, Meal> {

    override fun map(from: MealDto): Meal {
        return Meal(
            title = from.foodItems?.firstOrNull()?.name ?: "Meal Analysis",
            carbs = from.totalCarbs ?: 0f,
            insulinDose = from.actualDose ?: from.recommendedDose,
            timestamp = DateTimeHelper.parseTimestamp(from.scannedTimestamp),
            serverId = from.mealId?.id,

            // Portion analysis data
            portionWeightGrams = from.estimatedWeight,
            portionVolumeCm3 = from.plateVolumeCm3,
            plateDiameterCm = from.plateDiameterCm,
            plateDepthCm = from.plateDepthCm,
            analysisConfidence = from.analysisConfidence,
            referenceObjectDetected = from.referenceDetected,

            // Food items
            foodItems = from.foodItems?.map { FoodItemDtoMapper.map(it) },

            // Profile status
            profileComplete = from.profileComplete ?: false,
            missingProfileFields = from.missingProfileFields ?: emptyList(),
            insulinMessage = from.insulinMessage,

            // Glucose and calculation data
            glucoseLevel = from.insulinCalculation?.currentGlucose,
            carbDose = from.insulinCalculation?.carbDose,
            correctionDose = from.insulinCalculation?.correctionDose,

            // Context flags
            wasSickMode = from.wasSickMode == true,
            wasStressMode = from.wasStressMode == true
        )
    }

    fun mapToDto(meal: Meal): MealDto {
        return MealDto(
            mealId = if (meal.serverId != null) MealIdDto("", meal.serverId) else null, // System ID handled by backend/interceptor
            userId = null, // Backend handles user context
            imageUrl = meal.imagePath, // Or URL if available
            foodItems = meal.foodItems?.map { FoodItemDtoMapper.mapToDto(it) },
            totalCarbs = meal.carbs,
            estimatedWeight = meal.portionWeightGrams,
            plateVolumeCm3 = meal.portionVolumeCm3,
            plateDiameterCm = meal.plateDiameterCm,
            plateDepthCm = meal.plateDepthCm,
            analysisConfidence = meal.analysisConfidence,
            referenceDetected = meal.referenceObjectDetected,
            insulinCalculation = InsulinCalculationDto(
                totalCarbs = meal.carbs,
                carbDose = meal.carbDose,
                correctionDose = meal.correctionDose,
                recommendedDose = meal.insulinDose,
                insulinCarbRatio = null, // Backend recalculates/stores
                currentGlucose = meal.glucoseLevel,
                targetGlucose = null,
                correctionFactor = null
            ),
            profileComplete = meal.profileComplete,
            missingProfileFields = meal.missingProfileFields,
            insulinMessage = meal.insulinMessage,
            wasSickMode = meal.wasSickMode,
            wasStressMode = meal.wasStressMode,
            recommendedDose = meal.insulinDose,
            actualDose = meal.insulinDose, // Assuming confirmed dose
            status = "CONFIRMED",
            scannedTimestamp = DateTimeHelper.formatForApi(meal.timestamp),
            confirmedTimestamp = null,
            completedTimestamp = null
        )
    }
}