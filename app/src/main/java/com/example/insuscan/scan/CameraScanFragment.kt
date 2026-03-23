package com.example.insuscan.scan

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.insuscan.R
import com.example.insuscan.analysis.PortionEstimator
import com.example.insuscan.analysis.PortionResult
import com.example.insuscan.camera.CameraManager
import com.example.insuscan.camera.ImageQualityResult
import com.example.insuscan.camera.ImageValidator
import com.example.insuscan.camera.ValidationResult
import com.example.insuscan.meal.Meal
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.network.exception.ScanException
import com.example.insuscan.utils.ReferenceObjectHelper
import com.example.insuscan.utils.ToastHelper
import kotlinx.coroutines.launch
import java.io.File
// new
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.view.animation.Animation
import android.view.animation.ScaleAnimation
import com.example.insuscan.scan.coach.CameraCoachEvaluator
import com.example.insuscan.scan.coach.CameraCoachState
import com.example.insuscan.scan.coach.CoachSeverity
import com.example.insuscan.scan.coach.MeasurementStrategy

class CameraScanFragment : Fragment(R.layout.fragment_camera_scan) {

    companion object {
        private const val TAG = "CameraScanFragment"
    }

    private val callback: ScanResultCallback?
        get() = (parentFragment as? ScanResultCallback)
            ?: (activity as? ScanResultCallback)

    private lateinit var cameraPreview: PreviewView
    private lateinit var capturedImageView: ImageView
    private lateinit var captureButton: Button
    private lateinit var galleryButton: Button
    private lateinit var qualityStatusText: TextView
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var loadingMessage: TextView
    private lateinit var subtitleText: TextView
    private lateinit var tvCoachPill: TextView
    private lateinit var chipGroupRefObject: LinearLayout
    private lateinit var btnRefToggle: TextView
    private lateinit var viewTargetZone: View
    private lateinit var arGuidanceOverlay: View
    private lateinit var arStatusText: TextView
    private lateinit var btnCancelAr: Button
    private lateinit var hiddenArSurfaceView: android.opengl.GLSurfaceView
    private lateinit var tvArExplanation: TextView

    private lateinit var cameraManager: CameraManager
    private lateinit var pipelineManager: ScanPipelineManager
    private lateinit var refChipsController: ReferenceChipsController
    private lateinit var orientationHelper: com.example.insuscan.camera.OrientationHelper

    private var portionEstimator: PortionEstimator? = null
    private var arCoreManager: com.example.insuscan.ar.ArCoreManager? = null

    private var selectedReferenceType: String? = null
    private var capturedImagePath: String? = null
    private var isCaptureOnlyMode: Boolean = false
    private var isShowingCapturedImage = false
    private var isDeviceLevel = true
    private var isScanningSurface = false

