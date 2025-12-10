package com.example.insuscan.meal

object MealSessionManager {

    var currentMeal: Meal? = null
        private set

    private val historyInternal = mutableListOf<Meal>()

    fun setCurrentMeal(meal: Meal) {
        currentMeal = meal
    }

    fun saveCurrentMealWithDose(dose: Float) {
        val meal = currentMeal ?: return
        val mealWithDose = meal.copy(insulinDose = dose)
        historyInternal.add(0, mealWithDose) // add in the first place of the list
    }

    fun getHistory(): List<Meal> = historyInternal.toList()
}