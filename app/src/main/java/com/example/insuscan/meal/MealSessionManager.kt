package com.example.insuscan.meal

object MealSessionManager {
    var currentMeal: Meal? = null
        private set

    var activePlanIcr: Float? = null
        private set
    var activePlanIsf: Float? = null
        private set
    var activePlanTargetGlucose: Int? = null
        private set

    fun setActivePlan(icr: Float?, isf: Float?, targetGlucose: Int?) {
        activePlanIcr = icr
        activePlanIsf = isf
        activePlanTargetGlucose = targetGlucose
    }

    fun clearActivePlan() {
        activePlanIcr = null
        activePlanIsf = null
        activePlanTargetGlucose = null
    }

    fun setCurrentMeal(meal: Meal) {
        currentMeal = meal
    }

    fun updateCurrentMealDose(dose: Float) {
        val meal = currentMeal ?: return
        currentMeal = meal.copy(insulinDose = dose)
    }

    fun clearSession() {
        currentMeal = null
        clearActivePlan()
    }
}