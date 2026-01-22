package com.example.insuscan.history

import android.content.Context
import android.text.format.DateUtils
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.example.insuscan.meal.FoodItem
import com.example.insuscan.meal.Meal
import com.example.insuscan.network.dto.MealDto
import com.example.insuscan.network.repository.MealRepository
import com.example.insuscan.profile.UserProfileManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryViewModel(
    private val repository: MealRepository,
    private val context: Context
) : ViewModel() {

    private val userEmail = UserProfileManager.getUserEmail(context) ?: ""

    val historyFlow: Flow<PagingData<HistoryUiModel>> = Pager(
        config = PagingConfig(pageSize = 20, enablePlaceholders = false)
    ) {
        MealPagingSource(repository, userEmail)
    }.flow
        .map { pagingData ->
            // Convert DTO to Domain/UI Model
            pagingData.map { dto ->
                HistoryUiModel.MealItem(mapDtoToMeal(dto))
            }
        }
        .map { pagingData ->
            // Insert Date Headers
            pagingData.insertSeparators { before, after ->
                if (after == null) {
                    // End of list
                    return@insertSeparators null
                }

                val afterDate = formatDate(after.meal.timestamp)

                if (before == null) {
                    // Beginning of list
                    return@insertSeparators HistoryUiModel.Header(afterDate)
                }

                val beforeDate = formatDate(before.meal.timestamp)

                if (beforeDate != afterDate) {
                    HistoryUiModel.Header(afterDate)
                } else {
                    null
                }
            }
        }
        .cachedIn(viewModelScope)

    private fun formatDate(timestamp: Long): String {
        return if (DateUtils.isToday(timestamp)) {
            "Today"
        } else if (DateUtils.isToday(timestamp + DateUtils.DAY_IN_MILLIS)) {
            "Yesterday"
        } else {
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestamp))
        }
    }

    // Helper to convert DTO to existing Meal object to keep Adapter logic simpler
    private fun mapDtoToMeal(dto: MealDto): Meal {
        val calc = dto.insulinCalculation

        return Meal(
            title = "Meal Analysis",
            carbs = dto.totalCarbs ?: 0f,
            insulinDose = dto.actualDose ?: dto.recommendedDose,
            timestamp = parseTimestamp(dto.scannedTimestamp),

            // use serverId instead of id
            serverId = dto.mealId?.id,

            foodItems = dto.foodItems?.map {
                FoodItem(
                    name = it.name,
                    nameHebrew = it.nameHebrew,
                    carbsGrams = it.carbsGrams,
                    weightGrams = it.estimatedWeightGrams,
                    confidence = it.confidence
                )
            },

            glucoseLevel = calc?.currentGlucose,
            correctionDose = calc?.correctionDose,
            carbDose = calc?.carbDose,

            wasSickMode = dto.wasSickMode == true,
            wasStressMode = dto.wasStressMode == true
        )
    }
    private fun parseTimestamp(ts: String?): Long {
        if (ts == null) return System.currentTimeMillis()
        ts.toLongOrNull()?.let { return it }

        try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            return format.parse(ts)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            return System.currentTimeMillis()
        }
    }
}



// Sealed class for the list items
sealed class HistoryUiModel {
    data class MealItem(val meal: Meal) : HistoryUiModel()
    data class Header(val date: String) : HistoryUiModel()
}

// Factory to pass Context/Repo to ViewModel
class HistoryViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HistoryViewModel(MealRepository(), context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}