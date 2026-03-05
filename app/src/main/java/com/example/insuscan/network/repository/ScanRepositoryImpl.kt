package com.example.insuscan.network.repository

import android.graphics.Bitmap
import com.example.insuscan.network.RetrofitClient
import com.example.insuscan.network.dto.MealDto
import com.example.insuscan.network.exception.ScanException
import com.example.insuscan.network.repository.base.BaseRepository
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.net.UnknownHostException
import java.net.SocketTimeoutException

class ScanRepositoryImpl : BaseRepository(), ScanRepository {

    private val api = RetrofitClient.api

    override suspend fun scanImage(
        bitmap: Bitmap,
        email: String,
        estimatedWeight: Float?,
        volumeCm3: Float?,
        confidence: Float?,
        referenceObjectType: String?,
        plateDiameterCm: Float?,
        plateDepthCm: Float?,
        // v2 pipeline fields
        containerType: String?,
        pixelToCmRatio: Float?,
        foodRegionsJson: String?,
        // Side image for depth estimation fallback
        sideImageBitmap: Bitmap?
    ): Result<MealDto> {
        return try {
            val part = createImagePart(bitmap, "file", "meal.jpg")
            val sidePart = sideImageBitmap?.let { createImagePart(it, "sideFile", "side.jpg") }

            val response = api.analyzeImage(
                part, email, estimatedWeight, volumeCm3, confidence, 
                referenceObjectType, plateDiameterCm, plateDepthCm,
                containerType, pixelToCmRatio, foodRegionsJson,
                sideFile = sidePart
            )

            when {
                response.isSuccessful && response.body() != null -> {
                    Result.success(response.body()!!)
                }
                else -> {
                    Result.failure(mapErrorResponse(response.code(), response.message()))
                }
            }
        } catch (e: UnknownHostException) {
            Result.failure(ScanException.NetworkError(e))
        } catch (e: SocketTimeoutException) {
            Result.failure(ScanException.NetworkError(e))
        } catch (e: Exception) {
            Result.failure(ScanException.Unknown(e.message ?: "Unknown error"))
        }
    }

    // Maps HTTP error codes to specific exceptions
    private fun mapErrorResponse(code: Int, message: String): ScanException {
        return when (code) {
            401 -> ScanException.Unauthorized()
            422 -> ScanException.NoFoodDetected(message)
            in 500..599 -> ScanException.ServerError(code, message)
            else -> ScanException.Unknown("Error $code: $message")
        }
    }

    private fun createImagePart(bitmap: Bitmap, partName: String, fileName: String): MultipartBody.Part {
        // Downscale to 800px max dimension for faster upload and significantly faster Gemini processing
        val maxDim = 800f
        val scale = Math.min(maxDim / bitmap.width, maxDim / bitmap.height)
        
        val scaledBitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true
            )
        } else {
            bitmap
        }

        val stream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        val byteArray = stream.toByteArray()
        val requestBody = byteArray.toRequestBody("image/jpeg".toMediaType())
        return MultipartBody.Part.createFormData(partName, fileName, requestBody)
    }
}