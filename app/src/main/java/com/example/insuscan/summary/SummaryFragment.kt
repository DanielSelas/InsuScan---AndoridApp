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
import com.example.insuscan.network.repository.InsulinCalcRepositoryImpl
import com.example.insuscan.profile.UserProfileManager
import com.example.insuscan.summary.helpers.DoseResult
import com.example.insuscan.summary.helpers.SummaryCalculationHelper
import com.example.insuscan.summary.helpers.SummaryDoseDisplayHandler
import com.example.insuscan.summary.helpers.SummaryImageHandler
import com.example.insuscan.summary.helpers.SummaryMealDisplayHandler
import com.example.insuscan.summary.helpers.SummaryPersistenceHandler
import com.example.insuscan.summary.helpers.SummaryUiManager
import com.example.insuscan.utils.TopBarHelper
import kotlinx.coroutines.launch
import androidx.navigation.NavOptions

/**
 * Meal summary screen: shows the scanned meal, computes and displays the insulin dose
 * (local preview + server result), and logs the confirmed meal.
 */
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
        setupUseLastGlucose()


        ui.updateEmptyStateVisibility()
        if (MealSessionManager.currentMeal != null) {
            restoreGlucoseField()
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

    private fun navigateClearingBackStack(destinationId: Int) {
        val navOptions = NavOptions.Builder()
            .setPopUpTo(R.id.nav_graph, false)
            .build()
        findNavController().navigate(destinationId, null, navOptions)
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
            val imm =
                ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(ui.glucoseEditText.windowToken, 0)
            true
        }

        ui.glucoseEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                MealSessionManager.setEnteredGlucose(s?.toString()?.toIntOrNull())
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


    private fun setupUseLastGlucose() {
        ui.useLastGlucoseButton.isEnabled = false
        val email = UserProfileManager.getUserEmail(ctx) ?: return
        viewLifecycleOwner.lifecycleScope.launch {
            com.example.insuscan.network.repository.MealRepositoryImpl()
                .getLatestMeal(email)
                .onSuccess { last ->
                    val glucose = last?.currentGlucose ?: last?.insulinCalculation?.currentGlucose
                    if (glucose != null) {
                        ui.useLastGlucoseButton.isEnabled = true
                        ui.useLastGlucoseButton.setOnClickListener {
                            ui.glucoseEditText.setText(glucose.toString())
                        }
                    }
                }
        }
    }

    private fun recalculateDose() {
        persistenceHandler.highDoseAcknowledged = false
        val ready = doseDisplayHandler.calculateAndDisplayDose()
        if (ready) fetchServerDose()
    }

    private fun restoreGlucoseField() {
        if (!ui.glucoseEditText.text.isNullOrEmpty()) return
        val glucose =
            MealSessionManager.enteredGlucose ?: MealSessionManager.currentMeal?.glucoseLevel
        if (glucose != null) {
            ui.glucoseEditText.setText(glucose.toString())
        }
    }

    private fun fetchServerDose() {
        val meal = MealSessionManager.currentMeal ?: return
        val email = UserProfileManager.getUserEmail(ctx) ?: return
        val glucose = ui.glucoseEditText.text.toString().toIntOrNull()

        val wasEnabled = ui.logButton.isEnabled
        ui.recommendedDoseText.alpha = 0.4f
        ui.setLogButtonEnabled(false)

        viewLifecycleOwner.lifecycleScope.launch {
            insulinCalcRepository.calculate(
                totalCarbs = meal.carbs,
                currentGlucose = glucose,
                email = email,
                planIcr = MealSessionManager.activePlanIcr,
                planIsf = MealSessionManager.activePlanIsf,
                planTargetGlucose = MealSessionManager.activePlanTargetGlucose
            ).onSuccess { server ->
                val total = server.totalRecommendedDose
                if (total != null) {
                    lastCalculatedResult = doseDisplayHandler.displayServerResult(
                        carbDose = server.carbDose ?: 0f,
                        correctionDose = server.correctionDose ?: 0f,
                        total = total
                    )
                }
                ui.recommendedDoseText.alpha = 1f
                val dose = lastCalculatedResult?.roundedDose ?: 0f
                ui.setLogButtonEnabled(wasEnabled && dose <= SummaryCalculationHelper.DOSE_HARD_CAP)
            }.onFailure { e ->
                ui.recommendedDoseText.alpha = 1f
                ui.setLogButtonEnabled(wasEnabled)
                Log.d("CALC_COMPARE", "Server call failed: ${e.message}")
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


    private fun proceedToHistory() {
        navigateClearingBackStack(R.id.historyFragment)
    }

    private fun proceedToScan() {
        navigateClearingBackStack(R.id.scanFragment)
    }

    private fun navigateToProfile() {
        findNavController().navigate(R.id.action_summaryFragment_to_profileFragment)
    }

    private fun navigateToManualEntry() {
        findNavController().navigate(R.id.action_summaryFragment_to_manualEntryFragment)
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

        restoreGlucoseField()

        recalculateDose()
    }

}
