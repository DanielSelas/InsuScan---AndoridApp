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
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.insuscan.R
import com.example.insuscan.analysis.PortionEstimator
import com.example.insuscan.analysis.PortionResult
import com.example.insuscan.camera.CameraManager
import com.example.insuscan.camera.ImageQualityResult
import com.example.insuscan.camera.ImageValidator
import com.example.insuscan.camera.ValidationResult
import com.example.insuscan.meal.Meal
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.utils.ToastHelper
import com.example.insuscan.utils.TopBarHelper
import java.io.File
import com.example.insuscan.network.exception.ScanException

import androidx.lifecycle.lifecycleScope
import com.example.insuscan.meal.FoodItem
import com.example.insuscan.network.dto.MealDto
import com.example.insuscan.network.repository.ScanRepository
import com.example.insuscan.profile.UserProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.provider.MediaStore
import androidx.activity.result.PickVisualMediaRequest
import com.bumptech.glide.Glide
import com.example.insuscan.analysis.FoodRegionAnalyzer
import com.example.insuscan.network.repository.ScanRepositoryImpl

// ScanFragment - food scan screen with camera preview and portion analysis
class ScanFragment : Fragment(R.layout.fragment_scan) {

    companion object {
        private const val TAG = "ScanFragment"
    }

    // Views
    private lateinit var cameraPreview: PreviewView
    private lateinit var capturedImageView: ImageView
    private lateinit var captureButton: Button
    private lateinit var qualityStatusText: TextView
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var loadingMessage: TextView
    private lateinit var galleryButton: Button
    private lateinit var subtitleText: TextView
    private lateinit var tvCoachPill: TextView

    private var capturedImagePath: String? = null
    private var sideImageBitmap: android.graphics.Bitmap? = null

    // State: are we showing the captured image or live camera?
    private var isShowingCapturedImage = false

    // Selected reference object type (chosen before capture)
    private var selectedReferenceType: String? = null

    // Camera
    private lateinit var cameraManager: CameraManager
    private var isImageQualityOk = false

    // Portion analysis (ARCore + OpenCV)
    private var portionEstimator: PortionEstimator? = null

    // ARCore session manager for real depth + plate size
    private var arCoreManager: com.example.insuscan.ar.ArCoreManager? = null

    // Add as class member
    private val scanRepository = ScanRepositoryImpl()

    // Quality override timer
    private var plateInFrameStartTime: Long = 0L
    private var isForceCaptureAllowed = false

