package com.example.insuscan.utils

object MealInputValidator {

    const val INVALID_WEIGHT_MESSAGE = "Enter a valid weight in grams (greater than 0)"

    fun parsePositiveWeight(raw: String): Float? =
        raw.trim().toFloatOrNull()?.takeIf { it > 0f }
}