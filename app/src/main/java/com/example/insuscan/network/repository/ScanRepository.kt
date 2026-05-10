package com.example.insuscan.network.repository

import android.graphics.Bitmap

import com.example.insuscan.network.dto.MealDto

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