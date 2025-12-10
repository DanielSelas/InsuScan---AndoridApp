package com.example.insuscan.summary

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.insuscan.R
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.profile.UserProfileManager

class SummaryFragment : Fragment(R.layout.fragment_summary) {

    // use the current meal from MealSessionManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val editButton = view.findViewById<Button>(R.id.btn_edit_meal)
        val calcButton = view.findViewById<Button>(R.id.btn_calculate_insulin)
        val totalCarbsTextView = view.findViewById<TextView>(R.id.tv_total_carbs)

        // show the local values - for now coming from the current meal stored in MealSessionManager
        val currentMeal = MealSessionManager.currentMeal
        if (currentMeal == null) {
            totalCarbsTextView.text = "Total carbs: -"
        } else {
            totalCarbsTextView.text = "Total carbs: ${currentMeal.carbs.toInt()} g"
        }

        // manual edit
        editButton.setOnClickListener {
            findNavController().navigate(R.id.action_summaryFragment_to_manualEntryFragment)
        }

        // insulin from profile
        calcButton.setOnClickListener {
            val meal = MealSessionManager.currentMeal
            if (meal == null) {
                Toast.makeText(
                    requireContext(),
                    "No meal data - please scan your meal first",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val unitsPerGram = UserProfileManager.getUnitsPerGram(requireContext())

            if (unitsPerGram == null) {
                Toast.makeText(
                    requireContext(),
                    "Please set insulin to carb ratio in Profile first",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            val dose = meal.carbs * unitsPerGram
            val doseRounded = String.format("%.1f", dose)

            // save this calculated dose into history via MealSessionManager
            MealSessionManager.saveCurrentMealWithDose(dose)

            Toast.makeText(
                requireContext(),
                "Recommended dose: $doseRounded units",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}