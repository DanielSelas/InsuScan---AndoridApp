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

        fun bind(item: MealHistoryItem) {
            titleText.text = item.title
            detailsText.text = "${item.carbsText}   |   ${item.insulinText}"
            timeText.text = item.timeText
        }
    }
}