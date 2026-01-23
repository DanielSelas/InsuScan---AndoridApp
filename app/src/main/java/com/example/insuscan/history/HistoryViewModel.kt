package com.example.insuscan.history

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.example.insuscan.meal.Meal
import com.example.insuscan.network.dto.MealDto
import com.example.insuscan.network.repository.MealRepository
import com.example.insuscan.profile.UserProfileManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.example.insuscan.utils.DateTimeHelper
import com.example.insuscan.mapping.MealDtoMapper
import com.example.insuscan.network.repository.MealRepositoryImpl
import com.example.insuscan.utils.DoseFormatter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import android.util.Log

class HistoryViewModel(
    private val repository: MealRepository,
    private val context: Context
) : ViewModel() {

    private val userEmail = UserProfileManager.getUserEmail(context) ?: ""
    private val _dateFilter = MutableStateFlow<String?>(null)

    fun setDateFilter(date: String?) {
        Log.d("HistoryFilter", "ViewModel setDateFilter called with: $date")

        _dateFilter.value = date
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val historyFlow: Flow<PagingData<HistoryUiModel>> = _dateFilter.flatMapLatest { date ->
        // Re-create the Pager whenever the date changes
        Log.d("HistoryFilter", "flatMapLatest triggered with date: $date")

        Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false)
        ) {
            MealPagingSource(repository, userEmail, date)
        }.flow
    }
        .map { pagingData ->
            pagingData.map { dto ->
                val meal = mapDtoToMeal(dto)
                HistoryUiModel.MealItem(meal)
            }
        }
        .map { pagingData ->
            pagingData.insertSeparators { before, after ->
                if (after == null) return@insertSeparators null
                val afterDate = DateTimeHelper.formatDate(after.meal.timestamp)

                if (before == null) return@insertSeparators HistoryUiModel.Header(afterDate)

                val beforeDate = DateTimeHelper.formatDate(before.meal.timestamp)
                if (beforeDate != afterDate) HistoryUiModel.Header(afterDate) else null
            }
        }
        .cachedIn(viewModelScope)

    private fun mapDtoToMeal(dto: MealDto): Meal = MealDtoMapper.map(dto)
}

// Sealed class for the list items with UI logic moved here
sealed class HistoryUiModel {
    data class Header(val date: String) : HistoryUiModel()

    data class MealItem(val meal: Meal) : HistoryUiModel() {

        // Logic moved from Adapter to Model
        val formattedFoodList: String
            get() = meal.foodItems?.joinToString("\n") { item ->
                "• ${item.name} (${item.weightGrams?.toInt() ?: 0}g) - ${item.carbsGrams?.toInt() ?: 0}g carbs"
            } ?: "• ${meal.title}"

        val isGlucoseVisible: Boolean
            get() = meal.glucoseLevel != null

        val isActivityVisible: Boolean
            get() = meal.activityLevel != null && meal.activityLevel != "normal"

        val isCorrectionVisible: Boolean
            get() = meal.correctionDose != null && meal.correctionDose != 0f

        val isExerciseVisible: Boolean
            get() = meal.exerciseAdjustment != null && meal.exerciseAdjustment != 0f

        val glucoseText: String
            get() = "${meal.glucoseLevel} mg/dL"

        val activityText: String
            get() = meal.activityLevel ?: ""

        val carbDoseText: String
            get() = "Carb dose: ${DoseFormatter.formatDose(meal.carbDose)}u"

        val correctionDoseText: String
            get() = "Correction: ${DoseFormatter.formatDose(meal.correctionDose)}u"

        val exerciseDoseText: String
            get() = "Exercise adj: ${DoseFormatter.formatDose(meal.exerciseAdjustment)}u"

        val finalDoseText: String
            get() = "Final dose: ${DoseFormatter.formatDose(meal.insulinDose)}u"

        val summaryDetailsText: String
            get() = "${meal.carbs.toInt()}g carbs  |  ${DoseFormatter.formatDose(meal.insulinDose)} units"
    }
}

class HistoryViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HistoryViewModel(MealRepositoryImpl(), context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}