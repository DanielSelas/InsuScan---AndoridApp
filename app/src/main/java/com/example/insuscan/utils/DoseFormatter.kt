package com.example.insuscan.utils

object DoseFormatter {

    // Formats insulin dose - removes .0 for whole numbers
    fun formatDose(dose: Float?, placeholder: String = "—"): String {
        if (dose == null) return placeholder
        return if (dose == dose.toInt().toFloat()) {
            dose.toInt().toString()
        } else {
            String.format("%.1f", dose)
        }
    }

    // Formats dose with unit suffix (e.g. "5u" or "3.5u")
    fun formatDoseWithUnit(dose: Float?, unit: String = "u"): String {
        return "${formatDose(dose)}$unit"
    }
}