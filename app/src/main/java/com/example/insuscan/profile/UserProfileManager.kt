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
    private const val KEY_CUSTOM_SYRINGE_LENGTH = "custom_syringe_length"
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
    private const val KEY_REGISTRATION_COMPLETE = "registration_complete"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private inline fun <reified T> getPref(context: Context, key: String, default: T): T {
        val p = prefs(context)
        return when (T::class) {
            String::class -> p.getString(key, default as String?) as T
            Int::class -> p.getInt(key, default as Int) as T
            Float::class -> p.getFloat(key, default as Float) as T
            Boolean::class -> p.getBoolean(key, default as Boolean) as T
            Long::class -> p.getLong(key, default as Long) as T
            else -> default
        }
    }

    private inline fun <reified T> getPrefNullable(context: Context, key: String): T? {
        val p = prefs(context)
        if (!p.contains(key)) return null
        return when (T::class) {
            String::class -> p.getString(key, null) as T?
            Int::class -> p.getInt(key, 0) as T?
            Float::class -> p.getFloat(key, 0f) as T?
            Boolean::class -> p.getBoolean(key, false) as T?
            Long::class -> p.getLong(key, 0L) as T?
            else -> null
        }
    }

    private fun <T> savePref(context: Context, key: String, value: T) {
        val editor = prefs(context).edit()
        when (value) {
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Float -> editor.putFloat(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Long -> editor.putLong(key, value)
            null -> editor.remove(key)
        }
        editor.apply()
    }

    fun init(context: Context) { FileLogger.init(context) }

    fun saveInsulinCarbRatio(context: Context, ratioText: String) = savePref(context, KEY_RATIO, ratioText)
    fun getInsulinCarbRatioRaw(context: Context): String? = getPrefNullable(context, KEY_RATIO)
    
    fun getUnitsPerGram(context: Context): Float? = UserProfileCalculations.getUnitsPerGram(context)
    fun getGramsPerUnit(context: Context): Float? = UserProfileCalculations.getGramsPerUnit(context)

    fun saveUserProfile(context: Context, profile: UserProfile) = UserProfileDataManager.saveUserProfile(context, profile)
    fun getUserProfile(context: Context): UserProfile? = UserProfileDataManager.getUserProfile(context)

    fun isRegistrationComplete(context: Context): Boolean {
        val p = prefs(context)
        if (p.contains(KEY_REGISTRATION_COMPLETE)) return p.getBoolean(KEY_REGISTRATION_COMPLETE, false)
        if (p.contains(KEY_TARGET_GLUCOSE)) return true
        return false
    }

    fun setRegistrationComplete(context: Context, complete: Boolean) = savePref(context, KEY_REGISTRATION_COMPLETE, complete)
    
    fun saveUserName(context: Context, name: String) = savePref(context, KEY_USER_NAME, name)
    fun getUserName(context: Context): String? = getPrefNullable(context, KEY_USER_NAME)

    fun saveCorrectionFactor(context: Context, value: Float) = savePref(context, KEY_CORRECTION_FACTOR, value)
    fun getCorrectionFactor(context: Context): Float? = getPrefNullable(context, KEY_CORRECTION_FACTOR)

    fun saveTargetGlucose(context: Context, value: Int) = savePref(context, KEY_TARGET_GLUCOSE, value)
    fun getTargetGlucose(context: Context): Int? = getPrefNullable(context, KEY_TARGET_GLUCOSE)

    fun saveUserEmail(context: Context, email: String) = savePref(context, KEY_USER_EMAIL, email)
    fun getUserEmail(context: Context): String? = getPrefNullable(context, KEY_USER_EMAIL)
    fun clearUserEmail(context: Context) = savePref(context, KEY_USER_EMAIL, null as String?)

    fun saveUserAge(context: Context, age: Int) = savePref(context, KEY_USER_AGE, age)
    fun getUserAge(context: Context): Int? = getPrefNullable(context, KEY_USER_AGE)

    fun saveUserGender(context: Context, gender: String) = savePref(context, KEY_USER_GENDER, gender)
    fun getUserGender(context: Context): String? = getPrefNullable(context, KEY_USER_GENDER)

    fun saveIsPregnant(context: Context, isPregnant: Boolean) = savePref(context, KEY_IS_PREGNANT, isPregnant)
    fun getIsPregnant(context: Context): Boolean = getPref(context, KEY_IS_PREGNANT, false)
    fun saveDueDate(context: Context, dueDate: String) = savePref(context, KEY_DUE_DATE, dueDate)
    fun getDueDate(context: Context): String? = getPrefNullable(context, KEY_DUE_DATE)

    fun saveDiabetesType(context: Context, type: String) = savePref(context, KEY_DIABETES_TYPE, type)
    fun getDiabetesType(context: Context): String? = getPrefNullable(context, KEY_DIABETES_TYPE)

    fun saveInsulinType(context: Context, type: String) {
        savePref(context, KEY_INSULIN_TYPE, type)
        val dia = when (type) {
            "Rapid-acting" -> 4f
            "Short-acting" -> 5f
            else -> 4f
        }
        saveActiveInsulinTime(context, dia)
    }

    fun getInsulinType(context: Context): String? = getPrefNullable(context, KEY_INSULIN_TYPE)
    fun saveActiveInsulinTime(context: Context, hours: Float) = savePref(context, KEY_ACTIVE_INSULIN_TIME, hours)
    fun getActiveInsulinTime(context: Context): Float = getPref(context, KEY_ACTIVE_INSULIN_TIME, 4f)

    fun saveSyringeSize(context: Context, size: String) = savePref(context, KEY_SYRINGE_SIZE, size)
    fun getSyringeSize(context: Context): String = getPref(context, KEY_SYRINGE_SIZE, "0.5ml")

    fun saveCustomSyringeLength(context: Context, length: Float) = savePref(context, KEY_CUSTOM_SYRINGE_LENGTH, length)
    fun getCustomSyringeLength(context: Context): Float = getPref(context, KEY_CUSTOM_SYRINGE_LENGTH, 12.0f)

    fun saveDoseRounding(context: Context, rounding: Float) = savePref(context, KEY_DOSE_ROUNDING, rounding)
    fun getDoseRounding(context: Context): Float = getPref(context, KEY_DOSE_ROUNDING, 0.5f)

    fun getReferenceObjectType(context: Context): String = UserProfileCalculations.getReferenceObjectType(context)

    fun saveGlucoseUnits(context: Context, units: String) = savePref(context, KEY_GLUCOSE_UNITS, units)
    fun getGlucoseUnits(context: Context): String = getPref(context, KEY_GLUCOSE_UNITS, "mg/dL")

    fun saveSickDayAdjustment(context: Context, percent: Int) = savePref(context, KEY_SICK_DAY_ADJUSTMENT, percent)
    fun getSickDayAdjustment(context: Context): Int = getPref(context, KEY_SICK_DAY_ADJUSTMENT, 15)

    fun saveStressAdjustment(context: Context, percent: Int) = savePref(context, KEY_STRESS_ADJUSTMENT, percent)
    fun getStressAdjustment(context: Context): Int = getPref(context, KEY_STRESS_ADJUSTMENT, 10)

    fun saveLightExerciseAdjustment(context: Context, percent: Int) = savePref(context, KEY_LIGHT_EXERCISE_ADJUSTMENT, percent)
    fun getLightExerciseAdjustment(context: Context): Int = getPref(context, KEY_LIGHT_EXERCISE_ADJUSTMENT, 15)

    fun saveIntenseExerciseAdjustment(context: Context, percent: Int) = savePref(context, KEY_INTENSE_EXERCISE_ADJUSTMENT, percent)
    fun getIntenseExerciseAdjustment(context: Context): Int = getPref(context, KEY_INTENSE_EXERCISE_ADJUSTMENT, 30)

    fun setSickModeEnabled(context: Context, enabled: Boolean) {
        savePref(context, KEY_SICK_MODE_ENABLED, enabled)
        if (enabled) {
            savePref(context, KEY_SICK_MODE_START_DATE, System.currentTimeMillis())
        }
    }
    fun isSickModeEnabled(context: Context): Boolean = getPref(context, KEY_SICK_MODE_ENABLED, false)

    fun getSickModeStartDate(context: Context): Long = getPref(context, KEY_SICK_MODE_START_DATE, 0L)
    fun getSickModeDays(context: Context): Int = UserProfileCalculations.getSickModeDays(context)

    fun setStressModeEnabled(context: Context, enabled: Boolean) = savePref(context, KEY_STRESS_MODE_ENABLED, enabled)
    fun isStressModeEnabled(context: Context): Boolean = getPref(context, KEY_STRESS_MODE_ENABLED, false)

    fun setExerciseModeEnabled(context: Context, enabled: Boolean) = savePref(context, KEY_EXERCISE_MODE_ENABLED, enabled)
    fun isExerciseModeEnabled(context: Context): Boolean = getPref(context, KEY_EXERCISE_MODE_ENABLED, false)

    fun saveProfilePhotoUrl(context: Context, url: String) = savePref(context, KEY_PROFILE_PHOTO_URL, url)
    fun getProfilePhotoUrl(context: Context): String? = getPrefNullable(context, KEY_PROFILE_PHOTO_URL)

    fun syncFromServer(context: Context, user: com.example.insuscan.network.dto.UserDto) = UserProfileSyncManager.syncFromServer(context, user)

    fun resetTransientModes(context: Context) {
        setStressModeEnabled(context, false)
        setExerciseModeEnabled(context, false)
    }

    fun clearAllData(context: Context) {
        prefs(context).edit().clear().apply()
    }
}