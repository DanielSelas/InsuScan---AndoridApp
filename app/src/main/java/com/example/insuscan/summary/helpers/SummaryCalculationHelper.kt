package com.example.insuscan.summary.helpers

import android.content.Context
import com.example.insuscan.profile.UserProfileManager

data class DoseResult(
    val carbDose: Float,
    val correctionDose: Float,
    val baseDose: Float,
    val finalDose: Float,
    val roundedDose: Float
)

object SummaryCalculationHelper {
    const val DOSE_WARNING_THRESHOLD = 15f
    const val DOSE_BLOCKING_THRESHOLD = 30f
    const val DOSE_HARD_CAP = 100f

    fun roundForPen(context: Context, dose: Float): Float {
        val step = UserProfileManager.getDoseRounding(context) ?: 0.5f
        return if (step > 0) {
            (Math.round(dose / step) * step * 100).toInt() / 100f
        } else {
            (Math.round(dose * 100) / 100f)
        }
    }
}