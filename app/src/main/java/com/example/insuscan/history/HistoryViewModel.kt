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

// Sealed class for the list items with UI logic moved here
sealed class HistoryUiModel {
    data class Header(val date: String) : HistoryUiModel()

    data class MealItem(val meal: Meal) : HistoryUiModel() {

        // title: "Banana, Lemon • 81g" or "Banana, Lemon +2 • 120g"
        val displayTitle: String
            get() {
                val items = meal.foodItems
                val carbsText = "${meal.carbs.toInt()}g"

                if (items.isNullOrEmpty()) {
                    return "${meal.title} • $carbsText"
                }

                val names = items.map { it.name }
                val foodsText = when {
                    names.size <= 3 -> names.joinToString(", ")
                    else -> "${names.take(2).joinToString(", ")} +${names.size - 2}"
                }

                return "$foodsText • $carbsText"
            }

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
            get() {
                val level = meal.glucoseLevel ?: return ""
                val units = meal.glucoseUnits ?: "mg/dL"
                return "$level $units"
            }

        val activityText: String
            get() = when (meal.activityLevel) {
                "light" -> "Light exercise"
                "intense" -> "Intense exercise"
                else -> meal.activityLevel ?: ""
            }

        val carbDoseText: String
            get() = "Carb dose: ${DoseFormatter.formatDose(meal.carbDose)}u"

        val correctionDoseText: String
            get() = "Correction: ${DoseFormatter.formatDose(meal.correctionDose)}u"

        val exerciseDoseText: String
            get() = "Exercise adj: ${DoseFormatter.formatDose(meal.exerciseAdjustment)}u"

        val finalDoseText: String
            get() {
                val recommended = meal.recommendedDose
                val actual = meal.insulinDose

                return if (recommended != null && actual != null && recommended != actual) {
                    // show both if different
                    "Recommended: ${DoseFormatter.formatDose(recommended)}u → Actual: ${DoseFormatter.formatDose(actual)}u"
                } else {
                    // show only actual
                    "Final dose: ${DoseFormatter.formatDose(actual)}u"
                }
            }

        val summaryDetailsText: String
            get() = "${meal.carbs.toInt()}g carbs  |  ${DoseFormatter.formatDose(meal.insulinDose)} units"

        // added: confidence info
        val hasConfidenceInfo: Boolean
            get() = meal.analysisConfidence != null || meal.referenceObjectDetected == true

        val confidenceText: String
            get() = buildString {
                meal.analysisConfidence?.let {
                    val percentage = (it * 100).toInt()
                    append("Confidence: $percentage%")
                }
                if (meal.analysisConfidence != null && meal.referenceObjectDetected == true) {
                    append("  •  ")
                }
                if (meal.referenceObjectDetected == true) {
                    append("Reference detected ✓")
                }
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
}