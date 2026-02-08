package com.example.insuscan.profile

import android.content.Context
import com.example.insuscan.utils.FileLogger

object UserProfileManager {
    private const val KEY_USER_EMAIL = "user_email"
    private const val PREFS_NAME = "insu_profile_prefs"
    private const val KEY_RATIO = "insulin_carb_ratio"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_CORRECTION_FACTOR = "correction_factor"
    private const val KEY_TARGET_GLUCOSE = "target_glucose"

    private const val KEY_USER_AGE = "user_age"
    private const val KEY_USER_GENDER = "user_gender"
    private const val KEY_IS_PREGNANT = "is_pregnant"
    private const val KEY_DUE_DATE = "due_date"
    private const val KEY_DIABETES_TYPE = "diabetes_type"
    private const val KEY_INSULIN_TYPE = "insulin_type"
    private const val KEY_ACTIVE_INSULIN_TIME = "active_insulin_time"
    private const val KEY_SYRINGE_SIZE = "syringe_size"
    private const val KEY_CUSTOM_SYRINGE_LENGTH = "custom_syringe_length" // New: Store custom length (e.g. 15.0 for fork)
    private const val KEY_DOSE_ROUNDING = "dose_rounding"

    private const val KEY_GLUCOSE_UNITS = "glucose_units"
    private const val KEY_SICK_DAY_ADJUSTMENT = "sick_day_adjustment"
    private const val KEY_STRESS_ADJUSTMENT = "stress_adjustment"
    private const val KEY_LIGHT_EXERCISE_ADJUSTMENT = "light_exercise_adjustment"
    private const val KEY_INTENSE_EXERCISE_ADJUSTMENT = "intense_exercise_adjustment"
    private const val KEY_SICK_MODE_ENABLED = "sick_mode_enabled"
    private const val KEY_SICK_MODE_START_DATE = "sick_mode_start_date"
    private const val KEY_STRESS_MODE_ENABLED = "stress_mode_enabled"
    private const val KEY_EXERCISE_MODE_ENABLED = "exercise_mode_enabled"
    private const val KEY_PROFILE_PHOTO_URL = "profile_photo_url"

    // small helper to get prefs once
    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // region insulin to carb ratio

    fun saveInsulinCarbRatio(context: Context, ratioText: String) {
        prefs(context).edit()
            .putString(KEY_RATIO, ratioText)
            .apply()
    }

    fun getInsulinCarbRatioRaw(context: Context): String? {
        return prefs(context).getString(KEY_RATIO, null)
    }

    fun getUnitsPerGram(context: Context): Float? {
        val ratioText = getInsulinCarbRatioRaw(context) ?: return null

        // Case 1: Format "1:10"
        if (ratioText.contains(":")) {
            val parts = ratioText.split(":")
            if (parts.size != 2) return null
            val units = parts[0].toFloatOrNull() ?: return null
            val grams = parts[1].toFloatOrNull() ?: return null
            if (grams == 0f) return null
            return units / grams
        }

        // Case 2: Just a number "10" (means 1 unit : 10 grams)
        val grams = ratioText.toFloatOrNull()
        if (grams != null && grams > 0) {
            return 1.0f / grams
        }

        return null
    }

    /**
     * Returns the "Grams per Unit" value (e.g. 10.0 for 1:10).
     * This matches the Server's Golden Logic parameters.
     */
    fun getGramsPerUnit(context: Context): Float? {
        val ratioText = getInsulinCarbRatioRaw(context) ?: return null
        
        // Case 1: "1:10" -> returns 10.0
        if (ratioText.contains(":")) {
            val parts = ratioText.split(":")
            if (parts.size >= 2) {
                return parts[1].toFloatOrNull()
            }
        }
        
        // Case 2: "10" -> returns 10.0
        return ratioText.toFloatOrNull()
    }

    // endregion

    // region user name

    // This init block cannot directly access 'context' as UserProfileManager is an object.
    // Assuming there's an external mechanism to call FileLogger.init(context) or
    // that the user intends for a different structure (e.g., a class with a constructor).
    // For now, I will add a placeholder init function.
    fun init(context: Context) {
        FileLogger.init(context)
    }

    // Assuming UserProfile is a data class defined elsewhere
    // data class UserProfile(val name: String, val insulinCarbRatio: String, val correctionFactor: Float, val targetGlucose: Int, val activeInsulinTime: Float)

