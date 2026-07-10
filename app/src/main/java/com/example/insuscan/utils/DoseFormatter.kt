package com.example.insuscan.utils

/**
 * Formats insulin dose values for display.
 *
 * Strips the decimal for whole-number doses (e.g. `5.0` → `"5"`)
 * and optionally appends a unit suffix.
 */
object DoseFormatter {

    /**
     * Returns the dose as a string, removing `.0` for whole numbers.
     * Returns [placeholder] if [dose] is null.
     */
    fun formatDose(dose: Float?, placeholder: String = "—"): String {
        if (dose == null) return placeholder
        return if (dose == dose.toInt().toFloat()) {
            dose.toInt().toString()
        } else {
            String.format("%.1f", dose)
        }
    }

    /**
     * Returns the formatted dose with a unit suffix (e.g. `"5u"`, `"3.5u"`).
     * Returns `"—"` if [dose] is null.
     */
    fun formatDoseWithUnit(dose: Float?, unit: String = "u"): String {
        if (dose == null) return "—"
        return "${formatDose(dose)}$unit"
    }
}