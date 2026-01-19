package com.example.insuscan.network.repository

import android.graphics.Bitmap
import com.example.insuscan.network.RetrofitClient
import com.example.insuscan.network.dto.MealDto
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

// Handles image scanning API calls
class ScanRepository {

    private val api = RetrofitClient.api

    // Send image to server for analysis
    suspend fun scanImage(
        bitmap: Bitmap,
        email: String,
        estimatedWeight: Float? = null,
        confidence: Float? = null
    ): Result<MealDto> {
        return try {
            // Convert bitmap to byte array
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            val byteArray = stream.toByteArray()

            // Create multipart body
            val requestBody = byteArray.toRequestBody("image/jpeg".toMediaType())
            val part = MultipartBody.Part.createFormData("file", "meal.jpg", requestBody)

            val response = api.analyzeImage(part, email, estimatedWeight, confidence)

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!

                // Defensive: if server returned a different JSON shape (e.g. error/vision-only),
                // Gson may still deserialize into MealDto with all nulls -> leads to "0g carbs".
                val looksLikeMeal =
                    body.mealId != null ||
                    body.foodItems != null ||
                    body.totalCarbs != null ||
                    body.recommendedDose != null ||
                    body.actualDose != null

                val isFailed =
                    body.status?.equals("FAILED", ignoreCase = true) == true &&
                            (body.foodItems.isNullOrEmpty()) &&
                            ((body.totalCarbs ?: 0f) <= 0f)

                if (!looksLikeMeal || isFailed) {
                    Result.failure(Exception("Scan failed: server returned unexpected response"))
                } else {
                    Result.success(body)
                }
            } else {
                Result.failure(Exception("Scan failed: ${response.code()} - ${response.message()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}