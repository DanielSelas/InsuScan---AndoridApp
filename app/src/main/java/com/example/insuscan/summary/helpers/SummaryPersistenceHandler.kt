package com.example.insuscan.summary.helpers

import android.app.AlertDialog
import android.content.Context
import com.example.insuscan.appdata.AppDataStore
import com.example.insuscan.meal.Meal
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.profile.UserProfileManager
import com.example.insuscan.network.exception.ApiException
import com.example.insuscan.utils.ToastHelper
import com.example.insuscan.network.repository.MealRepositoryImpl
import com.example.insuscan.summary.helpers.SummaryCalculationHelper.DOSE_BLOCKING_THRESHOLD
import com.example.insuscan.summary.helpers.SummaryCalculationHelper.DOSE_HARD_CAP
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.example.insuscan.mapping.MealDtoMapper
import com.example.insuscan.auth.AuthManager
import com.example.insuscan.R

/**
 * Validates and saves the summary meal: pre-save checks, high-dose confirmation,
 * building the updated meal, and persisting it to the server.
 */
sealed class SaveValidation {
    object Valid : SaveValidation()
    object NoMealData : SaveValidation()
    object NoFoodDetected : SaveValidation()
    object ProfileIncomplete : SaveValidation()

    data class PlanInvalid(val messageResId: Int) : SaveValidation()
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

