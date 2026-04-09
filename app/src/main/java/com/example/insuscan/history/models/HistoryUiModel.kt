package com.example.insuscan.history.models

import com.example.insuscan.meal.Meal
import com.example.insuscan.utils.DoseFormatter

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

        // reference object type selected by user (raw server value)
        val referenceObjectTypeRaw: String?
            get() {
                val type = meal.referenceObjectType
                return if (!type.isNullOrBlank() && type != "NONE") type else null
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

        val planDisplayText: String
            get() = meal.savedPlanName?.takeIf { it.isNotBlank() } ?: "Default"

        val isPlanVisible: Boolean
            get() = meal.carbDose != null || meal.savedPlanName != null


        // Subtitle showing context at a glance
        val displaySubtitle: String
            get() = buildList {
                // Show the insulin plan used
                val planName = meal.savedPlanName
                if (!planName.isNullOrBlank()) {
                    add(planName)
                }

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
                    level < 70 -> "✅"   // ✅ low (actually should be warning)
                    level > 180 -> "⚠️"  // ⚠️ high
                    else -> "✅"         // ✅ normal
                }
                return "$emoji $level $units"
            }

        // Enhanced activity text with emoji
        val activityText: String
            get() = when (meal.activityLevel?.lowercase()) {
                "sedentary" -> "🪑 Resting"      // 🪑
                "light" -> "🚶 Light activity"    // 🚶
                "moderate" -> "🏃 Active"         // 🏃
                "vigorous" -> "💪 High intensity" // 💪
                else -> meal.activityLevel ?: ""
            }
    }
}
