package com.example.insuscan.profile

import android.content.Context
import com.example.insuscan.network.dto.UserDto

object UserProfileSyncManager {
    fun syncFromServer(context: Context, user: UserDto) {
        user.userId?.email?.let { UserProfileManager.saveUserEmail(context, it) }
        user.username?.let { UserProfileManager.saveUserName(context, it) }
        user.age?.let { UserProfileManager.saveUserAge(context, it) }
        user.gender?.let { UserProfileManager.saveUserGender(context, it) }
        user.pregnant?.let { UserProfileManager.saveIsPregnant(context, it) }
        user.dueDate?.let { UserProfileManager.saveDueDate(context, it) }
        user.avatar?.let { UserProfileManager.saveProfilePhotoUrl(context, it) }
        user.insulinCarbRatio?.let { UserProfileManager.saveInsulinCarbRatio(context, it) }
        user.correctionFactor?.let { UserProfileManager.saveCorrectionFactor(context, it) }
        user.targetGlucose?.let { UserProfileManager.saveTargetGlucose(context, it) }
        user.diabetesType?.let { UserProfileManager.saveDiabetesType(context, it) }
        user.insulinType?.let { UserProfileManager.saveInsulinType(context, it) }
        user.activeInsulinTime?.let { UserProfileManager.saveActiveInsulinTime(context, it.toFloat()) }
        user.syringeType?.let { UserProfileManager.saveSyringeSize(context, it) }
        user.customSyringeLength?.let { UserProfileManager.saveCustomSyringeLength(context, it) }
        user.doseRounding?.toFloatOrNull()?.let { UserProfileManager.saveDoseRounding(context, it) }
        user.sickDayAdjustment?.let { UserProfileManager.saveSickDayAdjustment(context, it) }
        user.stressAdjustment?.let { UserProfileManager.saveStressAdjustment(context, it) }
        user.lightExerciseAdjustment?.let { UserProfileManager.saveLightExerciseAdjustment(context, it) }
        user.intenseExerciseAdjustment?.let { UserProfileManager.saveIntenseExerciseAdjustment(context, it) }
        user.glucoseUnits?.let { UserProfileManager.saveGlucoseUnits(context, it) }
        UserProfileManager.setRegistrationComplete(context, true)
        UserProfileManager.resetTransientModes(context)
    }
}