    private lateinit var arIndicatorDot: View
    private lateinit var layoutArIndicator: View
    private lateinit var viewPlateTargetZone: View
    private var isSidePhotoMode = false
    private var isDeviceSideAngle = false
    private val coachEvaluator = CameraCoachEvaluator()

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) startCamera()
        else {
            ToastHelper.showShort(requireContext(), "Camera permission is required to scan")
            callback?.onScanCancelled()
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            processGalleryImage(uri)
        } else {
            if (!::cameraManager.isInitialized) {
                initializeArCore()
                initializeCameraManager()
                checkCameraPermission()
                initializeOrientationHelper()

                if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                    if (::orientationHelper.isInitialized) orientationHelper.start()
                }
            } else {
                startCamera()
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        findViews(view)

        val openGalleryDirectly = arguments?.getBoolean("open_gallery_directly") == true
        isCaptureOnlyMode = arguments?.getBoolean("capture_only_mode", false) ?: false

        if (openGalleryDirectly) {
            arguments?.putBoolean("open_gallery_directly", false)

            initializePortionEstimator()
            setupReferenceChips()
            initializeListeners()

            selectedReferenceType = "NONE"
            refChipsController.setType(ReferenceObjectHelper.ReferenceObjectType.NONE)

            galleryLauncher.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        } else {
            // Normal Camera start
            initializeArCore()
            initializeCameraManager()
            initializePortionEstimator()
            setupReferenceChips()
            initializeListeners()
            checkCameraPermission()
            initializeOrientationHelper()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::orientationHelper.isInitialized) orientationHelper.start()
        arCoreManager?.resume()
        hiddenArSurfaceView.onResume()
    }

    override fun onPause() {
        super.onPause()
        if (::orientationHelper.isInitialized) orientationHelper.stop()
        arCoreManager?.pause()
        hiddenArSurfaceView.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (::cameraManager.isInitialized) cameraManager.shutdown()
        portionEstimator?.release()
        portionEstimator = null
        arCoreManager?.release()
        arCoreManager = null
    }

    private fun findViews(view: View) {
        cameraPreview = view.findViewById(R.id.camera_preview)
        capturedImageView = view.findViewById(R.id.iv_captured_image)
        captureButton = view.findViewById(R.id.btn_capture)
        galleryButton = view.findViewById(R.id.btn_gallery)
        qualityStatusText = view.findViewById(R.id.tv_quality_status)
        loadingOverlay = view.findViewById(R.id.loading_overlay)
        loadingMessage = view.findViewById(R.id.tv_loading_message)
        subtitleText = view.findViewById(R.id.tv_scan_subtitle)
        tvCoachPill = view.findViewById(R.id.tv_coach_pill)
        chipGroupRefObject = view.findViewById(R.id.chip_group_ref_object)
        btnRefToggle = view.findViewById(R.id.btn_ref_toggle)
        viewTargetZone = view.findViewById(R.id.view_target_zone)
        arGuidanceOverlay = view.findViewById(R.id.ar_guidance_overlay)
        arStatusText = view.findViewById(R.id.tv_ar_status)
        btnCancelAr = view.findViewById(R.id.btn_cancel_ar)
        hiddenArSurfaceView = view.findViewById(R.id.hidden_ar_surface_view)
        arIndicatorDot = view.findViewById(R.id.view_ar_indicator)
        tvArExplanation = view.findViewById(R.id.tv_ar_explanation)
        layoutArIndicator = view.findViewById(R.id.layout_ar_indicator)
        viewPlateTargetZone = view.findViewById(R.id.view_plate_target_zone)

        layoutArIndicator.setOnClickListener {
            Log.d(TAG, "AR Indicator clicked! Current visibility: ${tvArExplanation.visibility}")
            tvArExplanation.visibility = if (tvArExplanation.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
    }

    private fun initializeArCore() {
        arCoreManager = com.example.insuscan.ar.ArCoreManager(requireContext())
        val arReady = arCoreManager?.initialize(requireActivity()) == true
        Log.d(TAG, "ArCoreManager initialized: $arReady (supported=${arCoreManager?.isSupported})")

        // Set up the hidden GLSurfaceView to drive ARCore
        hiddenArSurfaceView.preserveEGLContextOnPause = true
        hiddenArSurfaceView.setEGLContextClientVersion(2)
        hiddenArSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0) // Alpha used to hide view
        hiddenArSurfaceView.setRenderer(arCoreManager)
        hiddenArSurfaceView.renderMode = android.opengl.GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }

    private fun initializeCameraManager() {
        cameraManager = CameraManager(requireContext())
        cameraManager.arCoreManager = arCoreManager
        cameraManager.onImageQualityUpdate = { quality -> updateQualityUI(quality) }
        cameraPreview.post { cameraManager.startCamera(viewLifecycleOwner, cameraPreview) }
    }

    private fun initializePortionEstimator() {
        portionEstimator = PortionEstimator(requireContext())
        val result = portionEstimator?.initialize()
        result?.let {
            Log.d(TAG, "Portion estimator — OpenCV: ${if (it.openCvReady) "Ready" else "Failed"}")
            if (!it.isReady) Log.w(TAG, "Analysis module not fully ready")
        }
        pipelineManager = ScanPipelineManager(requireContext())
        pipelineManager.portionEstimator = portionEstimator
        pipelineManager.arCoreManager = arCoreManager
    }

    private fun setupReferenceChips() {
        refChipsController = ReferenceChipsController(
            context = requireContext(),
            chipGroup = chipGroupRefObject,
            toggleButton = btnRefToggle,
            targetZone = viewTargetZone,
            dismissAnchor = cameraPreview
        )
        refChipsController.onSelectionChanged = { type ->
            selectedReferenceType = type.serverValue
            // Only update cameraManager if it has been initialized
            if (::cameraManager.isInitialized) {
                cameraManager.selectedReferenceType = type.serverValue
            }
        }
        refChipsController.setup()
        selectedReferenceType = refChipsController.selectedServerValue

        // Only update cameraManager if it has been initialized
        if (::cameraManager.isInitialized) {
            cameraManager.selectedReferenceType = selectedReferenceType
        }
    }


    private fun initializeListeners() {
        isSidePhotoMode = false
        captureButton.setOnClickListener {
            if (isShowingCapturedImage) {
                switchToCameraMode()
                return@setOnClickListener
            }

            val quality = cameraManager.lastQualityResult
            if (quality == null || !quality.isPlateFound) {
                ToastHelper.showShort(requireContext(), "Center the plate in the frame 🍽️")
                return@setOnClickListener
            }
            val state = coachEvaluator.evaluate(quality, isDeviceLevel, selectedReferenceType)
            if (!state.canCapture) {
                ToastHelper.showShort(requireContext(), state.message)
                return@setOnClickListener
            }

            onCaptureClicked()
        }

        galleryButton.setOnClickListener {
            selectedReferenceType = "NONE"
            refChipsController.setType(ReferenceObjectHelper.ReferenceObjectType.NONE)
            if (::cameraManager.isInitialized) {
                cameraManager.selectedReferenceType = "NONE"
                cameraManager.stopPreview()
            }
            galleryLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    private fun initializeOrientationHelper() {
        orientationHelper = com.example.insuscan.camera.OrientationHelper(requireContext())
        orientationHelper.onOrientationChanged = { _, _, isLevel, isSideAngle ->
            val levelChanged = isDeviceLevel != isLevel
            val sideChanged = isDeviceSideAngle != isSideAngle
            isDeviceLevel = isLevel
            isDeviceSideAngle = isSideAngle
            if (levelChanged || (isSidePhotoMode && sideChanged)) {
                activity?.runOnUiThread {
                    val quality = cameraManager.lastQualityResult
                    if (quality != null) {
                        updateQualityUI(quality)
                    } else if (isSidePhotoMode) {
                        applyCoachState(coachEvaluator.evaluateSidePhoto(isDeviceSideAngle))
                    }
                }
            }
        }
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED -> startCamera()
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                ToastHelper.showLong(requireContext(), "Camera permission is required for scanning")
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            else -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        cameraManager.startCamera(
            lifecycleOwner = viewLifecycleOwner,
            previewView = cameraPreview,
            onCameraReady = {
                captureButton.isEnabled = false
                captureButton.alpha = 0.5f
            },
            onError = { errorMessage ->
                ToastHelper.showShort(requireContext(), errorMessage)
                captureButton.isEnabled = false
            }
        )
    }

    private fun isRefObjectExpectedInFrame(): Boolean {
        val refType = ReferenceObjectHelper.fromServerValue(selectedReferenceType)
        return refType != null && refType != ReferenceObjectHelper.ReferenceObjectType.NONE
    }


    private fun applyCoachState(state: CameraCoachState) {
        if (!isAdded || context == null) return

        val coachColor = when (state.severity) {
            CoachSeverity.BLOCKING -> R.color.status_critical
            CoachSeverity.WARNING -> R.color.status_critical
            CoachSeverity.TIP -> R.color.status_warning
            CoachSeverity.ACCEPTABLE -> R.color.status_warning
            CoachSeverity.GOOD -> R.color.status_normal
        }

        tvCoachPill.apply {
            text = state.message
            visibility = View.VISIBLE
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_status_pill)?.mutate()
            val color = ContextCompat.getColor(requireContext(), coachColor)
            background.setTint(androidx.core.graphics.ColorUtils.setAlphaComponent(color, 0x99))
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_on_primary))
        }

        captureButton.isEnabled = state.canCapture
        captureButton.alpha = if (state.canCapture) 1.0f else 0.5f
    }


    private fun updateQualityUI(quality: ImageQualityResult) {
        if (!isAdded || context == null) return

        val state = if (isSidePhotoMode) {
            coachEvaluator.evaluateSidePhoto(isDeviceSideAngle)
        } else {
            coachEvaluator.evaluate(quality, isDeviceLevel, selectedReferenceType)
        }

        applyCoachState(state)
        qualityStatusText.visibility = View.GONE
        updateArIndicator()
    }



    private fun updateArIndicator() {
        if (!::arIndicatorDot.isInitialized) return
        if (isShowingCapturedImage) return
        val arReady = arCoreManager?.isReady == true
        val arSupported = arCoreManager?.isSupported == true
        
        val color = when {
            arReady -> R.color.status_normal
            arSupported -> R.color.primary
            else -> R.color.text_disabled
        }

        val message = when {
            arReady -> "High accuracy"
            arSupported -> "Calibrating..."
            else -> "Basic mode"
        }
        tvArExplanation.text = message

        arIndicatorDot.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), color))
        arIndicatorDot.visibility = View.VISIBLE
        arIndicatorDot.contentDescription = when {
            arReady -> "AR: high accuracy"
            arSupported -> "AR: calibrating"
            else -> "AR: basic mode"
        }
    }


    private fun onCaptureClicked() {

        showLoading(true, "Capturing image...")
        cameraManager.captureImage(
            outputDirectory = requireContext().cacheDir,
            onImageCaptured = { imageFile ->
                capturedImagePath = imageFile.absolutePath
                switchToCapturedImageMode(imageFile)
                validateAndProcessImage(imageFile)
            },
            onError = { errorMessage ->
                showLoading(false)
                ToastHelper.showShort(requireContext(), errorMessage)
            }
        )
    }

    private fun switchToCapturedImageMode(imageFile: File) {
        isShowingCapturedImage = true
        Glide.with(this).load(imageFile).into(capturedImageView)
        cameraPreview.visibility = View.GONE
        capturedImageView.visibility = View.VISIBLE
        captureButton.text = "Retake"
        captureButton.setBackgroundResource(R.drawable.button_primary)
        captureButton.isEnabled = true
        captureButton.alpha = 1f
        captureButton.clearAnimation()
        subtitleText.text = "Analyzing your meal..."
        qualityStatusText.visibility = View.GONE
        tvCoachPill.visibility = View.GONE

        chipGroupRefObject.visibility = View.GONE
        btnRefToggle.visibility = View.GONE
        layoutArIndicator.visibility = View.GONE
        viewPlateTargetZone.visibility = View.GONE
    }

    private fun switchToCameraMode() {
        coachEvaluator.reset()

        isShowingCapturedImage = false
        capturedImagePath = null
        pipelineManager.resetState()
        capturedImageView.visibility = View.GONE
        cameraPreview.visibility = View.VISIBLE
        captureButton.text = "Capture"
        captureButton.setBackgroundResource(R.drawable.button_primary)
        qualityStatusText.visibility = View.GONE
        subtitleText.text = "Place your plate, then tap Capture"

        btnRefToggle.visibility = View.VISIBLE
        isSidePhotoMode = false
        isDeviceSideAngle = false
        layoutArIndicator.visibility = View.VISIBLE
        viewPlateTargetZone.visibility = View.VISIBLE
        viewTargetZone.visibility = View.VISIBLE
    }

    private fun validateAndProcessImage(imageFile: File) {
        if (isCaptureOnlyMode) {
            returnCapturedImage(imageFile)
            return
        }
        val devMode = true
        if (devMode) {
            analyzePortionAndContinue(imageFile)
            return
        }
        val validationResult = ImageValidator.validateCapturedImage(imageFile)
        when (validationResult) {
            is ValidationResult.Valid -> analyzePortionAndContinue(imageFile)
            is ValidationResult.Invalid -> {
                showLoading(false)
                ToastHelper.showLong(requireContext(), "Image quality issues:\n${validationResult.getFormattedMessage()}")
            }
            is ValidationResult.Error -> {
                showLoading(false)
                ToastHelper.showShort(requireContext(), validationResult.message)
            }
        }
    }

    private fun returnCapturedImage(imageFile: File) {
        showLoading(false)
        val wasRefFound = if (::cameraManager.isInitialized)
            cameraManager.lastQualityResult?.isReferenceObjectFound ?: false
        else false

        val data = CapturedScanData(
            imagePath = imageFile.absolutePath,
            referenceType = selectedReferenceType,
            wasRefFoundInPreview = wasRefFound
        )
        callback?.onImageCapturedForBackground(data)
    }

    private fun analyzePortionAndContinue(imageFile: File) {
        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
        capturedImagePath = imageFile.absolutePath

        if (bitmap == null) {
            showLoading(false)
            ToastHelper.showShort(requireContext(), "Failed to process image")
            return
        }

        showLoading(true, "Analyzing your meal...")
        pipelineManager.isRefObjectExpectedInFrame = isRefObjectExpectedInFrame()
        pipelineManager.wasRefFoundInLivePreview =
            if (::cameraManager.isInitialized) cameraManager.lastQualityResult?.isReferenceObjectFound ?: false else false

        viewLifecycleOwner.lifecycleScope.launch {
            val refCheck = pipelineManager.checkReferenceObject(bitmap, selectedReferenceType)
            val effectiveRefType = when (refCheck) {
                is RefCheckResult.Proceed -> selectedReferenceType
                is RefCheckResult.AlternativeFound -> {
                    val autoType = when (refCheck.detectedMode) {
                        com.example.insuscan.analysis.ReferenceObjectDetector.DetectionMode.STRICT -> "INSULIN_SYRINGE"
                        com.example.insuscan.analysis.ReferenceObjectDetector.DetectionMode.FLEXIBLE -> "SYRINGE_KNIFE"
                        com.example.insuscan.analysis.ReferenceObjectDetector.DetectionMode.CARD -> "CARD"
                    }
                    Log.d(TAG, "Auto-switching ref type: $selectedReferenceType -> $autoType")
                    autoType
                }
            }
            val result = pipelineManager.runAnalysis(
                bitmap, imageFile, effectiveRefType, capturedImagePath
            )
            handlePipelineResult(result, bitmap, imageFile)
        }

//        viewLifecycleOwner.lifecycleScope.launch {
//            val refCheck = pipelineManager.checkReferenceObject(bitmap, selectedReferenceType)
//            when (refCheck) {
//                is RefCheckResult.Proceed -> {
//                    val result = pipelineManager.runAnalysis(
//                        bitmap, imageFile, selectedReferenceType, capturedImagePath
//                    )
//                    handlePipelineResult(result, bitmap, imageFile)
//                }
//                is RefCheckResult.AlternativeFound -> {
//                    showLoading(false)
//                    showAlternativeRefObjectDialog(bitmap, imageFile, refCheck.selectedType, refCheck.detectedMode)
//                }
//            }
//        }
    }

    private fun handlePipelineResult(
        result: PipelineResult,
        bitmap: android.graphics.Bitmap,
        imageFile: File
    ) {
        when (result) {
            is PipelineResult.Success -> {
                showLoading(false)
                showConfidenceBanner()
                if (result.warning != null) {
                    ToastHelper.showLong(requireContext(), result.warning)
                }
                callback?.onScanSuccess(result.meal)
            }
            is PipelineResult.NeedSidePhoto -> {
                showLoading(false)
                showSidePhotoDialog(result.bitmap, result.imageFile, result.refType)
            }
            PipelineResult.NoFoodDetected -> {
                showLoading(false)
                showNoFoodDetectedDialog()
            }
            is PipelineResult.Failed -> {
                showLoading(false)
                handleScanError(result.error)
            }
        }
    }

    private fun showConfidenceBanner() {
        val hasRef = if (::cameraManager.isInitialized)
            cameraManager.lastQualityResult?.isReferenceObjectFound ?: false
        else false
        val hasAr = arCoreManager?.isReady == true

        val strategy = MeasurementStrategy.decide(hasRef, hasAr)

        val bannerColor = when (strategy.accuracy) {
            MeasurementStrategy.Accuracy.HIGH -> R.color.status_normal
            MeasurementStrategy.Accuracy.GOOD -> R.color.status_warning
            MeasurementStrategy.Accuracy.MODERATE -> R.color.status_critical
        }
        val icon = when (strategy.accuracy) {
            MeasurementStrategy.Accuracy.HIGH -> "🟢"
            MeasurementStrategy.Accuracy.GOOD -> "🟡"
            MeasurementStrategy.Accuracy.MODERATE -> "🟠"
        }

        qualityStatusText.apply {
            text = "$icon ${strategy.label}"
            visibility = View.VISIBLE
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_status_pill)?.mutate()
            val color = ContextCompat.getColor(requireContext(), bannerColor)
            background.setTint(androidx.core.graphics.ColorUtils.setAlphaComponent(color, 0xCC))
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_on_primary))
        }
    }

    private fun proceedWithPortionAnalysis(
        bitmap: android.graphics.Bitmap,
        imageFile: File,
        refType: String?,
        sideImage: android.graphics.Bitmap? = null
    ) {
        pipelineManager.isRefObjectExpectedInFrame = isRefObjectExpectedInFrame()
        pipelineManager.wasRefFoundInLivePreview =
            if (::cameraManager.isInitialized) cameraManager.lastQualityResult?.isReferenceObjectFound ?: false else false

        viewLifecycleOwner.lifecycleScope.launch {
            val result = pipelineManager.runAnalysis(bitmap, imageFile, refType, capturedImagePath, sideImage)
            handlePipelineResult(result, bitmap, imageFile)
        }
    }


    private fun showNoFoodDetectedDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("No Food Detected")
            .setMessage("We couldn't identify any food items.\n\nTry a clearer photo with good lighting.")
            .setPositiveButton("Try Again") { d, _ -> d.dismiss(); switchToCameraMode() }
            .setNegativeButton("Cancel") { d, _ -> d.dismiss(); callback?.onScanCancelled() }
            .setCancelable(false)
            .show()
    }

    private fun showScanFailedDialog(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Scan Failed")
            .setMessage(message)
            .setPositiveButton("Try Again") { d, _ -> d.dismiss(); switchToCameraMode() }
            .setNegativeButton("Cancel") { d, _ -> d.dismiss(); callback?.onScanCancelled() }
            .setCancelable(false)
            .show()
    }

    private fun handleScanError(error: Throwable) {
        when (error) {
            is ScanException.NoFoodDetected -> showNoFoodDetectedDialog()
            is ScanException.NetworkError -> showScanFailedDialog("No internet connection. Please check your network and try again.")
            is ScanException.ServerError -> showScanFailedDialog("Server error. Please try again later.")
            is ScanException.Unauthorized -> ToastHelper.showLong(requireContext(), "Session expired. Please log in again.")
            else -> showScanFailedDialog("Something went wrong. Please try again.")
        }
    }

    private fun showLoading(show: Boolean, message: String = "Processing image...") {
        loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        loadingMessage.text = message
        captureButton.isEnabled = !show
        galleryButton.isEnabled = !show
    }

    private fun processGalleryImage(uri: android.net.Uri) {
        showLoading(true, "Loading image...")
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap == null) {
                showLoading(false)
                ToastHelper.showShort(requireContext(), "Failed to load image")
                return
            }

            val cacheFile = File(requireContext().cacheDir, "gallery_${System.currentTimeMillis()}.jpg")
            cacheFile.outputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            }
            capturedImagePath = cacheFile.absolutePath

            if (isCaptureOnlyMode) {
                selectedReferenceType = "NONE"
                returnCapturedImage(cacheFile)
                return
            }

            selectedReferenceType = "NONE"
            if (::cameraManager.isInitialized) {
                cameraManager.selectedReferenceType = "NONE"
            }
            refChipsController.setType(ReferenceObjectHelper.ReferenceObjectType.NONE)
            switchToCapturedImageMode(cacheFile)
            showLoading(true, "Analyzing your meal...")
            pipelineManager.skipSidePhoto()
            analyzePortionAndContinue(cacheFile)
        } catch (e: Exception) {
            showLoading(false)
            ToastHelper.showShort(requireContext(), "Failed to process image: ${e.message}")
        }
    }

    private fun showSidePhotoDialog(
        bitmap: Bitmap,
        imageFile: File,
        refType: String?
    ) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Side Photo Needed")
            .setMessage("For better accuracy, take a photo from the side to measure depth.\n\nHold the phone at table level, showing the side of the plate/bowl.")
            .setPositiveButton("Take Side Photo") { d, _ ->
                d.dismiss()
                captureSidePhoto(bitmap, imageFile, refType)
            }
            .setNegativeButton("Skip") { d, _ ->
                d.dismiss()
                pipelineManager.skipSidePhoto()
                proceedWithPortionAnalysis(bitmap, imageFile, refType, sideImage = null)
            }
            .setCancelable(false)
            .show()
    }

    private fun captureSidePhoto(
        originalBitmap: Bitmap,
        originalFile: File,
        refType: String?
    ) {
        switchToCameraMode()
        isSidePhotoMode = true
        viewPlateTargetZone.visibility = View.GONE
        viewTargetZone.visibility = View.GONE
        applyCoachState(coachEvaluator.evaluateSidePhoto(isDeviceSideAngle))

        subtitleText.text = "Hold phone at table level — capture the side of the plate"

        captureButton.setOnClickListener {
            showLoading(true, "Capturing side photo...")
            cameraManager.captureImage(
                outputDirectory = requireContext().cacheDir,
                onImageCaptured = { sideFile ->
                    val sideBitmap = android.graphics.BitmapFactory.decodeFile(sideFile.absolutePath)
                    showLoading(true, "Analyzing your meal...")
                    proceedWithPortionAnalysis(originalBitmap, originalFile, refType, sideImage = sideBitmap)
                    initializeListeners()
                },
                onError = { errorMessage ->
                    showLoading(false)
                    ToastHelper.showShort(requireContext(), errorMessage)
                    initializeListeners()
                }
            )
        }
    }
}