package com.example.insuscan.summary.helpers

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.insuscan.R
import com.example.insuscan.meal.FoodItem
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.utils.ToastHelper

class SummaryMealDisplayHandler(
    private val context: Context,
    private val ui: SummaryUiManager,
    private val onMealEdited: () -> Unit,
    private val onNavigateToManualEntry: () -> Unit
) {
    fun updateFoodDisplay() {
        val meal = MealSessionManager.currentMeal

        ui.mealItemsContainer.removeAllViews()

        if (meal == null) {
            addSingleMessageRow("No meal data")
            ui.totalCarbsText.text = "Total carbs: -- g"
            return
        }

        val items = meal.foodItems
        if (!items.isNullOrEmpty()) {
            var calculatedTotalCarbs = 0f

            items.forEachIndexed { index, item ->
                calculatedTotalCarbs += (item.carbsGrams ?: 0f)
                addFoodItemRow(item, index, index == items.lastIndex)
            }
            
            ui.totalCarbsText.text = String.format("Total carbs: %.2f g", calculatedTotalCarbs)
        } else {
            addSingleMessageRow("No food detected in image")
            ui.totalCarbsText.text = "Total carbs: 0 g"
        }
    }

    private fun addFoodItemRow(item: FoodItem, index: Int, isLast: Boolean) {
        val row = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 16, 0, 16)
            gravity = android.view.Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            val outValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
        }

        val name = item.nameHebrew ?: item.name
        val carbs = item.carbsGrams ?: 0f
        val weight = item.weightGrams?.toInt()
        val hasMissingData = carbs == 0f

        val nameText = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = if (hasMissingData) "⚠️ $name" else name
            textSize = 16f
            setTextColor(ContextCompat.getColor(context, if (hasMissingData) R.color.error else R.color.text_primary))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        row.addView(nameText)

        if (weight != null && weight > 0) {
            val weightText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                val fullText = "Weight: ${weight}g ✎"
                val spannable = android.text.SpannableString(fullText)
                val iconColor = ContextCompat.getColor(context, R.color.status_normal)
                spannable.setSpan(
                    android.text.style.ForegroundColorSpan(iconColor),
                    fullText.length - 1,
                    fullText.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                text = spannable
                textSize = 13f
                setTextColor(ContextCompat.getColor(context, R.color.primary))
                setPadding(16, 0, 16, 0)
                setOnClickListener { showWeightEditDialog(index, item) }
            }
            row.addView(weightText)
        }

        val trailingText = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            if (hasMissingData) {
                text = "Fix >"
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.error))
                setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                text = String.format("Carbs: %.2f g", carbs)
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.primary))
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
        }
        row.addView(trailingText)

        if (hasMissingData) {
            row.setOnClickListener { onNavigateToManualEntry() }
        }

        ui.mealItemsContainer.addView(row)

        if (!isLast) {
             val divider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2)
                setBackgroundColor(0xFFEEEEEE.toInt())
             }
             ui.mealItemsContainer.addView(divider)
        }
    }

    private fun showWeightEditDialog(index: Int, item: FoodItem) {
        val currentWeight = item.weightGrams ?: 0f
        val input = EditText(context).apply {
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setText(currentWeight.toInt().toString())
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }

        AlertDialog.Builder(context)
            .setTitle("Edit Weight (grams)")
            .setMessage("Enter new weight for ${item.nameHebrew ?: item.name}:")
            .setView(input)
            .setPositiveButton("Update") { _, _ ->
                val newWeight = input.text.toString().toFloatOrNull()
                if (newWeight != null && newWeight > 0) {
                    updateMealItemWeight(index, item, newWeight)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateMealItemWeight(index: Int, item: FoodItem, newWeight: Float) {
        val currentMeal = MealSessionManager.currentMeal ?: return
        val currentItems = currentMeal.foodItems?.toMutableList() ?: return
        
        val oldWeight = item.weightGrams ?: 100f 
        val safeOldWeight = if (oldWeight == 0f) 100f else oldWeight
        
        val currentCarbs = item.carbsGrams ?: 0f
        val carbsPerGram = currentCarbs / safeOldWeight
        val newCarbs = newWeight * carbsPerGram
        
        currentItems[index] = item.copy(weightGrams = newWeight, carbsGrams = newCarbs)
        
        var newTotalCarbs = 0f
        currentItems.forEach { newTotalCarbs += (it.carbsGrams ?: 0f) }
        
        MealSessionManager.setCurrentMeal(currentMeal.copy(foodItems = currentItems, carbs = newTotalCarbs))
        
        updateFoodDisplay()
        onMealEdited()
        ToastHelper.showShort(context, "Weight updated: ${newWeight.toInt()}g")
    }

    private fun addSingleMessageRow(message: String) {
        val textView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            text = message
            textSize = 15f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setPadding(0, 8, 0, 8)
        }
        ui.mealItemsContainer.addView(textView)
    }
}
