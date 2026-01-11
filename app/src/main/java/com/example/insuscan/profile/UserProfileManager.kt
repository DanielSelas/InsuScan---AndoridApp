package com.example.insuscan.profile

import android.content.Context

object UserProfileManager {
    private const val KEY_USER_EMAIL = "user_email"
    private const val PREFS_NAME = "insu_profile_prefs"
    private const val KEY_RATIO = "insulin_carb_ratio"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_CORRECTION_FACTOR = "correction_factor"
    private const val KEY_TARGET_GLUCOSE = "target_glucose"

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

        // expecting format like "1:10"
        val parts = ratioText.split(":")
        if (parts.size != 2) return null

        val units = parts[0].toFloatOrNull() ?: return null
        val grams = parts[1].toFloatOrNull() ?: return null

        if (grams == 0f) return null
        return units / grams
    }

    // endregion

    // region user name

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

    fun saveUserEmail(context: Context, email: String) {
        prefs(context).edit()
            .putString(KEY_USER_EMAIL, email)
            .apply()
    }

    fun getUserEmail(context: Context): String? {
        return prefs(context).getString(KEY_USER_EMAIL, null)
    }

    // endregion
}