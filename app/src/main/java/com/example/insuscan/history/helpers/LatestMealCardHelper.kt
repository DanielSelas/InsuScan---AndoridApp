package com.example.insuscan.history.helpers

import android.view.View
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import com.example.insuscan.R
import com.example.insuscan.history.models.HistoryUiModel
import com.example.insuscan.meal.Meal

class LatestMealCardHelper(private val view: View?) {
    private var isExpanded = false
    private var currentMeal: Meal? = null

    fun updateCard(meal: Meal?) {
        val cardLatest = view?.findViewById<CardView>(R.id.card_latest_meal)
        val tvPreviousLabel = view?.findViewById<TextView>(R.id.tv_previous_label)
        
        currentMeal = meal

        if (meal == null) {
            cardLatest?.visibility = View.GONE
            tvPreviousLabel?.visibility = View.GONE
            return
        }

        cardLatest?.visibility = View.VISIBLE
        tvPreviousLabel?.visibility = View.VISIBLE

        val header = view?.findViewById<View>(R.id.layout_latest_header)
        val expanded = view?.findViewById<View>(R.id.layout_latest_expanded)
        
        val tvTitle = view?.findViewById<TextView>(R.id.tv_latest_title)
        val tvSubtitle = view?.findViewById<TextView>(R.id.tv_latest_subtitle)
        val tvCarbs = view?.findViewById<TextView>(R.id.tv_latest_carbs)
        val tvTime = view?.findViewById<TextView>(R.id.tv_latest_time)
        val tvDoseBadge = view?.findViewById<TextView>(R.id.tv_latest_dose_badge)
        val tvIndicatorSick = view?.findViewById<TextView>(R.id.tv_latest_indicator_sick)
        val tvIndicatorStress = view?.findViewById<TextView>(R.id.tv_latest_indicator_stress)

        val uiModel = HistoryUiModel.MealItem(meal)

        // Header Binding
        tvTitle?.text = uiModel.displayTitle
        tvSubtitle?.text = uiModel.displaySubtitle
        tvCarbs?.text = "${meal.carbs.toInt()}g"
        tvTime?.text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(meal.timestamp))
        
        tvIndicatorSick?.isVisible = meal.wasSickMode
        tvIndicatorStress?.isVisible = meal.wasStressMode
        
        tvDoseBadge?.isVisible = meal.insulinDose != null || meal.recommendedDose != null
        tvDoseBadge?.text = uiModel.totalDoseValue

        // Expanded Section Binding
        expanded?.isVisible = isExpanded
        if (isExpanded) {
            // Context Row
            val layoutGlucose = view?.findViewById<View>(R.id.layout_latest_context_glucose)
            val tvGlucose = view?.findViewById<TextView>(R.id.tv_latest_context_glucose)
            val layoutActivity = view?.findViewById<View>(R.id.layout_latest_context_activity)
            val tvActivity = view?.findViewById<TextView>(R.id.tv_latest_context_activity)
            val layoutModes = view?.findViewById<View>(R.id.layout_latest_context_modes)
            val tvStatusSick = view?.findViewById<TextView>(R.id.tv_latest_status_sick)
            val tvStatusStress = view?.findViewById<TextView>(R.id.tv_latest_status_stress)

            layoutGlucose?.isVisible = uiModel.isGlucoseVisible
            tvGlucose?.text = uiModel.glucoseText
            layoutActivity?.isVisible = uiModel.isActivityVisible
            tvActivity?.text = uiModel.activityText
            
            val hasModes = meal.wasSickMode || meal.wasStressMode
            layoutModes?.isVisible = hasModes
            tvStatusSick?.isVisible = meal.wasSickMode
            tvStatusStress?.isVisible = meal.wasStressMode

            // Food List
            val tvFoodList = view?.findViewById<TextView>(R.id.tv_latest_food_list)
            tvFoodList?.text = uiModel.receiptFoodList

            // Receipt
            val tvReceiptCarbLabel = view?.findViewById<TextView>(R.id.tv_latest_receipt_carb_label)
            val tvReceiptCarbValue = view?.findViewById<TextView>(R.id.tv_latest_receipt_carb_value)
            val rowCorrection = view?.findViewById<View>(R.id.row_latest_receipt_correction)
            val tvCorrectionValue = view?.findViewById<TextView>(R.id.tv_latest_receipt_correction_value)
            val rowExercise = view?.findViewById<View>(R.id.row_latest_receipt_exercise)
            val tvExerciseValue = view?.findViewById<TextView>(R.id.tv_latest_receipt_exercise_value)
            val rowSick = view?.findViewById<View>(R.id.row_latest_receipt_sick)
            val tvSickValue = view?.findViewById<TextView>(R.id.tv_latest_receipt_sick_value)
            val rowStress = view?.findViewById<View>(R.id.row_latest_receipt_stress)
            val tvStressValue = view?.findViewById<TextView>(R.id.tv_latest_receipt_stress_value)
            val tvReceiptTotal = view?.findViewById<TextView>(R.id.tv_latest_receipt_total)

            tvReceiptCarbLabel?.text = uiModel.carbDoseLabel
            tvReceiptCarbValue?.text = uiModel.carbDoseValue
            
            rowCorrection?.isVisible = uiModel.isCorrectionVisible
            tvCorrectionValue?.text = uiModel.correctionDoseValue
            
            rowExercise?.isVisible = uiModel.isExerciseVisible
            tvExerciseValue?.text = uiModel.exerciseDoseValue
            
            rowSick?.isVisible = uiModel.isSickVisible
            tvSickValue?.text = uiModel.sickDoseValue
            
            rowStress?.isVisible = uiModel.isStressVisible
            tvStressValue?.text = uiModel.stressDoseValue
            
            tvReceiptTotal?.text = uiModel.totalDoseValue
        }

        header?.setOnClickListener {
            isExpanded = !isExpanded
            updateCard(currentMeal)
        }
    }
}
