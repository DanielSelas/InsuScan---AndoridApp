package com.example.insuscan.manualentry.helpers

import com.example.insuscan.manualentry.EditableFoodItem
import com.example.insuscan.meal.FoodItem
import com.example.insuscan.meal.Meal
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.meal.exception.MealException

class MealPersistenceHelper {

    fun loadExistingItems(): List<EditableFoodItem> {
        val currentMeal = MealSessionManager.currentMeal ?: return emptyList()
        val items = mutableListOf<EditableFoodItem>()

        currentMeal.foodItems?.forEach { item ->
            items.add(EditableFoodItem(
                name = item.name,
                weightGrams = item.weightGrams ?: FoodItem.DEFAULT_WEIGHT_GRAMS,
                carbsPer100g = calculateCarbsPer100g(item),
                usdaFdcId = null,
                isLoading = false
            ))
        }

        if (items.isEmpty() && currentMeal.carbs > 0) {
            items.add(EditableFoodItem(
                name = currentMeal.title,
                weightGrams = FoodItem.DEFAULT_WEIGHT_GRAMS,
                carbsPer100g = currentMeal.carbs,
                usdaFdcId = null,
                isLoading = false
            ))
        }

        return items
    }

    fun buildUpdatedMeal(editableItems: List<EditableFoodItem>, totalCarbs: Float): Meal {
        if (editableItems.isEmpty()) throw MealException.EmptyFoodList

        val foodItems = editableItems.map { editable ->
            FoodItem(
                name = editable.name,
                nameHebrew = null,
                carbsGrams = editable.totalCarbs,
                weightGrams = editable.weightGrams,
                confidence = 1.0f
            )
        }

        val title = if (foodItems.size == 1) {
            foodItems.first().name
        } else {
            "${foodItems.first().name} + ${foodItems.size - 1} more"
        }

        val existingMeal = MealSessionManager.currentMeal

        return existingMeal?.copy(
            title = title,
            carbs = totalCarbs,
            foodItems = foodItems
        ) ?: Meal(
            title = title,
            carbs = totalCarbs,
            foodItems = foodItems
        )
    }

    private fun calculateCarbsPer100g(item: FoodItem): Float {
        val weight = item.weightGrams ?: FoodItem.DEFAULT_WEIGHT_GRAMS
        val carbs = item.carbsGrams ?: 0f
        return if (weight > 0) (carbs * 100f) / weight else 0f
    }
}
