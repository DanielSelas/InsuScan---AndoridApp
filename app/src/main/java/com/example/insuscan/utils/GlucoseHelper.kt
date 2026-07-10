package com.example.insuscan.utils

/**
 * Converts glucose values between units and maps them to a clinical status.
 *
 * Status boundaries are relative to the user's [targetMgDl], with absolute
 * low/high limits taken from [GlucoseThresholds].
 */
object GlucoseHelper {

    /** Clinical glucose status with display properties. */
    enum class Status(val emoji: String, val text: String, val colorHex: Long) {
        LOW("⚠️", "Low!", 0xFFD32F2F),
        BELOW_TARGET("", "Below target", 0xFFF57C00),
        IN_RANGE("✓", "In range", 0xFF388E3C),
        ABOVE_TARGET("", "Above target", 0xFFF57C00),
        HIGH("⚠️", "High!", 0xFFD32F2F)
    }

    /**
     * Converts [value] from the given [unit] to mg/dL.
     * Multiplies by 18 for mmol/L; returns [value] unchanged for mg/dL.
     */
    fun toMgDl(value: Int, unit: String): Int {
        return if (unit == "mmol/L") value * 18 else value
    }

    /**
     * Returns the [Status] for [glucoseMgDl] relative to [targetMgDl].
     * Uses ±20/30 mg/dL margins around the target and the global thresholds for LOW/HIGH.
     */
    fun getStatus(glucoseMgDl: Int, targetMgDl: Int): Status {
        return when {
            glucoseMgDl < GlucoseThresholds.LOW -> Status.LOW
            glucoseMgDl < targetMgDl - 20 -> Status.BELOW_TARGET
            glucoseMgDl <= targetMgDl + 30 -> Status.IN_RANGE
            glucoseMgDl <= GlucoseThresholds.HIGH -> Status.ABOVE_TARGET
            else -> Status.HIGH
        }
    }
}