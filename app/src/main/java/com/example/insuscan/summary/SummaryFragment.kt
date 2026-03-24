package com.example.insuscan.summary

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.insuscan.R
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.profile.UserProfileManager
import com.example.insuscan.utils.TopBarHelper
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.example.insuscan.summary.helpers.*

class SummaryFragment : Fragment(R.layout.fragment_summary) {

    private lateinit var ui: SummaryUiManager
    private lateinit var mealDisplayHandler: SummaryMealDisplayHandler
    private lateinit var imageHandler: SummaryImageHandler
    private lateinit var persistenceHandler: SummaryPersistenceHandler
    private lateinit var doseDisplayHandler: SummaryDoseDisplayHandler

    private var lastCalculatedResult: DoseResult? = null

    private val ctx get() = requireContext()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ui = SummaryUiManager(view, ctx)
        imageHandler = SummaryImageHandler(ctx, ui)
        
        mealDisplayHandler = SummaryMealDisplayHandler(
            context = ctx,
            ui = ui,
            onMealEdited = { recalculateDose() },
            onNavigateToManualEntry = { navigateToManualEntry() }
        )

        persistenceHandler = SummaryPersistenceHandler(
            context = ctx,
            ui = ui,
            scope = viewLifecycleOwner.lifecycleScope,
            onIncompleteProfileRequested = { navigateToProfile() },
            onMealSavedSuccessfully = { proceedToHistory() }
        )

        doseDisplayHandler = SummaryDoseDisplayHandler(
            context = ctx,
            ui = ui,
            onProfileIncompleteRequested = { navigateToProfile() }
        )

        initializeTopBar(view)
        initializeListeners()

        ui.updateEmptyStateVisibility()
        if (MealSessionManager.currentMeal != null) {
            recalculateDose()
        }
    }

    private fun initializeTopBar(rootView: View) {
        TopBarHelper.setupTopBarBackToScan(
            rootView = rootView,
            title = "Meal Summary"
        ) {
            proceedToScan()
        }
    }

    private fun initializeListeners() {
        ui.editButton.setOnClickListener { navigateToManualEntry() }

        ui.setLogButtonEnabled(false)

        ui.logButton.setOnClickListener {
            persistenceHandler.saveMeal(lastCalculatedResult)
        }

        ui.glucoseEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                ui.updateGlucoseStatus()
                recalculateDose()
            }
        })

        ui.activeInsulinEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val iob = s?.toString()?.toFloatOrNull()
                when {
                    iob != null && iob > SummaryCalculationHelper.IOB_HARD_LIMIT -> {
                        ui.activeInsulinEditText.error = "Maximum IOB is ${SummaryCalculationHelper.IOB_HARD_LIMIT.toInt()} units"
                        return
                    }
                    iob != null && iob > SummaryCalculationHelper.IOB_SOFT_LIMIT -> {
                        ui.activeInsulinEditText.error = "Unusually high — please verify"
                    }
                    iob != null && iob < 0 -> {
                        ui.activeInsulinEditText.error = "IOB cannot be negative"
                        return
                    }
                }
                recalculateDose()
            }
        })

        if (UserProfileManager.isExerciseModeEnabled(ctx)) {
            ui.rbLight.isChecked = true
        }

        ui.activityRadioGroup.setOnCheckedChangeListener { _, _ ->
            recalculateDose()
        }

        ui.scanNowButton.setOnClickListener {
            proceedToScan()
        }

        ui.setupScrollListener()
        ui.updateActivityLabels()
    }

    private fun recalculateDose() {
        persistenceHandler.highDoseAcknowledged = false
        lastCalculatedResult = doseDisplayHandler.calculateAndDisplayDose()
    }

    private fun checkAndRefreshProfileStatus() {
        val meal = MealSessionManager.currentMeal ?: return
        if (meal.profileComplete) return

        val pm = UserProfileManager
        val hasICR = pm.getInsulinCarbRatioRaw(ctx) != null
        val hasISF = pm.getCorrectionFactor(ctx) != null
        val hasTarget = pm.getTargetGlucose(ctx) != null

        if (hasICR && hasISF && hasTarget) {
            val updatedMeal = meal.copy(
                profileComplete = true,
                insulinMessage = null
            )
            MealSessionManager.setCurrentMeal(updatedMeal)
        }
    }

    override fun onResume() {
        super.onResume()

        ui.updateEmptyStateVisibility()
        if (MealSessionManager.currentMeal == null) return

        checkAndRefreshProfileStatus()

        ui.setupGlucoseUnit()
        mealDisplayHandler.updateFoodDisplay()
        ui.updateAnalysisResults()
        imageHandler.displayMealImage()
        ui.updateActivityLabels()
        recalculateDose()
    }

    private fun proceedToHistory() {
        val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav?.selectedItemId = R.id.historyFragment
    }

    private fun proceedToScan() {
        val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)
        bottomNav?.selectedItemId = R.id.scanFragment
    }

    private fun navigateToProfile() {
        findNavController().navigate(R.id.action_summaryFragment_to_profileFragment)
    }

    private fun navigateToManualEntry() {
        findNavController().navigate(R.id.action_summaryFragment_to_manualEntryFragment)
    }
}