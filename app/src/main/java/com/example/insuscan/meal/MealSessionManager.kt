package com.example.insuscan.meal

import com.example.insuscan.meal.exception.MealException
import com.example.insuscan.profile.InsulinPlan

object MealSessionManager {
    var currentMeal: Meal? = null
        private set

    var activePlanName: String? = null
        private set

    var activePlanIcr: Float? = null
        private set
    var activePlanIsf: Float? = null
        private set
    var activePlanTargetGlucose: Int? = null
        private set

    var availablePlans: List<InsulinPlan> = emptyList()

    fun setActivePlan(name: String?, icr: Float?, isf: Float?, targetGlucose: Int?) {
        activePlanName = name
        activePlanIcr = icr
        activePlanIsf = isf
        activePlanTargetGlucose = targetGlucose
    }

    fun clearActivePlan() {
        activePlanName = null
        activePlanIcr = null
        activePlanIsf = null
        activePlanTargetGlucose = null
    }

    /** Returns the current meal or throws [MealException.NoActiveSession] if none exists. */
    fun getCurrentMealOrThrow(): Meal =
        currentMeal ?: throw MealException.NoActiveSession

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