package com.example.insuscan.summary.helpers

import android.content.Context
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.profile.UserProfileManager
import com.example.insuscan.utils.FileLogger

data class DoseResult(
    val carbDose: Float,
    val correctionDose: Float,
    val baseDose: Float,
    val sickAdj: Float,
    val stressAdj: Float,
    val exerciseAdj: Float,
    val iob: Float,
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
        val carbDose = if (gramsPerUnit > 0) carbs / gramsPerUnit else 0f

        var correctionDose = 0f
        if (glucose != null) {
            val target = MealSessionManager.activePlanTargetGlucose ?: UserProfileManager.getTargetGlucose(context) ?: 100
            val isf = MealSessionManager.activePlanIsf ?: UserProfileManager.getCorrectionFactor(context) ?: 50f
            val unit = UserProfileManager.getGlucoseUnits(context)
            val glucoseInMgDl = if (unit == "mmol/L") (glucose * 18) else glucose
            if (isf > 0) {
                correctionDose = (glucoseInMgDl - target) / isf
            }
        }

        val baseDose = carbDose + correctionDose

        var finalDose = baseDose
        if (finalDose < 0) finalDose = 0f

        val roundedDose = (Math.round(finalDose * 100) / 100f)
        FileLogger.log("CALC", "--- Calculation Complete ---")

        return DoseResult(
            carbDose, correctionDose, baseDose,
            0f, 0f, 0f, 0f,
            finalDose, roundedDose
        )
    }
}
