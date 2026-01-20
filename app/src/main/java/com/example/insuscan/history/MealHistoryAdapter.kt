package com.example.insuscan.history

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.R

class MealHistoryAdapter(
    private val items: List<MealHistoryItem>
) : RecyclerView.Adapter<MealHistoryAdapter.MealHistoryViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MealHistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_meal_history, parent, false)
        return MealHistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: MealHistoryViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item)
    }

    override fun getItemCount(): Int = items.size

    class MealHistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val titleText: TextView = itemView.findViewById(R.id.tv_item_meal_title)
        private val detailsText: TextView = itemView.findViewById(R.id.tv_item_meal_details)
        private val timeText: TextView = itemView.findViewById(R.id.tv_item_meal_time)

        private val sickIcon: TextView = itemView.findViewById(R.id.tv_item_sick_indicator)
        private val stressIcon: TextView = itemView.findViewById(R.id.tv_item_stress_indicator)
        private val glucoseIndicator: TextView =
            itemView.findViewById(R.id.tv_item_glucose_indicator)

        fun bind(item: MealHistoryItem) {
            titleText.text = item.title
            detailsText.text = "${item.carbsText}   |   ${item.insulinText}"
            timeText.text = item.timeText

            // Ensuring indicators align with the Summary screen logic
            sickIcon.visibility = if (item.wasSickMode) View.VISIBLE else View.GONE
            stressIcon.visibility = if (item.wasStressMode) View.VISIBLE else View.GONE

            if (!item.glucoseLevel.isNullOrBlank()) {
                glucoseIndicator.visibility = View.VISIBLE
                // Updated to professional English terminology
                glucoseIndicator.text = "🩸 Glucose: ${item.glucoseLevel} mg/dL"
            } else {
                glucoseIndicator.visibility = View.GONE
            }
        }
    }
}