    fun saveUserProfile(context: Context, profile: UserProfile) {
        val editor = prefs(context).edit()
        
        FileLogger.log("PROFILE", "💾 Saving User Profile")
        FileLogger.log("PROFILE", "   Name: ${profile.name}")
        FileLogger.log("PROFILE", "   ICR: ${profile.insulinCarbRatio} g/unit")
        FileLogger.log("PROFILE", "   ISF: ${profile.correctionFactor}")
        FileLogger.log("PROFILE", "   Target: ${profile.targetGlucose}")
        FileLogger.log("PROFILE", "   Active Insulin Time: ${profile.activeInsulinTime}")
        
        editor.putString(KEY_USER_NAME, profile.name)
            .putString(KEY_RATIO, profile.insulinCarbRatio)
            .putFloat(KEY_CORRECTION_FACTOR, profile.correctionFactor)
            .putInt(KEY_TARGET_GLUCOSE, profile.targetGlucose)
            .putFloat(KEY_ACTIVE_INSULIN_TIME, profile.activeInsulinTime)
            .apply()
    }

    fun getUserProfile(context: Context): UserProfile? {
        val p = prefs(context)
        val name = p.getString(KEY_USER_NAME, null)
        val insulinCarbRatio = p.getString(KEY_RATIO, null)
        val correctionFactor = if (p.contains(KEY_CORRECTION_FACTOR)) p.getFloat(KEY_CORRECTION_FACTOR, 0f) else null
        val targetGlucose = if (p.contains(KEY_TARGET_GLUCOSE)) p.getInt(KEY_TARGET_GLUCOSE, 0) else null
        val activeInsulinTime = p.getFloat(KEY_ACTIVE_INSULIN_TIME, 4f)

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

    fun saveUserName(context: Context, name: String) {
        prefs(context).edit()
            .putString(KEY_USER_NAME, name)
            .apply()
    }

    fun getUserName(context: Context): String? {
        return prefs(context).getString(KEY_USER_NAME, null)
    }

    // endregion

    // region correction factor

    fun saveCorrectionFactor(context: Context, value: Float) {
        prefs(context).edit()
            .putFloat(KEY_CORRECTION_FACTOR, value)
            .apply()
    }

    fun getCorrectionFactor(context: Context): Float? {
        val p = prefs(context)
        return if (p.contains(KEY_CORRECTION_FACTOR))
            p.getFloat(KEY_CORRECTION_FACTOR, 0f)
        else null
    }

    // endregion

    // region target glucose

    fun saveTargetGlucose(context: Context, value: Int) {
        prefs(context).edit()
            .putInt(KEY_TARGET_GLUCOSE, value)
            .apply()
    }

    fun getTargetGlucose(context: Context): Int? {
        val p = prefs(context)
        return if (p.contains(KEY_TARGET_GLUCOSE))
            p.getInt(KEY_TARGET_GLUCOSE, 0)
        else null
    }

//    fun saveUserEmail(context: Context, email: String) {
//        prefs(context).edit()
//            .putString(KEY_USER_EMAIL, email)
//            .apply()
//    }

    fun saveUserEmail(context: Context, email: String) {
        prefs(context).edit().putString(KEY_USER_EMAIL, email).apply()
    }
    fun getUserEmail(context: Context): String? {
        return prefs(context).getString(KEY_USER_EMAIL, null)
    }

    fun clearUserEmail(context: Context) {
        prefs(context).edit().remove(KEY_USER_EMAIL).apply()
    }

    // ============== Age ==============
    fun saveUserAge(context: Context, age: Int) {
        prefs(context).edit().putInt(KEY_USER_AGE, age).apply()
    }

    fun getUserAge(context: Context): Int? {
        val p = prefs(context)
        return if (p.contains(KEY_USER_AGE)) p.getInt(KEY_USER_AGE, 0) else null
    }

    // ============== Gender ==============
    fun saveUserGender(context: Context, gender: String) {
        prefs(context).edit().putString(KEY_USER_GENDER, gender).apply()
    }

    fun getUserGender(context: Context): String? {
        return prefs(context).getString(KEY_USER_GENDER, null)
    }

    // ============== Pregnancy ==============
    fun saveIsPregnant(context: Context, isPregnant: Boolean) {
        prefs(context).edit().putBoolean(KEY_IS_PREGNANT, isPregnant).apply()
    }

    fun getIsPregnant(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_IS_PREGNANT, false)
    }

    fun saveDueDate(context: Context, dueDate: String) {
        prefs(context).edit().putString(KEY_DUE_DATE, dueDate).apply()
    }

    fun getDueDate(context: Context): String? {
        return prefs(context).getString(KEY_DUE_DATE, null)
    }

