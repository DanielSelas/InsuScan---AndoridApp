package com.example.insuscan.profile.helpers

import android.content.Context
import com.example.insuscan.network.dto.UserDto
import com.example.insuscan.profile.InsulinPlanViewManager
import com.example.insuscan.profile.UserProfileManager
import com.google.firebase.auth.FirebaseAuth

/**
 * Loads profile data into the UI, pulls the Google account profile,
 * and builds the UserDto sent to the server.
 */
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
        ui.setRowValue(ui.rowDoseRounding, if (rounding == UserProfileManager.DEFAULT_DOSE_ROUNDING) "0.5 u" else "1 u")

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

    /**
     * Builds a UserDto from the stored profile, normalizing the ICR to "1:x" form.
     */
    fun buildUserDto(): UserDto {
        val doseRounding = if (pm.getDoseRounding(context) == UserProfileManager.DEFAULT_DOSE_ROUNDING) "0.5" else "1"
        return UserProfileManager.buildUserDto(context, doseRounding)
    }
}