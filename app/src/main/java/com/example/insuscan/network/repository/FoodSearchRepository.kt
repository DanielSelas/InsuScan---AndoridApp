// TODO: Dead code — this interface is never imported or used by any active code. Verify and delete.
package com.example.insuscan.network.repository

import com.example.insuscan.manualentry.FoodSearchResult

interface FoodSearchRepository {
    suspend fun searchFood(query: String): Result<List<FoodSearchResult>>
}