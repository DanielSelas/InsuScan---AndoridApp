package com.example.insuscan.mapping

import com.example.insuscan.meal.Meal
import com.example.insuscan.network.dto.MealDto
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
}