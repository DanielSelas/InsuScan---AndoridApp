package com.example.insuscan.network.repository

import android.graphics.Bitmap
import com.example.insuscan.network.RetrofitClient
import com.example.insuscan.network.dto.MealDto
import com.example.insuscan.network.repository.base.BaseRepository
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

class ScanRepositoryImpl : BaseRepository(), ScanRepository {

    private val api = RetrofitClient.api

    override suspend fun scanImage(
        bitmap: Bitmap,
        email: String,
        estimatedWeight: Float?,
        confidence: Float?
    ): Result<MealDto> {
        return try {
            val part = createImagePart(bitmap)
            val response = api.analyzeImage(part, email, estimatedWeight, confidence)

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (isValidMealResponse(body)) {
                    Result.success(body)
                } else {
                    Result.failure(Exception("Scan failed: server returned unexpected response"))
                }
            } else {
                Result.failure(Exception("Scan failed: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun createImagePart(bitmap: Bitmap): MultipartBody.Part {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        val byteArray = stream.toByteArray()
        val requestBody = byteArray.toRequestBody("image/jpeg".toMediaType())
        return MultipartBody.Part.createFormData("file", "meal.jpg", requestBody)
    }

    private fun isValidMealResponse(body: MealDto): Boolean {
        val looksLikeMeal = body.mealId != null ||
                body.foodItems != null ||
                body.totalCarbs != null ||
                body.recommendedDose != null ||
                body.actualDose != null

        val isFailed = body.status?.equals("FAILED", ignoreCase = true) == true &&
                body.foodItems.isNullOrEmpty() &&
                (body.totalCarbs ?: 0f) <= 0f

        return looksLikeMeal && !isFailed
    }
}