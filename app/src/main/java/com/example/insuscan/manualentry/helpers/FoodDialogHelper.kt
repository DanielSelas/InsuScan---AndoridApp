package com.example.insuscan.manualentry.helpers

import android.app.AlertDialog
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import com.example.insuscan.R
import com.example.insuscan.manualentry.EditableFoodItem
import com.example.insuscan.network.dto.ScoredFoodResultDto
import com.example.insuscan.utils.ToastHelper
import com.google.android.material.textfield.TextInputEditText

class FoodDialogHelper(
    private val context: Context,
    private val layoutInflater: LayoutInflater,
    private val onItemAdded: (name: String, weight: Float, carbsPer100g: Float) -> Unit,
    private val onItemUpdated: (EditableFoodItem) -> Unit
) {

    fun showSelectFoodDialog(
        originalQuery: String,
        weightGrams: Float,
        options: List<ScoredFoodResultDto>,
        onResultSelected: (ScoredFoodResultDto, Float) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_select_food, null)

        val tvSearchQuery = dialogView.findViewById<TextView>(R.id.tv_search_query)
        val radioGroup = dialogView.findViewById<RadioGroup>(R.id.radio_group_options)
        val option1 = dialogView.findViewById<RadioButton>(R.id.option_1)
        val option2 = dialogView.findViewById<RadioButton>(R.id.option_2)
        val option3 = dialogView.findViewById<RadioButton>(R.id.option_3)
        val btnManual = dialogView.findViewById<Button>(R.id.btn_manual_entry)

        tvSearchQuery.text = "Results for: $originalQuery"

        val radioButtons = listOf(option1, option2, option3)
        options.forEachIndexed { index, result ->
            if (index < radioButtons.size) {
                radioButtons[index].visibility = View.VISIBLE
                val displayName = result.displayName ?: result.foodName ?: "Unknown"
                radioButtons[index].text = "$displayName (${result.carbsPer100g?.toInt() ?: 0}g/100g)"
                radioButtons[index].tag = result
            }
        }

        for (i in options.size until radioButtons.size) {
            radioButtons[i].visibility = View.GONE
        }

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setPositiveButton("Add") { _, _ ->
                val selectedId = radioGroup.checkedRadioButtonId
                if (selectedId != -1) {
                    val selectedButton = dialogView.findViewById<RadioButton>(selectedId)
                    val selectedResult = selectedButton.tag as? ScoredFoodResultDto
                    if (selectedResult != null) {
                        onResultSelected(selectedResult, weightGrams)
                    }
                } else {
                    ToastHelper.showShort(context, "Please select an item")
                }
            }
            .setNegativeButton("Cancel", null)
            .create()

        btnManual.setOnClickListener {
            dialog.dismiss()
            showManualEntryDialog(originalQuery, weightGrams, null)
        }

        dialog.show()
    }

    fun showManualEntryDialog(foodName: String, weightGrams: Float, message: String?) {
        showFoodDialog(
            title = message,
            buttonText = "Add",
            prefillName = foodName,
            prefillWeight = weightGrams.toInt().toString(),
            prefillCarbs = null,
            showInitialPreview = false
        ) { name, weight, carbsPer100g ->
            if (name.isEmpty()) {
                ToastHelper.showShort(context, "Enter food name")
                return@showFoodDialog
            }
            if (weight == null || weight <= 0) {
                ToastHelper.showShort(context, "Enter valid weight")
                return@showFoodDialog
            }
            if (carbsPer100g == null || carbsPer100g < 0) {
                ToastHelper.showShort(context, "Enter carbs per 100g")
                return@showFoodDialog
            }
            onItemAdded(name, weight, carbsPer100g)
        }
    }

    fun showEditItemDialog(item: EditableFoodItem, onNotifyAdapter: () -> Unit) {
        showFoodDialog(
            title = "Edit Item",
            buttonText = "Update",
            prefillName = item.name,
            prefillWeight = item.weightGrams.toInt().toString(),
            prefillCarbs = item.carbsPer100g?.toInt()?.toString() ?: "0",
            showInitialPreview = true
        ) { name, weight, carbsPer100g ->
            if (name.isNotEmpty() && weight != null && carbsPer100g != null) {
                item.name = name
                item.weightGrams = weight
                item.carbsPer100g = carbsPer100g
                onItemUpdated(item)
                onNotifyAdapter()
                ToastHelper.showShort(context, "Updated")
            }
        }
    }

    private fun showFoodDialog(
        title: String?,
        buttonText: String,
        prefillName: String,
        prefillWeight: String,
        prefillCarbs: String?,
        showInitialPreview: Boolean,
        onConfirm: (name: String, weight: Float?, carbsPer100g: Float?) -> Unit
    ) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_custom_food, null)

        val etName = dialogView.findViewById<TextInputEditText>(R.id.et_food_name)
        val etDialogWeight = dialogView.findViewById<TextInputEditText>(R.id.et_weight)
        val etCarbsPer100g = dialogView.findViewById<TextInputEditText>(R.id.et_carbs_per_100g)
        val tvCarbsPreview = dialogView.findViewById<TextView>(R.id.tv_carbs_preview)

        etName.setText(prefillName)
        etDialogWeight.setText(prefillWeight)
        prefillCarbs?.let { etCarbsPer100g.setText(it) }

        val updatePreview = {
            val weight = etDialogWeight.text.toString().toFloatOrNull() ?: 0f
            val carbs = etCarbsPer100g.text.toString().toFloatOrNull() ?: 0f
            val totalCarbs = (weight * carbs) / 100f
            tvCarbsPreview.text = "Total carbs: ${totalCarbs.toInt()}g"
        }

        if (showInitialPreview) updatePreview()

        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updatePreview() }
        }

        etDialogWeight.addTextChangedListener(textWatcher)
        etCarbsPer100g.addTextChangedListener(textWatcher)

        val builder = AlertDialog.Builder(context)
            .setView(dialogView)
            .setPositiveButton(buttonText) { _, _ ->
                val name = etName.text.toString().trim()
                val weight = etDialogWeight.text.toString().toFloatOrNull()
                val carbsPer100gVal = etCarbsPer100g.text.toString().toFloatOrNull()
                onConfirm(name, weight, carbsPer100gVal)
            }
            .setNegativeButton("Cancel", null)

        if (title != null) {
            builder.setTitle(title)
        }

        builder.show()
    }
}
