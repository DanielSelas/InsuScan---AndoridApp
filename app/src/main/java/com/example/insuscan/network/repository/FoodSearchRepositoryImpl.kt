package com.example.insuscan.network.repository

import com.example.insuscan.manualentry.FoodSearchResult
import com.example.insuscan.network.RetrofitClient
import com.example.insuscan.network.repository.base.BaseRepository

class FoodSearchRepositoryImpl : BaseRepository(), FoodSearchRepository {

    private val api = RetrofitClient.api

    override suspend fun searchFood(query: String): Result<List<FoodSearchResult>> {
        return try {
            // Simple USDA search - top 3 results only
            val response = api.searchFood(query, limit = 3)

            if (response.isSuccessful && response.body() != null) {
                val results = response.body()!!
                    .filter { it.found == true && it.foodName != null }
                    .map { dto ->
                        FoodSearchResult(
                            fdcId = dto.fdcId ?: "",
                            name = dto.foodName!!,
                            carbsPer100g = dto.carbsPer100g ?: 0f,
                            servingSize = dto.servingSizeGrams,
                            servingUnit = dto.servingSize
                        )
                    }
                Result.success(results)
            } else {
                Result.failure(Exception("Search failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}