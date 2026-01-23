package com.example.insuscan.network.repository

import android.graphics.Bitmap
import com.example.insuscan.network.dto.MealDto

interface ScanRepository {
    suspend fun scanImage(
        bitmap: Bitmap,
        email: String,
        estimatedWeight: Float? = null,
        confidence: Float? = null
    ): Result<MealDto>
}