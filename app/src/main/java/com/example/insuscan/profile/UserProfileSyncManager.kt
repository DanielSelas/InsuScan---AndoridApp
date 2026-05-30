package com.example.insuscan.profile

import android.content.Context
import com.example.insuscan.network.dto.UserDto

object UserProfileSyncManager {
    fun syncFromServer(context: Context, user: UserDto) {
        user.userId?.email?.let { UserProfileManager.saveUserEmail(context, it) }
        user.username?.let { UserProfileManager.saveUserName(context, it) }
        user.age?.let { UserProfileManager.saveUserAge(context, it) }
        user.gender?.let { UserProfileManager.saveUserGender(context, it) }
        user.avatar?.let { UserProfileManager.saveProfilePhotoUrl(context, it) }
        user.insulinCarbRatio?.let { UserProfileManager.saveInsulinCarbRatio(context, it) }
        user.correctionFactor?.let { UserProfileManager.saveCorrectionFactor(context, it) }
        user.targetGlucose?.let { UserProfileManager.saveTargetGlucose(context, it) }
        user.doseRounding?.toFloatOrNull()?.let { UserProfileManager.saveDoseRounding(context, it) }
        user.insulinPlans?.let { UserProfileManager.saveInsulinPlans(context, it) }
        UserProfileManager.setRegistrationComplete(context, true)
    }
}