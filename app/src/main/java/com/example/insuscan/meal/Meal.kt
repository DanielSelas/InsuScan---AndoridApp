package com.example.insuscan.meal

data class Meal(
    val title: String,
    val carbs: Float,
    val insulinDose: Float? = null,
    val timestamp: Long = System.currentTimeMillis()
)