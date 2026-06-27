package com.example.insuscan.summary.helpers

import android.content.Context
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.profile.UserProfileManager
import com.example.insuscan.utils.FileLogger

data class DoseResult(
    val carbDose: Float,
    val correctionDose: Float,
    val baseDose: Float,
    val finalDose: Float,
    val roundedDose: Float
)

object SummaryCalculationHelper {
    const val DOSE_WARNING_THRESHOLD = 15f
    const val DOSE_BLOCKING_THRESHOLD = 30f
    const val DOSE_HARD_CAP = 100f

    fun performCalculation(
        context: Context,
        carbs: Float,
        glucose: Int?,
        gramsPerUnit: Float
    ): DoseResult {
        FileLogger.log("CALC", "--- New Calculation Started ---")
        FileLogger.log("CALC", "INPUTS: Carbs=$carbs, Glucose=$glucose, ICR=$gramsPerUnit")

        val carbDose = if (gramsPerUnit > 0) carbs / gramsPerUnit else 0f
        FileLogger.log("CALC", "STEP 1: Carb Dose = $carbs / $gramsPerUnit = $carbDose u")

        var correctionDose = 0f
        if (glucose != null) {
            val target = MealSessionManager.activePlanTargetGlucose
                ?: UserProfileManager.getTargetGlucose(context) ?: 100
            val isf = MealSessionManager.activePlanIsf
                ?: UserProfileManager.getCorrectionFactor(context) ?: 50f
            if (isf > 0) {
                correctionDose = (glucose - target) / isf
                FileLogger.log("CALC", "STEP 2: Correction = ($glucose - $target) / $isf = $correctionDose u")
            }
        } else {
            FileLogger.log("CALC", "STEP 2: Correction = 0 (No glucose provided)")
        }

        val baseDose = carbDose + correctionDose
        FileLogger.log("CALC", "STEP 3: Base Dose = $carbDose + $correctionDose = $baseDose u")

        var finalDose = baseDose
        if (finalDose < 0) finalDose = 0f
        FileLogger.log("CALC", "STEP 4: Final (floored) = $finalDose u")

        val step = UserProfileManager.getDoseRounding(context) ?: 0.5f
        val roundedDose = if (step > 0) {
            (Math.round(finalDose / step) * step * 100).toInt() / 100f
        } else {
            (Math.round(finalDose * 100) / 100f)
        }
        FileLogger.log("CALC", "STEP 5: Rounded (step=$step) = $roundedDose u")
        FileLogger.log("CALC", "--- Calculation Complete ---")

        return DoseResult(carbDose, correctionDose, baseDose, finalDose, roundedDose)
    }

    fun roundForPen(context: Context, dose: Float): Float {
        val step = UserProfileManager.getDoseRounding(context) ?: 0.5f
        return if (step > 0) {
            (Math.round(dose / step) * step * 100).toInt() / 100f
        } else {
            (Math.round(dose * 100) / 100f)
        }
    }
}