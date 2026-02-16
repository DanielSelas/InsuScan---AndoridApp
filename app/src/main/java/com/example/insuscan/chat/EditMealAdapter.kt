package com.example.insuscan.chat

import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.R
import com.example.insuscan.meal.FoodItem

class EditMealAdapter(
    private var items: MutableList<FoodItem>,
    private val onItemRemoved: (Int) -> Unit,
    private val onCarbLookup: ((position: Int, name: String, grams: Float) -> Unit)? = null
) : RecyclerView.Adapter<EditMealAdapter.ViewHolder>() {

    private val loadingPositions = mutableSetOf<Int>()

    /** Positions where the user changed grams but has NOT pressed Calc yet */
    private val pendingCalcPositions = mutableSetOf<Int>()

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

    fun updateItemCarbs(position: Int, carbs: Float) {
        if (position in items.indices) {
            items[position] = items[position].copy(carbsGrams = carbs)
            loadingPositions.remove(position)
            pendingCalcPositions.remove(position)
            notifyItemChanged(position)
        }
    }

    fun removeItem(position: Int) {
        if (position in items.indices) {
            loadingPositions.remove(position)
            pendingCalcPositions.remove(position)
            items.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, items.size)
        }
    }

    fun setItemLoading(position: Int, loading: Boolean) {
        if (loading) loadingPositions.add(position) else loadingPositions.remove(position)
        if (position in items.indices) notifyItemChanged(position)
    }

    /** Reset all items back to the given list (e.g. original scan analysis) */
    fun resetItems(newItems: List<FoodItem>) {
        items.clear()
        items.addAll(newItems)
        loadingPositions.clear()
        pendingCalcPositions.clear()
        notifyDataSetChanged()
    }

    /** Returns true if any item has unsaved weight changes (user typed but didn't press Calc) */
    fun hasPendingCalc(): Boolean = pendingCalcPositions.isNotEmpty()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nameInput: EditText = view.findViewById(R.id.et_food_name)
        private val weightInput: EditText = view.findViewById(R.id.et_food_weight)
        private val carbsInput: EditText = view.findViewById(R.id.et_food_carbs)
        private val removeBtn: ImageButton = view.findViewById(R.id.btn_remove_item)
        private val confirmWeightBtn: Button = view.findViewById(R.id.btn_confirm_weight)
        private val carbLoading: ProgressBar = view.findViewById(R.id.pb_carb_loading)

        private var nameWatcher: TextWatcher? = null
        private var weightWatcher: TextWatcher? = null
        private var carbsWatcher: TextWatcher? = null

        fun bind(item: FoodItem, position: Int) {
            // Remove old watchers BEFORE setting text to avoid feedback loops
            if (nameWatcher != null) nameInput.removeTextChangedListener(nameWatcher)
            if (weightWatcher != null) weightInput.removeTextChangedListener(weightWatcher)
            if (carbsWatcher != null) carbsInput.removeTextChangedListener(carbsWatcher)

            nameInput.setText(item.name)
            weightInput.setText(item.weightGrams?.toInt()?.toString() ?: "")
            carbsInput.setText(item.carbsGrams?.toString() ?: "")

            // Show/hide loading spinner
            val isLoading = loadingPositions.contains(position)
            carbLoading.visibility = if (isLoading) View.VISIBLE else View.GONE
            carbsInput.alpha = if (isLoading) 0.3f else 1.0f
            carbsInput.isEnabled = !isLoading

            removeBtn.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onItemRemoved(adapterPosition)
                }
            }

            // Confirm weight button — immediately fires carb lookup
            confirmWeightBtn.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    val currentItem = items[adapterPosition]
                    val name = currentItem.name
                    val grams = currentItem.weightGrams
                    if (name.isNotBlank() && grams != null && grams > 0f) {
                        onCarbLookup?.invoke(adapterPosition, name, grams)
                    }
                }
            }

            // Name watcher — only updates the data model, no rebind
            nameWatcher = object : SimpleTextWatcher() {
                override fun afterTextChanged(s: Editable?) {
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        val current = items[adapterPosition]
                        items[adapterPosition] = current.copy(name = s.toString())
                    }
                }
            }

            // Weight watcher — marks position as pending calc (no auto-lookup)
            weightWatcher = object : SimpleTextWatcher() {
                override fun afterTextChanged(s: Editable?) {
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        val grams = s.toString().toFloatOrNull()
                        val current = items[adapterPosition]
                        items[adapterPosition] = current.copy(weightGrams = grams)
                        // Mark that the user should press Calc
                        pendingCalcPositions.add(adapterPosition)
                    }
                }
            }

            // Carbs watcher — manual carb entry clears the pending flag
            carbsWatcher = object : SimpleTextWatcher() {
                override fun afterTextChanged(s: Editable?) {
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        val current = items[adapterPosition]
                        items[adapterPosition] = current.copy(carbsGrams = s.toString().toFloatOrNull())
                        pendingCalcPositions.remove(adapterPosition)
                    }
                }
            }

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
