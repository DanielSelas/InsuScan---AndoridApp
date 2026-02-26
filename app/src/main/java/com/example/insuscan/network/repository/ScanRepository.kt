package com.example.insuscan.network.repository

import android.graphics.Bitmap
import com.example.insuscan.network.dto.MealDto

interface ScanRepository {
    suspend fun scanImage(
        bitmap: Bitmap,
        email: String,
        estimatedWeight: Float? = null,
        volumeCm3: Float? = null,
        confidence: Float? = null,
        referenceObjectType: String? = null,
        plateDiameterCm: Float? = null,
        plateDepthCm: Float? = null,
        // v2 pipeline fields
        containerType: String? = null,
        pixelToCmRatio: Float? = null,
        foodRegionsJson: String? = null
    ): Result<MealDto>
}