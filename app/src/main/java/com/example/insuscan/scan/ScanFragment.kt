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
        initializeCameraManager()
        initializePortionEstimator()
        initializeListeners()
        checkCameraPermission()
        
        // Add guide overlay for reference object
        addReferenceObjectOverlay(view)
        
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

    private fun showMissingPenDialog() {
        val isArSupported = portionEstimator?.isArCoreSupported == true
        val arButtonText = if (isArSupported) "Scan Surface (AR)" else "Scan Surface (N/A)"

        AlertDialog.Builder(requireContext())
            .setTitle("Reference Object Missing")
            .setMessage("The insulin pen was not detected.\n\nTo ensure accurate results, we can scan the table surface instead.")
            .setPositiveButton(arButtonText) { dialog, _ ->
                if (isArSupported) {
                    dialog.dismiss()
                    startArScanMode()
                } else {
                    // Keep dialog open or dismiss? User said "toast". 
                    // To show toast, we need to handle the click. The default behavior is dismiss.
                    // We can just show the toast and let it dismiss, or try to keep it open.
                    // Simpler: Dismiss and Toast.
                    dialog.dismiss()
                    ToastHelper.showLong(requireContext(), "Your device does not support AR features needed for this mode.")
                }
            }
            .setNegativeButton("Capture Anyway") { dialog, _ ->
                dialog.dismiss()
                onCaptureClicked() // Proceed with lower accuracy
            }
            .setNeutralButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun startArScanMode() {
        isScanningSurface = true
        // Show UI
        arGuidanceOverlay.visibility = View.VISIBLE
        captureButton.isEnabled = false
        galleryButton.isEnabled = false
        
        // Start Logic
        portionEstimator?.startArScan { success ->
             activity?.runOnUiThread {
                 if (success) {
                     // Surface Found!
                     ToastHelper.showShort(requireContext(), "Surface detected! Capturing...")
                     stopArScanMode()
                     onCaptureClicked() // Auto-capture or let user tap? Let's auto-capture for smooth flow.
                 } else {
                     ToastHelper.showShort(requireContext(), "AR Scan failed or not supported.")
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
        portionEstimator?.stopArScan()
    }

    // Dev mode version - forces good quality for emulator testing
    private fun initializeCameraManager() {
        // Camera setup
        cameraManager = CameraManager(requireContext())
        cameraManager.onImageQualityUpdate = { quality ->
            // Update legacy quality status (bottom bar)
            qualityStatusText.text = quality.getValidationMessage()
            qualityStatusText.setBackgroundColor(
                ContextCompat.getColor(requireContext(), if (quality.isValid) R.color.secondary_light else R.color.error)
            )
            captureButton.isEnabled = quality.isValid || true // Allow capture anyway for now (debug)

            // Update Live Coach Pill (Top Overlay)
            // Prioritize feedback: Light > Focus > Framing
            val coachMessage = when {
                !quality.isBrightnessOk && quality.brightness < 50f -> "Too Dark 🌑"
                !quality.isBrightnessOk && quality.brightness > 200f -> "Too Bright ☀️"
                !quality.isSharpnessOk -> "Hold Steady 📷"
                !quality.isPlateFound -> "Find Plate 🍽️"
                !quality.isReferenceObjectFound -> "Place Ref Obj on Right 🖊️"
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

    // Initialize ARCore + OpenCV for portion estimation
    private fun initializePortionEstimator() {
        portionEstimator = PortionEstimator(requireContext())

        val result = portionEstimator?.initialize()

        result?.let {
            val arStatus = it.arCoreStatus.name
            val cvStatus = if (it.openCvReady) "Ready" else "Failed"

            Log.d(TAG, "Portion estimator init - ARCore: $arStatus, OpenCV: $cvStatus")

            if (!it.isReady) {
                Log.w(TAG, "Analysis module not fully ready, using fallbacks")
            }
        }
    }

    private fun initializeListeners() {
        captureButton.setOnClickListener {
            if (isShowingCapturedImage) {
                // User wants to retake - go back to camera mode
                switchToCameraMode()
            } else {
                // User wants to capture
                val result = cameraManager.lastQualityResult

                if (result != null) {
                    if (result.isPlateFound) {
                        // Show reference dialog before capture
                        showReferenceDialogThenAction {
                            if (result.isReferenceObjectFound) {
                                onCaptureClicked()
                            } else {
                                showMissingPenDialog()
                            }
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
            // Show reference dialog before gallery pick
            showReferenceDialogThenAction {
                galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
        }
    }

    private fun showReferenceDialogThenAction(action: () -> Unit) {
        com.example.insuscan.utils.ReferenceObjectHelper.showSelectionDialog(requireContext()) { selectedType ->
            selectedReferenceType = selectedType.serverValue
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
    }

    override fun onPause() {
        super.onPause()
        if (::orientationHelper.isInitialized) {
            orientationHelper.stop()
        }
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
            // Case 3: Plate Found but No Ref -> Warn (Allow Capture)
            isPlateFound && !isRefFound -> {
                isImageQualityOk = true // Allow capture (Logic handled in click listener)
                
                val isArSupported = portionEstimator?.isArCoreSupported == true
                val msg = if (isArSupported) "Ref Obj Missing. Tap for AR Mode" else "Ref Obj Missing. Estimating..."
                
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

        // Get user email
        val email = UserProfileManager.getUserEmail(requireContext()) ?: "test@example.com"

        // Local portion analysis (optional - for extra data)
        val portionResult = portionEstimator?.estimatePortion(bitmap)
        val estimatedWeight = (portionResult as? PortionResult.Success)?.estimatedWeightGrams
        val volumeCm3 = (portionResult as? PortionResult.Success)?.volumeCm3
        val confidence = (portionResult as? PortionResult.Success)?.confidence

        // Use the reference type selected before capture
        val referenceType = selectedReferenceType

        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                scanRepository.scanImage(bitmap, email, estimatedWeight, volumeCm3, confidence, referenceType)
            }

            result.onSuccess { mealDto ->
                handleScanSuccess(mealDto, portionResult)

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
                    val result = scanRepository.scanImage(bitmap, email, null, null, null, selectedReferenceType)

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