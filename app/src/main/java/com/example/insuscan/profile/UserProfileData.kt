package com.example.insuscan.profile

import android.content.Context
import com.example.insuscan.utils.FileLogger

data class UserProfile(
    val name: String,
    val insulinCarbRatio: String,
    val correctionFactor: Float,
    val targetGlucose: Int,
    val activeInsulinTime: Float
)

object UserProfileDataManager {
    fun saveUserProfile(context: Context, profile: UserProfile) {
        FileLogger.log("PROFILE", "💾 Saving User Profile")
        FileLogger.log("PROFILE", "   Name: ${profile.name}")
        FileLogger.log("PROFILE", "   ICR: ${profile.insulinCarbRatio} g/unit")
        FileLogger.log("PROFILE", "   ISF: ${profile.correctionFactor}")
        FileLogger.log("PROFILE", "   Target: ${profile.targetGlucose}")
        FileLogger.log("PROFILE", "   Active Insulin Time: ${profile.activeInsulinTime}")
        
        UserProfileManager.saveUserName(context, profile.name)
        UserProfileManager.saveInsulinCarbRatio(context, profile.insulinCarbRatio)
        UserProfileManager.saveCorrectionFactor(context, profile.correctionFactor)
        UserProfileManager.saveTargetGlucose(context, profile.targetGlucose)
        UserProfileManager.saveActiveInsulinTime(context, profile.activeInsulinTime)
    }

    fun getUserProfile(context: Context): UserProfile? {
        val name = UserProfileManager.getUserName(context)
        val insulinCarbRatio = UserProfileManager.getInsulinCarbRatioRaw(context)
        val correctionFactor = UserProfileManager.getCorrectionFactor(context)
        val targetGlucose = UserProfileManager.getTargetGlucose(context)
        val activeInsulinTime = UserProfileManager.getActiveInsulinTime(context)

        if (name == null || insulinCarbRatio == null || correctionFactor == null || targetGlucose == null) {
            FileLogger.log("PROFILE", "⚠️ User Profile not fully available.")
            return null
        }

        val profile = UserProfile(name, insulinCarbRatio, correctionFactor, targetGlucose, activeInsulinTime)
        FileLogger.log("PROFILE", "📖 Loading User Profile")
        FileLogger.log("PROFILE", "   Name: ${profile.name}")
        FileLogger.log("PROFILE", "   ICR: ${profile.insulinCarbRatio} g/unit")
        FileLogger.log("PROFILE", "   ISF: ${profile.correctionFactor}")
        FileLogger.log("PROFILE", "   Target: ${profile.targetGlucose}")
        FileLogger.log("PROFILE", "   Active Insulin Time: ${profile.activeInsulinTime}")
        return profile
    }
}
