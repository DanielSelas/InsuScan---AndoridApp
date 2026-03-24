package com.example.insuscan.summary.helpers

import android.content.Context
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
    const val IOB_SOFT_LIMIT = 20f
    const val IOB_HARD_LIMIT = 50f

    fun performCalculation(
        context: Context,
        carbs: Float,
        glucose: Int?,
        activeInsulin: Float?,
        activityLevel: String,
        gramsPerUnit: Float
    ): DoseResult {
        FileLogger.log("CALC", "--- New Calculation Started ---")
        val carbDose = if (gramsPerUnit > 0) carbs / gramsPerUnit else 0f
        
        var correctionDose = 0f
        if (glucose != null) {
            val target = UserProfileManager.getTargetGlucose(context) ?: 100
            val isf = UserProfileManager.getCorrectionFactor(context) ?: 50f
            val unit = UserProfileManager.getGlucoseUnits(context)
            val glucoseInMgDl = if (unit == "mmol/L") (glucose * 18) else glucose
            if (isf > 0) {
                correctionDose = (glucoseInMgDl - target) / isf
            }
        }

        val baseDose = carbDose + correctionDose
        var sickAdj = 0f
        if (UserProfileManager.isSickModeEnabled(context)) {
            sickAdj = baseDose * (UserProfileManager.getSickDayAdjustment(context) / 100f)
        }
        
        var stressAdj = 0f
        if (UserProfileManager.isStressModeEnabled(context)) {
            stressAdj = baseDose * (UserProfileManager.getStressAdjustment(context) / 100f)
        }
        
        var exerciseAdj = 0f 
        if (activityLevel == "light") {
            exerciseAdj = baseDose * (UserProfileManager.getLightExerciseAdjustment(context) / 100f)
        } else if (activityLevel == "intense") {
            exerciseAdj = baseDose * (UserProfileManager.getIntenseExerciseAdjustment(context) / 100f)
        } else if (UserProfileManager.isExerciseModeEnabled(context) && activityLevel == "normal") {
            exerciseAdj = baseDose * (UserProfileManager.getLightExerciseAdjustment(context) / 100f)
        }

        val rawIob = activeInsulin ?: 0f
        val iob = rawIob.coerceIn(0f, IOB_HARD_LIMIT)

        var finalDose = baseDose + sickAdj + stressAdj - exerciseAdj - iob
        if (finalDose < 0) finalDose = 0f

        val roundedDose = (Math.round(finalDose * 100) / 100f)
        FileLogger.log("CALC", "--- Calculation Complete ---")

        return DoseResult(
            carbDose, correctionDose, baseDose, 
            sickAdj, stressAdj, exerciseAdj, iob, 
            finalDose, roundedDose
        )
    }
}
