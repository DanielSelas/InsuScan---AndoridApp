package com.example.insuscan.utils

object GlucoseHelper {
    enum class Status(val emoji: String, val text: String, val colorHex: Long) {
        LOW("⚠️", "Low!", 0xFFD32F2F),
        BELOW_TARGET("", "Below target", 0xFFF57C00),
        IN_RANGE("✓", "In range", 0xFF388E3C),
        ABOVE_TARGET("", "Above target", 0xFFF57C00),
        HIGH("⚠️", "High!", 0xFFD32F2F)
    }

    fun toMgDl(value: Int, unit: String): Int {
        return if (unit == "mmol/L") value * 18 else value
    }

    fun getStatus(glucoseMgDl: Int, targetMgDl: Int): Status {
        return when {
            glucoseMgDl < 70 -> Status.LOW
            glucoseMgDl < targetMgDl - 20 -> Status.BELOW_TARGET
            glucoseMgDl <= targetMgDl + 30 -> Status.IN_RANGE
            glucoseMgDl <= 180 -> Status.ABOVE_TARGET
            else -> Status.HIGH
        }
    }
}