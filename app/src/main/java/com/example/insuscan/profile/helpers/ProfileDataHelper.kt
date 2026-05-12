package com.example.insuscan.profile.helpers

import android.content.Context
import com.example.insuscan.network.dto.InsulinPlanDto
import com.example.insuscan.network.dto.UserDto
import com.example.insuscan.profile.InsulinPlan
import com.example.insuscan.profile.InsulinPlanViewManager
import com.example.insuscan.profile.UserProfileManager
import com.google.firebase.auth.FirebaseAuth

class ProfileDataHelper(private val context: Context, private val ui: ProfileUiManager) {

    private val pm get() = UserProfileManager
    val planViewManager = InsulinPlanViewManager(context, ui.plansContainer)

    fun loadProfile(imageHandler: ProfileImageHandler) {
        val ctx = context
        ui.nameDisplay.text = pm.getUserName(ctx) ?: ""

        val rawRatio = pm.getInsulinCarbRatioRaw(ctx)
        val icrValue = rawRatio?.split(":")?.lastOrNull()?.trim()?.let { "1u : ${it}g" } ?: "—"
        ui.setRowValue(ui.rowIcr, icrValue)

        val isf = pm.getCorrectionFactor(ctx)
        ui.setRowValue(ui.rowIsf, if (isf != null) "${isf.toInt()} mg/dL" else "—")

        val target = pm.getTargetGlucose(ctx)
        ui.setRowValue(ui.rowTargetGlucose, if (target != null) "$target mg/dL" else "—")

        val rounding = pm.getDoseRounding(ctx)
        ui.setRowValue(ui.rowDoseRounding, if (rounding == 0.5f) "0.5 u" else "1 u")

        val age = pm.getUserAge(ctx)
        ui.setRowValue(ui.rowAge, if (age != null) "$age" else "—")

        ui.setRowValue(ui.rowGender, pm.getUserGender(ctx) ?: "—")

        imageHandler.loadProfilePhoto(pm.getProfilePhotoUrl(ctx))
    }

    fun loadGoogleProfile(imageHandler: ProfileImageHandler) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        ui.emailTextView.text = user.email ?: ""
        if (pm.getUserName(context).isNullOrBlank()) {
            user.displayName?.let { ui.nameDisplay.text = it }
        }
        val savedUrl = pm.getProfilePhotoUrl(context)
        if (!savedUrl.isNullOrBlank()) {
            imageHandler.loadProfilePhoto(savedUrl)
        } else {
            user.photoUrl?.toString()?.let { imageHandler.loadProfilePhoto(it) }
        }
    }

    fun buildUserDto(): UserDto {
        val ctx = context
        var rawRatio = pm.getInsulinCarbRatioRaw(ctx)
        if (rawRatio != null && !rawRatio.contains(":")) rawRatio = "1:$rawRatio"
        return UserDto(
            userId = null,
            username = pm.getUserName(ctx),
            role = null,
            avatar = pm.getProfilePhotoUrl(ctx),
            insulinCarbRatio = rawRatio,
            correctionFactor = pm.getCorrectionFactor(ctx),
            targetGlucose = pm.getTargetGlucose(ctx),
            syringeType = pm.getSyringeSize(ctx).ifBlank { null },
            customSyringeLength = pm.getCustomSyringeLength(ctx),
            age = pm.getUserAge(ctx),
            gender = pm.getUserGender(ctx),
            pregnant = pm.getIsPregnant(ctx),
            dueDate = pm.getDueDate(ctx),
            diabetesType = pm.getDiabetesType(ctx),
            insulinType = pm.getInsulinType(ctx),
            activeInsulinTime = pm.getActiveInsulinTime(ctx).toInt(),
            doseRounding = if (pm.getDoseRounding(ctx) == 0.5f) "0.5" else "1",
            sickDayAdjustment = pm.getSickDayAdjustment(ctx),
            stressAdjustment = pm.getStressAdjustment(ctx),
            lightExerciseAdjustment = pm.getLightExerciseAdjustment(ctx),
            intenseExerciseAdjustment = pm.getIntenseExerciseAdjustment(ctx),
            glucoseUnits = pm.getGlucoseUnits(ctx),
            insulinPlans = pm.getInsulinPlans(ctx),
            createdTimestamp = null,
            updatedTimestamp = null
        )
    }
}