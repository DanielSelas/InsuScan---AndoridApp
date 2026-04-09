package com.example.insuscan.meal.exception

/**
 * Sealed exception hierarchy for meal session and food search failures.
 */
sealed class MealException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    // ── Session ───────────────────────────────────────────────────────────────

    object NoActiveSession : MealException(
        "No active meal session. Please start a new scan or manual entry."
    )

    object SessionExpired : MealException(
        "Meal session has expired. Please start a new scan."
    )

    // ── Food Items ────────────────────────────────────────────────────────────

    class InvalidFoodItem(
        val fieldName: String,
        reason: String
    ) : MealException("Invalid food item — $fieldName: $reason")

    object EmptyFoodList : MealException(
        "No food items in this meal. Add at least one item before saving."
    )

    // ── Food Search ───────────────────────────────────────────────────────────

    class FoodSearchFailed(
        cause: Throwable? = null
    ) : MealException("Food search failed: ${cause?.message ?: "unknown error"}", cause)

    object FoodSearchEmpty : MealException(
        "No results found for this food. Try a different search term."
    )

    // ── Save / Persist ────────────────────────────────────────────────────────

    class SaveFailed(
        cause: Throwable? = null
    ) : MealException("Failed to save meal: ${cause?.message ?: "unknown error"}", cause)
}
