package com.example.insuscan.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.R
import com.example.insuscan.meal.Meal

class MealHistoryAdapter : RecyclerView.Adapter<MealHistoryAdapter.ViewHolder>() {

    private var meals = listOf<Meal>()

    // Track which items are expanded
    private val expandedPositions = mutableSetOf<Int>()

    fun submitList(newMeals: List<Meal>) {
        meals = newMeals
        expandedPositions.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_meal_history_expandable, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val meal = meals[position]
        val isExpanded = expandedPositions.contains(position)
        holder.bind(meal, isExpanded)
    }

    override fun getItemCount(): Int = meals.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

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
            // Header - always visible
            titleText.text = meal.title
            detailsText.text = "${meal.carbs.toInt()}g carbs  |  ${formatDose(meal.insulinDose)} units"
            timeText.text = formatTime(meal.timestamp)

            // Indicators
            sickIndicator.visibility = if (meal.wasSickMode) View.VISIBLE else View.GONE
            stressIndicator.visibility = if (meal.wasStressMode) View.VISIBLE else View.GONE

            // Expand icon rotation
            expandIcon.rotation = if (isExpanded) 180f else 0f

            // Expanded section visibility
            expandedLayout.visibility = if (isExpanded) View.VISIBLE else View.GONE

            // Populate expanded content
            if (isExpanded) {
                bindExpandedContent(meal)
            }

            // Click to expand/collapse
            headerLayout.setOnClickListener {
                val pos = adapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener

                if (expandedPositions.contains(pos)) {
                    expandedPositions.remove(pos)
                } else {
                    expandedPositions.add(pos)
                }
                notifyItemChanged(pos)
            }
        }

        private fun bindExpandedContent(meal: Meal) {
            // Food items list
            val foodItemsStr = meal.foodItems?.joinToString("\n") { item ->
                val weight = item.weightGrams?.toInt() ?: 0
                val carbs = item.carbsGrams?.toInt() ?: 0
                "• ${item.name} (${weight}g) - ${carbs}g carbs"
            } ?: "• ${meal.title} - ${meal.carbs.toInt()}g carbs"

            foodItemsText.text = foodItemsStr

            // Glucose info
            if (meal.glucoseLevel != null) {
                glucoseLayout.visibility = View.VISIBLE
                val units = meal.glucoseUnits ?: "mg/dL"
                glucoseValue.text = "${meal.glucoseLevel} $units"
            } else {
                glucoseLayout.visibility = View.GONE
            }

            // Activity level
            if (meal.activityLevel != null && meal.activityLevel != "normal") {
                activityLayout.visibility = View.VISIBLE
                activityValue.text = when (meal.activityLevel) {
                    "light" -> "Light exercise"
                    "intense" -> "Intense exercise"
                    else -> "Normal"
                }
            } else {
                activityLayout.visibility = View.GONE
            }

            // Calculation breakdown
            calcCarbDose.text = "Carb dose: ${formatDose(meal.carbDose)}u"

            if (meal.correctionDose != null && meal.correctionDose != 0f) {
                calcCorrection.visibility = View.VISIBLE
                val sign = if (meal.correctionDose > 0) "+" else ""
                calcCorrection.text = "Correction: ${sign}${formatDose(meal.correctionDose)}u"
            } else {
                calcCorrection.visibility = View.GONE
            }

            if (meal.exerciseAdjustment != null && meal.exerciseAdjustment != 0f) {
                calcExercise.visibility = View.VISIBLE
                val sign = if (meal.exerciseAdjustment > 0) "+" else ""
                calcExercise.text = "Exercise adj: ${sign}${formatDose(meal.exerciseAdjustment)}u"
            } else {
                calcExercise.visibility = View.GONE
            }

            calcFinal.text = "Final dose: ${formatDose(meal.insulinDose)}u"
        }

        private fun formatDose(dose: Float?): String {
            if (dose == null) return "—"
            return if (dose == dose.toInt().toFloat()) {
                dose.toInt().toString()
            } else {
                String.format("%.1f", dose)
            }
        }

        private fun formatTime(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            val time = sdf.format(java.util.Date(timestamp))

            return when {
                diff < 24 * 60 * 60 * 1000 -> "Today, $time"
                diff < 48 * 60 * 60 * 1000 -> "Yesterday, $time"
                else -> {
                    val dateSdf = java.text.SimpleDateFormat("dd/MM", java.util.Locale.getDefault())
                    "${dateSdf.format(java.util.Date(timestamp))}, $time"
                }
            }
        }
    }
}