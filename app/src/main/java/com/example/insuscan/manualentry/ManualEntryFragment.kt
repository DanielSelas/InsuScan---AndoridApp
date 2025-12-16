package com.example.insuscan.manualentry

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.insuscan.R
import com.example.insuscan.meal.Meal
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.utils.ToastHelper
import com.example.insuscan.utils.TopBarHelper

class ManualEntryFragment : Fragment(R.layout.fragment_manual_entry) {
    private lateinit var foodNameEditText: EditText
    private lateinit var carbsEditText: EditText
    private lateinit var saveButton: Button

    private val ctx get() = requireContext()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        TopBarHelper.setupTopBar(
            rootView = view,
            title = "Manual meal edit",
            onBack = { findNavController().popBackStack() }
        )

        findViews(view)
        prefillIfExistingMeal()
        initializeListeners()
    }

    private fun findViews(view: View) {
        foodNameEditText = view.findViewById(R.id.et_food_name)
        carbsEditText = view.findViewById(R.id.et_carbs)
        saveButton = view.findViewById(R.id.btn_save_manual)
    }

    private fun prefillIfExistingMeal() {
        // TODO: In the future, support editing multiple items instead of a single meal title + total carbs
        val currentMeal = MealSessionManager.currentMeal
        if (currentMeal != null) {
            // Pre-fill manual form with the current meal data
            foodNameEditText.setText(currentMeal.title)
            carbsEditText.setText(currentMeal.carbs.toInt().toString())
        }
    }

    private fun initializeListeners() {
        saveButton.setOnClickListener { onSaveClicked() }
    }

    private fun onSaveClicked() {
        val name = foodNameEditText.text.toString().trim()
        val carbs = carbsEditText.text.toString().trim()

        // TODO: Move common validation logic (name + carbs) to a shared helper if reused in other screens
        if (name.isEmpty() || carbs.isEmpty()) {
            ToastHelper.showShort(ctx, "Please fill food name and carbs")
            return
        }

        val carbsValue = carbs.toFloatOrNull()
        if (carbsValue == null || carbsValue <= 0f) {
            ToastHelper.showShort(ctx, "Carbs must be a positive number")
            return
        }

        // Build updated meal based on manual input
        // TODO: When backend is ready, also send this manual update to the server for sync
        val updatedMeal = Meal(
            title = name,
            carbs = carbsValue
        )
        MealSessionManager.setCurrentMeal(updatedMeal)

        // Summary tab should be available once a valid meal exists
//        (requireActivity() as? MainActivity)?.enableSummaryTab()

        // TODO: Replace Toast with a more user-friendly confirmation (e.g. Snackbar or inline info)
        ToastHelper.showShort(ctx, "Edited meal: $name - ${carbsValue.toInt()} g carbs")

        // For now just go back to the previous screen (usually Summary)
        // TODO: Consider navigating explicitly back to Summary via nav graph action if flow changes
        findNavController().popBackStack()
    }
}