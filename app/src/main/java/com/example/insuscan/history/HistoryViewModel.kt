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
                    return meal.title
                }

                val names = items.map { it.name }
                return when {
                    names.size <= 3 -> names.joinToString(", ")
                    else -> "${names.take(2).joinToString(", ")} +${names.size - 2}"
                }
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

        // Receipt Style Data
        val carbDoseLabel: String
            get() {
                // Show formula if we have the data
                return if (meal.carbDose != null) {
                    "Insulin for Food (${meal.carbs.toInt()}g carbs)"
                } else {
                    "Insulin for Food"
                }
            }

        val carbDoseValue: String
            get() {
                return if (meal.carbDose != null) {
                    DoseFormatter.formatDoseWithUnit(meal.carbDose)
                } else {
                    "Not calculated"
                }
            }
        
        val carbDoseExplanation: String
            get() = if (meal.carbDose == null) {
                "Profile data was missing when this meal was saved"
            } else {
                ""
            }

        val correctionDoseValue: String
            get() {
                val dose = meal.correctionDose ?: 0f
                return if (dose > 0) "+${DoseFormatter.formatDoseWithUnit(dose)}"
                       else DoseFormatter.formatDoseWithUnit(dose)
            }

        val exerciseDoseValue: String
            get() = DoseFormatter.formatDoseWithUnit(meal.exerciseAdjustment)

        val sickDoseValue: String
            get() = "+${DoseFormatter.formatDoseWithUnit(meal.sickAdjustment)}"
        
        val stressDoseValue: String
            get() = "+${DoseFormatter.formatDoseWithUnit(meal.stressAdjustment)}"

        val totalDoseValue: String
            get() = DoseFormatter.formatDoseWithUnit(meal.insulinDose ?: meal.recommendedDose)

        // Formatted food list for the new view
        // "Rice (150g) ... 35g carbs"
        val receiptFoodList: String
            get() = meal.foodItems?.joinToString("\n") { item ->
                val name = item.name
                val weight = item.weightGrams?.toInt() ?: 0
                val carbs = item.carbsGrams?.toInt() ?: 0
                "• $name ($weight" + "g) ... $carbs" + "g carbs"
            } ?: "• ${meal.title}"
        
        val isSickVisible: Boolean
            get() = meal.sickAdjustment != null && meal.sickAdjustment != 0f

        val isStressVisible: Boolean
            get() = meal.stressAdjustment != null && meal.stressAdjustment != 0f
            
        val hasProfileError: Boolean
             get() = !meal.profileComplete && (meal.insulinDose == null || meal.insulinDose == 0f)

        // -- extra info for expanded view --

        // full date + time: "Sun, 2 Feb 2025 • 14:30"
        val fullDateTime: String
            get() {
                val sdf = java.text.SimpleDateFormat("EEE, d MMM yyyy • HH:mm", java.util.Locale.getDefault())
                return sdf.format(java.util.Date(meal.timestamp))
            }

        // total weight of all food items combined
        val totalWeightText: String?
            get() {
                val weight = meal.portionWeightGrams
                    ?: meal.foodItems?.mapNotNull { it.weightGrams }?.sum()
                return if (weight != null && weight > 0f) "${weight.toInt()}g" else null
            }

        // how many items were detected
        val foodItemCountText: String
            get() {
                val count = meal.foodItems?.size ?: 0
                return "$count item${if (count != 1) "s" else ""} detected"
            }

        // confidence display: "85%" or null if unavailable
        val confidenceText: String?
            get() {
                val conf = meal.analysisConfidence ?: return null
                return "${(conf * 100).toInt()}%"
            }

        // reference object (syringe) found or not
        val referenceDetectedText: String?
            get() = when (meal.referenceObjectDetected) {
                true -> "✓ Reference detected"
                false -> "✗ No reference object"
                null -> null
            }

        // show recommended vs actual if they differ
        val isActualDoseDifferent: Boolean
            get() {
                val rec = meal.recommendedDose ?: return false
                val actual = meal.insulinDose ?: return false
                return kotlin.math.abs(rec - actual) > 0.05f
            }

        val recommendedDoseText: String
            get() = DoseFormatter.formatDoseWithUnit(meal.recommendedDose)

        val actualDoseText: String
            get() = DoseFormatter.formatDoseWithUnit(meal.insulinDose)

        // show insulin message from server if available
        val hasInsulinMessage: Boolean
            get() = !meal.insulinMessage.isNullOrBlank()

        val insulinMessageText: String
            get() = meal.insulinMessage ?: ""


        // Subtitle showing context at a glance
        val displaySubtitle: String
            get() = buildList {
                // Glucose status
                if (meal.glucoseLevel != null) {
                    val level = meal.glucoseLevel!!
                    val status = when {
                        level < 70 -> "Low glucose"
                        level > 180 -> "High glucose"
                        else -> "Good glucose"
                    }
                    add(status)
                }

                // Activity level (only if not normal)
                if (meal.activityLevel != null && meal.activityLevel != "normal") {
                    val activity = when (meal.activityLevel?.lowercase()) {
                        "sedentary" -> "Resting"
                        "light" -> "Light activity"
                        "moderate" -> "Active"
                        "vigorous" -> "High intensity"
                        else -> meal.activityLevel
                    }
                    add(activity)
                }

                // Special modes
                if (meal.wasSickMode) add("Sick day")
                if (meal.wasStressMode) add("Stressed")

                // If nothing to show, add formatted date/time as fallback
                if (isEmpty()) {
                    val sdf = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault())
                    add(sdf.format(java.util.Date(meal.timestamp)))
                }

            }.joinToString(" • ")

        // Enhanced glucose text with visual indicator
        val glucoseText: String
            get() {
                val level = meal.glucoseLevel ?: return ""
                val units = meal.glucoseUnits ?: "mg/dL"
                val emoji = when {
                    level < 70 -> "\u2705"   // ✅ low (actually should be warning)
                    level > 180 -> "\u26A0"  // ⚠️ high
                    else -> "\u2705"         // ✅ normal
                }
                return "$emoji $level $units"
            }

        // Enhanced activity text with emoji
        val activityText: String
            get() = when (meal.activityLevel?.lowercase()) {
                "sedentary" -> "\uD83E\uDE91 Resting"      // 🪑
                "light" -> "\uD83D\uDEB6 Light activity"    // 🚶
                "moderate" -> "\uD83C\uDFC3 Active"         // 🏃
                "vigorous" -> "\uD83D\uDCAA High intensity" // 💪
                else -> meal.activityLevel ?: ""
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