    // Camera permission flow
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            ToastHelper.showShort(requireContext(), "Camera permission is required to scan")
            findNavController().popBackStack()
        }
    }

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { processGalleryImage(it) }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Top bar (shared component)
        TopBarHelper.setupTopBar(
            rootView = view,
            title = "Scan your meal",
            onBack = { findNavController().navigate(R.id.homeFragment) }
        )

        findViews(view)
        initializeArCore()
        initializeCameraManager()
        initializePortionEstimator()
        initializeListeners()
        checkCameraPermission()

        // Add guide overlay for reference object
        addReferenceObjectOverlay(view)

        // Show reference object selection dialog immediately on screen entry
        showReferenceDialogThenAction {
            // Dialog done — user can now capture with the chosen type
            Log.d(TAG, "Reference type selected on entry: $selectedReferenceType")
        }

        // Initialize Orientation Helper
        orientationHelper = com.example.insuscan.camera.OrientationHelper(requireContext())
        orientationHelper.onOrientationChanged = { pitch, roll, isLevel ->
            if (isSidePhotoCaptureMode) {
                // For side photo, we want the phone held upright, looking at table
                // pitch around 0 means phone is vertical
                val isUpright = Math.abs(pitch) < 30.0
                activity?.runOnUiThread {
                    if (isUpright) {
                        tvCoachPill.text = "✅ Good angle"
                        tvCoachPill.setBackgroundResource(R.drawable.bg_coach_pill)
                        captureButton.isEnabled = true
                    } else {
                        tvCoachPill.text = "📐 Hold phone vertically"
                        tvCoachPill.setBackgroundResource(R.drawable.bg_pill_warning)
                        captureButton.isEnabled = false
                    }
                }
            } else {
                // Normal top-down behavior
                if (isDeviceLevel != isLevel) {
                    isDeviceLevel = isLevel
                    activity?.runOnUiThread {
                        // Update state and UI whenever tilt changes
                        cameraManager.lastQualityResult?.let { updateQualityUI(it) }
                    }
                }
            }
        }
    }

    private fun updateQualityUI(quality: ImageQualityResult) {
        // Track how long a plate has been in frame to allow "Force Capture"
        if (quality.isPlateFound) {
            if (plateInFrameStartTime == 0L) {
                plateInFrameStartTime = System.currentTimeMillis()
            } else {
                val elapsed = System.currentTimeMillis() - plateInFrameStartTime
                if (elapsed > 5000) { // 5 seconds
                    isForceCaptureAllowed = true
                }
            }
        } else {
            plateInFrameStartTime = 0L
            isForceCaptureAllowed = false
        }

        // 1. Update legacy quality status (bottom bar)
        val validationMsg = when {
            !isDeviceLevel -> "Hold Phone Flat 📱"
            !quality.isValid && isForceCaptureAllowed -> "Quality low, but you can capture now."
            else -> quality.getValidationMessage()
        }
        
        qualityStatusText.text = validationMsg
        
        val statusColor = when {
            quality.isValid && isDeviceLevel -> R.color.secondary_light
            isForceCaptureAllowed -> R.color.warning // Orange for force capture
            else -> R.color.error
        }
        qualityStatusText.setBackgroundColor(ContextCompat.getColor(requireContext(), statusColor))

        // 2. Decide if capture is allowed
        // Allowed if: perfect quality OR (plate found + 5 seconds passed)
        captureButton.isEnabled = (quality.isValid && isDeviceLevel) || isForceCaptureAllowed
        captureButton.alpha = if (captureButton.isEnabled) 1.0f else 0.5f
        
        // 3. Update Live Coach Pill (Top Overlay)
        // ... (rest of method) ...
        val refType = com.example.insuscan.utils.ReferenceObjectHelper.fromServerValue(
            selectedReferenceType
        )
        val refObjLabel = when (refType) {
            com.example.insuscan.utils.ReferenceObjectHelper.ReferenceObjectType.CARD -> "Place Card 💳"
            com.example.insuscan.utils.ReferenceObjectHelper.ReferenceObjectType.INSULIN_SYRINGE -> "Place Pen 🖊️"
            com.example.insuscan.utils.ReferenceObjectHelper.ReferenceObjectType.SYRINGE_KNIFE -> "Place Fork/Knife 🍴"
            else -> "Place Ref Obj"
        }

        val coachMessage = when {
            !isDeviceLevel -> "Phone Tilted 📐"
            !quality.isBrightnessOk && quality.brightness < 50f -> "Too Dark 🌑"
            !quality.isBrightnessOk && quality.brightness > 200f -> "Too Bright ☀️"
            !quality.isSharpnessOk -> "Image Blurry 📷"
            !quality.isPlateFound -> "Find Plate 🍽️"
            isRefObjectExpectedInFrame() && !quality.isReferenceObjectFound -> refObjLabel
            else -> "Perfect! ✅"
        }

        tvCoachPill.apply {
            text = coachMessage
            visibility = View.VISIBLE

            // Color coding for the pill
            background.setTint(
                if (coachMessage == "Perfect! ✅") android.graphics.Color.parseColor("#994CAF50") // Green
                else android.graphics.Color.parseColor("#99F44336") // Red for errors
            )
        }
    }

    private fun addReferenceObjectOverlay(rootView: View) {
        val context = requireContext()
        val container = rootView as? FrameLayout ?: return

        // Create overlay container
        val overlay = FrameLayout(context).apply {
            // Semi-transparent overlay on the right side (20% width)
            layoutParams = FrameLayout.LayoutParams(
                (resources.displayMetrics.widthPixels * 0.25).toInt(),
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = android.view.Gravity.END
            }
            setBackgroundColor(android.graphics.Color.parseColor("#2000FF00")) // Very faint green tint

            // Add border/stroke effect (using a simple view for border)
            val border = View(context).apply {
                layoutParams =
                    FrameLayout.LayoutParams(4, FrameLayout.LayoutParams.MATCH_PARENT).apply {
                        gravity = android.view.Gravity.START
                    }
                setBackgroundColor(android.graphics.Color.parseColor("#80FFFFFF"))
            }
            addView(border)

            // Add Text Label
            val label = TextView(context).apply {
                text = "Reference\nObject\nZone"
                setTextColor(ContextCompat.getColor(context, R.color.white))
                textSize = 14f
                gravity = android.view.Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = android.view.Gravity.CENTER
                }
            }
            addView(label)
        }

        // Add behind the other UI elements but on top of camera
        container.addView(overlay, 1) // Index 1 to be above camera (0) but below controls

        // Reset force capture state
        plateInFrameStartTime = 0L
        isForceCaptureAllowed = false
    }

    private lateinit var arGuidanceOverlay: View
    private lateinit var arStatusText: TextView
    private lateinit var btnCancelAr: Button

    private fun findViews(view: View) {
        cameraPreview = view.findViewById(R.id.camera_preview)
        capturedImageView = view.findViewById(R.id.iv_captured_image)
        captureButton = view.findViewById(R.id.btn_capture)
        qualityStatusText = view.findViewById(R.id.tv_quality_status)
        loadingOverlay = view.findViewById(R.id.loading_overlay)
        loadingMessage = view.findViewById(R.id.tv_loading_message)
        galleryButton = view.findViewById(R.id.btn_gallery)
        subtitleText = view.findViewById(R.id.tv_scan_subtitle)
        tvCoachPill = view.findViewById(R.id.tv_coach_pill)

        // AR Overlay Views (from include)
        arGuidanceOverlay = view.findViewById(R.id.ar_guidance_overlay)
        arStatusText = view.findViewById(R.id.tv_ar_status)
        btnCancelAr = view.findViewById(R.id.btn_cancel_ar)

        btnCancelAr.setOnClickListener {
            stopArScanMode()
        }
    }

    // ... (rest of simple methods)

    private fun showMissingRefObjectDialog() {
        val isArReady = arCoreManager?.isReady == true
        val isArSupported = arCoreManager?.isSupported == true
        val arButtonText = when {
            isArReady -> "Use AR Measurement ✅"
            isArSupported -> "Scan Surface (AR)"
            else -> "Scan Surface (N/A)"
        }

        // Build message based on selected reference type
        val refType =
            com.example.insuscan.utils.ReferenceObjectHelper.fromServerValue(selectedReferenceType)
        val objectName = when (refType) {
            com.example.insuscan.utils.ReferenceObjectHelper.ReferenceObjectType.CARD -> "credit card"
            com.example.insuscan.utils.ReferenceObjectHelper.ReferenceObjectType.INSULIN_SYRINGE -> "insulin pen"
            com.example.insuscan.utils.ReferenceObjectHelper.ReferenceObjectType.SYRINGE_KNIFE -> "fork/knife"
            else -> "reference object"
        }

        val message = if (isArReady) {
            "The $objectName was not detected.\n\nAR is ready — we can measure the plate size automatically using depth sensing."
        } else {
            "The $objectName was not detected in the image.\n\nMake sure it is placed next to the plate and clearly visible.\n\nTo ensure accurate results, we can scan the table surface instead."
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Reference Object Missing")
            .setMessage(message)
            .setPositiveButton(arButtonText) { dialog, _ ->
                if (isArReady) {
                    dialog.dismiss()
                    // AR already has measurements — capture directly
                    onCaptureClicked()
                } else if (isArSupported) {
                    dialog.dismiss()
                    startArScanMode()
                } else {
                    dialog.dismiss()
                    ToastHelper.showLong(
                        requireContext(),
                        "Your device does not support AR features needed for this mode."
                    )
                }
            }
            .setNegativeButton("Capture Anyway") { dialog, _ ->
                dialog.dismiss()
                onCaptureClicked()
            }
            .setNeutralButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun startArScanMode() {
        isScanningSurface = true
        arGuidanceOverlay.visibility = View.VISIBLE
        captureButton.isEnabled = false
        galleryButton.isEnabled = false

        // ARCore continuously updates during live preview.
        // We just wait until it reports ready (enough depth frames accumulated).
        viewLifecycleOwner.lifecycleScope.launch {
            var waitCycles = 0
            val maxWait = 30 // ~6 seconds at 200ms interval
            while (waitCycles < maxWait && arCoreManager?.isReady != true) {
                kotlinx.coroutines.delay(200)
                waitCycles++
                activity?.runOnUiThread {
                    arStatusText.text = "Scanning surface... Move phone slowly"
                }
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

    // Dev mode version - forces good quality for emulator testing
    private fun initializeCameraManager() {
        // Camera setup
        cameraManager = CameraManager(requireContext())
        cameraManager.arCoreManager = arCoreManager // Feed AR frames during live preview
        cameraManager.onImageQualityUpdate = { quality ->
            updateQualityUI(quality)
        }

        cameraPreview.post {
            cameraManager.startCamera(viewLifecycleOwner, cameraPreview)
        }
    }

    /**
     * Shows a dialog asking the user to take a side-angle photo for better depth estimation.
     * This is triggered as a fallback when ARCore doesn't provide measurements.
     */
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
                "This helps us measure how deep the container is, which improves portion accuracy.\n\n" +
                "Hold your phone at table level and capture the container from the side."
            )
            .setPositiveButton("📸 Take Side Photo") { dialog, _ ->
                dialog.dismiss()
                // Store the top-down bitmap and imageFile for later use
                pendingSidePhotoBitmap = topDownBitmap
                pendingSidePhotoFile = imageFile
                pendingSidePhotoRefType = refType
                // Reset to live camera for the side photo
                showLoading(false) // IMPORTANT: Dismiss the "Analyzing..." loading screen
                isShowingCapturedImage = false
                capturedImageView.visibility = View.GONE
                cameraPreview.visibility = View.VISIBLE
                captureButton.isEnabled = false // wait for orientation angle
                isSidePhotoCaptureMode = true
                subtitleText.text = "Capture from the SIDE"
                tvCoachPill.text = "📐 Hold phone upright"
                tvCoachPill.setBackgroundResource(R.drawable.bg_pill_warning)
                tvCoachPill.visibility = View.VISIBLE
            }
            .setNegativeButton("Skip") { dialog, _ ->
                dialog.dismiss()
                showLoading(true, "Analyzing your meal...") // Keep loading if skipping
                // Continue without side photo
                proceedWithPortionAnalysis(topDownBitmap, imageFile, refType, sideImage = null)
            }
            .setCancelable(false)
            .show()
    }

    // Pending side-photo state
    private var isSidePhotoCaptureMode = false
    private var sidePhotoOffered = false
    private var pendingSidePhotoBitmap: android.graphics.Bitmap? = null
    private var pendingSidePhotoFile: File? = null
    private var pendingSidePhotoRefType: String? = null

    // Initialize ARCore manager
    private fun initializeArCore() {
        arCoreManager = com.example.insuscan.ar.ArCoreManager(requireContext())
        val arReady = arCoreManager?.initialize(requireActivity()) == true
        Log.d(TAG, "ArCoreManager initialized: $arReady (supported=${arCoreManager?.isSupported})")
    }

    // Initialize OpenCV for portion estimation
    private fun initializePortionEstimator() {
        portionEstimator = PortionEstimator(requireContext())

        val result = portionEstimator?.initialize()

        result?.let {
            val cvStatus = if (it.openCvReady) "Ready" else "Failed"
            Log.d(TAG, "Portion estimator init — OpenCV: $cvStatus")

            if (!it.isReady) {
                Log.w(TAG, "Analysis module not fully ready")
            }
        }
    }

    /** Returns true if the user chose a reference object type that needs live detection */
    private fun isRefObjectExpectedInFrame(): Boolean {
        // Skip requirement for side photos (reference object only needed in top-down view)
        if (isSidePhotoCaptureMode) return false
        
        val refType =
            com.example.insuscan.utils.ReferenceObjectHelper.fromServerValue(selectedReferenceType)
        return refType != null &&
                refType != com.example.insuscan.utils.ReferenceObjectHelper.ReferenceObjectType.NONE
    }

    private fun initializeListeners() {
        captureButton.setOnClickListener {
            if (isShowingCapturedImage) {
                // User wants to retake - go back to camera mode
                switchToCameraMode()
            } else {
                // User wants to capture — dialog already shown on screen entry
                val result = cameraManager.lastQualityResult

                if (result != null) {
                    if (result.isPlateFound) {
                        // If user chose pen/syringe, warn if not found in frame
                        if (isRefObjectExpectedInFrame() && !result.isReferenceObjectFound) {
                            showMissingRefObjectDialog()
                        } else {
                            onCaptureClicked()
                        }
                    } else {
                        ToastHelper.showShort(
                            requireContext(),
                            "Please center the food plate first"
                        )
                    }
                } else {
                    ToastHelper.showShort(requireContext(), "Camera initializing...")
                }
            }
        }
        galleryButton.setOnClickListener {
            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    private fun showReferenceDialogThenAction(action: () -> Unit) {
        com.example.insuscan.utils.ReferenceObjectHelper.showSelectionDialog(requireContext()) { selectedType ->
            selectedReferenceType = selectedType.serverValue
            // Sync to camera manager so live preview uses correct detection mode
            cameraManager.selectedReferenceType = selectedType.serverValue
            action()
        }
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                startCamera()
            }

            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                ToastHelper.showLong(requireContext(), "Camera permission is required for scanning")
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }

            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
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

    private lateinit var orientationHelper: com.example.insuscan.camera.OrientationHelper
    private var isDeviceLevel = true
    private var isScanningSurface = false


    override fun onResume() {
        super.onResume()
        if (::orientationHelper.isInitialized) {
            orientationHelper.start()
        }
        arCoreManager?.resume()
    }

    override fun onPause() {
        super.onPause()
        if (::orientationHelper.isInitialized) {
            orientationHelper.stop()
        }
        arCoreManager?.pause()
    }



    // In updateQualityStatus, we can also integrate this check
    // ...

    // Updates UI based on live quality checks


    private fun onCaptureClicked() {
        // Side photo capture mode: capture and resume analysis with both photos
        if (isSidePhotoCaptureMode) {
            showLoading(true, "Capturing side photo...")
            val outputDir = requireContext().cacheDir
            cameraManager.captureImage(
                outputDirectory = outputDir,
                onImageCaptured = { sideFile ->
                    isSidePhotoCaptureMode = false
                    val sideBitmap = android.graphics.BitmapFactory.decodeFile(sideFile.absolutePath)
                    val topBitmap = pendingSidePhotoBitmap
                    val origFile = pendingSidePhotoFile
                    val origRefType = pendingSidePhotoRefType

                    // Show original captured image again
                    if (origFile != null) {
                        switchToCapturedImageMode(origFile)
                    }
                    subtitleText.text = "Analyzing with side photo..."
                    tvCoachPill.visibility = View.GONE

                    // Clear pending state
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

        val outputDir = requireContext().cacheDir

        cameraManager.captureImage(
            outputDirectory = outputDir,
            onImageCaptured = { imageFile ->
                // Show captured image immediately
                capturedImagePath = imageFile.absolutePath
                switchToCapturedImageMode(imageFile)

                // Then process it
                validateAndProcessImage(imageFile)
            },
            onError = { errorMessage ->
                showLoading(false)
                ToastHelper.showShort(requireContext(), errorMessage)
            }
        )
    }

    // Switch UI to show the captured image instead of camera
    private fun switchToCapturedImageMode(imageFile: File) {
        isShowingCapturedImage = true

        // Load and display the captured image
        Glide.with(this)
            .load(imageFile)
            .into(capturedImageView)

        // Toggle visibility
        cameraPreview.visibility = View.GONE
        capturedImageView.visibility = View.VISIBLE

        // Update button text
        captureButton.text = "Retake"
        captureButton.isEnabled = true
        captureButton.alpha = 1f

        // Update subtitle
        subtitleText.text = "Analyzing your meal..."

        // Update status bar
        qualityStatusText.text = "Image captured"
        qualityStatusText.setBackgroundColor(
            ContextCompat.getColor(requireContext(), R.color.primary_light)
        )
    }

    // Switch UI back to camera mode
    private fun switchToCameraMode() {
        isShowingCapturedImage = false
        capturedImagePath = null
        sidePhotoOffered = false

        // Toggle visibility
        capturedImageView.visibility = View.GONE
        cameraPreview.visibility = View.VISIBLE

        // Update button text
        captureButton.text = "Capture"

        // Update subtitle
        subtitleText.text = "Place your plate and insulin pen in the frame, then tap Capture"

        // Reset status
        qualityStatusText.text = "Ready to capture"
        qualityStatusText.setBackgroundColor(
            ContextCompat.getColor(requireContext(), R.color.secondary_light)
        )
    }

    // Dev mode version - skips validation for emulator
    private fun validateAndProcessImage(imageFile: File) {
        val devMode = true

        if (devMode) {
            analyzePortionAndContinue(imageFile)
            return
        }

        val validationResult = ImageValidator.validateCapturedImage(imageFile)

        when (validationResult) {
            is ValidationResult.Valid -> {
                analyzePortionAndContinue(imageFile)
            }

            is ValidationResult.Invalid -> {
                showLoading(false)
                ToastHelper.showLong(
                    requireContext(),
                    "Image quality issues:\n${validationResult.getFormattedMessage()}"
                )
            }

            is ValidationResult.Error -> {
                showLoading(false)
                ToastHelper.showShort(requireContext(), validationResult.message)
            }
        }
    }

    private fun analyzePortionAndContinue(imageFile: File) {
        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
        capturedImagePath = imageFile.absolutePath

        if (bitmap == null) {
            Log.e(TAG, "Failed to decode image file")
            showLoading(false)
            ToastHelper.showShort(requireContext(), "Failed to process image")
            return
        }

        showLoading(true, "Analyzing your meal...")

        // Smart reference object pre-check
        val refType =
            com.example.insuscan.utils.ReferenceObjectHelper.fromServerValue(selectedReferenceType)
        val detectionMode = when (refType) {
            com.example.insuscan.utils.ReferenceObjectHelper.ReferenceObjectType.INSULIN_SYRINGE ->
                com.example.insuscan.analysis.ReferenceObjectDetector.DetectionMode.STRICT

            com.example.insuscan.utils.ReferenceObjectHelper.ReferenceObjectType.SYRINGE_KNIFE ->
                com.example.insuscan.analysis.ReferenceObjectDetector.DetectionMode.FLEXIBLE

            com.example.insuscan.utils.ReferenceObjectHelper.ReferenceObjectType.CARD ->
                com.example.insuscan.analysis.ReferenceObjectDetector.DetectionMode.CARD

            else -> null
        }

        if (detectionMode != null) {
            // Run smart fallback in background
            viewLifecycleOwner.lifecycleScope.launch {
                val fallbackResult = withContext(Dispatchers.IO) {
                    portionEstimator?.referenceDetector?.detectWithFallback(
                        bitmap,
                        null,
                        detectionMode
                    )
                }

                if (fallbackResult != null && fallbackResult.isAlternative && fallbackResult.result is com.example.insuscan.analysis.DetectionResult.Found) {
                    // Found a different object than selected! Show dialog
                    showLoading(false)
                    showAlternativeRefObjectDialog(
                        bitmap = bitmap,
                        imageFile = imageFile,
                        selectedType = refType!!,
                        detectedMode = fallbackResult.detectedMode!!
                    )
                } else {
                    // Either found the correct type, or nothing found — proceed normally
                    proceedWithPortionAnalysis(bitmap, imageFile, selectedReferenceType)
                }
            }
        } else {
            // No reference object expected (NONE) — proceed directly
            proceedWithPortionAnalysis(bitmap, imageFile, selectedReferenceType)
        }
    }

    /**
     * Shows a dialog when a different reference object was detected than what the user selected.
     * Offers to use the detected object instead, or proceed without it.
     */
    private fun showAlternativeRefObjectDialog(
        bitmap: android.graphics.Bitmap,
        imageFile: File,
        selectedType: com.example.insuscan.utils.ReferenceObjectHelper.ReferenceObjectType,
        detectedMode: com.example.insuscan.analysis.ReferenceObjectDetector.DetectionMode
    ) {
        val selectedName = getString(selectedType.displayNameResId)
        val detectedName = when (detectedMode) {
            com.example.insuscan.analysis.ReferenceObjectDetector.DetectionMode.STRICT -> getString(
                R.string.ref_option_insulin_syringe
            )

            com.example.insuscan.analysis.ReferenceObjectDetector.DetectionMode.FLEXIBLE -> getString(
                R.string.ref_option_syringe_knife
            )

            com.example.insuscan.analysis.ReferenceObjectDetector.DetectionMode.CARD -> getString(R.string.ref_option_card)
        }
        val detectedServerValue = when (detectedMode) {
            com.example.insuscan.analysis.ReferenceObjectDetector.DetectionMode.STRICT -> "INSULIN_SYRINGE"
            com.example.insuscan.analysis.ReferenceObjectDetector.DetectionMode.FLEXIBLE -> "SYRINGE_KNIFE"
            com.example.insuscan.analysis.ReferenceObjectDetector.DetectionMode.CARD -> "CARD"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Different Object Detected")
            .setMessage("You selected \"$selectedName\" but we detected what looks like \"$detectedName\".\n\nWould you like to use the detected object for more accurate measurements?")
            .setPositiveButton("Use $detectedName") { dialog, _ ->
                dialog.dismiss()
                showLoading(true, "Analyzing with detected reference...")
                // Override reference type with detected alternative
                selectedReferenceType = detectedServerValue
                cameraManager.selectedReferenceType = detectedServerValue
                proceedWithPortionAnalysis(bitmap, imageFile, detectedServerValue)
            }
            .setNegativeButton("Ignore, Capture Anyway") { dialog, _ ->
                dialog.dismiss()
                showLoading(true, "Analyzing without reference object...")
                // Proceed with NONE — no reference object
                proceedWithPortionAnalysis(bitmap, imageFile, "NONE")
            }
            .setNeutralButton("Retake") { dialog, _ ->
                dialog.dismiss()
                switchToCameraMode()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Proceeds with the actual portion analysis and server upload.
     * Uses ARCore measurement when available for real depth + plate size.
     */
    private fun proceedWithPortionAnalysis(
        bitmap: android.graphics.Bitmap,
        imageFile: File,
        refType: String?,
        sideImage: android.graphics.Bitmap? = null
    ) {
        val email = UserProfileManager.getUserEmail(requireContext()) ?: "test@example.com"

        // detect plate ONCE — reuse for AR, portion estimation, and GrabCut
        val plateResult = com.example.insuscan.analysis.PlateDetector().detectPlate(bitmap)
        val plateBounds = if (plateResult.isFound) plateResult.bounds else null

        // use cached plate bounds for AR projection
        val arMeasurement = if (arCoreManager?.isReady == true && plateBounds != null) {
            arCoreManager?.measurePlate(plateBounds, bitmap.width, bitmap.height)
        } else null

        if (arMeasurement != null) {
            Log.d(
                TAG, "AR measurement: depth=${arMeasurement.depthCm}cm, " +
                        "diameter=${arMeasurement.plateDiameterCm}cm"
            )
        } else {
            Log.d(TAG, "No AR measurement available")
        }

        // Local portion analysis with AR data
        // pass cached plate result so it won't re-detect
        val portionResult = portionEstimator?.estimatePortion(
            bitmap,
            refType,
            arMeasurement,
            plateResult
        )        // Send null for weight/volume when 0 — server will calculate using plate physics
        // Send null for weight/volume when 0 — server will calculate using plate physics
        val successRes = portionResult as? PortionResult.Success
        val rawWeight = successRes?.estimatedWeightGrams
        val estimatedWeight = if (rawWeight != null && rawWeight > 0f) rawWeight else null
        val rawVolume = successRes?.volumeCm3
        val volumeCm3 = if (rawVolume != null && rawVolume > 0f) rawVolume else null

        // CRITICAL: Only send diameter/depth if they come from ARCore AND no reference object was found.
        // If a reference object (Card/Syringe) is detected, we send null to force the AI to use its physical scale.
        // This prevents inaccurate ARCore data (e.g., 33.5cm) from biasing the AI.
        val diameter = if (successRes?.referenceObjectDetected == true) {
            null
        } else if (successRes?.arMeasurementUsed == true) {
            successRes.plateDiameterCm
        } else null

        val depth = if (successRes?.referenceObjectDetected == true) {
            null
        } else if (successRes?.arMeasurementUsed == true && successRes.arDepthIsReal) {
            successRes.depthCm
        } else null

        val confidence = successRes?.confidence
        val containerType = successRes?.containerType?.name

        // If no depth measurement (from AR) and no side image yet, prompt user for side photo (once)
        // We prompt if depth is missing (even if diameter was found via reference object)
        if (sideImage == null && depth == null && !sidePhotoOffered) {
            sidePhotoOffered = true
            activity?.runOnUiThread {
                showSidePhotoDialog(bitmap, imageFile, refType)
            }
            return
        }

        // Check for reference object discrepancy
        if (isRefObjectExpectedInFrame() && cameraManager.lastQualityResult?.isReferenceObjectFound == true) {
            if (successRes?.referenceObjectDetected == false) {
                Log.w(TAG, "DISCREPANCY: Reference object detected in preview but missed in final high-res capture!")
                activity?.runOnUiThread {
                   ToastHelper.showLong(requireContext(), "Reference object was lost in the final photo. Try holding steadier.")
                }
            }
        }

        // Check for warning from portion estimator
        val warning = (portionResult as? PortionResult.Success)?.warning
        if (warning != null) {
            Log.w(TAG, "Portion warning: $warning")
        }

        val referenceType = refType

// get pixelToCmRatio + plate bounds for GrabCut refinement
        // reuse cached plate bounds for ratio calc
        val pixelToCmRatio = (portionResult as? PortionResult.Success)?.let { pr ->
            if (pr.referenceObjectDetected && pr.plateDiameterCm > 0 && plateBounds != null) {
                pr.plateDiameterCm / plateBounds.width().toFloat()
            } else null
        }
        // plateBounds already defined above — just use it for GrabCut
        val plateBoundsForGrabCut = plateBounds

        viewLifecycleOwner.lifecycleScope.launch {
            // step 1: send image to server (gets food items + P2/P3 weights)
            val scanResult = withContext(Dispatchers.IO) {
                scanRepository.scanImage(
                    bitmap, email, estimatedWeight, volumeCm3,
                    confidence, referenceType, diameter, depth,
                    containerType = containerType,
                    sideImageBitmap = sideImage
                )
            }

            scanResult.onSuccess { mealDto ->
                // step 2: if we have scale, try GrabCut refinement and re-scan with P1
                val foodItems = mealDto.foodItems?.map {
                    com.example.insuscan.mapping.FoodItemDtoMapper.map(it)
                } ?: emptyList()

                val hasBboxes = foodItems.any { it.bboxXPct != null && it.bboxWPct != null }

                if (pixelToCmRatio != null && pixelToCmRatio > 0f && hasBboxes) {
                    Log.d(TAG, "Running GrabCut refinement (P1 path)")
                    val regions = withContext(Dispatchers.IO) {
                        FoodRegionAnalyzer.analyze(
                            bitmap,
                            foodItems,
                            pixelToCmRatio,
                            plateBoundsForGrabCut
                        )
                    }

                    if (regions.isNotEmpty()) {
                        // re-scan with precise per-food measurements
                        val regionsJson = FoodRegionAnalyzer.toFoodRegionsJson(regions)
                        Log.d(TAG, "Sending ${regions.size} food regions to server")

                        val refinedResult = withContext(Dispatchers.IO) {
                            scanRepository.scanImage(
                                bitmap, email, estimatedWeight, volumeCm3,
                                confidence, referenceType, diameter, depth,
                                containerType = containerType,
                                foodRegionsJson = regionsJson
                            )
                        }

                        refinedResult.onSuccess { refinedDto ->
                            Log.d(TAG, "P1 refined scan success")
                            handleScanSuccess(refinedDto, portionResult)
                        }.onFailure { error ->
                            // P1 failed — fall back to original P2/P3 result
                            Log.w(TAG, "P1 refinement failed, using P2/P3: ${error.message}")
                            handleScanSuccess(mealDto, portionResult)
                        }
                    } else {
                        // GrabCut returned nothing — use original result
                        handleScanSuccess(mealDto, portionResult)
                    }
                } else {
                    // no scale or no bboxes — use P2/P3 as-is
                    handleScanSuccess(mealDto, portionResult)
                }

            }.onFailure { error ->
                Log.e(TAG, "Scan failed: ${error.message}")
                showLoading(false)
                handleScanError(error)
            }
        }
    }

    // Handles different scan errors with appropriate UI feedback
    private fun handleScanError(error: Throwable) {
        when (error) {
            is ScanException.NoFoodDetected -> {
                showNoFoodDetectedDialog()
            }

            is ScanException.NetworkError -> {
                showScanFailedDialog("No internet connection. Please check your network and try again.")
            }

            is ScanException.ServerError -> {
                showScanFailedDialog("Server error. Please try again later.")
            }

            is ScanException.Unauthorized -> {
                ToastHelper.showLong(requireContext(), "Session expired. Please log in again.")
                // Optionally navigate to login
            }

            else -> {
                showScanFailedDialog("Something went wrong. Please try again.")
            }
        }
    }

    // Helper to convert server response to local Meal
    private fun convertMealDtoToMeal(dto: MealDto, portionResult: PortionResult?): Meal {
        // Use the centralized mapper which includes name sanitization logic
        val mappedMeal = com.example.insuscan.mapping.MealDtoMapper.map(dto)

        // Apply local overrides (portion analysis fallbacks that might not be in DTO if server failed to update)
        return mappedMeal.copy(
            portionWeightGrams = dto.estimatedWeight
                ?: (portionResult as? PortionResult.Success)?.estimatedWeightGrams,
            portionVolumeCm3 = dto.plateVolumeCm3
                ?: (portionResult as? PortionResult.Success)?.volumeCm3,
            plateDiameterCm = dto.plateDiameterCm
                ?: (portionResult as? PortionResult.Success)?.plateDiameterCm,
            plateDepthCm = dto.plateDepthCm ?: (portionResult as? PortionResult.Success)?.depthCm,
            analysisConfidence = dto.analysisConfidence
                ?: (portionResult as? PortionResult.Success)?.confidence,
            referenceObjectDetected = dto.referenceDetected
                ?: (portionResult as? PortionResult.Success)?.referenceObjectDetected
        )
    }

    private fun handleScanSuccess(mealDto: MealDto, portionResult: PortionResult?) {
        Log.d(TAG, "Scan response: status=${mealDto.status}, items=${mealDto.foodItems?.size}")

        // Check if server returned a failed scan (no food detected)
        if (mealDto.status == "FAILED" || mealDto.foodItems.isNullOrEmpty()) {
            showLoading(false)
            showNoFoodDetectedDialog()
            return
        }

        val meal = convertMealDtoToMeal(mealDto, portionResult).copy(imagePath = capturedImagePath)
        MealSessionManager.setCurrentMeal(meal)

        showLoading(false)
        navigateToSummary()
    }

    // Show dialog when no food is detected - clearer than a toast
    private fun showNoFoodDetectedDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("No Food Detected")
            .setMessage("We couldn't identify any food items in this image.\n\nTry taking a clearer photo with good lighting, or add items manually.")
            .setPositiveButton("Try Again") { dialog, _ ->
                dialog.dismiss()
                switchToCameraMode()
            }
            .setNegativeButton("Add Manually") { dialog, _ ->
                dialog.dismiss()
                // Create empty meal and go to manual entry
                val emptyMeal = Meal(
                    title = "Manual Entry",
                    carbs = 0f,
                    imagePath = capturedImagePath,
                    foodItems = emptyList(),
                    profileComplete = true
                )
                MealSessionManager.setCurrentMeal(emptyMeal)
                findNavController().navigate(R.id.action_scanFragment_to_manualEntryFragment)
            }
            .setCancelable(false)
            .show()
    }

    // Show dialog when scan fails (server error, network issue, etc.)
    private fun showScanFailedDialog(message: String) {
        AlertDialog.Builder(requireContext())
            .setTitle("Scan Failed")
            .setMessage(message)
            .setPositiveButton("Try Again") { dialog, _ ->
                dialog.dismiss()
                switchToCameraMode()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                switchToCameraMode()
            }
            .setCancelable(false)
            .show()
    }

    // Fallback when server is unavailable - no fake nutritional values
    private fun createFallbackMeal(portionResult: PortionResult?): Meal {
        return Meal(
            title = "Analysis failed",
            carbs = 0f,
            imagePath = capturedImagePath,
            foodItems = emptyList(),
            portionWeightGrams = (portionResult as? PortionResult.Success)?.estimatedWeightGrams,
            analysisConfidence = (portionResult as? PortionResult.Success)?.confidence
        )
    }

    private fun navigateToSummary() {
        findNavController().navigate(R.id.summaryFragment)
    }

    private fun showLoading(show: Boolean, message: String = "Processing image...") {
        loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        loadingMessage.text = message
        captureButton.isEnabled = !show
        galleryButton.isEnabled = !show
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraManager.shutdown()
        portionEstimator?.release()
        portionEstimator = null
        arCoreManager?.release()
        arCoreManager = null
    }

    // Old implementation removed in favor of the one added in findViews/startArScanMode section
    // Keeping this clean.


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

            switchToCapturedImageMode(cacheFile)

            showLoading(true, "Analyzing your meal...")

            analyzePortionAndContinue(cacheFile)

        } catch (e: Exception) {
            showLoading(false)
            ToastHelper.showShort(requireContext(), "Failed to process image: ${e.message}")
        }
    }
}