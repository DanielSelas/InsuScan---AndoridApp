package com.example.insuscan.summary.helpers

import android.content.Context
import android.view.View
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.Group
import androidx.core.content.ContextCompat
import com.example.insuscan.R
import com.example.insuscan.meal.Meal
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.profile.UserProfileManager

class SummaryUiManager(val view: View, val context: Context) {
    val mealItemsContainer: LinearLayout = view.findViewById(R.id.layout_meal_items_container)
    val totalCarbsText: TextView = view.findViewById(R.id.tv_total_carbs)
    val editButton: Button = view.findViewById(R.id.btn_edit_meal)
    val glucoseEditText: EditText = view.findViewById(R.id.et_current_glucose)
    val glucoseUnitText: TextView = view.findViewById(R.id.tv_glucose_unit)
    val glucoseStatusText: TextView = view.findViewById(R.id.tv_glucose_status)
    val carbDoseText: TextView = view.findViewById(R.id.tv_carb_dose)
    val activePlanText: TextView? = view.findViewById(R.id.tv_active_plan)
    val activePlanLayout: View? = view.findViewById(R.id.layout_active_plan)
    val correctionLayout: LinearLayout = view.findViewById(R.id.layout_correction_dose)
    val correctionDoseText: TextView = view.findViewById(R.id.tv_correction_dose)
    val finalDoseText: TextView = view.findViewById(R.id.tv_final_dose)
    val analysisCard: CardView = view.findViewById(R.id.card_analysis_results)
    val portionWeightText: TextView = view.findViewById(R.id.tv_portion_weight)
    val plateDimensionsText: TextView = view.findViewById(R.id.tv_plate_dimensions)
    val confidenceText: TextView = view.findViewById(R.id.tv_analysis_confidence)
    val referenceStatusText: TextView = view.findViewById(R.id.tv_reference_status)
    val highDoseWarningLayout: LinearLayout = view.findViewById(R.id.layout_high_dose_warning)
    val highDoseWarningText: TextView = view.findViewById(R.id.tv_high_dose_warning)
    val logButton: Button = view.findViewById(R.id.btn_log_meal)
    val imageCard: CardView = view.findViewById(R.id.card_image)
    val mealImageView: ImageView = view.findViewById(R.id.iv_meal_image)
    val contentGroup: Group = view.findViewById(R.id.group_content)
    val emptyStateLayout: LinearLayout = view.findViewById(R.id.layout_empty_state)
    val scanNowButton: Button = view.findViewById(R.id.btn_scan_now)
    val contentScrollView: ScrollView? = view.findViewById(R.id.content_scroll_view)

    fun updateEmptyStateVisibility() {
        if (MealSessionManager.currentMeal != null) {
            contentGroup.visibility = View.VISIBLE
            emptyStateLayout.visibility = View.GONE
        } else {
            contentGroup.visibility = View.GONE
            emptyStateLayout.visibility = View.VISIBLE
        }
    }

    fun setLogButtonEnabled(enabled: Boolean) {
        logButton.isEnabled = enabled
        if (enabled) {
            logButton.backgroundTintList = android.content.res.ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.primary)
            )
            logButton.alpha = 1.0f
        } else {
            logButton.alpha = 0.5f
        }
    }

    fun setupGlucoseUnit() {
        glucoseUnitText.text = UserProfileManager.getGlucoseUnits(context)
    }

    fun updateGlucoseStatus() {
        val glucoseStr = glucoseEditText.text.toString()
        val glucose = glucoseStr.toIntOrNull()

        if (glucose == null) {
            glucoseStatusText.text = ""
            return
        }

        val target = UserProfileManager.getTargetGlucose(context) ?: 100
        val unit = UserProfileManager.getGlucoseUnits(context)

        val glucoseInMgDl = if (unit == "mmol/L") (glucose * 18) else glucose

        when {
            glucoseInMgDl < 70 -> {
                glucoseStatusText.text = "⚠️ Low!"
                glucoseStatusText.setTextColor(ContextCompat.getColor(context, R.color.status_critical))
            }
            glucoseInMgDl < target - 20 -> {
                glucoseStatusText.text = "Below target"
                glucoseStatusText.setTextColor(ContextCompat.getColor(context, R.color.status_warning))
            }
            glucoseInMgDl <= target + 30 -> {
                glucoseStatusText.text = "✓ In range"
                glucoseStatusText.setTextColor(ContextCompat.getColor(context, R.color.status_normal))
            }
            glucoseInMgDl <= 180 -> {
                glucoseStatusText.text = "Above target"
                glucoseStatusText.setTextColor(ContextCompat.getColor(context, R.color.status_warning))
            }
            else -> {
                glucoseStatusText.text = "⚠️ High!"
                glucoseStatusText.setTextColor(ContextCompat.getColor(context, R.color.status_critical))
            }
        }
    }

    fun updateAnalysisResults() {
        val meal = MealSessionManager.currentMeal

        if (meal == null || !hasAnalysisData(meal)) {
            analysisCard.visibility = View.GONE
            return
        }
        analysisCard.visibility = View.VISIBLE

        meal.portionWeightGrams?.let { weight ->
            portionWeightText.text = "Estimated weight: ${weight.toInt()} g"
        }

        val diameter = meal.plateDiameterCm
        val depth = meal.plateDepthCm
        if (diameter != null && depth != null) {
            plateDimensionsText.text = String.format("Plate: %.1f cm × %.1f cm", diameter, depth)
        }

        meal.analysisConfidence?.let { confidence ->
            val percentage = (confidence * 100).toInt()
            confidenceText.text = "Confidence: $percentage% (estimate)"
            val color = when {
                percentage >= 80 -> R.color.status_normal
                percentage >= 50 -> R.color.status_warning
                else -> R.color.status_critical
            }
            confidenceText.setTextColor(ContextCompat.getColor(context, color))
        }

        referenceStatusText.text = when (meal.referenceObjectDetected) {
            true -> "Reference: Detected ✓"
            false -> "Reference: Not detected"
            null -> "Reference: N/A"
        }
    }

    private fun hasAnalysisData(meal: Meal): Boolean {
        return meal.portionWeightGrams != null || meal.plateDiameterCm != null || meal.analysisConfidence != null
    }

    fun setupScrollListener() {
        if (contentScrollView == null) return
        contentScrollView.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                contentScrollView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                checkScrollAndEnable()
            }
        })
        contentScrollView.viewTreeObserver.addOnScrollChangedListener { checkScrollAndEnable() }
    }

    private fun checkScrollAndEnable() {
        if (logButton.isEnabled) return
        val child = contentScrollView?.getChildAt(0) ?: return
        val scrollHeight = contentScrollView.height
        val diff = child.height - (scrollHeight + contentScrollView.scrollY)
        if (child.height <= scrollHeight || diff <= 50) setLogButtonEnabled(true)
    }
}
