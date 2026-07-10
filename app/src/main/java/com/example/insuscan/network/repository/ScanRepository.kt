package com.example.insuscan.network.repository

import android.graphics.Bitmap

import com.example.insuscan.network.dto.MealDto

/**
 * Sends a two-photo meal scan (top and side) to the backend for food and portion analysis.
 */
interface ScanRepository {
    suspend fun scanImage(
        topImage: Bitmap,
        sideImage: Bitmap,
        referenceObjectType: String,
        email: String,
        arcoreDataJson: String? = null,
        topImageWidth: Int? = null,
        topImageHeight: Int? = null,
        sideImageWidth: Int? = null,
        sideImageHeight: Int? = null
    ): Result<MealDto>
}