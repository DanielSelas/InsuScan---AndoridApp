package com.example.insuscan.history

data class MealHistoryItem(
    val title: String,
    val carbsText: String,
    val insulinText: String,
    val timeText: String,
    val wasSickMode: Boolean = false,
    val wasStressMode: Boolean = false,
    val glucoseLevel: String? = null
)