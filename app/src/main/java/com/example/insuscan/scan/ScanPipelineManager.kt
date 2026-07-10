package com.example.insuscan.scan

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.example.insuscan.analysis.detection.PlateDetector
import com.example.insuscan.ar.ArCoreManager
import com.example.insuscan.camera.exception.CameraException
import com.example.insuscan.mapping.MealDtoMapper
import com.example.insuscan.meal.Meal
import com.example.insuscan.network.dto.MealDto
import com.example.insuscan.network.repository.ScanRepository
import com.example.insuscan.network.repository.ScanRepositoryImpl
import com.example.insuscan.profile.UserProfileManager
import com.example.insuscan.utils.ReferenceObjectHelper
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Outcome of the reference-object pre-check step.
 * Client-side detection is delegated to the server in V2; this sealed class
 * is kept for extensibility.
 */
sealed class RefCheckResult {
    data class Proceed(val refType: String?) : RefCheckResult()
    data class AlternativeFound(
        val selectedType: ReferenceObjectHelper.ReferenceObjectType,
        val detectedMode: Any
    ) : RefCheckResult()
}

/**
 * Result of a complete scan pipeline run.
 */
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

/**
 * Orchestrates the two-phase scan pipeline: optional ARCore data snapshot,
 * side-photo gate, server upload, and mapping the response to a [PipelineResult].
 *
 * A side photo is always requested on the first call to [runAnalysis] unless
 * the user explicitly skips (via [skipSidePhoto]) or a side image is provided.
 */
class ScanPipelineManager(private val context: Context) {

    companion object {
        private const val TAG = "ScanPipeline"
    }

    private var collectedArcoreDataJson: String? = null

    /** Set to `true` when the user's selected reference object should be visible in the frame. */
    var isRefObjectExpectedInFrame: Boolean = false

    /** Set to `true` when the reference object was detected during the live camera preview. */
    var wasRefFoundInLivePreview: Boolean = false

    var arCoreManager: ArCoreManager? = null

    private val plateDetector = PlateDetector()
    private val scanRepository: ScanRepository = ScanRepositoryImpl()
    private var sidePhotoOffered = false

    fun resetState() {
        sidePhotoOffered = false
    }

    /** Marks the side-photo step as already handled, bypassing the gate in [runAnalysis]. */
    fun skipSidePhoto() {
        sidePhotoOffered = true
    }

    /**
     * Pre-check step — currently a no-op pass-through (server handles reference detection in V2).
     */
    suspend fun checkReferenceObject(
        bitmap: Bitmap,
        selectedRefType: String?
    ): RefCheckResult = withContext(Dispatchers.IO) {
        RefCheckResult.Proceed(selectedRefType)
    }

    /**
     * Runs the full analysis pipeline for the captured [bitmap].
     *
     * Requests a side photo on the first invocation unless [sideImage] is provided
     * or [skipSidePhoto] was called. On subsequent calls (or when side image is supplied),
     * uploads both images to the server and returns a [PipelineResult].
     *
     * @param bitmap             Top-view meal image.
     * @param imageFile          Backing file for the top image (used for image-path resolution).
     * @param refType            Server value of the selected reference object.
     * @param capturedImagePath  Local path to the top image (for display in the summary).
     * @param sideImage          Side-view meal image, or null to trigger the side-photo gate.
     */
    suspend fun runAnalysis(
        bitmap: Bitmap,
        imageFile: File,
        refType: String?,
        capturedImagePath: String?,
        sideImage: Bitmap? = null
    ): PipelineResult = withContext(Dispatchers.IO) {
        try {
            val email = UserProfileManager.getUserEmail(context) ?: "test@example.com"

            if (sideImage == null && !sidePhotoOffered) {
                sidePhotoOffered = true
                Log.d(TAG, "Requesting side photo")
                return@withContext PipelineResult.NeedSidePhoto(bitmap, imageFile, refType)
            }

            val finalRefType = refType ?: "UNKNOWN"

            // When the user skipped the side photo, reuse the top image to avoid breaking the server contract
            val actualSideImage = sideImage ?: bitmap
            val arcoreDataJson = collectedArcoreDataJson

            val scanResult = scanRepository.scanImage(
                topImage = bitmap,
                sideImage = actualSideImage,
                referenceObjectType = finalRefType,
                email = email,
                arcoreDataJson = arcoreDataJson,
                topImageWidth = null,
                topImageHeight = null,
                sideImageWidth = null,
                sideImageHeight = null
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

    private fun collectArcoreData(bitmap: Bitmap): String? {
        val manager = arCoreManager ?: return null
        if (!manager.isReady) return null

        val plate = plateDetector.detectPlate(bitmap)
        val bounds = plate.bounds
        if (!plate.isFound || bounds == null) {
            Log.w(TAG, "Plate not detected for ARCore measurement, skipping")
            return null
        }

        val measurement = manager.measurePlate(
            plateBoundsPixels = bounds,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height
        ) ?: return null

        val data = mapOf(
            "plateDepthM" to (measurement.depthCm / 100f),
            "plateDiameterCm" to measurement.plateDiameterCm,
            "arConfidence" to measurement.confidence,
            "itemDepthsM" to emptyMap<String, Float>()
        )

        return try {
            Gson().toJson(data)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to serialize ARCore data: ${e.message}")
            null
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

        val warning = if (!dto.reviewWarnings.isNullOrEmpty()) {
            dto.reviewWarnings.joinToString("\n")
        } else {
            null
        }

        return PipelineResult.Success(meal, warning)
    }

    /**
     * Captures ARCore depth data for the given [bitmap] and caches it for the next [runAnalysis] call.
     * Should be called immediately after image capture, while the AR session is still active.
     */
    fun snapshotArcoreData(bitmap: Bitmap) {
        collectedArcoreDataJson = collectArcoreData(bitmap)
        Log.d(TAG, "ARCore snapshot: ${if (collectedArcoreDataJson != null) "present" else "null"}")
    }
}