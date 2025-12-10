package com.example.insuscan.manualentry

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.insuscan.R
import com.example.insuscan.meal.Meal
import com.example.insuscan.meal.MealSessionManager

class ManualEntryFragment : Fragment(R.layout.fragment_manual_entry) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val foodNameEditText = view.findViewById<EditText>(R.id.et_food_name)
        val carbsEditText = view.findViewById<EditText>(R.id.et_carbs)
        val saveButton = view.findViewById<Button>(R.id.btn_save_manual)

        // TODO: In the future, support editing multiple items instead of a single meal title + total carbs
        val currentMeal = MealSessionManager.currentMeal
        if (currentMeal != null) {
            foodNameEditText.setText(currentMeal.title)
            carbsEditText.setText(currentMeal.carbs.toInt().toString())
        }

        saveButton.setOnClickListener {
            val name = foodNameEditText.text.toString().trim()
            val carbs = carbsEditText.text.toString().trim()

            // TODO: Move common validation logic (name + carbs) to a shared helper if reused in other screens
            if (name.isEmpty() || carbs.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "Please fill food name and carbs",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val carbsValue = carbs.toFloatOrNull()
            if (carbsValue == null || carbsValue <= 0f) {
                Toast.makeText(
                    requireContext(),
                    "Carbs must be a positive number",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            // TODO: When backend is ready, also send this manual update to the server for sync
            val updatedMeal = Meal(
                title = name,
                carbs = carbsValue
            )
            MealSessionManager.setCurrentMeal(updatedMeal)

            // TODO: Replace Toast with a more user-friendly confirmation (e.g. Snackbar or inline info)
            Toast.makeText(
                requireContext(),
                "Edited meal: $name - ${carbsValue.toInt()} g carbs",
                Toast.LENGTH_SHORT
            ).show()

            // TODO: Consider navigating explicitly back to Summary via nav graph action if flow changes
            findNavController().popBackStack()
        }
    }
}