package com.example.insuscan.history.viewholders

import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.R
import com.example.insuscan.history.models.HistoryUiModel
import java.text.SimpleDateFormat
import java.util.Locale

class MealViewHolder(
    itemView: View,
    private val onHeaderClick: (String, Int) -> Unit
) : RecyclerView.ViewHolder(itemView) {

    private val headerLayout: LinearLayout = itemView.findViewById(R.id.layout_meal_header)
    private val tvMealIcon: TextView = itemView.findViewById(R.id.tv_meal_icon)
    private val tvMealName: TextView = itemView.findViewById(R.id.tv_meal_name)
    private val tvMealTime: TextView = itemView.findViewById(R.id.tv_meal_time)
    private val tvMealCarbs: TextView = itemView.findViewById(R.id.tv_meal_carbs)
    private val tvDoseBadge: TextView = itemView.findViewById(R.id.tv_dose_badge)
    private val tvChevron: TextView = itemView.findViewById(R.id.tv_expand_chevron)
    private val layoutExpanded: LinearLayout = itemView.findViewById(R.id.layout_expanded)
    private val tvGlucose: TextView = itemView.findViewById(R.id.tv_glucose)
    private val tvPlan: TextView = itemView.findViewById(R.id.tv_plan)
    private val tvStatus: TextView = itemView.findViewById(R.id.tv_status)
    private val tvFoodItems: TextView = itemView.findViewById(R.id.tv_food_items)
    private val tvCarbDose: TextView = itemView.findViewById(R.id.tv_carb_dose)
    private val layoutCorrectionRow: LinearLayout = itemView.findViewById(R.id.layout_correction_row)
    private val tvCorrection: TextView = itemView.findViewById(R.id.tv_correction)
    private val tvFinalDose: TextView = itemView.findViewById(R.id.tv_final_dose)

    fun bind(item: HistoryUiModel.MealItem, isExpanded: Boolean) {
        val meal = item.meal

        val hour = java.util.Calendar.getInstance().apply {
            timeInMillis = meal.timestamp
        }.get(java.util.Calendar.HOUR_OF_DAY)

        tvMealIcon.text = when {
            hour < 11 -> "🌅"
            hour < 16 -> "☀️"
            else -> "🌙"
        }

        itemView.findViewById<FrameLayout?>(R.id.fl_meal_icon)?.setBackgroundResource(
            when {
                hour < 11 -> R.drawable.bg_meal_icon_morning
                hour < 16 -> R.drawable.bg_meal_icon_afternoon
                else -> R.drawable.bg_meal_icon_evening
            }
        )

        tvMealName.text = item.displayTitle
        tvMealTime.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(
            java.util.Date(meal.timestamp)
        )
        tvMealCarbs.text = "${meal.carbs.toInt()}g"
        tvDoseBadge.text = item.totalDoseValue
        tvChevron.text = if (isExpanded) "∨" else "›"

        layoutExpanded.isVisible = isExpanded

        if (isExpanded) {
            val glucose = meal.glucoseLevel
            tvGlucose.text = if (glucose != null) "$glucose ${meal.glucoseUnits ?: "mg/dL"}" else "--"

            tvPlan.text = item.planDisplayText

            tvStatus.text = when {
                meal.wasSickMode -> "Sick"
                meal.wasStressMode -> "Stress"
                else -> "Normal"
            }
            tvStatus.setTextColor(ContextCompat.getColor(itemView.context, when {
                meal.wasSickMode -> R.color.status_critical
                meal.wasStressMode -> R.color.status_warning
                else -> R.color.secondary
            }))

            val foodLines = item.meal.foodItems?.joinToString("\n") { food ->
                val name = food.name
                val weight = food.weightGrams?.toInt()?.let { "${it}g" } ?: ""
                val carbs = food.carbsGrams?.toInt()?.let { "${it}g carbs" } ?: ""
                "• $name  $weight · $carbs"
            } ?: item.receiptFoodList
            tvFoodItems.text = foodLines

            tvCarbDose.text = item.carbDoseValue

            val correctionVisible = item.isCorrectionVisible
            layoutCorrectionRow.isVisible = correctionVisible
            if (correctionVisible) {
                tvCorrection.text = item.correctionDoseValue
            }

            tvFinalDose.text = item.totalDoseValue
        }

        headerLayout.setOnClickListener {
            val mealId = meal.serverId ?: meal.timestamp.toString()
            onHeaderClick(mealId, bindingAdapterPosition)
        }
    }
}