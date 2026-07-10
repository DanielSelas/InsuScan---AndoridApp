package com.example.insuscan.mapping

import com.example.insuscan.meal.FoodItem
import com.example.insuscan.meal.Meal
import com.example.insuscan.network.dto.InsulinCalculationDto
import com.example.insuscan.network.dto.MealDto
import com.example.insuscan.network.dto.MealIdDto
import com.example.insuscan.utils.DateTimeHelper

object MealDtoMapper : Mapper<MealDto, Meal> {

    // converts server MealDto -> local Meal (for loading history)
    override fun map(from: MealDto): Meal {

        return Meal(
            title = from.foodItems?.firstOrNull()?.name ?: "Meal Analysis",
            carbs = from.totalCarbs ?: 0f,

            recommendedDose = from.recommendedDose,
            insulinDose = from.actualDose ?: from.recommendedDose,

            timestamp = DateTimeHelper.parseTimestamp(from.scannedTimestamp),
            serverId = from.mealId?.id,

            // portion analysis
            portionWeightGrams = from.estimatedWeight,
            portionVolumeCm3 = from.plateVolumeCm3,
            plateDiameterCm = from.plateDiameterCm,
            plateDepthCm = from.plateDepthCm,
            analysisConfidence = from.analysisConfidence,
            referenceObjectDetected = from.referenceDetected,
            referenceObjectType = from.referenceObjectType,

            foodItems = from.foodItems?.map { FoodItemDtoMapper.map(it) }
                ?.let(::sanitizeFoodNames),

            // technical (save for documentation)
            profileComplete = from.profileComplete ?: false,
            missingProfileFields = from.missingProfileFields ?: emptyList(),
            insulinMessage = from.insulinMessage,

            glucoseLevel = from.currentGlucose ?: from.insulinCalculation?.currentGlucose,
            glucoseUnits = from.glucoseUnits,

            carbDose = from.carbDose ?: from.insulinCalculation?.carbDose,
            correctionDose = from.correctionDose ?: from.insulinCalculation?.correctionDose,

            // medical settings used at calculation time
            savedIcr = from.insulinCalculation?.insulinCarbRatio?.toFloatOrNull(),
            savedIsf = from.insulinCalculation?.correctionFactor,
            savedTargetGlucose = from.insulinCalculation?.targetGlucose,
            savedPlanName = from.savedPlanName ?: from.insulinCalculation?.activePlanName,

            // pipeline warnings (used by UI to show reference-object notice)
            reviewWarnings = from.reviewWarnings
        )
    }

    // converts local Meal -> server MealDto (for saving)
    fun mapToDto(meal: Meal): MealDto {
        return MealDto(
            mealId = if (meal.serverId != null) MealIdDto("", meal.serverId) else null,
            userId = null, // backend handles user context
            imageUrl = null,

            foodItems = meal.foodItems?.map { FoodItemDtoMapper.mapToDto(it) },
            totalCarbs = meal.carbs,

            // portion (save but won't display)
            estimatedWeight = meal.portionWeightGrams,
            plateVolumeCm3 = meal.portionVolumeCm3,
            plateDiameterCm = meal.plateDiameterCm,
            plateDepthCm = meal.plateDepthCm,
            analysisConfidence = meal.analysisConfidence,
            referenceDetected = meal.referenceObjectDetected,
            referenceObjectType = meal.referenceObjectType,

            insulinCalculation = InsulinCalculationDto(
                totalCarbs = meal.carbs,
                carbDose = meal.carbDose,
                correctionDose = meal.correctionDose,
                recommendedDose = meal.recommendedDose,
                insulinCarbRatio = meal.savedIcr?.toString(),
                currentGlucose = meal.glucoseLevel,
                targetGlucose = meal.savedTargetGlucose,
                correctionFactor = meal.savedIsf,
                activePlanName = meal.savedPlanName,

                ),

            currentGlucose = meal.glucoseLevel,
            glucoseUnits = meal.glucoseUnits,
            activityLevel = meal.activityLevel,

            // Calculation breakdown at top level
            carbDose = meal.carbDose,
            correctionDose = meal.correctionDose,

            // technical (save for documentation)
            profileComplete = meal.profileComplete,
            missingProfileFields = meal.missingProfileFields,
            insulinMessage = meal.insulinMessage,

            recommendedDose = meal.recommendedDose,
            actualDose = meal.insulinDose,

            status = "CONFIRMED",
            scannedTimestamp = DateTimeHelper.formatForApi(meal.timestamp),
            confirmedTimestamp = null,
            completedTimestamp = null,
            savedPlanName = meal.savedPlanName
        )
    }

    // Remove " with X" suffixes when breakdown exists (e.g. "Rice with chicken" → "Rice")
    private fun sanitizeFoodNames(items: List<FoodItem>): List<FoodItem> {
        if (items.size <= 1) return items
        return items.map { item ->
            if (item.name.contains(" with ", ignoreCase = true)) {
                item.copy(name = item.name.replace(Regex(" with .*", RegexOption.IGNORE_CASE), ""))
            } else {
                item
            }
        }
    }
}