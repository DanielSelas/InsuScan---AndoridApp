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

    /**
     * Client-side fallback to clean up USDA food names
     * Examples:
     *   "Rice, white, cooked, Chinese restaurant" -> "White Rice"
     *   "Bread, whole wheat, commercially prepared" -> "Whole Wheat Bread"
     */
    private fun cleanFoodName(rawName: String): String {
        // List of terms to remove
        val removeTerms = listOf(
            "cooked", "raw", "commercially prepared", "Chinese restaurant",
            "fast food", "restaurant", "frozen", "canned", "dried",
            "infant formula", "baby food", "USDA Commodity"
        )
        
        // Split by comma and process
        val parts = rawName.split(",").map { it.trim() }
            .filter { part ->
                removeTerms.none { term -> 
                    part.equals(term, ignoreCase = true) 
                }
            }
            .filter { it.isNotBlank() && !it.matches(Regex("^\\d+.*")) }
        
        if (parts.isEmpty()) return rawName
        
        // Reorder: put adjectives before noun (e.g., "Rice, white" -> "White Rice")
        val result = if (parts.size >= 2) {
            // "${parts[1]} ${parts[0]}" format for typical USDA naming
            "${parts.getOrNull(1)?.replaceFirstChar { it.uppercase() } ?: ""} ${parts[0].replaceFirstChar { it.uppercase() }}"
                .trim()
        } else {
            parts[0].replaceFirstChar { it.uppercase() }
        }
        
        return result.ifBlank { rawName }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvFoodName: TextView = itemView.findViewById(R.id.tv_food_name)
        private val tvFoodOriginal: TextView = itemView.findViewById(R.id.tv_food_original)
        private val tvCarbs: TextView = itemView.findViewById(R.id.tv_carbs)

        fun bind(item: ScoredFoodResultDto) {
            val rawName = item.foodName ?: "Unknown"
            
            // Use displayName if available, otherwise clean up the raw name client-side
            val displayName = item.displayName ?: cleanFoodName(rawName)
            
            tvFoodName.text = displayName
            tvFoodOriginal.text = rawName
            tvCarbs.text = "${item.carbsPer100g?.toInt() ?: 0}g/100g"

            itemView.setOnClickListener {
                onItemClick(item)
            }
        }
    }
}

