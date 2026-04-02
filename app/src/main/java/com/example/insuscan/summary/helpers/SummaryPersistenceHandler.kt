package com.example.insuscan.summary.helpers

import android.app.AlertDialog
import android.content.Context
import com.example.insuscan.meal.Meal
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.profile.UserProfileManager
import com.example.insuscan.utils.ToastHelper
import com.example.insuscan.network.repository.MealRepositoryImpl
import com.example.insuscan.summary.helpers.SummaryCalculationHelper.DOSE_BLOCKING_THRESHOLD
import com.example.insuscan.summary.helpers.SummaryCalculationHelper.DOSE_HARD_CAP
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed class SaveValidation {
    object Valid : SaveValidation()
    object NoMealData : SaveValidation()
    object NoFoodDetected : SaveValidation()
    object ProfileIncomplete : SaveValidation()
}

class SummaryPersistenceHandler(
    private val context: Context,
    private val ui: SummaryUiManager,
    private val scope: CoroutineScope,
    private val onIncompleteProfileRequested: () -> Unit,
    private val onMealSavedSuccessfully: () -> Unit
) {
    private val mealRepository = MealRepositoryImpl()
    var highDoseAcknowledged: Boolean = false

    fun saveMeal(lastCalculatedResult: DoseResult?) {
        val meal = MealSessionManager.currentMeal

        when (validateBeforeSave(meal)) {
            is SaveValidation.Valid -> {
                val dose = lastCalculatedResult?.roundedDose ?: 0f

                if (dose > DOSE_HARD_CAP) {
                    AlertDialog.Builder(context)
                        .setTitle("Dose Rejected")
                        .setMessage(String.format("The calculated dose of %.1f units exceeds the maximum safe limit of %d units.\n\nThis is likely a scan error. Please review and correct your meal data before saving.", dose, DOSE_HARD_CAP.toInt()))
                        .setPositiveButton("OK", null)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .show()
                    return
                }

                if (dose > DOSE_BLOCKING_THRESHOLD && !highDoseAcknowledged) {
                    showHighDoseConfirmationDialog(meal!!, dose, lastCalculatedResult)
                    return
                }
                performSave(meal!!, lastCalculatedResult)
            }
            is SaveValidation.NoMealData -> showError("No meal data to save")
            is SaveValidation.NoFoodDetected -> showError("No food detected. Use 'Edit' to add items manually.")
            is SaveValidation.ProfileIncomplete -> showIncompleteProfileDialog(meal!!)
        }
    }

    private fun showHighDoseConfirmationDialog(meal: Meal, dose: Float, result: DoseResult?) {
        AlertDialog.Builder(context)
            .setTitle("Very High Dose Warning")
            .setMessage(String.format("The recommended dose is %.1f units, which is unusually high.\n\nPlease verify:\n  • The scanned food items are correct\n  • The portion sizes look right\n  • Your glucose reading is accurate\n\nAre you sure this dose is correct?", dose))
            .setPositiveButton("I Confirm This Dose") { _, _ ->
                highDoseAcknowledged = true
                performSave(meal, result)
            }
            .setNegativeButton("Cancel", null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setCancelable(false)
            .show()
    }

    private fun validateBeforeSave(meal: Meal?): SaveValidation = when {
        meal == null -> SaveValidation.NoMealData
        meal.carbs <= 0f && meal.foodItems.isNullOrEmpty() -> SaveValidation.NoFoodDetected
        !meal.profileComplete -> SaveValidation.ProfileIncomplete
        else -> SaveValidation.Valid
    }

    private fun performSave(meal: Meal, lastCalculatedResult: DoseResult?) {
        val updatedMeal = buildUpdatedMeal(meal, lastCalculatedResult)
        MealSessionManager.setCurrentMeal(updatedMeal)

        val mealId = updatedMeal.serverId
        if (mealId != null) {
            saveToServer(mealId, updatedMeal.insulinDose)
        } else {
            showError("Meal logged (Local Mode)")
            onMealSavedSuccessfully()
        }
    }

    private fun buildUpdatedMeal(meal: Meal, result: DoseResult?): Meal {
        val glucoseValue = ui.glucoseEditText.text.toString().toIntOrNull()
        val glucoseUnits = UserProfileManager.getGlucoseUnits(context)

        val finalResult = result ?: SummaryCalculationHelper.performCalculation(
            context, meal.carbs, glucoseValue,
            MealSessionManager.activePlanIcr ?: UserProfileManager.getGramsPerUnit(context) ?: 0f
        )

        return meal.copy(
            insulinDose = finalResult.roundedDose,
            recommendedDose = finalResult.roundedDose,
            savedPlanName = MealSessionManager.activePlanName ?: "Default",
            savedIcr = MealSessionManager.activePlanIcr ?: UserProfileManager.getGramsPerUnit(context),
            savedIsf = MealSessionManager.activePlanIsf ?: UserProfileManager.getCorrectionFactor(context),
            savedTargetGlucose = MealSessionManager.activePlanTargetGlucose ?: UserProfileManager.getTargetGlucose(context),
            glucoseLevel = glucoseValue,
            glucoseUnits = glucoseUnits,
            carbDose = finalResult.carbDose,
            correctionDose = finalResult.correctionDose
        )
    }

    private fun saveToServer(mealId: String, dose: Float?) {
        ui.logButton.isEnabled = false

        scope.launch {
            try {
                val currentMeal = MealSessionManager.currentMeal
                if (currentMeal == null) {
                     showError("Error: No meal data to save")
                     ui.logButton.isEnabled = true
                     return@launch
                }

                val mealDto = com.example.insuscan.mapping.MealDtoMapper.mapToDto(currentMeal)
                val userEmail = com.example.insuscan.auth.AuthManager.getUserEmail() ?: ""

                val result = mealRepository.saveScannedMeal(userEmail, mealDto)

                if (result.isSuccess) {
                    ToastHelper.showShort(context, "Meal logged successfully")
                    MealSessionManager.clearSession()
                    onMealSavedSuccessfully()
                } else {
                    val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                    showError("Failed to save: $errorMsg")
                    ui.logButton.isEnabled = true
                }
            } catch (e: Exception) {
                showError("Error: ${e.message}")
                ui.logButton.isEnabled = true
            }
        }
    }

    private fun showIncompleteProfileDialog(meal: Meal) {
        AlertDialog.Builder(context)
            .setTitle("Insulin Not Calculated")
            .setMessage("Your medical profile is incomplete, so no insulin dose was calculated.\n\nDo you want to complete your profile now, or save the meal without insulin data?")
            .setPositiveButton("Complete Profile") { _, _ -> onIncompleteProfileRequested() }
            .setNegativeButton("Save Anyway") { _, _ -> performSave(meal, null) }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun showError(message: String) = ToastHelper.showShort(context, message)
}
