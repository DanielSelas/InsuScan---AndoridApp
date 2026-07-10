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

/**
 * Uploads the top and side meal photos to the scan endpoint and returns the parsed [MealDto].
 *
 * Both images are downscaled before upload, and HTTP or network failures are mapped
 * to typed [ScanException] values for the caller.
 */
class ScanRepositoryImpl : BaseRepository(), ScanRepository {

    private val api = RetrofitClient.api

    override suspend fun scanImage(
        topImage: Bitmap,
        sideImage: Bitmap,
        referenceObjectType: String,
        email: String,
        arcoreDataJson: String?,
        topImageWidth: Int?,
        topImageHeight: Int?,
        sideImageWidth: Int?,
        sideImageHeight: Int?
    ): Result<MealDto> {
        return try {
            val scaledTopDims  = getScaledDimensions(topImage)
            val scaledSideDims = getScaledDimensions(sideImage)

            val topPart  = createImagePart(topImage,  "topFile",  "meal.jpg")
            val sidePart = createImagePart(sideImage, "sideFile", "side.jpg")

            val response = api.analyzeImage(

                topPart, sidePart, referenceObjectType, email, arcoreDataJson,
                scaledTopDims.first, scaledTopDims.second,
                scaledSideDims.first, scaledSideDims.second

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

    private fun getScaledDimensions(bitmap: Bitmap): Pair<Int, Int> {
        val scale = computeScale(bitmap)
        return if (scale < 1f) {
            Pair((bitmap.width * scale).toInt(), (bitmap.height * scale).toInt())
        } else {
            Pair(bitmap.width, bitmap.height)
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
        val scale = computeScale(bitmap)
        
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

    private fun computeScale(bitmap: Bitmap): Float =
        Math.min(MAX_IMAGE_DIMENSION / bitmap.width, MAX_IMAGE_DIMENSION / bitmap.height)

    private companion object {
        // 1200px cap keeps upload small and Gemini processing fast
        const val MAX_IMAGE_DIMENSION = 1200f
    }
}