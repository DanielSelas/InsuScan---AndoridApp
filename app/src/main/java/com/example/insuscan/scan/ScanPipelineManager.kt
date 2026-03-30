package com.example.insuscan.scan

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.insuscan.analysis.model.DetectionResult
import com.example.insuscan.analysis.model.FoodRegion
import com.example.insuscan.analysis.estimation.FoodRegionAnalyzer
import com.example.insuscan.analysis.detection.PlateDetector
import com.example.insuscan.analysis.estimation.PortionEstimator
import com.example.insuscan.analysis.model.PortionResult
import com.example.insuscan.analysis.detection.ReferenceObjectDetector
import com.example.insuscan.mapping.FoodItemDtoMapper
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
        val detectedMode: ReferenceObjectDetector.DetectionMode
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

    var portionEstimator: PortionEstimator? = null
    var arCoreManager: com.example.insuscan.ar.ArCoreManager? = null
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
        val refType = ReferenceObjectHelper.fromServerValue(selectedRefType)
        val detectionMode = when (refType) {
            ReferenceObjectHelper.ReferenceObjectType.INSULIN_SYRINGE ->
                ReferenceObjectDetector.DetectionMode.STRICT
            ReferenceObjectHelper.ReferenceObjectType.SYRINGE_KNIFE ->
                ReferenceObjectDetector.DetectionMode.FLEXIBLE
            ReferenceObjectHelper.ReferenceObjectType.CARD ->
                ReferenceObjectDetector.DetectionMode.CARD
            else -> return@withContext RefCheckResult.Proceed(selectedRefType)
        }

        val fallbackResult = portionEstimator?.referenceDetector?.detectWithFallback(
            bitmap, null, detectionMode
        )

        if (fallbackResult != null
            && fallbackResult.isAlternative
            && fallbackResult.result is DetectionResult.Found
        ) {
            RefCheckResult.AlternativeFound(refType!!, fallbackResult.detectedMode!!)
        } else {
            RefCheckResult.Proceed(selectedRefType)
        }
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

            val plateResult = PlateDetector().detectPlate(bitmap)
            val plateBounds = if (plateResult.isFound) plateResult.bounds else null

            val arMeasurement = if (arCoreManager?.isReady == true && plateBounds != null) {
                arCoreManager?.measurePlate(plateBounds, bitmap.width, bitmap.height)
            } else null

            if (arMeasurement != null) {
                Log.d(TAG, "AR measurement: depth=${arMeasurement.depthCm}cm, diameter=${arMeasurement.plateDiameterCm}cm")
            } else {
                Log.d(TAG, "No AR measurement available")
            }

            val portionResult = portionEstimator?.estimatePortion(
                bitmap, refType, arMeasurement, plateResult
            )

            val successRes = portionResult as? PortionResult.Success
            val rawWeight = successRes?.estimatedWeightGrams
            val estimatedWeight = if (rawWeight != null && rawWeight > 0f) rawWeight else null
            val rawVolume = successRes?.volumeCm3
            val volumeCm3 = if (rawVolume != null && rawVolume > 0f) rawVolume else null

            val diameter = if (successRes?.referenceObjectDetected == true && successRes.plateDiameterCm > 0) {
                successRes.plateDiameterCm
            } else if (successRes?.arMeasurementUsed == true) {
                successRes.plateDiameterCm
            } else null

            // Only send depth when from REAL AR Depth API — never send heuristic guesses.
            // When AR depth is unavailable, the server's AI will estimate depth visually from the image,
            // which is more accurate than any hardcoded fallback.
            val depth = if (successRes?.arDepthIsReal == true && successRes.depthCm > 0) {
                successRes.depthCm
            } else null

            val confidence = successRes?.confidence
            val containerType = successRes?.containerType?.name

            val scanMode = ScanMode.detect(
                arReady = arCoreManager?.isReady == true,
                hasRealDepth = arCoreManager?.hasRealDepth == true,
                hasRefObject = successRes?.referenceObjectDetected == true
            )
            Log.d(TAG, "Scan mode: $scanMode (requiresSidePhoto=${scanMode.requiresSidePhoto})")

            if (sideImage == null && scanMode.requiresSidePhoto && !sidePhotoOffered) {
                sidePhotoOffered = true
                return@withContext PipelineResult.NeedSidePhoto(bitmap, imageFile, refType)
            }

            var warningMessage: String? = null
            if (isRefObjectExpectedInFrame && wasRefFoundInLivePreview) {
                if (successRes?.referenceObjectDetected == false) {
                    Log.w(TAG, "DISCREPANCY: Reference object in preview but missed in capture")
                    warningMessage = "Reference object was lost in the final photo. Try holding steadier."
                }
            }

            val portionWarning = (portionResult as? PortionResult.Success)?.warning
            if (portionWarning != null) {
                Log.w(TAG, "Portion warning: $portionWarning")
            }

            val referenceType = refType

            val pixelToCmRatio = (portionResult as? PortionResult.Success)?.let { pr ->
                if (pr.referenceObjectDetected && pr.plateDiameterCm > 0 && plateBounds != null) {
                    pr.plateDiameterCm / plateBounds.width().toFloat()
                } else null
            }

            val scanResult = scanRepository.scanImage(
                bitmap, email, estimatedWeight, volumeCm3,
                confidence, referenceType, diameter, depth,
                containerType = containerType,
                sideImageBitmap = sideImage
            )

            scanResult.onSuccess { mealDto ->
                val foodItems = mealDto.foodItems?.map { FoodItemDtoMapper.map(it) } ?: emptyList()
                val hasBboxes = foodItems.any { it.bboxXPct != null && it.bboxWPct != null }

                if (pixelToCmRatio != null && pixelToCmRatio > 0f && hasBboxes) {
                    Log.d(TAG, "Running GrabCut refinement (P1 path)")
                    val regions = FoodRegionAnalyzer.analyze(
                        bitmap, foodItems, pixelToCmRatio, plateBounds
                    )

                    if (regions.isNotEmpty()) {
                        val regionsJson = FoodRegion.toJson(regions)
                        Log.d(TAG, "Sending ${regions.size} food regions to server")

                        val refinedResult = scanRepository.scanImage(
                            bitmap, email, estimatedWeight, volumeCm3,
                            confidence, referenceType, diameter, depth,
                            containerType = containerType,
                            foodRegionsJson = regionsJson
                        )

                        refinedResult.onSuccess { refinedDto ->
                            Log.d(TAG, "P1 refined scan success")
                            return@withContext buildSuccessResult(
                                refinedDto, portionResult, capturedImagePath, warningMessage
                            )
                        }.onFailure {
                            Log.w(TAG, "P1 refinement failed, using P2/P3: ${it.message}")
                        }
                    }
                }

                return@withContext buildSuccessResult(
                    mealDto, portionResult, capturedImagePath, warningMessage
                )
            }

            scanResult.onFailure { error ->
                return@withContext PipelineResult.Failed(error)
            }

            PipelineResult.Failed(Exception("Unexpected pipeline state"))

        } catch (e: Exception) {
            Log.e(TAG, "Pipeline error: ${e.message}")
            PipelineResult.Failed(e)
        }
    }

    private fun buildSuccessResult(
        dto: MealDto,
        portionResult: PortionResult?,
        capturedImagePath: String?,
        warning: String?
    ): PipelineResult {
        if (dto.status == "FAILED" || dto.foodItems.isNullOrEmpty()) {
            return PipelineResult.NoFoodDetected
        }

        val meal = convertMealDtoToMeal(dto, portionResult)
            .copy(imagePath = capturedImagePath)
        return PipelineResult.Success(meal, warning)
    }

    private fun convertMealDtoToMeal(dto: MealDto, portionResult: PortionResult?): Meal {
        val mappedMeal = MealDtoMapper.map(dto)
        val successRes = portionResult as? PortionResult.Success
        return mappedMeal.copy(
            portionWeightGrams = dto.estimatedWeight ?: successRes?.estimatedWeightGrams,
            portionVolumeCm3 = dto.plateVolumeCm3 ?: successRes?.volumeCm3,
            plateDiameterCm = dto.plateDiameterCm ?: successRes?.plateDiameterCm,
            plateDepthCm = dto.plateDepthCm ?: successRes?.depthCm,
            analysisConfidence = dto.analysisConfidence ?: successRes?.confidence,
            referenceObjectDetected = dto.referenceDetected ?: successRes?.referenceObjectDetected
        )
    }
}