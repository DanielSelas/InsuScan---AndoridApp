package com.example.insuscan.scan.notice

import com.example.insuscan.meal.Meal

object ReferenceNoticeBuilder {

    private const val CODE_ALTERNATIVE_TOP  = "ALTERNATIVE_OBJECT_USED_IN_TOP"
    private const val CODE_ALTERNATIVE_SIDE = "ALTERNATIVE_OBJECT_USED_IN_SIDE"
    private const val CODE_PLATE_TOP        = "PLATE_SIZE_ESTIMATE_IN_TOP"
    private const val CODE_PLATE_SIDE       = "PLATE_SIZE_ESTIMATE_IN_SIDE"

    fun build(meal: Meal): String? {
        val warnings = meal.reviewWarnings ?: return null
        if (warnings.isEmpty()) return null

        val hasPlateFallback = warnings.any { it.containsCode(CODE_PLATE_TOP) || it.containsCode(CODE_PLATE_SIDE) }
        val hasAlternative   = warnings.any { it.containsCode(CODE_ALTERNATIVE_TOP) || it.containsCode(CODE_ALTERNATIVE_SIDE) }

        return when {
            hasPlateFallback -> buildPlateFallbackMessage(meal)
            hasAlternative   -> buildAlternativeMessage(meal, warnings)
            else             -> null
        }
    }

    private fun buildPlateFallbackMessage(meal: Meal): String {
        val selected = meal.referenceObjectType
        val userSelectedNone = selected.isNullOrBlank() || selected.equals("NONE", ignoreCase = true)
        val body = if (userSelectedNone) {
            "No reference object was detected, so size was estimated from a standard plate."
        } else {
            "The selected reference wasn't found and no other object was detected, so size was estimated from a standard plate."
        }
        return "<b>Lower accuracy — please review portions</b><br>$body"
    }

    private fun buildAlternativeMessage(meal: Meal, warnings: List<String>): String {
        val selected = meal.referenceObjectType
        val userSelectedNone = selected.isNullOrBlank() || selected.equals("NONE", ignoreCase = true)
        val body = if (userSelectedNone) {
            "An object detected in the photo was used for sizing."
        } else {
            "The selected reference wasn't found, so another detected object was used for sizing."
        }
        return "<b>Backup reference used</b><br>$body"
    }
    private fun defaultAlternativeMessage(userSelectedNone: Boolean): String =
        if (userSelectedNone) {
            "No reference object was selected, but an alternative was detected and used for calibration."
        } else {
            "The selected reference object was not found, but an alternative was detected and used for calibration."
        }

    private fun humanize(serverValue: String?): String {
        if (serverValue.isNullOrBlank()) return "none"
        return serverValue.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
    }

    private fun stripCodePrefix(raw: String): String {
        val closeBracket = raw.indexOf(']')
        if (raw.startsWith("[") && closeBracket > 0 && closeBracket < raw.length - 1) {
            return raw.substring(closeBracket + 1).trim()
        }
        return raw
    }

    private fun String.containsCode(code: String): Boolean = this.contains(code, ignoreCase = true)
}