package com.example.insuscan.scan.helper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.insuscan.camera.model.ImageQualityResult
import com.example.insuscan.camera.validator.ImageValidator
import com.example.insuscan.camera.validator.ValidationResult
import com.example.insuscan.scan.CapturedScanData
import com.example.insuscan.scan.PipelineResult
import com.example.insuscan.scan.RefCheckResult
import com.example.insuscan.scan.ScanMode
import com.example.insuscan.scan.ScanResultCallback
import com.example.insuscan.scan.coach.CameraCoachEvaluator
import com.example.insuscan.scan.coach.MeasurementStrategy
import com.example.insuscan.scan.ui.ScanDialogHelper
import com.example.insuscan.scan.ui.ScanUiStateManager
import com.example.insuscan.utils.ReferenceObjectHelper
import com.example.insuscan.utils.ToastHelper
import kotlinx.coroutines.launch
import java.io.File

class ScanFlowController(
    private val fragment: Fragment,
    private val hardware: ScanHardwareController,
    private val uiState: ScanUiStateManager,
    private val dialogHelper: ScanDialogHelper,
    private val coachEvaluator: CameraCoachEvaluator,
    private val callback: ScanResultCallback?
) {

    var selectedReferenceType: String? = null
    var isCaptureOnlyMode: Boolean = false
    var isShowingCapturedImage = false
    var isDeviceLevel = true
    var isSidePhotoMode = false
    var isDeviceSideAngle = false

    private var capturedImagePath: String? = null
    private var pendingMainBitmap: Bitmap? = null
    private var pendingMainFile: File? = null
    private var pendingRefType: String? = null

    private val context get() = fragment.requireContext()

    fun updateQualityUI(quality: ImageQualityResult) {
        if (!fragment.isAdded || fragment.context == null) return
        val state = if (isSidePhotoMode) coachEvaluator.evaluateSidePhoto(isDeviceSideAngle) 
                    else coachEvaluator.evaluate(quality, isDeviceLevel, selectedReferenceType)
        uiState.applyCoachState(state)
        updateArIndicator()
    }

    private fun updateArIndicator() {
        if (isShowingCapturedImage) return
        val arReady = hardware.arCoreManager?.isReady == true
        val arSupported = hardware.arCoreManager?.isSupported == true
        uiState.updateArIndicator(arReady, arSupported)
        
        val scanMode = ScanMode.detect(
            arReady = arReady,
            hasRealDepth = hardware.arCoreManager?.hasRealDepth == true,
            hasRefObject = ReferenceObjectHelper.fromServerValue(selectedReferenceType) != ReferenceObjectHelper.ReferenceObjectType.NONE
        )
        uiState.updateTwoPhotoHint(scanMode.requiresSidePhoto, isShowingCapturedImage, isSidePhotoMode)
    }

    fun onCaptureClicked() {
        uiState.showLoading(true, "Capturing image...")
        hardware.cameraManager.captureImage(
            outputDirectory = context.cacheDir,
            onImageCaptured = { file ->
                capturedImagePath = file.absolutePath
                isShowingCapturedImage = true
                uiState.switchToCapturedImageMode(file, fragment, isSidePhotoMode)
                
                if (isCaptureOnlyMode) {
                    uiState.showLoading(false)
                    val wasRefFound = hardware.cameraManager.lastQualityResult?.isReferenceObjectFound ?: false
                    callback?.onImageCapturedForBackground(CapturedScanData(file.absolutePath, selectedReferenceType, wasRefFound))
                    return@captureImage
                }
                
                val validationResult = ImageValidator.validateCapturedImage(file)
                when (validationResult) {
                    is ValidationResult.Valid -> analyzePortionAndContinue(file)
                    is ValidationResult.Invalid -> {
                        uiState.showLoading(false)
                        ToastHelper.showLong(context, "Image quality issues:\n${validationResult.getFormattedMessage()}")
                    }
                    is ValidationResult.Error -> {
                        uiState.showLoading(false)
                        ToastHelper.showShort(context, validationResult.message)
                    }
                }
            },
            onError = { 
                uiState.showLoading(false)
                ToastHelper.showShort(context, it) 
            }
        )
    }

    fun switchToCameraMode() {
        coachEvaluator.reset()
        isShowingCapturedImage = false
        capturedImagePath = null
        hardware.pipelineManager.resetState()
        uiState.switchToCameraMode()
        isSidePhotoMode = false
        isDeviceSideAngle = false
        pendingMainBitmap = null
        pendingMainFile = null
        pendingRefType = null
    }

    private fun analyzePortionAndContinue(imageFile: File) {
        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
        if (bitmap == null) {
            uiState.showLoading(false)
            ToastHelper.showShort(context, "Failed to process image")
            return
        }

        uiState.showLoading(true, "Analyzing your meal...")
        hardware.pipelineManager.isRefObjectExpectedInFrame = ReferenceObjectHelper.fromServerValue(selectedReferenceType) != ReferenceObjectHelper.ReferenceObjectType.NONE
        hardware.pipelineManager.wasRefFoundInLivePreview = hardware.cameraManager.lastQualityResult?.isReferenceObjectFound ?: false

        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val refCheck = hardware.pipelineManager.checkReferenceObject(bitmap, selectedReferenceType)
            val effectiveRefType = when (refCheck) {
                is RefCheckResult.Proceed -> selectedReferenceType
                is RefCheckResult.AlternativeFound -> {
                    when (refCheck.detectedMode) {
                        com.example.insuscan.analysis.detection.ReferenceObjectDetector.DetectionMode.STRICT -> "INSULIN_SYRINGE"
                        com.example.insuscan.analysis.detection.ReferenceObjectDetector.DetectionMode.FLEXIBLE -> "SYRINGE_KNIFE"
                        com.example.insuscan.analysis.detection.ReferenceObjectDetector.DetectionMode.CARD -> "CARD"
                    }
                }
            }
            val result = hardware.pipelineManager.runAnalysis(bitmap, imageFile, effectiveRefType, capturedImagePath)
            handlePipelineResult(result)
        }
    }

    private fun handlePipelineResult(result: PipelineResult) {
        when (result) {
            is PipelineResult.Success -> {
                uiState.showLoading(false)
                val strategy = MeasurementStrategy.decide(
                    hardware.cameraManager.lastQualityResult?.isReferenceObjectFound ?: false, 
                    hardware.arCoreManager?.isReady == true
                )
                uiState.showConfidenceBanner(strategy)
                if (result.warning != null) ToastHelper.showLong(context, result.warning)
                callback?.onScanSuccess(result.meal)
            }
            is PipelineResult.NeedSidePhoto -> {
                uiState.showLoading(false)
                pendingMainBitmap = result.bitmap
                pendingMainFile = result.imageFile
                pendingRefType = result.refType
                uiState.showSidePhotoCard()
            }
            PipelineResult.NoFoodDetected -> {
                uiState.showLoading(false)
                dialogHelper.showNoFoodDetectedDialog()
            }
            is PipelineResult.Failed -> {
                uiState.showLoading(false)
                dialogHelper.handleScanError(result.error)
            }
        }
    }

    fun proceedWithPortionAnalysis(sideImage: Bitmap? = null) {
        hardware.pipelineManager.isRefObjectExpectedInFrame = ReferenceObjectHelper.fromServerValue(selectedReferenceType) != ReferenceObjectHelper.ReferenceObjectType.NONE
        hardware.pipelineManager.wasRefFoundInLivePreview = hardware.cameraManager.lastQualityResult?.isReferenceObjectFound ?: false

        fragment.viewLifecycleOwner.lifecycleScope.launch {
            pendingMainBitmap?.let { bmp ->
                pendingMainFile?.let { file ->
                    val result = hardware.pipelineManager.runAnalysis(bmp, file, pendingRefType, capturedImagePath, sideImage)
                    handlePipelineResult(result)
                }
            }
        }
    }

    fun captureSidePhoto(initializeListeners: () -> Unit) {
        if (pendingMainBitmap == null || pendingMainFile == null) return
        isSidePhotoMode = true
        uiState.switchToSideCameraMode()
        uiState.applyCoachState(coachEvaluator.evaluateSidePhoto(isDeviceSideAngle))

        uiState.captureButton.setOnClickListener {
            uiState.showLoading(true, "Capturing side photo...")
            hardware.cameraManager.captureImage(
                outputDirectory = context.cacheDir,
                onImageCaptured = { sideFile ->
                    val sideBitmap = BitmapFactory.decodeFile(sideFile.absolutePath)
                    uiState.showLoading(true, "Analyzing your meal...")
                    proceedWithPortionAnalysis(sideImage = sideBitmap)
                    initializeListeners()
                },
                onError = { 
                    uiState.showLoading(false)
                    ToastHelper.showShort(context, it)
                    initializeListeners()
                }
            )
        }
    }

    fun processGalleryImage(uri: Uri) {
        uiState.showLoading(true, "Loading image...")
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap == null) return

            val cacheFile = File(context.cacheDir, "gallery_${System.currentTimeMillis()}.jpg")
            cacheFile.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
            capturedImagePath = cacheFile.absolutePath

            if (isCaptureOnlyMode) {
                uiState.showLoading(false)
                callback?.onImageCapturedForBackground(CapturedScanData(cacheFile.absolutePath, "NONE", false))
                return
            }

            hardware.resetForGallery()
            isShowingCapturedImage = true
            uiState.switchToCapturedImageMode(cacheFile, fragment, isSidePhotoMode)
            uiState.showLoading(true, "Analyzing your meal...")
            hardware.pipelineManager.skipSidePhoto()
            
            hardware.pipelineManager.isRefObjectExpectedInFrame = false
            hardware.pipelineManager.wasRefFoundInLivePreview = false

            fragment.viewLifecycleOwner.lifecycleScope.launch {
                val result = hardware.pipelineManager.runAnalysis(bitmap, cacheFile, "NONE", capturedImagePath)
                handlePipelineResult(result)
            }
        } catch (e: Exception) {
            uiState.showLoading(false)
            ToastHelper.showShort(context, "Failed to process image: ${e.message}")
        }
    }
}
