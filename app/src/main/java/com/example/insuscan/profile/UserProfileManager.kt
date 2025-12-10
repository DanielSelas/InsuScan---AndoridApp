package com.example.insuscan.profile

import android.content.Context

object UserProfileManager {

    private const val PREFS_NAME = "insu_profile_prefs"
    private const val KEY_RATIO = "insulin_carb_ratio"

    fun saveInsulinCarbRatio(context: Context, ratioText: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_RATIO, ratioText)
            .apply()
    }


    fun getUnitsPerGram(context: Context): Float? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ratioText = prefs.getString(KEY_RATIO, null) ?: return null

        // מצפה לפורמט "1:10"
        val parts = ratioText.split(":")
        if (parts.size != 2) return null

        val units = parts[0].toFloatOrNull() ?: return null
        val grams = parts[1].toFloatOrNull() ?: return null
        if (grams == 0f) return null

        return units / grams
    }
}