    // ============== Diabetes Type ==============
    fun saveDiabetesType(context: Context, type: String) {
        prefs(context).edit().putString(KEY_DIABETES_TYPE, type).apply()
    }

    fun getDiabetesType(context: Context): String? {
        return prefs(context).getString(KEY_DIABETES_TYPE, null)
    }

    // ============== Insulin Type & DIA ==============
    fun saveInsulinType(context: Context, type: String) {
        prefs(context).edit().putString(KEY_INSULIN_TYPE, type).apply()
        // auto-set DIA based on insulin type
        val dia = when (type) {
            "Rapid-acting" -> 4f
            "Short-acting" -> 5f
            else -> 4f
        }
        saveActiveInsulinTime(context, dia)
    }

    fun getInsulinType(context: Context): String? {
        return prefs(context).getString(KEY_INSULIN_TYPE, null)
    }

    fun saveActiveInsulinTime(context: Context, hours: Float) {
        prefs(context).edit().putFloat(KEY_ACTIVE_INSULIN_TIME, hours).apply()
    }

    fun getActiveInsulinTime(context: Context): Float {
        return prefs(context).getFloat(KEY_ACTIVE_INSULIN_TIME, 4f)
    }

    // ============== Syringe Settings ==============
    fun saveSyringeSize(context: Context, size: String) {
        prefs(context).edit().putString(KEY_SYRINGE_SIZE, size).apply()
    }

    fun getSyringeSize(context: Context): String {
        return prefs(context).getString(KEY_SYRINGE_SIZE, "0.5ml") ?: "0.5ml"
    }

    // New: Custom Length
    fun saveCustomSyringeLength(context: Context, length: Float) {
        prefs(context).edit().putFloat(KEY_CUSTOM_SYRINGE_LENGTH, length).apply()
    }

    fun getCustomSyringeLength(context: Context): Float {
        // Default to Standard Pen (12.0cm) if not set
        return prefs(context).getFloat(KEY_CUSTOM_SYRINGE_LENGTH, 12.0f)
    }

    fun saveDoseRounding(context: Context, rounding: Float) {
        prefs(context).edit().putFloat(KEY_DOSE_ROUNDING, rounding).apply()
    }

    fun getDoseRounding(context: Context): Float {
        return prefs(context).getFloat(KEY_DOSE_ROUNDING, 0.5f)
    }

    // ============== Glucose Units ==============
    fun saveGlucoseUnits(context: Context, units: String) {
        prefs(context).edit().putString(KEY_GLUCOSE_UNITS, units).apply()
    }

    fun getGlucoseUnits(context: Context): String {
        return prefs(context).getString(KEY_GLUCOSE_UNITS, "mg/dL") ?: "mg/dL"
    }

    // ============== Adjustment Factors ==============
    fun saveSickDayAdjustment(context: Context, percent: Int) {
        prefs(context).edit().putInt(KEY_SICK_DAY_ADJUSTMENT, percent).apply()
    }

    fun getSickDayAdjustment(context: Context): Int {
        return prefs(context).getInt(KEY_SICK_DAY_ADJUSTMENT, 15)
    }

    fun saveStressAdjustment(context: Context, percent: Int) {
        prefs(context).edit().putInt(KEY_STRESS_ADJUSTMENT, percent).apply()
    }

    fun getStressAdjustment(context: Context): Int {
        return prefs(context).getInt(KEY_STRESS_ADJUSTMENT, 10)
    }

    fun saveLightExerciseAdjustment(context: Context, percent: Int) {
        prefs(context).edit().putInt(KEY_LIGHT_EXERCISE_ADJUSTMENT, percent).apply()
    }

    fun getLightExerciseAdjustment(context: Context): Int {
        return prefs(context).getInt(KEY_LIGHT_EXERCISE_ADJUSTMENT, 15)
    }

    fun saveIntenseExerciseAdjustment(context: Context, percent: Int) {
        prefs(context).edit().putInt(KEY_INTENSE_EXERCISE_ADJUSTMENT, percent).apply()
    }

    fun getIntenseExerciseAdjustment(context: Context): Int {
        return prefs(context).getInt(KEY_INTENSE_EXERCISE_ADJUSTMENT, 30)
    }

