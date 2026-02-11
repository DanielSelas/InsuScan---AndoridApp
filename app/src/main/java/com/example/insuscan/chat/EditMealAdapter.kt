package com.example.insuscan.chat

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.R
import com.example.insuscan.meal.FoodItem

class EditMealAdapter(
    private var items: MutableList<FoodItem>,
    private val onItemRemoved: (Int) -> Unit
) : RecyclerView.Adapter<EditMealAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_edit_food, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    fun getItems(): List<FoodItem> = items.toList()

    fun addItem(item: FoodItem) {
        items.add(item)
        notifyItemInserted(items.size - 1)
    }

    fun removeItem(position: Int) {
        if (position in items.indices) {
            items.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, items.size)
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nameInput: EditText = view.findViewById(R.id.et_food_name)
        private val weightInput: EditText = view.findViewById(R.id.et_food_weight)
        private val carbsInput: EditText = view.findViewById(R.id.et_food_carbs)
        private val removeBtn: ImageButton = view.findViewById(R.id.btn_remove_item)

        private var nameWatcher: TextWatcher? = null
        private var weightWatcher: TextWatcher? = null
        private var carbsWatcher: TextWatcher? = null

        fun bind(item: FoodItem, position: Int) {
            // Remove previous listeners to avoid loops/wrong updates due to recycling
            nameInput.removeTextChangedListener(nameWatcher)
            weightInput.removeTextChangedListener(weightWatcher)
            carbsInput.removeTextChangedListener(carbsWatcher)

            nameInput.setText(item.name)
            weightInput.setText(item.weightGrams?.toInt()?.toString() ?: "")
            carbsInput.setText(item.carbsGrams?.toString() ?: "")

            removeBtn.setOnClickListener {
                onItemRemoved(adapterPosition)
            }

            // Define new listeners
            nameWatcher = object : SimpleTextWatcher() {
                override fun afterTextChanged(s: Editable?) {
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        val current = items[adapterPosition]
                        items[adapterPosition] = current.copy(name = s.toString())
                    }
                }
            }
            weightWatcher = object : SimpleTextWatcher() {
                override fun afterTextChanged(s: Editable?) {
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        val current = items[adapterPosition]
                        items[adapterPosition] = current.copy(weightGrams = s.toString().toFloatOrNull())
                    }
                }
            }
            carbsWatcher = object : SimpleTextWatcher() {
                override fun afterTextChanged(s: Editable?) {
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        val current = items[adapterPosition]
                        items[adapterPosition] = current.copy(carbsGrams = s.toString().toFloatOrNull())
                    }
                }
            }

            // Add listeners
            nameInput.addTextChangedListener(nameWatcher)
            weightInput.addTextChangedListener(weightWatcher)
            carbsInput.addTextChangedListener(carbsWatcher)
        }
    }

    abstract class SimpleTextWatcher : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }
}
