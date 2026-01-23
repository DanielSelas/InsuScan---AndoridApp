package com.example.insuscan.history

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.insuscan.network.dto.MealDto
import com.example.insuscan.network.repository.MealRepository

class MealPagingSource(
    private val repository: MealRepository,
    private val userEmail: String,
    private val filterDate: String? // Null = Show all, String "YYYY-MM-DD" = Filter
) : PagingSource<Int, MealDto>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, MealDto> {
        val pageNumber = params.key ?: 0
        val pageSize = params.loadSize

        return try {
            // Determine which repository method to call based on filterDate
            val result = if (filterDate != null) {
                repository.getMealsByDate(userEmail, filterDate, pageNumber, pageSize)
            } else {
                repository.getUserMeals(userEmail, pageNumber, pageSize)
            }

            if (result.isSuccess) {
                val meals = result.getOrNull() ?: emptyList()
                val nextKey = if (meals.isEmpty() || meals.size < pageSize) null else pageNumber + 1
                val prevKey = if (pageNumber == 0) null else pageNumber - 1

                LoadResult.Page(
                    data = meals,
                    prevKey = prevKey,
                    nextKey = nextKey
                )
            } else {
                LoadResult.Error(result.exceptionOrNull() ?: Exception("Unknown error"))
            }
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, MealDto>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(anchorPosition)?.nextKey?.minus(1)
        }
    }
}