    // ============== Temporary Modes ==============
    fun setSickModeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SICK_MODE_ENABLED, enabled).apply()
        if (enabled) {
            prefs(context).edit().putLong(KEY_SICK_MODE_START_DATE, System.currentTimeMillis()).apply()
        }
    }

    fun isSickModeEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_SICK_MODE_ENABLED, false)
    }

    fun getSickModeDays(context: Context): Int {
        val startDate = prefs(context).getLong(KEY_SICK_MODE_START_DATE, 0L)
        if (startDate == 0L) return 0
        val diff = System.currentTimeMillis() - startDate
        return (diff / (1000 * 60 * 60 * 24)).toInt()
    }

    fun setStressModeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_STRESS_MODE_ENABLED, enabled).apply()
    }

    fun isStressModeEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_STRESS_MODE_ENABLED, false)
    }

    fun setExerciseModeEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_EXERCISE_MODE_ENABLED, enabled).apply()
    }

    fun isExerciseModeEnabled(context: Context): Boolean {
        return prefs(context).getBoolean(KEY_EXERCISE_MODE_ENABLED, false)
    }

    // ============== Profile Photo ==============
    fun saveProfilePhotoUrl(context: Context, url: String) {
        prefs(context).edit().putString(KEY_PROFILE_PHOTO_URL, url).apply()
    }

    fun getProfilePhotoUrl(context: Context): String? {
        return prefs(context).getString(KEY_PROFILE_PHOTO_URL, null)
    }

//    fun syncFromServer(context: Context, user: com.example.insuscan.network.dto.UserDto) {
//        // Sync personal info
//        user.username?.let { saveUserName(context, it) }
//
//        // Sync medical info
//        user.insulinCarbRatio?.let { saveInsulinCarbRatio(context, it) }
//        user.correctionFactor?.let { saveCorrectionFactor(context, it) }
//        user.targetGlucose?.let { saveTargetGlucose(context, it) }
//
//        // Sync syringe settings (assuming text match)
//        user.syringeType?.let { saveSyringeSize(context, it) }
//
//        // Reset transient modes (Stress/Exercise) to false
//        resetTransientModes(context)
//    }

    fun syncFromServer(context: Context, user: com.example.insuscan.network.dto.UserDto) {
        // 1. Keep current email AND photo to prevent data loss during wipe
        val currentEmail = getUserEmail(context)
        val currentPhotoUrl = getProfilePhotoUrl(context)

        // 2. Clear ALL local preferences to remove data from previous users
        prefs(context).edit().clear().apply()

        // 3. Restore the correct email
        val emailToSave = user.userId?.email ?: currentEmail
        if (emailToSave != null) {
            saveUserEmail(context, emailToSave)
        }

        // 4. Save fresh data from server
        // Personal info
        user.username?.let { saveUserName(context, it) }
        user.age?.let { saveUserAge(context, it) }
        user.gender?.let { saveUserGender(context, it) }
        user.pregnant?.let { saveIsPregnant(context, it) }
        user.dueDate?.let { saveDueDate(context, it) }

        // Logic: Use Server URL if exists, otherwise keep Local URL (e.g. from Google Sign-In)
        val finalPhotoUrl = user.avatar ?: currentPhotoUrl
        if (finalPhotoUrl != null) {
            saveProfilePhotoUrl(context, finalPhotoUrl)
        }

        // Medical info
        user.insulinCarbRatio?.let { saveInsulinCarbRatio(context, it) }
        user.correctionFactor?.let { saveCorrectionFactor(context, it) }
        user.targetGlucose?.let { saveTargetGlucose(context, it) }
        user.diabetesType?.let { saveDiabetesType(context, it) }
        user.insulinType?.let { saveInsulinType(context, it) }

        // Syringe settings
        user.syringeType?.let { saveSyringeSize(context, it) }
        user.customSyringeLength?.let { saveCustomSyringeLength(context, it) }

        // Dose settings
        user.doseRounding?.toFloatOrNull()?.let { saveDoseRounding(context, it) }

        // Adjustment factors
        user.sickDayAdjustment?.let { saveSickDayAdjustment(context, it) }
        user.stressAdjustment?.let { saveStressAdjustment(context, it) }
        user.lightExerciseAdjustment?.let { saveLightExerciseAdjustment(context, it) }
        user.intenseExerciseAdjustment?.let { saveIntenseExerciseAdjustment(context, it) }

        // Preferences
        user.glucoseUnits?.let { saveGlucoseUnits(context, it) }

        // Reset temporary modes
        resetTransientModes(context)
    }

    fun resetTransientModes(context: Context) {
        setStressModeEnabled(context, false)
        setExerciseModeEnabled(context, false)
        // Sick mode is intentionally left as-is (persistent)
    }

    fun clearAllData(context: Context) {
        prefs(context).edit().clear().apply()
    }
}

data class UserProfile(
    val name: String,
    val insulinCarbRatio: String,
    val correctionFactor: Float,
    val targetGlucose: Int,
    val activeInsulinTime: Float
)