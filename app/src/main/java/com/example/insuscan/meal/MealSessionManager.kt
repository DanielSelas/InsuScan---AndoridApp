package com.example.insuscan.meal

object MealSessionManager {
    var currentMeal: Meal? = null
        private set

    fun setCurrentMeal(meal: Meal) {
        currentMeal = meal
    }

    fun updateCurrentMealDose(dose: Float) {
        val meal = currentMeal ?: return
        currentMeal = meal.copy(insulinDose = dose)
    }

    fun updateCurrentMeal(meal: Meal) {
        currentMeal = meal
    }

    fun clearSession() {
        currentMeal = null
    }
}