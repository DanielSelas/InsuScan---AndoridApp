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
import android.view.Gravity
import android.util.TypedValue

/**
 * Renders the meal's food items in the summary screen and handles inline weight edits.
 */
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
            addSingleMessageRow(context.getString(R.string.msg_no_meal_data))
            ui.totalCarbsText.text = "--"
            return
        }

        val items = meal.foodItems
        if (!items.isNullOrEmpty()) {
            var calculatedTotalCarbs = 0f

            ui.detectedItemsLabel.text = context.getString(R.string.msg_detected_items_count, items.size)

            items.forEachIndexed { index, item ->
                calculatedTotalCarbs += (item.carbsGrams ?: 0f)
                addFoodItemRow(item, index, index == items.lastIndex)
            }

            ui.totalCarbsText.text = String.format("%.1f", calculatedTotalCarbs)
        } else {
            addSingleMessageRow(context.getString(R.string.msg_no_food_detected))
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
            val paddingVertical = context.resources.getDimensionPixelSize(R.dimen.padding_meal_item_vertical)
            setPadding(0, paddingVertical, 0, paddingVertical)
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            val outValue = TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
        }

        val name = item.nameHebrew ?: item.name
        val carbs = item.carbsGrams ?: 0f
        val weight = item.weightGrams?.toInt()
        val confidence = item.confidence
        val hasMissingData = carbs == 0f

        val infoLayout = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.VERTICAL
        }

        val nameText = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = if (hasMissingData) context.getString(R.string.msg_missing_data_warning, name) else name
            textSize = context.resources.getDimension(R.dimen.text_size_meal_item_name) / context.resources.displayMetrics.scaledDensity
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
            val carbStr = if (!hasMissingData) context.getString(R.string.msg_carbs_count, carbs.toInt()) else ""
            text = weightStr + carbStr
            textSize = context.resources.getDimension(R.dimen.text_size_meal_item_sub) / context.resources.displayMetrics.scaledDensity
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        }
        infoLayout.addView(subText)
        row.addView(infoLayout)

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
                textSize = context.resources.getDimension(R.dimen.text_size_meal_item_conf) / context.resources.displayMetrics.scaledDensity
                setTextColor(confColor)
                setTypeface(null, android.graphics.Typeface.BOLD)
                val paddingConf = context.resources.getDimensionPixelSize(R.dimen.padding_meal_item_conf)
                setPadding(0, 0, paddingConf, 0)
            }
            row.addView(confText)
        }

        val chevron = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = "›"
            textSize = context.resources.getDimension(R.dimen.text_size_meal_item_chevron) / context.resources.displayMetrics.scaledDensity
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
            .setTitle(R.string.dialog_edit_weight_title)
            .setMessage(context.getString(R.string.dialog_edit_weight_msg, item.nameHebrew ?: item.name))
            .setView(input)
            .setPositiveButton(R.string.action_update) { _, _ ->
                val newWeight = com.example.insuscan.utils.MealInputValidator.parsePositiveWeight(input.text.toString())
                if (newWeight == null) {
                    ToastHelper.showShort(context, com.example.insuscan.utils.MealInputValidator.INVALID_WEIGHT_MESSAGE)
                } else {
                    updateMealItemWeight(index, item, newWeight)
                }
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun updateMealItemWeight(index: Int, item: FoodItem, newWeight: Float) {
        val currentMeal = MealSessionManager.currentMeal ?: return
        val currentItems = currentMeal.foodItems?.toMutableList() ?: return
        
        val oldWeight = item.weightGrams ?: FoodItem.DEFAULT_WEIGHT_GRAMS
        val safeOldWeight = if (oldWeight == 0f) FoodItem.DEFAULT_WEIGHT_GRAMS else oldWeight
        
        val currentCarbs = item.carbsGrams ?: 0f
        val carbsPerGram = currentCarbs / safeOldWeight
        val newCarbs = newWeight * carbsPerGram
        
        currentItems[index] = item.copy(weightGrams = newWeight, carbsGrams = newCarbs)
        
        var newTotalCarbs = 0f
        currentItems.forEach { newTotalCarbs += (it.carbsGrams ?: 0f) }
        
        MealSessionManager.setCurrentMeal(currentMeal.copy(foodItems = currentItems, carbs = newTotalCarbs))
        
        updateFoodDisplay()
        onMealEdited()
        ToastHelper.showShort(context, context.getString(R.string.msg_weight_updated, newWeight.toInt()))
    }

    private fun addSingleMessageRow(message: String) {
        val textView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            text = message
            textSize = context.resources.getDimension(R.dimen.text_size_meal_item_msg) / context.resources.displayMetrics.scaledDensity
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            setPadding(0, 8, 0, 8)
        }
        ui.mealItemsContainer.addView(textView)
    }
}
