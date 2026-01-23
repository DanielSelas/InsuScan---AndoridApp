package com.example.insuscan.network.repository

import android.util.Log
import com.example.insuscan.network.ApiConfig
import com.example.insuscan.network.RetrofitClient
import com.example.insuscan.network.dto.CreateMealRequest
import com.example.insuscan.network.dto.FoodItemDto
import com.example.insuscan.network.dto.MealDto
import com.example.insuscan.network.repository.base.BaseRepository

class MealRepositoryImpl : BaseRepository(), MealRepository {

    private val api = RetrofitClient.api

    override suspend fun createMeal(userEmail: String, imageUrl: String): Result<MealDto> = safeApiCall {
        val request = CreateMealRequest(ApiConfig.SYSTEM_ID, userEmail, imageUrl)
        api.createMeal(request)
    }

    override suspend fun getMeal(mealId: String): Result<MealDto> = safeApiCall {
        api.getMeal(ApiConfig.SYSTEM_ID, mealId)
    }

    override suspend fun getUserMeals(email: String, page: Int, size: Int): Result<List<MealDto>> = safeApiCall {
        api.getUserMeals(ApiConfig.SYSTEM_ID, email, page, size)
    }

    override suspend fun getRecentMeals(email: String, count: Int): Result<List<MealDto>> = safeApiCall {
        api.getRecentMeals(ApiConfig.SYSTEM_ID, email, count)
    }

    override suspend fun updateFoodItems(mealId: String, items: List<FoodItemDto>): Result<MealDto> = safeApiCall {
        api.updateFoodItems(ApiConfig.SYSTEM_ID, mealId, items)
    }

    override suspend fun confirmMeal(mealId: String, actualDose: Float?): Result<MealDto> = safeApiCall {
        api.confirmMeal(ApiConfig.SYSTEM_ID, mealId, actualDose)
    }

    override suspend fun completeMeal(mealId: String): Result<MealDto> = safeApiCall {
        api.completeMeal(ApiConfig.SYSTEM_ID, mealId)
    }

    override suspend fun deleteMeal(mealId: String): Result<Unit> = safeApiCallUnit {
        api.deleteMeal(ApiConfig.SYSTEM_ID, mealId)
    }
    override suspend fun getMealsByDate(
        email: String,
        date: String,
        page: Int,
        size: Int
    ): Result<List<MealDto>> {
        Log.d("HistoryFilter", "API Request - email: $email, from: $date, to: $date, page: $page, size: $size")

        val res = api.getMealsByDate(
            systemId = ApiConfig.SYSTEM_ID,
            email = email,
            fromDate = date,
            toDate = date,
            page = page,
            size = size
        )
        Log.d("HistoryFilter", "API Response - code: ${res.code()}, body size: ${res.body()?.size ?: "null"}")

        return if (res.isSuccessful) Result.success(res.body().orEmpty())
        else Result.failure(Exception("HTTP ${res.code()}"))
    }
}