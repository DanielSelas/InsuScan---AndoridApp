package com.example.insuscan.network.repository

import com.example.insuscan.network.ApiConfig
import com.example.insuscan.network.RetrofitClient
import com.example.insuscan.network.dto.CreateMealRequest
import com.example.insuscan.network.dto.FoodItemDto
import com.example.insuscan.network.dto.MealDto

// Handles meal-related API calls
class MealRepository {

    private val api = RetrofitClient.api

    suspend fun createMeal(userEmail: String, imageUrl: String): Result<MealDto> {
        return try {
            val request = CreateMealRequest(
                userSystemId = ApiConfig.SYSTEM_ID,
                userEmail = userEmail,
                imageUrl = imageUrl
            )
            val response = api.createMeal(request)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Create meal failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMeal(mealId: String): Result<MealDto> {
        return try {
            val response = api.getMeal(ApiConfig.SYSTEM_ID, mealId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Meal not found: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserMeals(email: String, page: Int = 0, size: Int = 10): Result<List<MealDto>> {
        return try {
            val response = api.getUserMeals(ApiConfig.SYSTEM_ID, email, page, size)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to get meals: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRecentMeals(email: String, count: Int = 5): Result<List<MealDto>> {
        return try {
            val response = api.getRecentMeals(ApiConfig.SYSTEM_ID, email, count)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Failed to get recent meals: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateFoodItems(mealId: String, items: List<FoodItemDto>): Result<MealDto> {
        return try {
            val response = api.updateFoodItems(ApiConfig.SYSTEM_ID, mealId, items)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Update failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun confirmMeal(mealId: String, actualDose: Float? = null): Result<MealDto> {
        return try {
            val response = api.confirmMeal(ApiConfig.SYSTEM_ID, mealId, actualDose)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Confirm failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun completeMeal(mealId: String): Result<MealDto> {
        return try {
            val response = api.completeMeal(ApiConfig.SYSTEM_ID, mealId)
            if (response.isSuccessful && response.body() != null) {
                Result.success(response.body()!!)
            } else {
                Result.failure(Exception("Complete failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMeal(mealId: String): Result<Unit> {
        return try {
            val response = api.deleteMeal(ApiConfig.SYSTEM_ID, mealId)
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Delete failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}