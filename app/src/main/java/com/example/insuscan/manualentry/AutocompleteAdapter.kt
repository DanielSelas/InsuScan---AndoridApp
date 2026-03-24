package com.example.insuscan.manualentry

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.R
import com.example.insuscan.network.dto.ScoredFoodResultDto

class AutocompleteAdapter(
    private val onItemClick: (ScoredFoodResultDto) -> Unit
) : RecyclerView.Adapter<AutocompleteAdapter.ViewHolder>() {

    private val items = mutableListOf<ScoredFoodResultDto>()

    fun setItems(newItems: List<ScoredFoodResultDto>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun clear() {
        items.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_autocomplete, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvFoodName: TextView = itemView.findViewById(R.id.tv_food_name)
        private val tvFoodOriginal: TextView = itemView.findViewById(R.id.tv_food_original)
        private val tvCarbs: TextView = itemView.findViewById(R.id.tv_carbs)

        fun bind(item: ScoredFoodResultDto) {
            val rawName = item.foodName ?: "Unknown"
            
            val displayName = item.displayName ?: rawName
            
            tvFoodName.text = displayName
            tvFoodOriginal.text = rawName
            tvCarbs.text = "${item.carbsPer100g?.toInt() ?: 0}g/100g"

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}

