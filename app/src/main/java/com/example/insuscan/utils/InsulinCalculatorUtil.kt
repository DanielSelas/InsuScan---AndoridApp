package com.example.insuscan.utils

import android.content.Context
import com.example.insuscan.profile.UserProfileManager

// Extracted from SummaryFragment.performCalculation()
// Shared between SummaryFragment and Chat flow
object InsulinCalculatorUtil {

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

    /**
     * THE GOLDEN LOGIC (Client-side) — matches Server-Side InsulinCalculator.java
     */
    fun calculate(
        context: Context,
        carbs: Float,
        glucose: Int?,
        activeInsulin: Float?,
        activityLevel: String,
        gramsPerUnit: Float,
        isSick: Boolean = false,
        isStress: Boolean = false
    ): DoseResult {
        val pm = UserProfileManager

        FileLogger.log("CALC", "--- New Calculation Started ---")
        FileLogger.log("CALC", "INPUTS: Carbs=$carbs, Glucose=$glucose, IOB=$activeInsulin, Activity=$activityLevel, ICR=$gramsPerUnit")

        // 1. Carb Dose = Carbs / Ratio
        val carbDose = if (gramsPerUnit > 0) carbs / gramsPerUnit else 0f
        FileLogger.log("CALC", "STEP 1: Carb Dose = $carbs / $gramsPerUnit = $carbDose u")

        // 2. Correction Dose
        var correctionDose = 0f
        if (glucose != null) {
            val target = pm.getTargetGlucose(context) ?: 100
            val isf = pm.getCorrectionFactor(context) ?: 50f
            val unit = pm.getGlucoseUnits(context)
            val glucoseInMgDl = if (unit == "mmol/L") (glucose * 18) else glucose

            if (isf > 0) {
                correctionDose = (glucoseInMgDl - target) / isf
                FileLogger.log("CALC", "STEP 2: Correction = ($glucoseInMgDl - $target) / $isf = $correctionDose u")
            }
        } else {
            FileLogger.log("CALC", "STEP 2: Correction = 0 (No glucose)")
        }

        // 3. Base Dose
        val baseDose = carbDose + correctionDose
        FileLogger.log("CALC", "STEP 3: Base Dose = $carbDose + $correctionDose = $baseDose u")

        // 4. Adjustments (Applied to Base Dose)
        // Sick
        var sickAdj = 0f
        if (pm.isSickModeEnabled(context)) {
            val pct = pm.getSickDayAdjustment(context)
            sickAdj = baseDose * (pct / 100f)
            FileLogger.log("CALC", "STEP 4a: Sick Adj = $baseDose * $pct% = +$sickAdj u")
        }

        // Stress
        var stressAdj = 0f
        if (pm.isStressModeEnabled(context)) {
            val pct = pm.getStressAdjustment(context)
            stressAdj = baseDose * (pct / 100f)
            FileLogger.log("CALC", "STEP 4b: Stress Adj = $baseDose * $pct% = +$stressAdj u")
        }

        // Exercise
        var exerciseAdj = 0f
        if (activityLevel == "light") {
            val pct = pm.getLightExerciseAdjustment(context)
            exerciseAdj = baseDose * (pct / 100f)
            FileLogger.log("CALC", "STEP 4c: Exercise (Light) = $baseDose * $pct% = -$exerciseAdj u")
        } else if (activityLevel == "intense") {
            val pct = pm.getIntenseExerciseAdjustment(context)
            exerciseAdj = baseDose * (pct / 100f)
            FileLogger.log("CALC", "STEP 4c: Exercise (Intense) = $baseDose * $pct% = -$exerciseAdj u")
        } else if (pm.isExerciseModeEnabled(context) && activityLevel == "normal") {
            val pct = pm.getLightExerciseAdjustment(context)
            exerciseAdj = baseDose * (pct / 100f)
            FileLogger.log("CALC", "STEP 4c: Exercise (Home) = $baseDose * $pct% = -$exerciseAdj u")
        }

        // IOB
        val iob = activeInsulin ?: 0f
        FileLogger.log("CALC", "STEP 4d: Active Insulin (IOB) = -$iob u")

        // 5. Final
        var finalDose = baseDose + sickAdj + stressAdj - exerciseAdj - iob
        if (finalDose < 0) finalDose = 0f
        FileLogger.log("CALC", "STEP 5: Final Raw = $baseDose + $sickAdj + $stressAdj - $exerciseAdj - $iob = $finalDose u")

        // 6. Rounding (2-decimal precision)
        val roundedDose = (Math.round(finalDose * 100) / 100f)
        FileLogger.log("CALC", "STEP 6: Rounded = $roundedDose u")
        FileLogger.log("CALC", "--- Calculation Complete ---")

        return DoseResult(
            carbDose, correctionDose, baseDose,
            sickAdj, stressAdj, exerciseAdj, iob,
            finalDose, roundedDose
        )
    }
}
