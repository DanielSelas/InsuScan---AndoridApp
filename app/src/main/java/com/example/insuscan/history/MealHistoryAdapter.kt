package com.example.insuscan.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.R
import java.text.SimpleDateFormat
import java.util.Locale

class MealHistoryAdapter : PagingDataAdapter<HistoryUiModel, RecyclerView.ViewHolder>(UI_MODEL_COMPARATOR) {

    private val expandedPositions = mutableSetOf<String>()

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is HistoryUiModel.Header -> R.layout.item_history_header
            is HistoryUiModel.MealItem -> R.layout.item_meal_history_expandable
            else -> throw UnsupportedOperationException("Unknown view")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == R.layout.item_history_header) {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history_header, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_meal_history_expandable, parent, false)
            MealViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        if (holder is MealViewHolder && item is HistoryUiModel.MealItem) {
            val isExpanded = expandedPositions.contains(item.meal.serverId ?: "")
            holder.bind(item, isExpanded)
        } else if (holder is HeaderViewHolder && item is HistoryUiModel.Header) {
            holder.bind(item.date)
        }
    }

    inner class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val dateText: TextView = view.findViewById(R.id.tv_header_date)
        fun bind(date: String) {
            dateText.text = date
        }
    }

    inner class MealViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Header
        private val headerLayout: LinearLayout = itemView.findViewById(R.id.layout_header)
        private val titleText: TextView = itemView.findViewById(R.id.tv_meal_title)
        private val carbsText: TextView = itemView.findViewById(R.id.tv_meal_carbs)
        private val timeText: TextView = itemView.findViewById(R.id.tv_meal_time)
        private val headerDoseBadge: TextView = itemView.findViewById(R.id.tv_header_dose)
        private val sickIndicator: TextView = itemView.findViewById(R.id.tv_indicator_sick)
        private val stressIndicator: TextView = itemView.findViewById(R.id.tv_indicator_stress)

        // Expanded
        private val expandedLayout: LinearLayout = itemView.findViewById(R.id.layout_expanded)

        // Meal details section
        private val detailDateTime: TextView = itemView.findViewById(R.id.tv_detail_datetime)
        private val detailWeight: TextView = itemView.findViewById(R.id.tv_detail_weight)
        private val detailItemCount: TextView = itemView.findViewById(R.id.tv_detail_item_count)
        private val detailConfidence: TextView = itemView.findViewById(R.id.tv_detail_confidence)
        private val detailReference: TextView = itemView.findViewById(R.id.tv_detail_reference)

        // Dose comparison (recommended vs actual)
        private val doseComparisonLayout: LinearLayout = itemView.findViewById(R.id.layout_dose_comparison)
        private val doseRecommended: TextView = itemView.findViewById(R.id.tv_dose_recommended)
        private val doseActual: TextView = itemView.findViewById(R.id.tv_dose_actual)

        // Server message
        private val insulinMessage: TextView = itemView.findViewById(R.id.tv_insulin_message)

        // Context Row
        private val glucoseLayout: LinearLayout = itemView.findViewById(R.id.layout_context_glucose)
        private val glucoseValue: TextView = itemView.findViewById(R.id.tv_context_glucose)
        private val activityLayout: LinearLayout = itemView.findViewById(R.id.layout_context_activity)
        private val activityValue: TextView = itemView.findViewById(R.id.tv_context_activity)
        private val modesLayout: LinearLayout = itemView.findViewById(R.id.layout_context_modes)
        private val statusSick: TextView = itemView.findViewById(R.id.tv_status_sick)
        private val statusStress: TextView = itemView.findViewById(R.id.tv_status_stress)
        private val profileErrorText: TextView = itemView.findViewById(R.id.tv_profile_error)
        
        // Warning Box
        private val profileWarningLayout: LinearLayout = itemView.findViewById(R.id.layout_profile_warning)

        // Food List
        private val foodListText: TextView = itemView.findViewById(R.id.tv_food_list_formatted)

        // Receipt Rows
        private val receiptCarbLabel: TextView = itemView.findViewById(R.id.tv_receipt_carb_label)
        private val receiptCarbValue: TextView = itemView.findViewById(R.id.tv_receipt_carb_value)
        
        private val rowCorrection: View = itemView.findViewById(R.id.row_receipt_correction)
        private val receiptCorrectionValue: TextView = itemView.findViewById(R.id.tv_receipt_correction_value)
        
        private val rowExercise: View = itemView.findViewById(R.id.row_receipt_exercise)
        private val receiptExerciseValue: TextView = itemView.findViewById(R.id.tv_receipt_exercise_value)
        
        private val rowSick: View = itemView.findViewById(R.id.row_receipt_sick)
        private val receiptSickValue: TextView = itemView.findViewById(R.id.tv_receipt_sick_value)
        
        private val rowStress: View = itemView.findViewById(R.id.row_receipt_stress)
        private val receiptStressValue: TextView = itemView.findViewById(R.id.tv_receipt_stress_value)

        private val receiptTotalValue: TextView = itemView.findViewById(R.id.tv_receipt_total)


        fun bind(item: HistoryUiModel.MealItem, isExpanded: Boolean) {
            val meal = item.meal

            // --- Header ---
            titleText.text = item.displayTitle
            carbsText.text = "${meal.carbs.toInt()}g"
            timeText.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(meal.timestamp))
            headerDoseBadge.text = item.totalDoseValue
            
            // Indicators in header
            sickIndicator.isVisible = meal.wasSickMode
            stressIndicator.isVisible = meal.wasStressMode

            // --- Expanded Control ---
            expandedLayout.isVisible = isExpanded
            
            if (isExpanded) {
                // 1. Context
                glucoseLayout.isVisible = item.isGlucoseVisible
                if (item.isGlucoseVisible) {
                    glucoseValue.text = item.glucoseText
                }
                
                activityLayout.isVisible = item.isActivityVisible
                if (item.isActivityVisible) {
                    activityValue.text = item.activityText
                }
                
                // Modes (Sick/Stress Status)
                val hasModes = meal.wasSickMode || meal.wasStressMode
                modesLayout.isVisible = hasModes
                if (hasModes) {
                    statusSick.isVisible = meal.wasSickMode
                    statusStress.isVisible = meal.wasStressMode
                }
                
                profileErrorText.isVisible = false // Hidden - we use warning box instead
                
                // Warning Box
                profileWarningLayout.isVisible = item.hasProfileError

                // 2. Food List
                foodListText.text = item.receiptFoodList

                // 3. Receipt
                receiptCarbLabel.text = item.carbDoseLabel
                receiptCarbValue.text = item.carbDoseValue

                rowCorrection.isVisible = item.isCorrectionVisible
                if (item.isCorrectionVisible) {
                    receiptCorrectionValue.text = item.correctionDoseValue
                }

                rowExercise.isVisible = item.isExerciseVisible
                if (item.isExerciseVisible) {
                    receiptExerciseValue.text = item.exerciseDoseValue
                }

                rowSick.isVisible = item.isSickVisible
                if (item.isSickVisible) {
                    receiptSickValue.text = item.sickDoseValue
                }
                
                rowStress.isVisible = item.isStressVisible
                if (item.isStressVisible) {
                    receiptStressValue.text = item.stressDoseValue
                }

                receiptTotalValue.text = item.totalDoseValue

                // -- Recommended vs Actual comparison --
                doseComparisonLayout.isVisible = item.isActualDoseDifferent
                if (item.isActualDoseDifferent) {
                    doseRecommended.text = item.recommendedDoseText
                    doseActual.text = item.actualDoseText
                }

                // -- Server insulin message --
                insulinMessage.isVisible = item.hasInsulinMessage
                if (item.hasInsulinMessage) {
                    insulinMessage.text = item.insulinMessageText
                }

                // -- Meal details --
                detailDateTime.text = item.fullDateTime

                val weightStr = item.totalWeightText
                detailWeight.isVisible = weightStr != null
                if (weightStr != null) {
                    detailWeight.text = "Estimated weight: $weightStr"
                }

                detailItemCount.text = item.foodItemCountText

                val confStr = item.confidenceText
                detailConfidence.isVisible = confStr != null
                if (confStr != null) {
                    detailConfidence.text = "Confidence: $confStr"
                }

                val refStr = item.referenceDetectedText
                detailReference.isVisible = refStr != null
                if (refStr != null) {
                    detailReference.text = refStr
                }
            }

            headerLayout.setOnClickListener {
                val mealId = meal.serverId ?: meal.timestamp.toString()
                if (expandedPositions.contains(mealId)) {
                    expandedPositions.remove(mealId)
                } else {
                    expandedPositions.add(mealId)
                }
                notifyItemChanged(bindingAdapterPosition)
            }
        }
    }

    companion object {
        private val UI_MODEL_COMPARATOR = object : DiffUtil.ItemCallback<HistoryUiModel>() {
            override fun areItemsTheSame(oldItem: HistoryUiModel, newItem: HistoryUiModel): Boolean {
                return (oldItem is HistoryUiModel.MealItem && newItem is HistoryUiModel.MealItem &&
                        oldItem.meal.serverId == newItem.meal.serverId) ||
                        (oldItem is HistoryUiModel.Header && newItem is HistoryUiModel.Header &&
                                oldItem.date == newItem.date)
            }

            override fun areContentsTheSame(oldItem: HistoryUiModel, newItem: HistoryUiModel): Boolean {
                return oldItem == newItem
            }
        }
    }
}