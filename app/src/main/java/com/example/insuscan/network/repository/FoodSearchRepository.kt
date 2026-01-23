package com.example.insuscan.network.repository

import com.example.insuscan.manualentry.FoodSearchResult

interface FoodSearchRepository {
    suspend fun searchFood(query: String): Result<List<FoodSearchResult>>
}