package com.example.insuscan.network.dto

// Request/Response DTOs for POST /chat/parse
data class ChatParseRequestDto(
    val text: String,
    val state: String?
)

data class ChatParseResponseDto(
    val action: String,        // "add_food", "set_glucose", "set_activity", "confirm", "unknown"
    val items: List<ChatFoodEntryDto>?,
    val glucose: Int?,
    val activity: String?,
    val icr: Double?,
    val isf: Double?,
    val targetGlucose: Int?,
    val message: String?
)

data class ChatFoodEntryDto(
    val name: String,
    val quantity: Int?,
    val estimatedCarbsGrams: Float?
)
