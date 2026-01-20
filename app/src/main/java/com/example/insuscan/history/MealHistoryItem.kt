package com.example.insuscan.history

import com.example.insuscan.meal.FoodItem

data class MealHistoryItem(
    val title: String,
    val carbsText: String,
    val insulinText: String,
    val timeText: String,

    // Status indicators
    val wasSickMode: Boolean = false,
    val wasStressMode: Boolean = false,
    val glucoseLevel: String? = null,

    // NEW: Expandable details
    val foodItems: List<FoodItem>? = null,
    val activityLevel: String? = null,

    // Calculation breakdown
    val carbDose: Float? = null,
    val correctionDose: Float? = null,
    val exerciseAdjustment: Float? = null,
    val totalCarbs: Float? = null
)