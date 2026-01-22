package com.example.insuscan.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.paging.PagingDataAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.R
import com.example.insuscan.meal.Meal
import java.text.SimpleDateFormat
import java.util.Locale

class MealHistoryAdapter : PagingDataAdapter<HistoryUiModel, RecyclerView.ViewHolder>(UI_MODEL_COMPARATOR) {

    private val expandedPositions = mutableSetOf<String>() // Using String ID for stability

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
            holder.bind(item.meal, isExpanded)
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
        // Header views
        private val headerLayout: LinearLayout = itemView.findViewById(R.id.layout_header)
        private val titleText: TextView = itemView.findViewById(R.id.tv_meal_title)
        private val detailsText: TextView = itemView.findViewById(R.id.tv_meal_details)
        private val timeText: TextView = itemView.findViewById(R.id.tv_meal_time)
        private val expandIcon: ImageView = itemView.findViewById(R.id.iv_expand_icon)
        private val sickIndicator: TextView = itemView.findViewById(R.id.tv_sick_indicator)
        private val stressIndicator: TextView = itemView.findViewById(R.id.tv_stress_indicator)

        // Expanded section views
        private val expandedLayout: LinearLayout = itemView.findViewById(R.id.layout_expanded)
        private val foodItemsText: TextView = itemView.findViewById(R.id.tv_food_items_list)
        private val glucoseLayout: LinearLayout = itemView.findViewById(R.id.layout_glucose_info)
        private val glucoseValue: TextView = itemView.findViewById(R.id.tv_glucose_value)
        private val activityLayout: LinearLayout = itemView.findViewById(R.id.layout_activity_info)
        private val activityValue: TextView = itemView.findViewById(R.id.tv_activity_value)
        private val calcCarbDose: TextView = itemView.findViewById(R.id.tv_calc_carb_dose)
        private val calcCorrection: TextView = itemView.findViewById(R.id.tv_calc_correction)
        private val calcExercise: TextView = itemView.findViewById(R.id.tv_calc_exercise)
        private val calcFinal: TextView = itemView.findViewById(R.id.tv_calc_final)

        fun bind(meal: Meal, isExpanded: Boolean) {
            titleText.text = meal.title
            detailsText.text = "${meal.carbs.toInt()}g carbs  |  ${formatDose(meal.insulinDose)} units"
            timeText.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(meal.timestamp))

            sickIndicator.visibility = if (meal.wasSickMode) View.VISIBLE else View.GONE
            stressIndicator.visibility = if (meal.wasStressMode) View.VISIBLE else View.GONE

            expandIcon.rotation = if (isExpanded) 180f else 0f
            expandedLayout.visibility = if (isExpanded) View.VISIBLE else View.GONE

            if (isExpanded) bindExpandedContent(meal)

            headerLayout.setOnClickListener {
                val mealId = meal.serverId ?: return@setOnClickListener
                if (expandedPositions.contains(mealId)) {
                    expandedPositions.remove(mealId)
                } else {
                    expandedPositions.add(mealId)
                }
                notifyItemChanged(bindingAdapterPosition)
            }
        }

        private fun bindExpandedContent(meal: Meal) {
            val foodItemsStr = meal.foodItems?.joinToString("\n") { item ->
                "• ${item.name} (${item.weightGrams?.toInt() ?: 0}g) - ${item.carbsGrams?.toInt() ?: 0}g carbs"
            } ?: "• ${meal.title}"

            foodItemsText.text = foodItemsStr

            if (meal.glucoseLevel != null) {
                glucoseLayout.visibility = View.VISIBLE
                glucoseValue.text = "${meal.glucoseLevel} mg/dL"
            } else {
                glucoseLayout.visibility = View.GONE
            }

            if (meal.activityLevel != null && meal.activityLevel != "normal") {
                activityLayout.visibility = View.VISIBLE
                activityValue.text = meal.activityLevel
            } else {
                activityLayout.visibility = View.GONE
            }

            calcCarbDose.text = "Carb dose: ${formatDose(meal.carbDose)}u"

            calcCorrection.visibility = if (meal.correctionDose != null && meal.correctionDose != 0f) View.VISIBLE else View.GONE
            calcCorrection.text = "Correction: ${formatDose(meal.correctionDose)}u"

            calcExercise.visibility = if (meal.exerciseAdjustment != null && meal.exerciseAdjustment != 0f) View.VISIBLE else View.GONE
            calcExercise.text = "Exercise adj: ${formatDose(meal.exerciseAdjustment)}u"

            calcFinal.text = "Final dose: ${formatDose(meal.insulinDose)}u"
        }

        private fun formatDose(dose: Float?): String {
            if (dose == null) return "—"
            return if (dose == dose.toInt().toFloat()) dose.toInt().toString() else String.format("%.1f", dose)
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