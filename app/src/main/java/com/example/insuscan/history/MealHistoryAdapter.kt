package com.example.insuscan.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
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
        private val subtitleText: TextView = itemView.findViewById(R.id.tv_meal_subtitle)

        private val carbsText: TextView = itemView.findViewById(R.id.tv_meal_carbs)
        private val timeText: TextView = itemView.findViewById(R.id.tv_meal_time)
        private val headerDoseBadge: TextView = itemView.findViewById(R.id.tv_header_dose)
        private val sickIndicator: TextView = itemView.findViewById(R.id.tv_indicator_sick)
        private val stressIndicator: TextView = itemView.findViewById(R.id.tv_indicator_stress)

        // Expanded
        private val expandedLayout: LinearLayout = itemView.findViewById(R.id.layout_expanded)

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

        // Medical Settings, Glucose & Adjustments
        private val medicalSettingsLayout: LinearLayout = itemView.findViewById(R.id.layout_medical_settings)
        private val medicalSettingsText: TextView = itemView.findViewById(R.id.tv_medical_settings)
        private val glucoseSectionLayout: LinearLayout = itemView.findViewById(R.id.layout_glucose_section)
        private val glucoseSectionText: TextView = itemView.findViewById(R.id.tv_glucose_section)
        private val adjustmentsSectionLayout: LinearLayout = itemView.findViewById(R.id.layout_adjustments_section)
        private val adjustmentsSectionText: TextView = itemView.findViewById(R.id.tv_adjustments_section)

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

        // Reference Object
        private val referenceObjectLayout: LinearLayout = itemView.findViewById(R.id.layout_reference_object)
        private val referenceObjectTypeText: TextView = itemView.findViewById(R.id.tv_reference_object_type)


        fun bind(item: HistoryUiModel.MealItem, isExpanded: Boolean) {
            val meal = item.meal

            // --- Header ---
            titleText.text = item.displayTitle
            carbsText.text = "${meal.carbs.toInt()}g"
            timeText.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(meal.timestamp))
            headerDoseBadge.text = item.totalDoseValue


            // NEW: Add subtitle
            subtitleText.text = item.displaySubtitle
            subtitleText.isVisible = item.displaySubtitle.isNotEmpty()

            // Indicators in header
            sickIndicator.isVisible = meal.wasSickMode
            stressIndicator.isVisible = meal.wasStressMode

            // --- Expanded Control ---
            expandedLayout.isVisible = isExpanded

            if (isExpanded) {
                // 1. Context with enhanced colors
                glucoseLayout.isVisible = item.isGlucoseVisible
                if (item.isGlucoseVisible) {
                    glucoseValue.text = item.glucoseText
                    // Color based on level
                    val level = (meal.glucoseLevel ?: 0f).toFloat()
                    val colorRes = when {
                        level < 70f -> R.color.status_critical
                        level > 180f -> R.color.status_warning
                        else -> R.color.status_normal
                    }
                    glucoseValue.setTextColor(ContextCompat.getColor(itemView.context, colorRes))
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

                // Warning Box
                profileWarningLayout.isVisible = item.hasProfileError

                // Reference Object
                val refRaw = item.referenceObjectTypeRaw
                referenceObjectLayout.isVisible = refRaw != null
                if (refRaw != null) {
                    referenceObjectTypeText.text = com.example.insuscan.utils.ReferenceObjectHelper.displayLabel(itemView.context, refRaw)
                }

                // Medical Settings
                val icr = meal.savedIcr
                val isf = meal.savedIsf
                val target = meal.savedTargetGlucose
                val hasMedical = icr != null || isf != null || target != null
                medicalSettingsLayout.isVisible = hasMedical
                if (hasMedical) {
                    val parts = mutableListOf<String>()
                    if (icr != null) parts.add("ICR: 1u per ${String.format("%.1f", icr)}g")
                    if (isf != null) parts.add("ISF: ${String.format("%.0f", isf)} mg/dL per 1u")
                    if (target != null) parts.add("Target: $target mg/dL")
                    medicalSettingsText.text = parts.joinToString("\n")
                }

                // Glucose Section
                val glucose = meal.glucoseLevel
                glucoseSectionLayout.isVisible = glucose != null
                if (glucose != null) {
                    val units = meal.glucoseUnits ?: "mg/dL"
                    glucoseSectionText.text = "$glucose $units"
                }

                // Adjustments Section
                val adjParts = mutableListOf<String>()
                if (meal.wasSickMode && meal.savedSickPct > 0) adjParts.add("🤒 Sick: +${meal.savedSickPct}%")
                if (meal.wasStressMode && meal.savedStressPct > 0) adjParts.add("😫 Stress: +${meal.savedStressPct}%")
                if (meal.savedExercisePct > 0) {
                    val actLabel = when (meal.activityLevel) {
                        "light" -> "🏃 Light"
                        "intense" -> "🏋️ Intense"
                        else -> "🏃 Exercise"
                    }
                    adjParts.add("$actLabel: -${meal.savedExercisePct}%")
                }
                adjustmentsSectionLayout.isVisible = adjParts.isNotEmpty()
                if (adjParts.isNotEmpty()) {
                    adjustmentsSectionText.text = adjParts.joinToString("\n")
                }

                // 2. Food List
                foodListText.text = item.receiptFoodList

                // 3. Receipt with color-coded values
                receiptCarbLabel.text = item.carbDoseLabel
                receiptCarbValue.text = item.carbDoseValue
                receiptCarbValue.setTextColor(ContextCompat.getColor(itemView.context, R.color.primary))

                rowCorrection.isVisible = item.isCorrectionVisible
                if (item.isCorrectionVisible) {
                    receiptCorrectionValue.text = item.correctionDoseValue
                    receiptCorrectionValue.setTextColor(ContextCompat.getColor(itemView.context, R.color.status_warning))
                }

                rowExercise.isVisible = item.isExerciseVisible
                if (item.isExerciseVisible) {
                    receiptExerciseValue.text = item.exerciseDoseValue
                    receiptExerciseValue.setTextColor(ContextCompat.getColor(itemView.context, R.color.secondary))
                }

                rowSick.isVisible = item.isSickVisible
                if (item.isSickVisible) {
                    receiptSickValue.text = item.sickDoseValue
                    receiptSickValue.setTextColor(ContextCompat.getColor(itemView.context, R.color.status_critical))
                }

                rowStress.isVisible = item.isStressVisible
                if (item.isStressVisible) {
                    receiptStressValue.text = item.stressDoseValue
                    receiptStressValue.setTextColor(ContextCompat.getColor(itemView.context, R.color.status_warning))
                }

                receiptTotalValue.text = item.totalDoseValue
                receiptTotalValue.setTextColor(ContextCompat.getColor(itemView.context, R.color.text_primary))
            }

            // Click listener with animation
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