package com.example.insuscan.summary.helpers

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.insuscan.R
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.profile.UserProfileManager

/**
 * Renders the calculated insulin dose in the summary screen: carb/correction/final doses,
 * the active plan, the pen-rounding hint, and high-dose warnings. Also hosts plan selection.
 */
class SummaryDoseDisplayHandler(
    private val context: Context,
    private val ui: SummaryUiManager,
    private val onProfileIncompleteRequested: () -> Unit,
    private val onPlanChanged: () -> Unit = {}
) {
    fun calculateAndDisplayDose(): Boolean {
        val meal = MealSessionManager.currentMeal ?: return false
        val gramsPerUnit =
            MealSessionManager.activePlanIcr ?: UserProfileManager.getGramsPerUnit(context)

        if (!meal.profileComplete || gramsPerUnit == null) {
            showProfileIncompleteState()
            return false
        }
        return true
    }

    fun displayServerResult(carbDose: Float, correctionDose: Float, total: Float): DoseResult {
        val rounded = SummaryCalculationHelper.roundForPen(context, total)
        val result = DoseResult(carbDose, correctionDose, carbDose + correctionDose, total, rounded)
        displayDoseResults(result)
        return result
    }

    private fun displayDoseResults(result: DoseResult) {
        ui.carbDoseText.text = String.format("%.1f u", result.carbDose)
        val planName = MealSessionManager.activePlanName ?: DEFAULT_PLAN_LABEL
        ui.activePlanText?.text = planName

        if (result.correctionDose != 0f) {
            ui.correctionLayout.visibility = View.VISIBLE
            val sign = if (result.correctionDose > 0) "+" else ""
            ui.correctionDoseText.text = String.format("%s%.1f u", sign, result.correctionDose)
            ui.correctionDoseText.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (result.correctionDose > 0) R.color.status_warning else R.color.status_normal
                )
            )
        } else {
            ui.correctionLayout.visibility = View.GONE
        }

        ui.finalDoseText.text = String.format("%.1f u", result.roundedDose)
        ui.recommendedDoseText.text = String.format("%.1f", result.roundedDose)

        val hintView = ui.view.findViewById<TextView>(R.id.tv_dose_rounding_hint)
        if (result.roundedDose > 0 && result.roundedDose % 0.5f != 0f) {
            val roundedHalf = (Math.round(result.roundedDose * 2) / 2f)
            hintView?.text = context.getString(R.string.msg_round_pen_hint, String.format("%.1f", roundedHalf))
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
        val customPlans = MealSessionManager.availablePlans

        val defaultIcr = UserProfileManager.getGramsPerUnit(context)
        val defaultIsf = UserProfileManager.getCorrectionFactor(context)
        val defaultTg = UserProfileManager.getTargetGlucose(context)

        val defaultLabel = buildString {
            append(DEFAULT_PLAN_LABEL)
            val details = planDetailsLabel(defaultIcr, defaultIsf, defaultTg)
            if (details.isNotEmpty()) append(" ($details)")
        }

        val allEntries = mutableListOf(defaultLabel)
        allEntries += customPlans.map { plan ->
            val details = planDetailsLabel(plan.icr, plan.isf, plan.targetGlucose)
            if (details.isNotEmpty()) "${plan.name} ($details)" else plan.name
        }

        val currentName = MealSessionManager.activePlanName
        val checkedIndex = if (currentName == null || currentName == DEFAULT_PLAN_LABEL) {
            0
        } else {
            val idx = customPlans.indexOfFirst { it.name == currentName }
            if (idx >= 0) idx + 1 else 0
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.dialog_select_plan_title)
            .setSingleChoiceItems(allEntries.toTypedArray(), checkedIndex) { dialog, which ->
                if (which == 0) {
                    MealSessionManager.clearActivePlan()
                } else {
                    val selected = customPlans[which - 1]
                    MealSessionManager.setActivePlan(
                        selected.name,
                        selected.icr,
                        selected.isf,
                        selected.targetGlucose
                    )
                }
                onPlanChanged()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun updateHighDoseWarning(dose: Float) {
        if (dose > SummaryCalculationHelper.DOSE_WARNING_THRESHOLD) {
            ui.highDoseWarningLayout.visibility = View.VISIBLE

            when {
                dose > SummaryCalculationHelper.DOSE_HARD_CAP -> {
                    ui.highDoseWarningText.text = context.getString(
                        R.string.warning_dose_blocked,
                        String.format("%.1f", dose),
                        SummaryCalculationHelper.DOSE_HARD_CAP.toInt()
                    )

                    ui.highDoseWarningLayout.setBackgroundColor(
                        ContextCompat.getColor(context, R.color.error)
                    )

                    ui.highDoseWarningText.setTextColor(
                        ContextCompat.getColor(context, R.color.white)
                    )

                    ui.setLogButtonEnabled(false)
                }

                dose > SummaryCalculationHelper.DOSE_BLOCKING_THRESHOLD -> {
                    ui.highDoseWarningText.text = context.getString(
                        R.string.warning_dose_very_high,
                        String.format("%.1f", dose)
                    )

                    ui.highDoseWarningLayout.setBackgroundColor(
                        ContextCompat.getColor(context, R.color.error)
                    )

                    ui.highDoseWarningText.setTextColor(
                        ContextCompat.getColor(context, R.color.white)
                    )
                }

                else -> {
                    ui.highDoseWarningText.text = context.getString(
                        R.string.warning_dose_high,
                        String.format("%.1f", dose)
                    )

                    ui.highDoseWarningLayout.setBackgroundColor(
                        ContextCompat.getColor(context, R.color.warning_bg)
                    )

                    ui.highDoseWarningText.setTextColor(
                        ContextCompat.getColor(context, R.color.warning)
                    )
                }
            }
        } else {
            ui.highDoseWarningLayout.visibility = View.GONE
        }
    }

    private fun showProfileIncompleteState() {
        ui.finalDoseText.text = context.getString(R.string.state_setup_required)
        ui.finalDoseText.setTextColor(ContextCompat.getColor(context, R.color.error))
        ui.carbDoseText.text = context.getString(R.string.state_tap_to_complete)
        ui.carbDoseText.setTextColor(ContextCompat.getColor(context, R.color.primary))
        ui.carbDoseText.setOnClickListener { onProfileIncompleteRequested() }
        ui.correctionLayout.visibility = View.GONE
    }

    private fun planDetailsLabel(icr: Float?, isf: Float?, targetGlucose: Int?): String {
        return buildList {
            if (icr != null) add("ICR 1:${icr.toInt()}")
            if (isf != null) add("ISF ${isf.toInt()}")
            if (targetGlucose != null) add("TG $targetGlucose")
        }.joinToString(" · ")
    }

    companion object {
        private const val DEFAULT_PLAN_LABEL = "Default"
    }
}
