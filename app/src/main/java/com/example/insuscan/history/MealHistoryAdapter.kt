package com.example.insuscan.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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

        fun bind(item: HistoryUiModel.MealItem, isExpanded: Boolean) {
            val meal = item.meal

            // Simple binding directly from UI Model
            titleText.text = item.displayTitle
            detailsText.text = item.summaryDetailsText
            timeText.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(meal.timestamp))

            sickIndicator.isVisible = meal.wasSickMode
            stressIndicator.isVisible = meal.wasStressMode

            expandIcon.rotation = if (isExpanded) 180f else 0f
            expandedLayout.isVisible = isExpanded

            if (isExpanded) {
                bindExpandedContent(item)
            }

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

        private fun bindExpandedContent(item: HistoryUiModel.MealItem) {
            // No logic here, just assignment
            foodItemsText.text = item.formattedFoodList

            glucoseValue.text = item.glucoseText
            glucoseLayout.isVisible = item.isGlucoseVisible

            activityValue.text = item.activityText
            activityLayout.isVisible = item.isActivityVisible

            calcCarbDose.text = item.carbDoseText

            calcCorrection.text = item.correctionDoseText
            calcCorrection.isVisible = item.isCorrectionVisible

            calcExercise.text = item.exerciseDoseText
            calcExercise.isVisible = item.isExerciseVisible

            calcFinal.text = item.finalDoseText
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