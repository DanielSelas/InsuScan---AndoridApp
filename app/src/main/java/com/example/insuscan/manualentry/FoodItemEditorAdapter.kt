package com.example.insuscan.manualentry

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.R
import com.google.android.material.slider.Slider

class FoodItemEditorAdapter(
    private val onItemChanged: () -> Unit,
    private val onItemRemoved: (EditableFoodItem) -> Unit
) : RecyclerView.Adapter<FoodItemEditorAdapter.ViewHolder>() {

    private val items = mutableListOf<EditableFoodItem>()

    fun setItems(newItems: List<EditableFoodItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun addItem(item: EditableFoodItem) {
        items.add(item)
        notifyItemInserted(items.size - 1)
        onItemChanged()
    }

    fun removeItem(item: EditableFoodItem) {
        val index = items.indexOf(item)
        if (index >= 0) {
            items.removeAt(index)
            notifyItemRemoved(index)
            onItemChanged()
        }
    }

    fun getItems(): List<EditableFoodItem> = items.toList()

    fun getTotalCarbs(): Float = items.sumOf { it.totalCarbs.toDouble() }.toFloat()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_food_editor, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameText: TextView = itemView.findViewById(R.id.tv_food_name)
        private val weightText: TextView = itemView.findViewById(R.id.tv_weight)
        private val carbsText: TextView = itemView.findViewById(R.id.tv_carbs)
        private val weightSlider: Slider = itemView.findViewById(R.id.slider_weight)
        private val btnMinus: ImageButton = itemView.findViewById(R.id.btn_minus)
        private val btnPlus: ImageButton = itemView.findViewById(R.id.btn_plus)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete)

        fun bind(item: EditableFoodItem) {
            nameText.text = item.name
            updateDisplay(item)

            // Slider: 10g to 500g range
            val roundedWeight = (Math.round(item.weightGrams / 5f) * 5f).coerceIn(10f, 500f)
            weightSlider.value = roundedWeight
            weightSlider.addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    item.weightGrams = value
                    updateDisplay(item)
                    onItemChanged()
                }
            }

            // Minus button: -10g
            btnMinus.setOnClickListener {
                val newWeight = (item.weightGrams - 10f).coerceAtLeast(10f)
                item.weightGrams = newWeight
                weightSlider.value = newWeight
                updateDisplay(item)
                onItemChanged()
            }

            // Plus button: +10g
            btnPlus.setOnClickListener {
                val newWeight = (item.weightGrams + 10f).coerceAtMost(500f)
                item.weightGrams = newWeight
                weightSlider.value = newWeight
                updateDisplay(item)
                onItemChanged()
            }

            // Delete button
            btnDelete.setOnClickListener {
                onItemRemoved(item)
            }
        }

        private fun updateDisplay(item: EditableFoodItem) {
            weightText.text = "${item.weightGrams.toInt()}g"
            carbsText.text = "${item.totalCarbs.toInt()}g carbs"
        }
    }
}