package com.example.insuscan.utils

/**
 * Validates raw text input for meal-related fields.
 */
object MealInputValidator {

    const val INVALID_WEIGHT_MESSAGE = "Enter a valid weight in grams (greater than 0)"

    /**
     * Parses [raw] as a positive float weight (grams).
     * Returns `null` if the string is blank, non-numeric, or ≤ 0.
     */
    fun parsePositiveWeight(raw: String): Float? =
        raw.trim().toFloatOrNull()?.takeIf { it > 0f }
}