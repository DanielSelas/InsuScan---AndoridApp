package com.example.insuscan.summary

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.insuscan.R
import com.example.insuscan.meal.Meal
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.profile.UserProfileManager
import com.example.insuscan.utils.ToastHelper
import com.example.insuscan.utils.TopBarHelper
import com.google.android.material.bottomnavigation.BottomNavigationView

class SummaryFragment : Fragment(R.layout.fragment_summary) {

    // Main controls
    private lateinit var editButton: Button
    private lateinit var calcButton: Button
    private lateinit var logButton: Button
    private var totalCarbsTextView: TextView? = null

    // Analysis results views
    private var analysisLayout: LinearLayout? = null
    private var portionWeightText: TextView? = null
    private var plateDimensionsText: TextView? = null
    private var confidenceText: TextView? = null
    private var referenceStatusText: TextView? = null

    private val ctx get() = requireContext()

    companion object {
        private const val MSG_SET_RATIO = "Please set insulin to carb ratio in Profile first"
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        findViews(view)
        initializeTopBar(view)

        updateTotalCarbsLabel()
        updateAnalysisResults()
        initializeListeners()
    }

    private fun initializeTopBar(rootView: View) {
        TopBarHelper.setupTopBarBackToScan(
            rootView = rootView,
            title = "Meal summary"
        ) {
            val bottomNav =
                requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)
            bottomNav.selectedItemId = R.id.scanFragment
        }
    }

    private fun findViews(view: View) {
        // Main controls
        editButton = view.findViewById(R.id.btn_edit_meal)
        calcButton = view.findViewById(R.id.btn_calculate_insulin)
        logButton = view.findViewById(R.id.btn_log_meal)
        totalCarbsTextView = view.findViewById(R.id.tv_total_carbs)

        // Analysis results
        analysisLayout = view.findViewById(R.id.layout_analysis_results)
        portionWeightText = view.findViewById(R.id.tv_portion_weight)
        plateDimensionsText = view.findViewById(R.id.tv_plate_dimensions)
        confidenceText = view.findViewById(R.id.tv_analysis_confidence)
        referenceStatusText = view.findViewById(R.id.tv_reference_status)
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

        if (meal.carbs <= 0f) {
            ToastHelper.showShort(ctx, "No carbs detected. Try scanning again or tap Edit to enter items manually.")
            return
        }

        val unitsPerGram = UserProfileManager.getUnitsPerGram(ctx)
        if (unitsPerGram == null) {
            ToastHelper.showShort(ctx, MSG_SET_RATIO)
            return
        }

        val dose = meal.carbs * unitsPerGram
        val doseRounded = String.format("%.1f", dose)
        ToastHelper.showShort(ctx, "Recommended dose: $doseRounded units")
    }

    private fun onLogClicked() {
        val meal = MealSessionManager.currentMeal
        if (meal == null) {
            ToastHelper.showShort(ctx, "No meal data to save")
            return
        }

        if (meal.carbs <= 0f) {
            ToastHelper.showShort(ctx, "No carbs detected. Please Edit the meal before saving.")
            return
        }

        val unitsPerGram = UserProfileManager.getUnitsPerGram(ctx)
        if (unitsPerGram == null) {
            ToastHelper.showShort(ctx, MSG_SET_RATIO)
            return
        }

        val dose = meal.carbs * unitsPerGram
        MealSessionManager.saveCurrentMealWithDose(dose)

        ToastHelper.showShort(ctx, "Meal saved to history")
        selectHistoryTab()
    }

    private fun selectHistoryTab() {
        val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav.selectedItemId = R.id.historyFragment
    }

    override fun onResume() {
        super.onResume()
        updateTotalCarbsLabel()
        updateAnalysisResults()
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

    // Display the portion analysis results from ARCore + OpenCV
    private fun updateAnalysisResults() {
        val meal = MealSessionManager.currentMeal

        if (meal == null || !hasAnalysisData(meal)) {
            analysisLayout?.visibility = View.GONE
            return
        }

        analysisLayout?.visibility = View.VISIBLE

        meal.portionWeightGrams?.let { weight ->
            portionWeightText?.text = "Estimated weight: ${weight.toInt()} g"
        }

        val diameter = meal.plateDiameterCm
        val depth = meal.plateDepthCm
        if (diameter != null && depth != null) {
            plateDimensionsText?.text = String.format(
                "Plate: %.1f cm diameter, %.1f cm depth",
                diameter, depth
            )
        }

        meal.analysisConfidence?.let { confidence ->
            val percentage = (confidence * 100).toInt()
            confidenceText?.text = "Confidence: $percentage%"
        }

        val refStatus = when (meal.referenceObjectDetected) {
            true -> "Reference object: Detected ✓"
            false -> "Reference object: Not detected (using estimates)"
            null -> "Reference object: No data"
        }
        referenceStatusText?.text = refStatus
    }

    private fun hasAnalysisData(meal: Meal): Boolean {
        return meal.portionWeightGrams != null ||
                meal.plateDiameterCm != null ||
                meal.analysisConfidence != null
    }
}