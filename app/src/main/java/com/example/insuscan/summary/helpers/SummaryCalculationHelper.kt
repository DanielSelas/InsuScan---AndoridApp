package com.example.insuscan.summary.helpers

import android.content.Context
import com.example.insuscan.profile.UserProfileManager

/**
 * The insulin dose breakdown: carb, correction, base, final, and pen-rounded values.
 */
data class DoseResult(
    val carbDose: Float,
    val correctionDose: Float,
    val baseDose: Float,
    val finalDose: Float,
    val roundedDose: Float
)

/**
 * Insulin dose thresholds and pen-step rounding for the summary screen.
 */
object SummaryCalculationHelper {
    const val DOSE_WARNING_THRESHOLD = 15f
    const val DOSE_BLOCKING_THRESHOLD = 30f
    const val DOSE_HARD_CAP = 100f

    /**
     * Rounds the dose to the user's pen step (e.g. 0.5 u), keeping 2-decimal precision.
     */
    fun roundForPen(context: Context, dose: Float): Float {
        val step = UserProfileManager.getDoseRounding(context)
        return if (step > 0) {
            (Math.round(dose / step) * step * 100).toInt() / 100f
        } else {
            (Math.round(dose * 100) / 100f)
        }
    }
}