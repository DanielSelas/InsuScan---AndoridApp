package com.example.insuscan.summary.helpers

import android.app.AlertDialog
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
    private val onProfileIncompleteRequested: () -> Unit,
    private val onPlanChanged: () -> Unit = {}
) {
    fun calculateAndDisplayDose(): DoseResult? {
        val meal = MealSessionManager.currentMeal ?: return null
        val gramsPerUnit = MealSessionManager.activePlanIcr ?: UserProfileManager.getGramsPerUnit(context)

        if (!meal.profileComplete || gramsPerUnit == null) {
            showProfileIncompleteState()
            return null
        }

        val result = SummaryCalculationHelper.performCalculation(
            context = context,
            carbs = meal.carbs,
            glucose = ui.glucoseEditText.text.toString().toIntOrNull(),
            gramsPerUnit = gramsPerUnit
        )

        displayDoseResults(result)
        return result
    }

    private fun displayDoseResults(result: DoseResult) {
        ui.carbDoseText.text = String.format("%.1f u", result.carbDose)
        val planName = MealSessionManager.activePlanName ?: "Default"
        ui.activePlanText?.text = planName

        if (result.correctionDose != 0f) {
            ui.correctionLayout.visibility = View.VISIBLE
            val sign = if (result.correctionDose > 0) "+" else ""
            ui.correctionDoseText.text = String.format("%s%.1f u", sign, result.correctionDose)
            ui.correctionDoseText.setTextColor(ContextCompat.getColor(context, if (result.correctionDose > 0) R.color.status_warning else R.color.status_normal))
        } else {
            ui.correctionLayout.visibility = View.GONE
        }

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

    fun setupPlanChangeListener() {
        ui.activePlanLayout?.setOnClickListener {
            showPlanSelectionDialog()
        }
    }

    private fun showPlanSelectionDialog() {
        val plans = MealSessionManager.availablePlans
        if (plans.isEmpty()) return

        val planNames = plans.map { plan ->
            val details = buildList {
                if (plan.icr != null) add("ICR 1:${plan.icr.toInt()}")
                if (plan.isf != null) add("ISF ${plan.isf.toInt()}")
                if (plan.targetGlucose != null) add("TG ${plan.targetGlucose}")
            }.joinToString(" · ")
            if (details.isNotEmpty()) "${plan.name} ($details)" else plan.name
        }.toTypedArray()

        val currentPlanName = MealSessionManager.activePlanName ?: "Default"
        val checkedIndex = plans.indexOfFirst { it.name == currentPlanName }.coerceAtLeast(0)

        AlertDialog.Builder(context)
            .setTitle("Select Insulin Plan")
            .setSingleChoiceItems(planNames, checkedIndex) { dialog, which ->
                val selected = plans[which]
                MealSessionManager.setActivePlan(selected.name, selected.icr, selected.isf, selected.targetGlucose)
                onPlanChanged()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
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
