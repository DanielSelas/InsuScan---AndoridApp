package com.example.insuscan.summary

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
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

import com.example.insuscan.network.repository.InsulinCalcRepositoryImpl
import com.example.insuscan.utils.FileLogger
import kotlinx.coroutines.launch

class SummaryFragment : Fragment(R.layout.fragment_summary) {

    private lateinit var ui: SummaryUiManager
    private lateinit var mealDisplayHandler: SummaryMealDisplayHandler
    private lateinit var imageHandler: SummaryImageHandler
    private lateinit var persistenceHandler: SummaryPersistenceHandler
    private lateinit var doseDisplayHandler: SummaryDoseDisplayHandler

    private var lastCalculatedResult: DoseResult? = null
    private val insulinCalcRepository = InsulinCalcRepositoryImpl()

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
            onProfileIncompleteRequested = { navigateToProfile() },
            onPlanChanged = { recalculateDose() }
        )

        initializeTopBar(view)
        initializeListeners()

        ui.updateEmptyStateVisibility()
        if (MealSessionManager.currentMeal != null) {
            val scannedGlucose = MealSessionManager.currentMeal?.glucoseLevel
            if (scannedGlucose != null && ui.glucoseEditText.text.isNullOrEmpty()) {
                ui.glucoseEditText.setText(scannedGlucose.toString())
            }
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

        ui.addFoodButton.setOnClickListener { navigateToManualEntry() }

        ui.glucoseEditText.setOnEditorActionListener { _, _, _ ->
            ui.glucoseEditText.clearFocus()
            val imm = ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(ui.glucoseEditText.windowToken, 0)
            true
        }

        ui.glucoseEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                ui.updateGlucoseStatus()
                recalculateDose()
            }
        })

        doseDisplayHandler.setupPlanChangeListener()

        ui.scanNowButton.setOnClickListener {
            proceedToScan()
        }

        ui.setupScrollListener()
    }

    private fun recalculateDose() {
        persistenceHandler.highDoseAcknowledged = false
        Log.d("CALC_COMPARE", "recalculateDose called")
        val localResult = doseDisplayHandler.calculateAndDisplayDose()
        lastCalculatedResult = localResult
        compareWithServer(localResult)
    }

    private fun compareWithServer(localResult: DoseResult?) {
        Log.d("CALC_COMPARE", "compareWithServer called")
        if (localResult == null) return
        val meal = MealSessionManager.currentMeal ?: return
        val email = UserProfileManager.getUserEmail(ctx) ?: return
        val glucose = ui.glucoseEditText.text.toString().toIntOrNull()

        viewLifecycleOwner.lifecycleScope.launch {
            insulinCalcRepository.calculate(
                totalCarbs = meal.carbs,
                currentGlucose = glucose,
                email = email,
                planIcr = MealSessionManager.activePlanIcr,
                planIsf = MealSessionManager.activePlanIsf,
                planTargetGlucose = MealSessionManager.activePlanTargetGlucose
            ).onSuccess { server ->
                Log.d(
                    "CALC_COMPARE",
                    "LOCAL carb=${localResult.carbDose} corr=${localResult.correctionDose} final=${localResult.finalDose} | SERVER carb=${server.carbDose} corr=${server.correctionDose} total=${server.totalRecommendedDose} | plan=${MealSessionManager.activePlanName ?: "Default"}"
                )
            }.onFailure { e ->
                Log.e("CALC_COMPARE", "Server call failed: ${e.message}")
            }
        }
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

        ui.updateEmptyStateVisibility()
        if (MealSessionManager.currentMeal != null) {
        imageHandler.displayMealImage()
        }

        val scannedGlucose = MealSessionManager.currentMeal?.glucoseLevel
        if (scannedGlucose != null && ui.glucoseEditText.text.isNullOrEmpty()) {
            ui.glucoseEditText.setText(scannedGlucose.toString())
        }

        recalculateDose()
    }

    private fun proceedToHistory() {
        val navOptions = androidx.navigation.NavOptions.Builder()
            .setPopUpTo(R.id.nav_graph, false)
            .build()
        findNavController().navigate(R.id.historyFragment, null, navOptions)
    }

    private fun proceedToScan() {
        val navOptions = androidx.navigation.NavOptions.Builder()
            .setPopUpTo(R.id.nav_graph, false)
            .build()
        findNavController().navigate(R.id.scanFragment, null, navOptions)
    }

    private fun navigateToProfile() {
        findNavController().navigate(R.id.action_summaryFragment_to_profileFragment)
    }

    private fun navigateToManualEntry() {
        findNavController().navigate(R.id.action_summaryFragment_to_manualEntryFragment)
    }
}
