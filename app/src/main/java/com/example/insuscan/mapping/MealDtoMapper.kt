package com.example.insuscan.mapping

import com.example.insuscan.meal.Meal
import com.example.insuscan.network.dto.*
import com.example.insuscan.utils.DateTimeHelper

object MealDtoMapper : Mapper<MealDto, Meal> {

    // converts server MealDto -> local Meal (for loading history)
    override fun map(from: MealDto): Meal {
        return Meal(
            title = from.foodItems?.firstOrNull()?.name ?: "Meal Analysis",
            carbs = from.totalCarbs ?: 0f,

            // fixed: separate recommended vs actual dose
            recommendedDose = from.recommendedDose,
            insulinDose = from.actualDose ?: from.recommendedDose,

            timestamp = DateTimeHelper.parseTimestamp(from.scannedTimestamp),
            serverId = from.mealId?.id,

            // portion analysis (save but don't display)
            portionWeightGrams = from.estimatedWeight,
            portionVolumeCm3 = from.plateVolumeCm3,
            plateDiameterCm = from.plateDiameterCm,
            plateDepthCm = from.plateDepthCm,
            analysisConfidence = from.analysisConfidence,
            referenceObjectDetected = from.referenceDetected,
            referenceObjectType = from.referenceObjectType,

            // food items (sanitize "Item with X" names if breakdown exists)
            foodItems = from.foodItems?.map { FoodItemDtoMapper.map(it) }?.let { items ->
                if (items.size > 1) {
                    items.map { item ->
                        if (item.name.contains(" with ", ignoreCase = true)) {
                            item.copy(name = item.name.replace(Regex(" with .*", RegexOption.IGNORE_CASE), ""))
                        } else {
                            item
                        }
                    }
                } else {
                    items
                }
            },

            // technical (save for documentation)
            profileComplete = from.profileComplete ?: false,
            missingProfileFields = from.missingProfileFields ?: emptyList(),
            insulinMessage = from.insulinMessage,

            // fixed: read glucose from both top level and insulinCalculation
            glucoseLevel = from.currentGlucose ?: from.insulinCalculation?.currentGlucose,
            glucoseUnits = from.glucoseUnits,  // added

            // fixed: read activity from both top level and insulinCalculation
            activityLevel = from.activityLevel ?: from.insulinCalculation?.activityLevel,

            // fixed: calculation breakdown - read from top level first, fallback to nested
            carbDose = from.carbDose ?: from.insulinCalculation?.carbDose,
            correctionDose = from.correctionDose ?: from.insulinCalculation?.correctionDose,
            exerciseAdjustment = from.exerciseAdjustment ?: from.insulinCalculation?.exerciseAdjustment,
            sickAdjustment = from.sickAdjustment ?: from.insulinCalculation?.sickAdjustment,
            stressAdjustment = from.stressAdjustment ?: from.insulinCalculation?.stressAdjustment,
            activeInsulin = from.activeInsulin, // top level only

            // context flags
            wasSickMode = from.wasSickMode == true,
            wasStressMode = from.wasStressMode == true,

            // medical settings used at calculation time
            savedIcr = from.insulinCalculation?.insulinCarbRatio?.toFloatOrNull(),
            savedIsf = from.insulinCalculation?.correctionFactor,
            savedTargetGlucose = from.insulinCalculation?.targetGlucose
        )
    }

    // converts local Meal -> server MealDto (for saving)
    fun mapToDto(meal: Meal): MealDto {
        return MealDto(
            mealId = if (meal.serverId != null) MealIdDto("", meal.serverId) else null,
            userId = null, // backend handles user context
            imageUrl = null,  // fixed: don't save image path (delete after save)

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

            // fixed: complete insulin calculation with all fields
            insulinCalculation = InsulinCalculationDto(
                totalCarbs = meal.carbs,
                carbDose = meal.carbDose,
                correctionDose = meal.correctionDose,
                recommendedDose = meal.recommendedDose,
                insulinCarbRatio = meal.savedIcr?.toString(),
                currentGlucose = meal.glucoseLevel,
                targetGlucose = meal.savedTargetGlucose,
                correctionFactor = meal.savedIsf,
                sickAdjustment = meal.sickAdjustment,
                stressAdjustment = meal.stressAdjustment,
                exerciseAdjustment = meal.exerciseAdjustment,
                activityLevel = meal.activityLevel
            ),

            // fixed: top-level fields that server expects
            currentGlucose = meal.glucoseLevel,
            glucoseUnits = meal.glucoseUnits,  // added
            activityLevel = meal.activityLevel,

            // Calculation breakdown at top level
            carbDose = meal.carbDose,
            correctionDose = meal.correctionDose,
            sickAdjustment = meal.sickAdjustment,
            stressAdjustment = meal.stressAdjustment,
            exerciseAdjustment = meal.exerciseAdjustment,
            activeInsulin = meal.activeInsulin,

            // technical (save for documentation)
            profileComplete = meal.profileComplete,
            missingProfileFields = meal.missingProfileFields,
            insulinMessage = meal.insulinMessage,

            wasSickMode = meal.wasSickMode,
            wasStressMode = meal.wasStressMode,

            // fixed: separate doses
            recommendedDose = meal.recommendedDose,
            actualDose = meal.insulinDose,

            status = "CONFIRMED",
            scannedTimestamp = DateTimeHelper.formatForApi(meal.timestamp),
            confirmedTimestamp = null,
            completedTimestamp = null
        )
    }
}