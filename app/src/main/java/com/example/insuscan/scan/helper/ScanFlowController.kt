package com.example.insuscan.scan.helper

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.insuscan.R
import com.example.insuscan.camera.exception.CameraException
import com.example.insuscan.camera.model.ImageQualityResult
import com.example.insuscan.camera.model.QualityLevel
import com.example.insuscan.camera.validator.ImageValidator
import com.example.insuscan.camera.validator.ValidationResult
import com.example.insuscan.network.exception.ApiException
import com.example.insuscan.scan.CapturedScanData
import com.example.insuscan.scan.PipelineResult
import com.example.insuscan.scan.ScanMode
import com.example.insuscan.scan.ScanResultCallback
import com.example.insuscan.scan.coach.CameraCoachEvaluator
import com.example.insuscan.scan.coach.MeasurementStrategy
import com.example.insuscan.scan.notice.ReferenceNoticeBuilder
import com.example.insuscan.scan.ui.ScanDialogHelper
import com.example.insuscan.scan.ui.ScanUiStateManager
import com.example.insuscan.utils.ReferenceObjectHelper
import com.example.insuscan.utils.ToastHelper
import kotlinx.coroutines.launch
import java.io.File

/**
 * Drives the scan UI flow: capture, validation, side-photo handling, and routing
 * each pipeline result to the right dialog or success path.
 */
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
    var capturedSideImagePath: String? = null
    private var pendingMainBitmap: Bitmap? = null
    private var pendingMainFile: File? = null
    private var pendingRefType: String? = null
    private var pendingSuccessMeal: com.example.insuscan.meal.Meal? = null
    private var pendingSuccessWarning: String? = null
    private var isWaitingForResults = false

    private val context get() = fragment.requireContext()

    init {
        uiState.glucoseInput.setOnEditorActionListener { _, _, _ ->
            uiState.btnSubmitScan.performClick()
            true
        }

        uiState.btnSubmitScan.setOnClickListener {
            val text = uiState.glucoseInput.text.toString().trim()
            if (text.isEmpty()) {
                ToastHelper.showShort(context, context.getString(R.string.scan_enter_glucose))
                return@setOnClickListener
            }
            val value = text.toIntOrNull()
            if (value == null || value < 30 || value > 600) {
                ToastHelper.showShort(context, context.getString(R.string.scan_glucose_range_error))
                return@setOnClickListener
            }

            val meal = pendingSuccessMeal
            if (meal != null) {
                submitSuccessMeal(meal, pendingSuccessWarning)
            } else {
                isWaitingForResults = true
                uiState.btnSubmitScan.isEnabled = false
                uiState.btnSubmitScan.text = context.getString(R.string.scan_waiting)
                uiState.glucoseInput.isEnabled = false
            }
        }
    }

    private fun submitSuccessMeal(meal: com.example.insuscan.meal.Meal, warning: String?) {
        uiState.showLoading(false)
        val strategy = MeasurementStrategy.decide(
            hardware.cameraManager.lastQualityResult?.isReferenceObjectFound ?: false, 
            hardware.arCoreManager?.isReady == true
        )
        uiState.showConfidenceBanner(strategy)
        callback?.onScanSuccess(meal)
    }

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
        Log.d(TAG, "📸 CAPTURE CLICKED | captureOnlyMode=$isCaptureOnlyMode | sidePhotoMode=$isSidePhotoMode | refType=$selectedReferenceType | arReady=${hardware.arCoreManager?.isReady}")
        uiState.showLoading(true, "Capturing image...")
        hardware.cameraManager.captureImage(
            outputDirectory = context.cacheDir,
            onImageCaptured = { file ->
                capturedImagePath = file.absolutePath
                Log.d(TAG, "🖼️ IMAGE CAPTURED | path=${file.absolutePath} | size=${"%.2f".format(file.length() / 1024.0)}KB")
                isShowingCapturedImage = true
                uiState.switchToCapturedImageMode(file, fragment, isSidePhotoMode)
                
                if (isCaptureOnlyMode) {
                    uiState.showLoading(false)
                    val wasRefFound = hardware.cameraManager.lastQualityResult?.isReferenceObjectFound ?: false
                    callback?.onImageCapturedForBackground(CapturedScanData(file.absolutePath, selectedReferenceType, wasRefFound))
                    return@captureImage
                }

                Log.d(TAG, "Image captured, running validation...")
                val capturedLux = hardware.currentLux
                val validationResult = ImageValidator.validateCapturedImage(file, capturedLux)
                when (validationResult) {
                    is ValidationResult.Evaluated -> {
                        when (validationResult.report.overall) {
                            QualityLevel.FAILED -> {
                                Log.w(TAG, "Image failed validation: ${validationResult.report.message}")
                                uiState.showLoading(false)
                                ToastHelper.showLong(context, context.getString(R.string.scan_quality_issue, validationResult.report.message))
                                switchToCameraMode()
                            }
                            QualityLevel.BORDERLINE -> {
                                Log.w(TAG, "Image borderline quality: ${validationResult.report.message}")
                                ToastHelper.showLong(context, context.getString(R.string.scan_quality_warning, validationResult.report.message))
                                analyzePortionAndContinue(file)
                            }
                            QualityLevel.OK -> {
                                Log.d(TAG, "Image passed validation, proceeding to analysis")
                                analyzePortionAndContinue(file)
                            }
                        }
                    }
                    is ValidationResult.Error -> {
                        Log.e(TAG, "Validation error: ${validationResult.message}")
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
        Log.d(TAG, "🔄 SWITCH TO CAMERA MODE | clearing pending state | pendingBitmap=${pendingMainBitmap != null} | pendingFile=${pendingMainFile?.name}")
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
        pendingSuccessMeal = null
        pendingSuccessWarning = null
        isWaitingForResults = false
    }

    private fun analyzePortionAndContinue(imageFile: File) {
        Log.d(TAG, "🔬 ANALYSIS START | file=${imageFile.name} | refType=$selectedReferenceType | refExpected=${ReferenceObjectHelper.fromServerValue(selectedReferenceType) != ReferenceObjectHelper.ReferenceObjectType.NONE} | refFoundInPreview=${hardware.cameraManager.lastQualityResult?.isReferenceObjectFound}")
        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
        if (bitmap == null) {
            uiState.showLoading(false)
            ToastHelper.showShort(context, context.getString(R.string.scan_process_failed))
            return
        }

        hardware.pipelineManager.snapshotArcoreData(bitmap)
        uiState.showLoading(true, "Processing top image...", isFullAnalysis = false)
        hardware.pipelineManager.isRefObjectExpectedInFrame = ReferenceObjectHelper.fromServerValue(selectedReferenceType) != ReferenceObjectHelper.ReferenceObjectType.NONE
        hardware.pipelineManager.wasRefFoundInLivePreview = hardware.cameraManager.lastQualityResult?.isReferenceObjectFound ?: false

        fragment.viewLifecycleOwner.lifecycleScope.launch {
            val refCheck = hardware.pipelineManager.checkReferenceObject(bitmap, selectedReferenceType)
            Log.d(TAG, "🎯 REF CHECK RESULT | type=${refCheck::class.simpleName} | selectedRef=$selectedReferenceType | effectiveRef will be determined next")
            val effectiveRefType = selectedReferenceType
            val result = hardware.pipelineManager.runAnalysis(bitmap, imageFile, effectiveRefType, capturedImagePath)
            handlePipelineResult(result)
        }
    }

    private fun handlePipelineResult(result: PipelineResult) {
        when (result) {

            is PipelineResult.Success -> {
                Log.d(TAG, "✅ PIPELINE SUCCESS | warning=${result.warning} | meal=${result.meal}")
                pendingSuccessMeal = result.meal
                pendingSuccessWarning = result.warning
                if (isWaitingForResults) {
                    uiState.btnSubmitScan.post {
                        submitSuccessMeal(result.meal, result.warning)
                    }
                } else {
                    uiState.showLoadingReferenceNotice(ReferenceNoticeBuilder.build(result.meal))
                }
            }
            is PipelineResult.NeedSidePhoto -> {
                Log.d(TAG, "📷 NEED SIDE PHOTO | requesting second capture")
                uiState.showLoading(false)
                pendingMainBitmap = result.bitmap
                pendingMainFile = result.imageFile
                pendingRefType = result.refType
                uiState.showSidePhotoCard()
            }
            PipelineResult.NoFoodDetected -> {
                Log.w(TAG, "⚠️ NO FOOD DETECTED")
                uiState.showLoading(false)
                dialogHelper.showNoFoodDetectedDialog()
            }
            is PipelineResult.Failed -> {
                Log.e(TAG, "❌ PIPELINE FAILED | error=${result.error}")
                uiState.showLoading(false)
                dialogHelper.handleScanError(result.error)
            }
        }
    }

    fun proceedWithPortionAnalysis(sideImage: Bitmap? = null) {
        uiState.showLoading(true, "Analyzing your meal...", isFullAnalysis = true)
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
                    uiState.showLoading(true, "Analyzing your meal...", isFullAnalysis = true)
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
        Log.d(TAG, "🖼️ GALLERY IMAGE | uri=$uri | captureOnlyMode=$isCaptureOnlyMode")
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
            uiState.showLoading(true, "Analyzing your meal...", isFullAnalysis = true)
            hardware.pipelineManager.skipSidePhoto()
            
            hardware.pipelineManager.isRefObjectExpectedInFrame = false
            hardware.pipelineManager.wasRefFoundInLivePreview = false

            fragment.viewLifecycleOwner.lifecycleScope.launch {
                val result = hardware.pipelineManager.runAnalysis(bitmap, cacheFile, "NONE", capturedImagePath)
                handlePipelineResult(result)
            }
        } catch (e: CameraException) {
            uiState.showLoading(false)
            dialogHelper.handleScanError(e)
        } catch (e: ApiException) {
            uiState.showLoading(false)
            dialogHelper.handleScanError(e)
        } catch (e: Exception) {
            uiState.showLoading(false)
            ToastHelper.showShort(context, context.getString(R.string.scan_process_failed_retry))
        }
    }

    companion object {
        private const val TAG = "ScanFlowController"
    }
}