        when (val validation = validateBeforeSave(meal)) {
            is SaveValidation.Valid -> {
                val dose = lastCalculatedResult?.roundedDose ?: 0f

                if (dose > DOSE_HARD_CAP) {
                    AlertDialog.Builder(context)
                        .setTitle(R.string.dialog_dose_rejected_title)
                        .setMessage(context.getString(R.string.dialog_dose_rejected_msg, String.format("%.1f", dose), DOSE_HARD_CAP.toInt()))
                        .setPositiveButton(R.string.action_ok, null)
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
            is SaveValidation.NoMealData -> showError(context.getString(R.string.error_no_meal_save))
            is SaveValidation.NoFoodDetected -> showError(context.getString(R.string.error_no_food_detected_save))
            is SaveValidation.ProfileIncomplete -> showIncompleteProfileDialog()
            is SaveValidation.PlanInvalid -> showPlanInvalidDialog(validation.messageResId)

        }
    }

    private fun showHighDoseConfirmationDialog(meal: Meal, dose: Float, result: DoseResult?) {
        AlertDialog.Builder(context)
            .setTitle(R.string.dialog_high_dose_title)
            .setMessage(context.getString(R.string.dialog_high_dose_msg, String.format("%.1f", dose)))
            .setPositiveButton(R.string.action_confirm_dose) { _, _ ->
                highDoseAcknowledged = true
                performSave(meal, result)
            }
            .setNegativeButton(R.string.action_cancel, null)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setCancelable(false)
            .show()
    }

    private fun validateBeforeSave(meal: Meal?): SaveValidation = when {
        meal == null -> SaveValidation.NoMealData
        meal.carbs <= 0f && meal.foodItems.isNullOrEmpty() -> SaveValidation.NoFoodDetected
        !meal.profileComplete -> SaveValidation.ProfileIncomplete
        else -> {
            val planError = validateActivePlan()
            if (planError != null) SaveValidation.PlanInvalid(planError) else SaveValidation.Valid
        }
    }

    private fun effectiveIcr(): Float? = MealSessionManager.activePlanIcr ?: UserProfileManager.getGramsPerUnit(context)
    private fun effectiveIsf(): Float? = MealSessionManager.activePlanIsf ?: UserProfileManager.getCorrectionFactor(context)
    private fun effectiveTarget(): Int? = MealSessionManager.activePlanTargetGlucose ?: UserProfileManager.getTargetGlucose(context)

    private fun validateActivePlan(): Int? = PlanValidator.validate(
        planActive = MealSessionManager.activePlanName != null &&
                MealSessionManager.activePlanName != DEFAULT_PLAN_LABEL,
        planIcr = MealSessionManager.activePlanIcr,
        planIsf = MealSessionManager.activePlanIsf,
        planTarget = MealSessionManager.activePlanTargetGlucose,
        effectiveIcr = effectiveIcr(),
        effectiveIsf = effectiveIsf(),
        effectiveTarget = effectiveTarget()
    )

    private fun showPlanInvalidDialog(messageResId: Int) {
        AlertDialog.Builder(context)
            .setTitle(R.string.dialog_plan_incomplete_title)
            .setMessage(messageResId)
            .setPositiveButton(R.string.action_complete_profile) { _, _ -> onIncompleteProfileRequested() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun performSave(meal: Meal, lastCalculatedResult: DoseResult?) {
        val updatedMeal = buildUpdatedMeal(meal, lastCalculatedResult)
        MealSessionManager.setCurrentMeal(updatedMeal)
        saveToServer()
    }

    private fun buildUpdatedMeal(meal: Meal, result: DoseResult?): Meal {
        val glucoseValue = ui.glucoseEditText.text.toString().toIntOrNull()

        val finalResult = result ?: DoseResult(0f, 0f, 0f, 0f, 0f)

        return meal.copy(
            insulinDose = finalResult.roundedDose,
            recommendedDose = finalResult.roundedDose,
            savedPlanName = MealSessionManager.activePlanName ?: DEFAULT_PLAN_LABEL,
            savedIcr = effectiveIcr(),
            savedIsf = effectiveIsf(),
            savedTargetGlucose = effectiveTarget(),
            glucoseLevel = glucoseValue,
            carbDose = finalResult.carbDose,
            correctionDose = finalResult.correctionDose
        )
    }

    private fun saveToServer() {
        ui.logButton.isEnabled = false

        scope.launch {
            try {
                val currentMeal = MealSessionManager.currentMeal
                if (currentMeal == null) {
                     showError(context.getString(R.string.error_no_meal_save))
                     ui.logButton.isEnabled = true
                     return@launch
                }

                val mealDto = MealDtoMapper.mapToDto(currentMeal)
                val userEmail = AuthManager.getUserEmail() ?: ""

                val result = mealRepository.saveScannedMeal(userEmail, mealDto)

                if (result.isSuccess) {
                    ToastHelper.showShort(context, context.getString(R.string.success_meal_saved))
                    MealSessionManager.clearSession()
                    AppDataStore.onMealsChanged()
                    onMealSavedSuccessfully()
                } else {
                    val errorMsg = when (val e = result.exceptionOrNull()) {
                        is ApiException.NoConnection -> context.getString(R.string.error_save_no_connection)
                        is ApiException.Timeout -> context.getString(R.string.error_save_timeout)
                        is ApiException.ServerError -> context.getString(R.string.error_save_server, e.code)
                        is ApiException.ClientError -> context.getString(R.string.error_save_client, e.code)
                        is ApiException.Unauthorized -> context.getString(R.string.error_session_expired)
                        else -> context.getString(R.string.error_save_failed)
                    }
                    showError(errorMsg)
                    ui.logButton.isEnabled = true
                }
            } catch (e: ApiException.NoConnection) {
                showError(context.getString(R.string.error_save_no_connection))
                ui.logButton.isEnabled = true
            } catch (e: Exception) {
                showError(context.getString(R.string.error_save_failed))
                ui.logButton.isEnabled = true
            }
        }
    }

    private fun showIncompleteProfileDialog() {
        AlertDialog.Builder(context)
            .setTitle(R.string.dialog_profile_incomplete_title)
            .setMessage(R.string.dialog_profile_incomplete_msg)
            .setPositiveButton(R.string.action_complete_profile) { _, _ -> onIncompleteProfileRequested() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun showError(message: String) = ToastHelper.showShort(context, message)

    companion object {
        private const val DEFAULT_PLAN_LABEL = "Default"
    }
}
