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
        topImage: Bitmap,
        sideImage: Bitmap,
        referenceObjectType: String,
        email: String
    ): Result<MealDto> {
        return try {
            val topPart = createImagePart(topImage, "topFile", "meal.jpg")
            val sidePart = createImagePart(sideImage, "sideFile", "side.jpg")

            val response = api.analyzeImage(
                topPart, sidePart, referenceObjectType, email
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
        // Downscale to 1200px max dimension for faster upload and significantly faster Gemini processing
        val maxDim = 1200f
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