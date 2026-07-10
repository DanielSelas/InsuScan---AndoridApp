package com.example.insuscan.profile

import android.content.Context

/**
 * Derives calculated values from the raw profile data stored in [UserProfileManager].
 */
object UserProfileCalculations {

    /**
     * Returns the grams-of-carbs-per-unit value from the stored ICR string.
     *
     * Supports both plain numeric format (`"15"`) and ratio format (`"1:15"`).
     * Returns `null` if no ICR is saved or the value cannot be parsed.
     */
    fun getGramsPerUnit(context: Context): Float? {
        val ratioText = UserProfileManager.getInsulinCarbRatioRaw(context) ?: return null
        if (ratioText.contains(":")) {
            val parts = ratioText.split(":")
            if (parts.size >= 2) return parts[1].toFloatOrNull()
        }
        return ratioText.toFloatOrNull()
    }
}
