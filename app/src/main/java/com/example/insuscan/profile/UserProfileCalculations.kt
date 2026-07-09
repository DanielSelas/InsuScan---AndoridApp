package com.example.insuscan.profile

import android.content.Context

object UserProfileCalculations {
    fun getGramsPerUnit(context: Context): Float? {
        val ratioText = UserProfileManager.getInsulinCarbRatioRaw(context) ?: return null
        if (ratioText.contains(":")) {
            val parts = ratioText.split(":")
            if (parts.size >= 2) return parts[1].toFloatOrNull()
        }
        return ratioText.toFloatOrNull()
    }
}
