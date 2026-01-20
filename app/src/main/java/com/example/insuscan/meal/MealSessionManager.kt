package com.example.insuscan.meal

object MealSessionManager {
    var currentMeal: Meal? = null
        private set

    private val historyInternal = mutableListOf<Meal>()

    fun setCurrentMeal(meal: Meal) {
        currentMeal = meal
    }

    // Updated: accepts full meal object with all details
    fun saveCurrentMealWithDose(meal: Meal) {
        historyInternal.add(0, meal)
        currentMeal = null  // clear after saving
    }

    // Legacy support: just add dose to current meal
    fun saveCurrentMealWithDose(dose: Float) {
        val meal = currentMeal ?: return
        val mealWithDose = meal.copy(insulinDose = dose)
        historyInternal.add(0, mealWithDose)
    }

    fun getHistory(): List<Meal> = historyInternal.toList()

    fun clearHistory() {
        historyInternal.clear()
    }
}