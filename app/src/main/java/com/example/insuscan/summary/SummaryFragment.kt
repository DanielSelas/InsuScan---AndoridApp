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
import com.google.android.material.bottomnavigation.BottomNavigationView

class SummaryFragment : Fragment(R.layout.fragment_summary) {

    private var totalCarbsTextView: TextView? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val editButton = view.findViewById<Button>(R.id.btn_edit_meal)
        val calcButton = view.findViewById<Button>(R.id.btn_calculate_insulin)
        val logButton = view.findViewById<Button>(R.id.btn_log_meal)
        totalCarbsTextView = view.findViewById(R.id.tv_total_carbs)

        updateTotalCarbsLabel()

        editButton.setOnClickListener {
            findNavController().navigate(R.id.action_summaryFragment_to_manualEntryFragment)
        }

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

            Toast.makeText(
                requireContext(),
                "Recommended dose: $doseRounded units",
                Toast.LENGTH_SHORT
            ).show()
        }

        logButton.setOnClickListener {
            val meal = MealSessionManager.currentMeal
            if (meal == null) {
                Toast.makeText(
                    requireContext(),
                    "No meal data to save",
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
            MealSessionManager.saveCurrentMealWithDose(dose)

            // TODO: Replace Toast with a more visible "meal saved" indication
            Toast.makeText(
                requireContext(),
                "Meal saved to history",
                Toast.LENGTH_SHORT
            ).show()

            val bottomNav =
                requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)
            bottomNav.selectedItemId = R.id.historyFragment
        }
    }

    override fun onResume() {
        super.onResume()
        updateTotalCarbsLabel()
    }

    private fun updateTotalCarbsLabel() {
        val tv = totalCarbsTextView ?: return
        val currentMeal = MealSessionManager.currentMeal

        if (currentMeal == null) {
            tv.text = "Total carbs: -"
        } else {
            tv.text = "Total carbs: ${currentMeal.carbs.toInt()} g"
        }
    }
}