package com.example.insuscan.summary.helpers

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.insuscan.R
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.profile.UserProfileManager

class SummaryDoseDisplayHandler(
    private val context: Context,
    private val ui: SummaryUiManager,
    private val onProfileIncompleteRequested: () -> Unit
) {
    fun calculateAndDisplayDose(): DoseResult? {
        val meal = MealSessionManager.currentMeal ?: return null
        val gramsPerUnit = UserProfileManager.getGramsPerUnit(context)

        if (!meal.profileComplete || gramsPerUnit == null) {
            showProfileIncompleteState()
            return null
        }

        val result = SummaryCalculationHelper.performCalculation(
            context = context,
            carbs = meal.carbs,
            glucose = ui.glucoseEditText.text.toString().toIntOrNull(),
            activeInsulin = ui.activeInsulinEditText.text.toString().toFloatOrNull(),
            activityLevel = ui.getSelectedActivityLevel(),
            gramsPerUnit = gramsPerUnit
        )

        displayDoseResults(result)
        return result
    }

    private fun displayDoseResults(result: DoseResult) {
        ui.carbDoseText.text = String.format("%.1f u", result.carbDose)

        if (result.correctionDose != 0f) {
            ui.correctionLayout.visibility = View.VISIBLE
            val sign = if (result.correctionDose > 0) "+" else ""
            ui.correctionDoseText.text = String.format("%s%.1f u", sign, result.correctionDose)
            ui.correctionDoseText.setTextColor(ContextCompat.getColor(context, if (result.correctionDose > 0) R.color.status_warning else R.color.status_normal))
        } else {
            ui.correctionLayout.visibility = View.GONE
        }

        if (result.sickAdj > 0) {
            ui.sickLayout.visibility = View.VISIBLE
            ui.sickAdjustmentText.text = String.format("+%.1f u", result.sickAdj)
        } else ui.sickLayout.visibility = View.GONE

        if (result.stressAdj > 0) {
            ui.stressLayout.visibility = View.VISIBLE
            ui.stressAdjustmentText.text = String.format("+%.1f u", result.stressAdj)
        } else ui.stressLayout.visibility = View.GONE

        if (result.exerciseAdj > 0) {
            ui.exerciseLayout.visibility = View.VISIBLE
            ui.exerciseAdjustmentText.text = String.format("-%.1f u", result.exerciseAdj)
        } else ui.exerciseLayout.visibility = View.GONE

        ui.finalDoseText.text = String.format("%.1f u", result.roundedDose)

        val hintView = ui.view.findViewById<TextView>(R.id.tv_dose_rounding_hint)
        if (result.roundedDose > 0 && result.roundedDose % 0.5f != 0f) {
            val roundedHalf = (Math.round(result.roundedDose * 2) / 2f)
            hintView?.text = "Round to ${String.format("%.1f", roundedHalf)} on your pen"
            hintView?.visibility = View.VISIBLE
        } else {
            hintView?.visibility = View.GONE
        }

        updateHighDoseWarning(result.roundedDose)
    }

    private fun updateHighDoseWarning(dose: Float) {
        if (dose > SummaryCalculationHelper.DOSE_WARNING_THRESHOLD) {
            ui.highDoseWarningLayout.visibility = View.VISIBLE
            when {
                dose > SummaryCalculationHelper.DOSE_HARD_CAP -> {
                    ui.highDoseWarningText.text = String.format("BLOCKED: %.1f units exceeds the maximum safe dose (%d units). Please check your meal data for errors.", dose, SummaryCalculationHelper.DOSE_HARD_CAP.toInt())
                    ui.highDoseWarningLayout.setBackgroundColor(ContextCompat.getColor(context, R.color.status_critical))
                    ui.setLogButtonEnabled(false)
                }
                dose > SummaryCalculationHelper.DOSE_BLOCKING_THRESHOLD -> {
                    ui.highDoseWarningText.text = String.format("Very high dose: %.1f units. You will need to confirm before saving.", dose)
                    ui.highDoseWarningLayout.setBackgroundColor(ContextCompat.getColor(context, R.color.status_warning))
                }
                else -> {
                    ui.highDoseWarningText.text = String.format("High dose: %.1f units. Please verify your meal data is correct.", dose)
                    ui.highDoseWarningLayout.setBackgroundColor(ContextCompat.getColor(context, R.color.status_warning))
                }
            }
        } else {
            ui.highDoseWarningLayout.visibility = View.GONE
        }
    }

    private fun showProfileIncompleteState() {
        ui.finalDoseText.text = "Setup Required"
        ui.finalDoseText.setTextColor(ContextCompat.getColor(context, R.color.error))
        ui.carbDoseText.text = "Tap here to complete profile"
        ui.carbDoseText.setTextColor(ContextCompat.getColor(context, R.color.primary))
        ui.carbDoseText.setOnClickListener { onProfileIncompleteRequested() }
        ui.correctionLayout.visibility = View.GONE
    }
}
