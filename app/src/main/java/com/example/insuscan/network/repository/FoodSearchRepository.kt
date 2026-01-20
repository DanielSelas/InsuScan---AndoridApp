package com.example.insuscan.network.repository

import com.example.insuscan.manualentry.FoodSearchResult
import com.example.insuscan.network.RetrofitClient

class FoodSearchRepository {

    private val api = RetrofitClient.api

    suspend fun searchFood(query: String): Result<List<FoodSearchResult>> {
        return try {
            val response = api.searchFood(query, limit = 15)

            if (response.isSuccessful) {
                val results = response.body()
                    ?.filter { it.found == true && it.foodName != null }
                    ?.map { dto ->
                        FoodSearchResult(
                            fdcId = dto.fdcId ?: "",
                            name = dto.foodName!!,
                            carbsPer100g = dto.carbsPer100g ?: 0f,
                            servingSize = dto.servingSizeGrams,
                            servingUnit = dto.servingSize
                        )
                    } ?: emptyList()

                Result.success(results)
            } else {
                Result.failure(Exception("Search failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}