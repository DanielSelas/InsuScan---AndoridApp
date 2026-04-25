package com.example.insuscan.network.repository

import android.graphics.Bitmap
import com.example.insuscan.network.dto.MealDto

interface ScanRepository {
    suspend fun scanImage(
        topImage: Bitmap,
        sideImage: Bitmap,
        referenceObjectType: String,
        email: String
    ): Result<MealDto>
}