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
import com.example.insuscan.utils.ToastHelper
import com.google.android.material.bottomnavigation.BottomNavigationView

class SummaryFragment : Fragment(R.layout.fragment_summary) {
    private lateinit var editButton: Button
    private lateinit var calcButton: Button
    private lateinit var logButton: Button
    private var totalCarbsTextView: TextView? = null

    private val ctx get() = requireContext()

    companion object {
        private const val MSG_SET_RATIO =
            "Please set insulin to carb ratio in Profile first"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        findViews(view)
        updateTotalCarbsLabel()
        initializeListeners()
    }

    private fun findViews(view: View) {
        editButton = view.findViewById(R.id.btn_edit_meal)
        calcButton = view.findViewById(R.id.btn_calculate_insulin)
        logButton = view.findViewById(R.id.btn_log_meal)
        totalCarbsTextView = view.findViewById(R.id.tv_total_carbs)
    }

    private fun initializeListeners() {
        editButton.setOnClickListener { onEditClicked() }
        calcButton.setOnClickListener { onCalculateClicked() }
        logButton.setOnClickListener { onLogClicked() }
    }

    private fun onEditClicked() {
        findNavController().navigate(R.id.action_summaryFragment_to_manualEntryFragment)
    }

    private fun onCalculateClicked() {
        val meal = MealSessionManager.currentMeal
        if (meal == null) {
            ToastHelper.showShort(ctx, "No meal data - please scan your meal first")
            return
        }

        val unitsPerGram = UserProfileManager.getUnitsPerGram(ctx)
        if (unitsPerGram == null) {
            ToastHelper.showShort(ctx,MSG_SET_RATIO)
            return
        }

        val dose = meal.carbs * unitsPerGram
        val doseRounded = String.format("%.1f", dose)
        ToastHelper.showShort(ctx,"Recommended dose: $doseRounded units")
    }

    private fun onLogClicked() {
        val meal = MealSessionManager.currentMeal
        if (meal == null) {
            ToastHelper.showShort(ctx,"No meal data to save")
            return
        }

        val unitsPerGram = UserProfileManager.getUnitsPerGram(ctx)
        if (unitsPerGram == null) {
            ToastHelper.showShort(ctx,MSG_SET_RATIO)
            return
        }

        val dose = meal.carbs * unitsPerGram
        MealSessionManager.saveCurrentMealWithDose(dose)

        // TODO: Replace Toast with a more visible "meal saved" indication
        ToastHelper.showShort(ctx,"Meal saved to history")
        selectHistoryTab()
    }

    private fun selectHistoryTab() {
        val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.selectedItemId = R.id.historyFragment
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