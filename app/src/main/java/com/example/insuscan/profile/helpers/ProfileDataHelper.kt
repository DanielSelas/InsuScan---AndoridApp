package com.example.insuscan.profile.helpers

import android.content.Context
import android.view.View
import com.example.insuscan.network.dto.UserDto
import com.example.insuscan.profile.InsulinPlanViewManager
import com.example.insuscan.profile.UserProfileManager
import com.example.insuscan.utils.ToastHelper
import com.google.firebase.auth.FirebaseAuth
import com.example.insuscan.network.dto.InsulinPlanDto


class ProfileDataHelper(private val context: Context, private val ui: ProfileUiManager) {

    val planViewManager = InsulinPlanViewManager(context, ui.plansContainer)


    fun loadProfile(imageHandler: ProfileImageHandler) {
        val pm = UserProfileManager
        val ctx = context

        ui.nameEditText.setText(pm.getUserName(ctx) ?: "")
        pm.getUserAge(ctx)?.let { ui.ageEditText.setText(it.toString()) }
        pm.getUserGender(ctx)?.let { ui.setSpinnerSelection(ui.genderSpinner, ui.genderOptions, it) }
        ui.pregnantSwitch.isChecked = pm.getIsPregnant(ctx)
        pm.getDueDate(ctx)?.let { ui.dueDateTextView.text = it }


        val rawRatio = pm.getInsulinCarbRatioRaw(ctx)
        if (rawRatio != null) {
            val ratioParts = rawRatio.split(":")
            if (ratioParts.size == 2) ui.icrEditText.setText(ratioParts[1].trim())
        } else ui.icrEditText.text.clear()

        val isf = pm.getCorrectionFactor(ctx)
        if (isf != null) ui.isfEditText.setText(isf.toInt().toString())
        else ui.isfEditText.text.clear()

        val target = pm.getTargetGlucose(ctx)
        if (target != null) ui.targetGlucoseEditText.setText(target.toString())
        else ui.targetGlucoseEditText.text.clear()

        val rounding = if (pm.getDoseRounding(ctx) == 0.5f) "0.5 units" else "1 unit"
        ui.setSpinnerByValue(ui.doseRoundingSpinner, ui.roundingOptions, rounding)

        val savedPhotoUrl = pm.getProfilePhotoUrl(ctx)
        imageHandler.loadProfilePhoto(savedPhotoUrl)
    }

    fun loadGoogleProfile(imageHandler: ProfileImageHandler) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        ui.emailTextView.text = user.email ?: "No email"
        if (UserProfileManager.getUserName(context).isNullOrBlank()) {
            user.displayName?.let { ui.nameEditText.setText(it) }
        }
        val savedUrl = UserProfileManager.getProfilePhotoUrl(context)
        if (!savedUrl.isNullOrBlank()) {
            imageHandler.loadProfilePhoto(savedUrl)
        } else {
            user.photoUrl?.toString()?.let { imageHandler.loadProfilePhoto(it) }
        }
    }

    fun validateAndSaveLocal(): UserDto? {
        val pm = UserProfileManager
        val ctx = context

        val icrValue = ui.icrEditText.text.toString().trim()
        val isf = ui.isfEditText.text.toString().trim()
        val target = ui.targetGlucoseEditText.text.toString().trim()

        var hasError = false
        if (icrValue.toFloatOrNull() == 0f) { ui.icrEditText.error = "Cannot be 0"; hasError = true }
        if (isf.toFloatOrNull() == 0f) { ui.isfEditText.error = "Cannot be 0"; hasError = true }
        if (target.toIntOrNull() == 0) { ui.targetGlucoseEditText.error = "Cannot be 0"; hasError = true }

        if (hasError) {
            ToastHelper.showShort(ctx, "Please fix invalid values")
            return null
        }

        val fullIcrString = "1:$icrValue"
        val name = ui.nameEditText.text.toString().trim()
        if (name.isNotBlank()) pm.saveUserName(ctx, name)

        ui.ageEditText.text.toString().toIntOrNull()?.let { pm.saveUserAge(ctx, it) }

        val gender = ui.genderOptions.getOrNull(ui.genderSpinner.selectedItemPosition)
        if (gender != null && gender != "Select") pm.saveUserGender(ctx, gender)

        pm.saveIsPregnant(ctx, ui.pregnantSwitch.isChecked)
        if (ui.pregnantSwitch.isChecked) {
            val dueDate = ui.dueDateTextView.text.toString()
            if (dueDate != "Select date") pm.saveDueDate(ctx, dueDate)
        }

        pm.saveInsulinCarbRatio(ctx, fullIcrString)
        isf.toFloatOrNull()?.let { pm.saveCorrectionFactor(ctx, it) }
        target.toIntOrNull()?.let { pm.saveTargetGlucose(ctx, it) }

        val doseRounding = if (ui.doseRoundingSpinner.selectedItemPosition == 0) 0.5f else 1f
        val doseRoundingValue = if (doseRounding == 0.5f) "0.5" else "1"
        pm.saveDoseRounding(ctx, doseRounding)

        FirebaseAuth.getInstance().currentUser?.email?.let { pm.saveUserEmail(ctx, it) }

        return UserDto(
            userId = null,
            username = name.ifEmpty { null },
            role = null,
            avatar = pm.getProfilePhotoUrl(ctx),
            insulinCarbRatio = if (icrValue.isEmpty()) null else "1:$icrValue",
            correctionFactor = isf.toFloatOrNull(),
            targetGlucose = target.toIntOrNull(),
            syringeType = null,
            customSyringeLength = null,
            age = ui.ageEditText.text.toString().toIntOrNull(),
            gender = gender.takeIf { it != "Select" },
            pregnant = ui.pregnantSwitch.isChecked,
            dueDate = ui.dueDateTextView.text.toString().takeIf { it != "Select date" },
            diabetesType = null,
            insulinType = null,
            activeInsulinTime = null,
            doseRounding = doseRoundingValue,

            insulinPlans = planViewManager.getPlans().map { plan ->
                InsulinPlanDto(
                    id = plan.id,
                    name = plan.name,
                    isDefault = plan.isDefault,
                    icr = plan.icr,
                    isf = plan.isf,
                    targetGlucose = plan.targetGlucose
                )
            },
            sickDayAdjustment = null,
            stressAdjustment = null,
            lightExerciseAdjustment = null,
            intenseExerciseAdjustment = null,
            glucoseUnits = null,
            createdTimestamp = null,
            updatedTimestamp = null
        )
    }
}
