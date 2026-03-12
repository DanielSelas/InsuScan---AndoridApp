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
    private var plateInFrameStartTime: Long = 0L
    private var isForceCaptureAllowed = false

    private var isSidePhotoCaptureMode = false
    private var pendingSidePhotoBitmap: android.graphics.Bitmap? = null
    private var pendingSidePhotoFile: File? = null
    private var pendingSidePhotoRefType: String? = null

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
    }

    override fun onPause() {
        super.onPause()
        if (::orientationHelper.isInitialized) orientationHelper.stop()
        arCoreManager?.pause()
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
        btnCancelAr.setOnClickListener { stopArScanMode() }
    }

    private fun initializeArCore() {
        arCoreManager = com.example.insuscan.ar.ArCoreManager(requireContext())
        val arReady = arCoreManager?.initialize(requireActivity()) == true
        Log.d(TAG, "ArCoreManager initialized: $arReady (supported=${arCoreManager?.isSupported})")
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
        captureButton.setOnClickListener {
            if (isShowingCapturedImage) {
                switchToCameraMode()
            } else {
                val result = cameraManager.lastQualityResult
                if (result != null) {
                    if (result.isPlateFound) {
                        if (isRefObjectExpectedInFrame() && !result.isReferenceObjectFound) {
                            showMissingRefObjectDialog()
                        } else {
                            onCaptureClicked()
                        }
                    } else {
                        ToastHelper.showShort(requireContext(), "Please center the food plate first")
                    }
                } else {
                    ToastHelper.showShort(requireContext(), "Camera initializing...")
                }
            }
        }
        galleryButton.setOnClickListener {
            selectedReferenceType = "NONE"
            refChipsController.setType(ReferenceObjectHelper.ReferenceObjectType.NONE)
            if (::cameraManager.isInitialized) {
                cameraManager.selectedReferenceType = "NONE"
                cameraManager.stopPreview()
            }
            galleryLauncher.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        }
    }

    private fun initializeOrientationHelper() {
        orientationHelper = com.example.insuscan.camera.OrientationHelper(requireContext())
        orientationHelper.onOrientationChanged = { pitch, _, isLevel ->
            if (isSidePhotoCaptureMode) {
                val pitchAbs = Math.abs(pitch)
                activity?.runOnUiThread {
                    val statusText: String
                    val statusColor: Int
                    val canCapture: Boolean

                    when {
                        pitchAbs < 15.0 -> {
                            statusText = "✅ Perfect Angle"
                            statusColor = R.color.status_normal
                            canCapture = true
                        }
                        pitchAbs < 30.0 -> {
                            statusText = "⚠️ Good enough for capture"
                            statusColor = R.color.status_warning
                            canCapture = true
                        }
                        else -> {
                            statusText = "❌ Hold phone vertically"
                            statusColor = R.color.status_critical
                            canCapture = false
                        }
                    }

                    val colorWithAlpha = androidx.core.graphics.ColorUtils.setAlphaComponent(
                        ContextCompat.getColor(requireContext(), statusColor),
                        0x99
                    )

                    tvCoachPill.apply {
                        text = statusText
                        background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_status_pill)?.mutate()
                        background.setTint(colorWithAlpha)
                        setTextColor(ContextCompat.getColor(requireContext(), R.color.text_on_primary))
                        visibility = View.VISIBLE
                    }

                    qualityStatusText.apply {
                        text = statusText
                        background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_status_pill)?.mutate()
                        background.setTint(colorWithAlpha)
                        setTextColor(ContextCompat.getColor(requireContext(), R.color.text_on_primary))
                        visibility = View.VISIBLE
                    }

                    captureButton.isEnabled = canCapture
                    captureButton.alpha = if (canCapture) 1.0f else 0.5f
                }
            } else {
                if (isDeviceLevel != isLevel) {
                    isDeviceLevel = isLevel
                    activity?.runOnUiThread {
                        cameraManager.lastQualityResult?.let { updateQualityUI(it) }
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
                captureButton.isEnabled = true
                qualityStatusText.text = "Ready to capture"
            },
            onError = { errorMessage ->
                ToastHelper.showShort(requireContext(), errorMessage)
                captureButton.isEnabled = false
            }
        )
    }

    private fun isRefObjectExpectedInFrame(): Boolean {
        if (isSidePhotoCaptureMode) return false
        val refType = ReferenceObjectHelper.fromServerValue(selectedReferenceType)
        return refType != null && refType != ReferenceObjectHelper.ReferenceObjectType.NONE
    }

    private fun updateQualityUI(quality: ImageQualityResult) {
        if (!isAdded || context == null) return

        if (quality.isPlateFound) {
            if (plateInFrameStartTime == 0L) {
                plateInFrameStartTime = System.currentTimeMillis()
            } else if (System.currentTimeMillis() - plateInFrameStartTime > 5000) {
                isForceCaptureAllowed = true
            }
        } else {
            plateInFrameStartTime = 0L
            isForceCaptureAllowed = false
        }

        val validationMsg = when {
            !isDeviceLevel -> "Hold Phone Flat 📱"
            !quality.isValid && isForceCaptureAllowed -> "Quality low, but you can capture now."
            else -> quality.getValidationMessage()
        }
        qualityStatusText.text = validationMsg

        // Removed old statusColor/setBackgroundColor logic to avoid overriding pill shape
        captureButton.isEnabled = (quality.isValid && isDeviceLevel) || isForceCaptureAllowed
        captureButton.alpha = if (captureButton.isEnabled) 1.0f else 0.5f

        val refType = ReferenceObjectHelper.fromServerValue(selectedReferenceType)
        val refObjLabel = when (refType) {
            ReferenceObjectHelper.ReferenceObjectType.CARD -> "Place Card 💳"
            ReferenceObjectHelper.ReferenceObjectType.INSULIN_SYRINGE -> "Place Pen 🖊️"
            ReferenceObjectHelper.ReferenceObjectType.SYRINGE_KNIFE -> "Place Fork/Knife 🍴"
            else -> "Place Ref Obj"
        }

        val coachMessage: String
        val coachColor: Int
        var canCapture = false

        when {
            !isDeviceLevel -> {
                coachMessage = "Phone Tilted 📐"
                coachColor = R.color.status_critical
            }
            !quality.isPlateFound -> {
                coachMessage = "Find Plate 🍽️"
                coachColor = R.color.status_critical
            }
            isRefObjectExpectedInFrame() && !quality.isReferenceObjectFound -> {
                coachMessage = refObjLabel
                coachColor = R.color.status_critical
            }
            !quality.isValid -> {
                // Orange state: plate found but quality issues (dark, blurry, etc)
                // If force capture is allowed (waited 5s), we show warning
                if (isForceCaptureAllowed) {
                    coachMessage = "⚠️ Quality low, capture if needed"
                    coachColor = R.color.status_warning
                    canCapture = true
                } else {
                    coachMessage = when {
                        !quality.isBrightnessOk && quality.brightness < 50f -> "Too Dark 🌑"
                        !quality.isBrightnessOk && quality.brightness > 200f -> "Too Bright ☀️"
                        !quality.isSharpnessOk -> "Image Blurry 📷"
                        else -> "Improving quality..."
                    }
                    coachColor = R.color.status_critical
                }
            }
            else -> {
                coachMessage = "Perfect! ✅"
                coachColor = R.color.status_normal
                canCapture = true
            }
        }

        tvCoachPill.apply {
            text = coachMessage
            visibility = View.VISIBLE
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_status_pill)?.mutate()
            val color = ContextCompat.getColor(requireContext(), coachColor)
            background.setTint(androidx.core.graphics.ColorUtils.setAlphaComponent(color, 0x99))
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_on_primary))
        }

        qualityStatusText.apply {
            text = coachMessage
            visibility = View.VISIBLE
            background = ContextCompat.getDrawable(requireContext(), R.drawable.bg_status_pill)?.mutate()
            val color = ContextCompat.getColor(requireContext(), coachColor)
            background.setTint(androidx.core.graphics.ColorUtils.setAlphaComponent(color, 0x99))
            setTextColor(ContextCompat.getColor(requireContext(), R.color.text_on_primary))
        }

        captureButton.isEnabled = canCapture
        captureButton.alpha = if (canCapture) 1.0f else 0.5f
    }

    private fun onCaptureClicked() {
        if (isSidePhotoCaptureMode) {
            showLoading(true, "Capturing side photo...")
            cameraManager.captureImage(
                outputDirectory = requireContext().cacheDir,
                onImageCaptured = { sideFile ->
                    isSidePhotoCaptureMode = false
                    val sideBitmap = BitmapFactory.decodeFile(sideFile.absolutePath)
                    val topBitmap = pendingSidePhotoBitmap
                    val origFile = pendingSidePhotoFile
                    val origRefType = pendingSidePhotoRefType

                    if (origFile != null) switchToCapturedImageMode(origFile)
                    subtitleText.text = "Analyzing with side photo..."
                    tvCoachPill.visibility = View.GONE

                    pendingSidePhotoBitmap = null
                    pendingSidePhotoFile = null
                    pendingSidePhotoRefType = null

                    if (topBitmap != null && origFile != null) {
                        Log.d(TAG, "Side photo captured — resuming analysis with both images")
                        proceedWithPortionAnalysis(topBitmap, origFile, origRefType, sideImage = sideBitmap)
                    } else {
                        showLoading(false)
                        ToastHelper.showShort(requireContext(), "Error: original photo lost")
                    }
                },
                onError = { errorMessage ->
                    showLoading(false)
                    ToastHelper.showShort(requireContext(), errorMessage)
                }
            )
            return
        }

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
        captureButton.isEnabled = true
        captureButton.alpha = 1f
        subtitleText.text = "Analyzing your meal..."
        qualityStatusText.text = "Image captured"
        qualityStatusText.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.primary_light))

        chipGroupRefObject.visibility = View.GONE
        btnRefToggle.visibility = View.GONE
    }

    private fun switchToCameraMode() {
        isShowingCapturedImage = false
        capturedImagePath = null
        pipelineManager.resetState()
        capturedImageView.visibility = View.GONE
        cameraPreview.visibility = View.VISIBLE
        captureButton.text = "Capture"
        subtitleText.text = "Place your plate and reference object, then tap Capture"
        qualityStatusText.text = "Ready to capture"
        qualityStatusText.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.secondary_light))

        btnRefToggle.visibility = View.VISIBLE
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
            when (refCheck) {
                is RefCheckResult.Proceed -> {
                    val result = pipelineManager.runAnalysis(
                        bitmap, imageFile, selectedReferenceType, capturedImagePath
                    )
                    handlePipelineResult(result, bitmap, imageFile)
                }
                is RefCheckResult.AlternativeFound -> {
                    showLoading(false)
                    showAlternativeRefObjectDialog(bitmap, imageFile, refCheck.selectedType, refCheck.detectedMode)
                }
            }
        }
    }

    private fun handlePipelineResult(
        result: PipelineResult,
        bitmap: android.graphics.Bitmap,
        imageFile: File
    ) {
        when (result) {
            is PipelineResult.Success -> {
                showLoading(false)
                if (result.warning != null) {
                    ToastHelper.showLong(requireContext(), result.warning)
                }
                callback?.onScanSuccess(result.meal)
            }
            is PipelineResult.NeedSidePhoto -> {
                if (!::cameraManager.isInitialized) {
                    showLoading(true, "Analyzing your meal...")
                    proceedWithPortionAnalysis(result.bitmap, result.imageFile, result.refType, sideImage = null)
                } else {
                    showLoading(false)
                    showSidePhotoDialog(result.bitmap, result.imageFile, result.refType)
                }
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

    private fun showSidePhotoDialog(
        topDownBitmap: android.graphics.Bitmap,
        imageFile: File,
        refType: String?
    ) {
        if (!isAdded) return
        AlertDialog.Builder(requireContext())
            .setTitle("📐 Improve Accuracy")
            .setMessage(
                "For better depth estimation, take a side-angle photo of the bowl/plate.\n\n" +
                        "Hold your phone at table level and capture the container from the side."
            )
            .setPositiveButton("📸 Take Side Photo") { dialog, _ ->
                dialog.dismiss()
                pendingSidePhotoBitmap = topDownBitmap
                pendingSidePhotoFile = imageFile
                pendingSidePhotoRefType = refType
                showLoading(false)
                isShowingCapturedImage = false
                capturedImageView.visibility = View.GONE
                cameraPreview.visibility = View.VISIBLE
                captureButton.isEnabled = false
                isSidePhotoCaptureMode = true
                subtitleText.text = "Capture from the SIDE"
                tvCoachPill.text = "📐 Hold phone upright"
                tvCoachPill.setBackgroundResource(R.drawable.bg_pill_warning)
                tvCoachPill.visibility = View.VISIBLE
            }
            .setNegativeButton("Skip") { dialog, _ ->
                dialog.dismiss()
                showLoading(true, "Analyzing your meal...")
                proceedWithPortionAnalysis(topDownBitmap, imageFile, refType, sideImage = null)
            }
            .setCancelable(false)
            .show()
    }

    private fun showAlternativeRefObjectDialog(
        bitmap: android.graphics.Bitmap,
        imageFile: File,
        selectedType: ReferenceObjectHelper.ReferenceObjectType,
        detectedMode: com.example.insuscan.analysis.ReferenceObjectDetector.DetectionMode
    ) {
        val selectedName = getString(selectedType.displayNameResId)
        val detectedName = when (detectedMode) {
            com.example.insuscan.analysis.ReferenceObjectDetector.DetectionMode.STRICT -> getString(R.string.ref_option_insulin_syringe)
            com.example.insuscan.analysis.ReferenceObjectDetector.DetectionMode.FLEXIBLE -> getString(R.string.ref_option_syringe_knife)
            com.example.insuscan.analysis.ReferenceObjectDetector.DetectionMode.CARD -> getString(R.string.ref_option_card)
        }
        val detectedServerValue = when (detectedMode) {
            com.example.insuscan.analysis.ReferenceObjectDetector.DetectionMode.STRICT -> "INSULIN_SYRINGE"
            com.example.insuscan.analysis.ReferenceObjectDetector.DetectionMode.FLEXIBLE -> "SYRINGE_KNIFE"
            com.example.insuscan.analysis.ReferenceObjectDetector.DetectionMode.CARD -> "CARD"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Different Object Detected")
            .setMessage("You selected \"$selectedName\" but we detected \"$detectedName\".\n\nUse the detected object for more accurate measurements?")
            .setPositiveButton("Use $detectedName") { dialog, _ ->
                dialog.dismiss()
                showLoading(true, "Analyzing with detected reference...")
                selectedReferenceType = detectedServerValue
                if (::cameraManager.isInitialized) cameraManager.selectedReferenceType = detectedServerValue
                proceedWithPortionAnalysis(bitmap, imageFile, detectedServerValue)
            }
            .setNegativeButton("Ignore, Capture Anyway") { dialog, _ ->
                dialog.dismiss()
                showLoading(true, "Analyzing without reference object...")
                proceedWithPortionAnalysis(bitmap, imageFile, "NONE")
            }
            .setNeutralButton("Retake") { dialog, _ ->
                dialog.dismiss()
                switchToCameraMode()
            }
            .setCancelable(false)
            .show()
    }

    private fun showMissingRefObjectDialog() {
        val isArReady = arCoreManager?.isReady == true
        val isArSupported = arCoreManager?.isSupported == true
        val arButtonText = when {
            isArReady -> "Use AR Measurement ✅"
            isArSupported -> "Scan Surface (AR)"
            else -> "Scan Surface (N/A)"
        }
        val refType = ReferenceObjectHelper.fromServerValue(selectedReferenceType)
        val objectName = when (refType) {
            ReferenceObjectHelper.ReferenceObjectType.CARD -> "credit card"
            ReferenceObjectHelper.ReferenceObjectType.INSULIN_SYRINGE -> "insulin pen"
            ReferenceObjectHelper.ReferenceObjectType.SYRINGE_KNIFE -> "fork/knife"
            else -> "reference object"
        }
        val message = if (isArReady) {
            "The $objectName was not detected.\n\nAR is ready — we can measure automatically."
        } else {
            "The $objectName was not detected.\n\nMake sure it's visible, or scan the table surface."
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Reference Object Missing")
            .setMessage(message)
            .setPositiveButton(arButtonText) { dialog, _ ->
                if (isArReady) { dialog.dismiss(); onCaptureClicked() }
                else if (isArSupported) { dialog.dismiss(); startArScanMode() }
                else { dialog.dismiss(); ToastHelper.showLong(requireContext(), "Your device does not support AR.") }
            }
            .setNegativeButton("Capture Anyway") { dialog, _ -> dialog.dismiss(); onCaptureClicked() }
            .setNeutralButton("Cancel") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    private fun startArScanMode() {
        isScanningSurface = true
        arGuidanceOverlay.visibility = View.VISIBLE
        captureButton.isEnabled = false
        galleryButton.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            var waitCycles = 0
            val maxWait = 30
            while (waitCycles < maxWait && arCoreManager?.isReady != true) {
                kotlinx.coroutines.delay(200)
                waitCycles++
                activity?.runOnUiThread { arStatusText.text = "Scanning surface... Move phone slowly" }
            }
            activity?.runOnUiThread {
                if (arCoreManager?.isReady == true) {
                    ToastHelper.showShort(requireContext(), "Surface detected! Capturing...")
                    stopArScanMode()
                    onCaptureClicked()
                } else {
                    ToastHelper.showShort(requireContext(), "AR Scan timed out. Try again.")
                    stopArScanMode()
                }
            }
        }
    }

    private fun stopArScanMode() {
        isScanningSurface = false
        arGuidanceOverlay.visibility = View.GONE
        captureButton.isEnabled = true
        galleryButton.isEnabled = true
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
}