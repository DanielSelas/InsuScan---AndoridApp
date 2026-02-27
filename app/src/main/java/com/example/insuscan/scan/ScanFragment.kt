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
            if (isDeviceLevel != isLevel) {
                isDeviceLevel = isLevel
                activity?.runOnUiThread {
                    updateCoachPill()
                }
            }
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
                 layoutParams = FrameLayout.LayoutParams(4, FrameLayout.LayoutParams.MATCH_PARENT).apply {
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
        val refType = com.example.insuscan.utils.ReferenceObjectHelper.fromServerValue(selectedReferenceType)
        val objectName = when (refType) {
            com.example.insuscan.utils.ReferenceObjectHelper.ReferenceObjectType.CARD -> "credit card"
            com.example.insuscan.utils.ReferenceObjectHelper.ReferenceObjectType.INSULIN_SYRINGE -> "insulin pen"
            com.example.insuscan.utils.ReferenceObjectHelper.ReferenceObjectType.SYRINGE_KNIFE -> "syringe/knife"
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
                    ToastHelper.showLong(requireContext(), "Your device does not support AR features needed for this mode.")
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
            // Update legacy quality status (bottom bar)
            qualityStatusText.text = quality.getValidationMessage()
            qualityStatusText.setBackgroundColor(
                ContextCompat.getColor(requireContext(), if (quality.isValid) R.color.secondary_light else R.color.error)
            )
            captureButton.isEnabled = quality.isValid || true // Allow capture anyway for now (debug)

            // Update Live Coach Pill (Top Overlay)
            // Prioritize feedback: Light > Focus > Framing
            // Coach message based on selected reference type
            val refType = com.example.insuscan.utils.ReferenceObjectHelper.fromServerValue(selectedReferenceType)
            val refObjLabel = when (refType) {
                com.example.insuscan.utils.ReferenceObjectHelper.ReferenceObjectType.CARD -> "Place Card 💳"
                com.example.insuscan.utils.ReferenceObjectHelper.ReferenceObjectType.INSULIN_SYRINGE -> "Place Pen 🖊️"
                com.example.insuscan.utils.ReferenceObjectHelper.ReferenceObjectType.SYRINGE_KNIFE -> "Place Ref Obj 🖊️"
                else -> "Place Ref Obj"
            }

            val coachMessage = when {
                !quality.isBrightnessOk && quality.brightness < 50f -> "Too Dark 🌑"
                !quality.isBrightnessOk && quality.brightness > 200f -> "Too Bright ☀️"
                !quality.isSharpnessOk -> "Hold Steady 📷"
                !quality.isPlateFound -> "Find Plate 🍽️"
                isRefObjectExpectedInFrame() && !quality.isReferenceObjectFound -> refObjLabel
                else -> "Perfect! ✅"
            }

            tvCoachPill.apply {
                text = coachMessage
                visibility = View.VISIBLE
                
                // Color coding for the pill
                background.setTint(
                    if (coachMessage.startsWith("Perfect")) android.graphics.Color.parseColor("#994CAF50") // Green tint
                    else android.graphics.Color.parseColor("#99000000") // Black tint
                )
            }
        }
        
        cameraPreview.post {
            cameraManager.startCamera(viewLifecycleOwner, cameraPreview)
        }
    }

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
        val refType = com.example.insuscan.utils.ReferenceObjectHelper.fromServerValue(selectedReferenceType)
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
                        ToastHelper.showShort(requireContext(), "Please center the food plate first")
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

    private fun updateCoachPill() {
        // If device is tilted, show warning
        if (!isDeviceLevel) {
            tvCoachPill.text = "Hold Phone Flat 📱"
            tvCoachPill.setBackgroundResource(R.drawable.bg_pill_warning) 
            // Assuming bg_pill_warning exists or reuse warning color
            tvCoachPill.background.setTint(ContextCompat.getColor(requireContext(), R.color.warning))
            tvCoachPill.visibility = View.VISIBLE
        } else {
            // Restore default text if needed or hide
            tvCoachPill.visibility = View.GONE
        }
    }
    
    // In updateQualityStatus, we can also integrate this check
    // ...

    // Updates UI based on live quality checks
    private fun updateQualityStatus(qualityResult: ImageQualityResult) {
        // Only update if we're in camera mode
        if (isScanningSurface || qualityResult == null) return

        val isPlateFound = qualityResult.isPlateFound
        val isRefFound = qualityResult.isReferenceObjectFound

        // Decide state with Priority: 
        // 1. Tilt (Hold Flat)
        // 2. Plate (Center)
        // 3. Ref Obj (Missing)
        // 4. Quality (Brightness/Blur)
        // 5. Success
        
        // Default text color and background
        var textColorRes = android.R.color.white
        var textBgRes = R.color.primary // Default or dark
        
        when {
            // Case 0: Tilt Check (Critical for accuracy)
            !isDeviceLevel -> {
                isImageQualityOk = false // Prevent bad angle shots? Or just warn? Warn is safer UX.
                // Let's allow capture but scream warning
                qualityStatusText.text = "Hold Phone Flat 📱"
                qualityStatusText.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.warning))
                captureButton.isEnabled = true 
                captureButton.alpha = 1f
            }
            
            // Case 1 & 2: No Plate -> Block
            !isPlateFound -> {
                isImageQualityOk = false
                qualityStatusText.text = "Plate not detected. Center the food."
                qualityStatusText.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.warning))
                captureButton.isEnabled = false
                captureButton.alpha = 0.5f
            }
            // Case 3: Plate Found but No Ref -> Warn only if pen/syringe expected
            isPlateFound && !isRefFound && isRefObjectExpectedInFrame() -> {
                isImageQualityOk = true // Allow capture (Logic handled in click listener)
                
                val isArReady = arCoreManager?.isReady == true
                val isArSupported = arCoreManager?.isSupported == true
                val msg = when {
                    isArReady -> "Ref Obj Missing. AR Ready 📐"
                    isArSupported -> "Ref Obj Missing. Tap for AR Mode"
                    else -> "Ref Obj Missing. Estimating..."
                }
                
                qualityStatusText.text = msg
                qualityStatusText.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.warning)) // Use yellow

                // Allow capture but maybe show a dialog on click
                captureButton.isEnabled = true
                captureButton.alpha = 1f
            }
            // Case 4: Both Found -> Check other quality metrics
            isPlateFound && isRefFound -> {
                if (!qualityResult.isValid) {
                    // Specific quality feedback
                    val msg = qualityResult.getValidationMessage() // Might say "Too Dark" etc
                    qualityStatusText.text = msg
                    qualityStatusText.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.warning))
                } else {
                    isImageQualityOk = true
                    qualityStatusText.text = "Perfect! Ready to capture."
                    qualityStatusText.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.secondary_light))
                }
                captureButton.isEnabled = true
                captureButton.alpha = 1f
            }
        }
        
        // Handle other quality issues (brightness/blur) as override warnings if critical
        if (!qualityResult.isBrightnessOk || !qualityResult.isSharpnessOk) {
             // We generally still allow capture if plate is found, but update text
             if (isPlateFound) {
                 qualityStatusText.text = qualityResult.getValidationMessage()
             }
        }
    }

    private fun onCaptureClicked() {
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
        val refType = com.example.insuscan.utils.ReferenceObjectHelper.fromServerValue(selectedReferenceType)
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
                    portionEstimator?.referenceDetector?.detectWithFallback(bitmap, null, detectionMode)
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
    private fun proceedWithPortionAnalysis(bitmap: android.graphics.Bitmap, imageFile: File, refType: String?) {
        val email = UserProfileManager.getUserEmail(requireContext()) ?: "test@example.com"

        // detect plate ONCE — reuse for AR, portion estimation, and GrabCut
        val plateResult = com.example.insuscan.analysis.PlateDetector().detectPlate(bitmap)
        val plateBounds = if (plateResult.isFound) plateResult.bounds else null

        // use cached plate bounds for AR projection
        val arMeasurement = if (arCoreManager?.isReady == true && plateBounds != null) {
            arCoreManager?.measurePlate(plateBounds, bitmap.width, bitmap.height)
        } else null

        if (arMeasurement != null) {
            Log.d(TAG, "AR measurement: depth=${arMeasurement.depthCm}cm, " +
                    "diameter=${arMeasurement.plateDiameterCm}cm")
        } else {
            Log.d(TAG, "No AR measurement available")
        }

        // Local portion analysis with AR data
        // pass cached plate result so it won't re-detect
        val portionResult = portionEstimator?.estimatePortion(bitmap, refType, arMeasurement, plateResult)        // Send null for weight/volume when 0 — server will calculate using plate physics
        val rawWeight = (portionResult as? PortionResult.Success)?.estimatedWeightGrams
        val estimatedWeight = if (rawWeight != null && rawWeight > 0f) rawWeight else null
        val rawVolume = (portionResult as? PortionResult.Success)?.volumeCm3
        val volumeCm3 = if (rawVolume != null && rawVolume > 0f) rawVolume else null
        val diameter = (portionResult as? PortionResult.Success)?.plateDiameterCm
        val depth = (portionResult as? PortionResult.Success)?.depthCm
        val confidence = (portionResult as? PortionResult.Success)?.confidence
        val containerType = (portionResult as? PortionResult.Success)?.containerType?.name

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
                    containerType = containerType
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
                        FoodRegionAnalyzer.analyze(bitmap, foodItems, pixelToCmRatio, plateBoundsForGrabCut)
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
            portionWeightGrams = dto.estimatedWeight ?: (portionResult as? PortionResult.Success)?.estimatedWeightGrams,
            portionVolumeCm3 = dto.plateVolumeCm3 ?: (portionResult as? PortionResult.Success)?.volumeCm3,
            plateDiameterCm = dto.plateDiameterCm ?: (portionResult as? PortionResult.Success)?.plateDiameterCm,
            plateDepthCm = dto.plateDepthCm ?: (portionResult as? PortionResult.Success)?.depthCm,
            analysisConfidence = dto.analysisConfidence ?: (portionResult as? PortionResult.Success)?.confidence,
            referenceObjectDetected = dto.referenceDetected ?: (portionResult as? PortionResult.Success)?.referenceObjectDetected
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
        // ... rest of existing function


        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap == null) {
                showLoading(false)
                ToastHelper.showShort(requireContext(), "Failed to load image")
                return
            }

            // Save gallery image to cache for summary screen
            val cacheFile = File(requireContext().cacheDir, "gallery_${System.currentTimeMillis()}.jpg")
            cacheFile.outputStream().use { out ->
                bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
            }
            capturedImagePath = cacheFile.absolutePath

            // Show the selected image
            switchToCapturedImageMode(cacheFile)

            showLoading(true, "Analyzing your meal...")

            // Get user email
            val email = UserProfileManager.getUserEmail(requireContext()) ?: "test@example.com"

            // Send to server
            lifecycleScope.launch {
                try {
                    val result = scanRepository.scanImage(
                        bitmap, email, 
                        null, null, null, selectedReferenceType,
                        null, null
                    )

                    withContext(Dispatchers.Main) {
                        result.onSuccess { mealDto ->
                            handleScanSuccess(mealDto, null)
                        }.onFailure { error ->
                            Log.e(TAG, "Scan failed: ${error.message}")
                            showLoading(false)
                            handleScanError(error)
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        showLoading(false)
                        Log.e(TAG, "Error: ${e.message}")
                        showScanFailedDialog("Error: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            showLoading(false)
            ToastHelper.showShort(requireContext(), "Failed to process image: ${e.message}")
        }
    }
}