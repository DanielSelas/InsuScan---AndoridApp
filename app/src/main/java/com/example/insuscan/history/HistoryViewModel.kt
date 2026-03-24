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
import com.example.insuscan.history.models.HistoryUiModel
import com.example.insuscan.meal.Meal
import com.example.insuscan.network.dto.MealDto
import com.example.insuscan.network.repository.MealRepository
import com.example.insuscan.profile.UserProfileManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.example.insuscan.utils.DateTimeHelper
import com.example.insuscan.mapping.MealDtoMapper
import com.example.insuscan.network.repository.MealRepositoryImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import android.util.Log

class HistoryViewModel(
    private val repository: MealRepository,
    private val context: Context
) : ViewModel() {

    private val userEmail = com.example.insuscan.auth.AuthManager.getUserEmail() ?: UserProfileManager.getUserEmail(context) ?: ""
    private val _dateFilter = MutableStateFlow<String?>(null)
    
    // Separate flow for the "Top Card"
    private val _latestMeal = MutableStateFlow<Meal?>(null)
    val latestMeal: kotlinx.coroutines.flow.StateFlow<Meal?> = _latestMeal

    init {
        refreshLatestMeal()
    }

    fun refreshLatestMeal() {
        viewModelScope.launch {
            if (userEmail.isNotEmpty()) {
                val result = repository.getLatestMeal(userEmail)
                result.onSuccess { dto ->
                     if (dto != null) {
                         _latestMeal.value = mapDtoToMeal(dto)
                     }
                }
            }
        }
    }

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
                val afterDate = DateTimeHelper.formatHeaderDate(after.meal.timestamp)

                if (before == null) return@insertSeparators HistoryUiModel.Header(afterDate)

                val beforeDate = DateTimeHelper.formatHeaderDate(before.meal.timestamp)
                if (beforeDate != afterDate) HistoryUiModel.Header(afterDate) else null
            }
        }
        .cachedIn(viewModelScope)

    private fun mapDtoToMeal(dto: MealDto): Meal = MealDtoMapper.map(dto)
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