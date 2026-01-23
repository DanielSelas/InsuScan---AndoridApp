package com.example.insuscan.network.repository

import com.example.insuscan.network.dto.FoodItemDto
import com.example.insuscan.network.dto.MealDto

interface MealRepository {
    suspend fun createMeal(userEmail: String, imageUrl: String): Result<MealDto>
    suspend fun getMeal(mealId: String): Result<MealDto>
    suspend fun getUserMeals(email: String, page: Int = 0, size: Int = 10): Result<List<MealDto>>
    suspend fun getRecentMeals(email: String, count: Int = 5): Result<List<MealDto>>
    suspend fun updateFoodItems(mealId: String, items: List<FoodItemDto>): Result<MealDto>
    suspend fun confirmMeal(mealId: String, actualDose: Float? = null): Result<MealDto>
    suspend fun completeMeal(mealId: String): Result<MealDto>
    suspend fun deleteMeal(mealId: String): Result<Unit>

    suspend fun getMealsByDate(email: String, date: String, page: Int = 0, size: Int = 10): Result<List<MealDto>>
}