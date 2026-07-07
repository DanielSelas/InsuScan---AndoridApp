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
            ui.totalCarbsText.text = "--"
            return
        }

        val items = meal.foodItems
        if (!items.isNullOrEmpty()) {
            var calculatedTotalCarbs = 0f

            ui.detectedItemsLabel.text = "DETECTED ITEMS · ${items.size}"

            items.forEachIndexed { index, item ->
                calculatedTotalCarbs += (item.carbsGrams ?: 0f)
                addFoodItemRow(item, index, index == items.lastIndex)
            }

            ui.totalCarbsText.text = String.format("%.1f", calculatedTotalCarbs)
        } else {
            addSingleMessageRow("No food detected in image")
            ui.totalCarbsText.text = "0"
        }
    }

    private fun addFoodItemRow(item: FoodItem, index: Int, isLast: Boolean) {
        val row = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 14, 0, 14)
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
        val confidence = item.confidence
        val hasMissingData = carbs == 0f

//        val numberText = TextView(context).apply {
//            layoutParams = LinearLayout.LayoutParams(
//                LinearLayout.LayoutParams.WRAP_CONTENT,
//                LinearLayout.LayoutParams.WRAP_CONTENT
//            )
//            text = String.format("%02d", index + 1)
//            textSize = 10f
//            typeface = android.graphics.Typeface.MONOSPACE
//            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
//            setPadding(0, 0, 12, 0)
//        }
//        row.addView(numberText)

        val infoLayout = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.VERTICAL
        }

        val nameText = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = if (hasMissingData) "⚠️ $name" else name
            textSize = 14.5f
            setTextColor(ContextCompat.getColor(context, if (hasMissingData) R.color.error else R.color.text_primary))
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        infoLayout.addView(nameText)

        val subText = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            val weightStr = if (weight != null && weight > 0) "${weight}g" else ""
            val carbStr = if (!hasMissingData) " · ${carbs.toInt()}g carbs" else ""
            text = weightStr + carbStr
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        }
        infoLayout.addView(subText)
        row.addView(infoLayout)

//        val carbsText = TextView(context).apply {
//            layoutParams = LinearLayout.LayoutParams(
//                LinearLayout.LayoutParams.WRAP_CONTENT,
//                LinearLayout.LayoutParams.WRAP_CONTENT
//            )
//            text = if (hasMissingData) "Fix >" else String.format("%.0fg", carbs)
//            textSize = 12f
//            typeface = android.graphics.Typeface.MONOSPACE
//            setTextColor(ContextCompat.getColor(context, if (hasMissingData) R.color.error else R.color.text_secondary))
//            setPadding(0, 0, 12, 0)
//        }
//        row.addView(carbsText)

        val conf = item.confidence
        if (conf != null && conf > 0f) {
            val confColor = when {
                conf >= 0.85f -> ContextCompat.getColor(context, R.color.secondary)
                conf >= 0.70f -> ContextCompat.getColor(context, R.color.status_warning)
                else -> ContextCompat.getColor(context, R.color.status_critical)
            }
            val confText = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = "${(conf * 100f).toInt()}%"
                textSize = 12f
                setTextColor(confColor)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(0, 0, 10, 0)
            }
            row.addView(confText)
        }

        val chevron = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = "›"
            textSize = 18f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        }
        row.addView(chevron)

        if (weight != null && weight > 0) {
            row.setOnClickListener { showWeightEditDialog(index, item) }
        }
        if (hasMissingData) {
            row.setOnClickListener { onNavigateToManualEntry() }
        }

        ui.mealItemsContainer.addView(row)

        if (!isLast) {
            val divider = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1
                )
                setBackgroundColor(ContextCompat.getColor(context, R.color.divider))
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
                val newWeight = com.example.insuscan.utils.MealInputValidator.parsePositiveWeight(input.text.toString())
                if (newWeight == null) {
                    com.example.insuscan.utils.ToastHelper.showShort(context, com.example.insuscan.utils.MealInputValidator.INVALID_WEIGHT_MESSAGE)
                } else {
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
