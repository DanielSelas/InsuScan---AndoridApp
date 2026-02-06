package com.example.insuscan.network.dto

data class AiSearchRequestDto(
    val query: String,
    val userLanguage: String = "en",
    val limit: Int = 10
)
