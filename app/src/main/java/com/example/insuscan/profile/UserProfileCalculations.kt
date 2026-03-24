package com.example.insuscan.profile

import android.content.Context

object UserProfileCalculations {
    fun getUnitsPerGram(context: Context): Float? {
        val ratioText = UserProfileManager.getInsulinCarbRatioRaw(context) ?: return null
        if (ratioText.contains(":")) {
            val parts = ratioText.split(":")
            if (parts.size != 2) return null
            val units = parts[0].toFloatOrNull() ?: return null
            val grams = parts[1].toFloatOrNull() ?: return null
            if (grams == 0f) return null
            return units / grams
        }
        val grams = ratioText.toFloatOrNull()
        if (grams != null && grams > 0) return 1.0f / grams
        return null
    }

    fun getGramsPerUnit(context: Context): Float? {
        val ratioText = UserProfileManager.getInsulinCarbRatioRaw(context) ?: return null
        if (ratioText.contains(":")) {
            val parts = ratioText.split(":")
            if (parts.size >= 2) return parts[1].toFloatOrNull()
        }
        return ratioText.toFloatOrNull()
    }

    fun getReferenceObjectType(context: Context): String {
        val size = UserProfileManager.getSyringeSize(context).lowercase()
        return when {
            size.contains("card") || size.contains("id") || size.contains("credit") -> "Card"
            else -> "Pen"
        }
    }

    fun getSickModeDays(context: Context): Int {
        val startDate = UserProfileManager.getSickModeStartDate(context)
        if (startDate == 0L) return 0
        val diff = System.currentTimeMillis() - startDate
        return (diff / (1000 * 60 * 60 * 24)).toInt()
    }
}
