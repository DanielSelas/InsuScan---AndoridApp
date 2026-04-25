package com.example.insuscan.scan

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.insuscan.camera.exception.CameraException
import com.example.insuscan.mapping.MealDtoMapper
import com.example.insuscan.meal.Meal
import com.example.insuscan.network.dto.MealDto
import com.example.insuscan.network.repository.ScanRepository
import com.example.insuscan.network.repository.ScanRepositoryImpl
import com.example.insuscan.profile.UserProfileManager
import com.example.insuscan.utils.ReferenceObjectHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

sealed class RefCheckResult {
    data class Proceed(val refType: String?) : RefCheckResult()
    data class AlternativeFound(
        val selectedType: ReferenceObjectHelper.ReferenceObjectType,
        val detectedMode: Any
    ) : RefCheckResult()
}

sealed class PipelineResult {
    data class Success(val meal: Meal, val warning: String? = null) : PipelineResult()
    data class NeedSidePhoto(
        val bitmap: Bitmap,
        val imageFile: File,
        val refType: String?
    ) : PipelineResult()
    object NoFoodDetected : PipelineResult()
    data class Failed(val error: Throwable) : PipelineResult()
}

class ScanPipelineManager(private val context: Context) {

    companion object {
        private const val TAG = "ScanPipeline"
    }

    var isRefObjectExpectedInFrame: Boolean = false
    var wasRefFoundInLivePreview: Boolean = false

    private val scanRepository: ScanRepository = ScanRepositoryImpl()
    private var sidePhotoOffered = false

    fun resetState() {
        sidePhotoOffered = false
    }

    fun skipSidePhoto() {
        sidePhotoOffered = true
    }

    suspend fun checkReferenceObject(
        bitmap: Bitmap,
        selectedRefType: String?
    ): RefCheckResult = withContext(Dispatchers.IO) {
        // Client-side detection is disabled in V2 (delegated to server).
        // Just proceed with the selected type.
        RefCheckResult.Proceed(selectedRefType)
    }

    suspend fun runAnalysis(
        bitmap: Bitmap,
        imageFile: File,
        refType: String?,
        capturedImagePath: String?,
        sideImage: Bitmap? = null
    ): PipelineResult = withContext(Dispatchers.IO) {
        try {
            val email = UserProfileManager.getUserEmail(context) ?: "test@example.com"

            // Milestone 7 V2 Pipeline ALWAYS requires a side photo unless user explicitly skips
            if (sideImage == null && !sidePhotoOffered) {
                sidePhotoOffered = true
                Log.d(TAG, "Requesting side photo for V2 pipeline")
                return@withContext PipelineResult.NeedSidePhoto(bitmap, imageFile, refType)
            }

            val finalRefType = refType ?: "UNKNOWN"

            Log.d(TAG, "========== SCAN PAYLOAD (V2) ==========")
            Log.d(TAG, "referenceType    : $finalRefType")
            Log.d(TAG, "email            : $email")
            Log.d(TAG, "sideImage        : ${sideImage != null}")
            Log.d(TAG, "=======================================")

            // If user skipped side photo, we must use the top image as the side image to not break the server
            val actualSideImage = sideImage ?: bitmap

            val scanResult = scanRepository.scanImage(
                topImage = bitmap,
                sideImage = actualSideImage,
                referenceObjectType = finalRefType,
                email = email
            )

            scanResult.onSuccess { mealDto ->
                return@withContext buildSuccessResult(mealDto, capturedImagePath)
            }

            scanResult.onFailure { error ->
                Log.e(TAG, "Server scan failed: ${error.message}")
                return@withContext PipelineResult.Failed(error)
            }

            PipelineResult.Failed(CameraException.PortionEstimationFailed("Unexpected pipeline state"))

        } catch (e: CameraException) {
            Log.e(TAG, "Camera error: ${e.message}")
            PipelineResult.Failed(e)
        } catch (e: Exception) {
            Log.e(TAG, "Pipeline error: ${e.message}")
            PipelineResult.Failed(e)
        }
    }

    private fun buildSuccessResult(
        dto: MealDto,
        capturedImagePath: String?
    ): PipelineResult.Success {
        var meal = MealDtoMapper.map(dto)
        
        if (capturedImagePath != null && meal.imagePath?.startsWith("uploaded://") == true) {
            meal = meal.copy(imagePath = "file://$capturedImagePath")
        }

        // Propagate review warnings from the server (V2 PipelineWarnings)
        val warning = if (!dto.reviewWarnings.isNullOrEmpty()) {
            dto.reviewWarnings.joinToString("\n")
        } else {
            null
        }

        return PipelineResult.Success(meal, warning)
    }
}