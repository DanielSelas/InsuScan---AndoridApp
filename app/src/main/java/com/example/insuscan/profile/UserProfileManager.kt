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
    private const val KEY_DOSE_ROUNDING = "dose_rounding"

    private const val KEY_PROFILE_PHOTO_URL = "profile_photo_url"
    private const val KEY_REGISTRATION_COMPLETE = "registration_complete"
    private const val KEY_INSULIN_PLANS = "insulin_plans"

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

    fun saveDoseRounding(context: Context, rounding: Float) = savePref(context, KEY_DOSE_ROUNDING, rounding)
    fun getDoseRounding(context: Context): Float = getPref(context, KEY_DOSE_ROUNDING, 0.5f)


    fun saveInsulinPlans(context: Context, plans: List<com.example.insuscan.network.dto.InsulinPlanDto>) {
        val json = com.google.gson.Gson().toJson(plans)
        savePref(context, KEY_INSULIN_PLANS, json)
    }

    fun getInsulinPlans(context: Context): List<com.example.insuscan.network.dto.InsulinPlanDto>? {
        val json = getPrefNullable<String>(context, KEY_INSULIN_PLANS) ?: return null
        val type = object : com.google.gson.reflect.TypeToken<List<com.example.insuscan.network.dto.InsulinPlanDto>>() {}.type
        return try {
            com.google.gson.Gson().fromJson(json, type)
        } catch (e: Exception) {
            null
        }
    }


    fun saveProfilePhotoUrl(context: Context, url: String) = savePref(context, KEY_PROFILE_PHOTO_URL, url)
    fun getProfilePhotoUrl(context: Context): String? = getPrefNullable(context, KEY_PROFILE_PHOTO_URL)

    fun syncFromServer(context: Context, user: com.example.insuscan.network.dto.UserDto) = UserProfileSyncManager.syncFromServer(context, user)

    fun clearAllData(context: Context) {
        prefs(context).edit().clear().apply()
    }
}