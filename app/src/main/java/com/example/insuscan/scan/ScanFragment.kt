package com.example.insuscan.scan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
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

import androidx.lifecycle.lifecycleScope
import com.example.insuscan.meal.FoodItem
import com.example.insuscan.network.dto.MealDto
import com.example.insuscan.network.repository.ScanRepository
import com.example.insuscan.profile.UserProfileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


// ScanFragment - food scan screen with camera preview and portion analysis
class ScanFragment : Fragment(R.layout.fragment_scan) {

    companion object {
        private const val TAG = "ScanFragment"
    }

    // Views
    private lateinit var cameraPreview: PreviewView
    private lateinit var captureButton: Button
    private lateinit var qualityStatusText: TextView
    private lateinit var loadingOverlay: FrameLayout

    // Camera
    private lateinit var cameraManager: CameraManager
    private var isImageQualityOk = false

    // Portion analysis (ARCore + OpenCV)
    private var portionEstimator: PortionEstimator? = null

    // Add as class member
    private val scanRepository = ScanRepository()

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
    }

    private fun findViews(view: View) {
        cameraPreview = view.findViewById(R.id.camera_preview)
        captureButton = view.findViewById(R.id.btn_capture)
        qualityStatusText = view.findViewById(R.id.tv_quality_status)
        loadingOverlay = view.findViewById(R.id.loading_overlay)
    }

    // Real version - use this on physical device
//    private fun initializeCameraManager() {
//        cameraManager = CameraManager(requireContext())
//        cameraManager.onImageQualityUpdate = { qualityResult ->
//            updateQualityStatus(qualityResult)
//        }
//    }

    // Dev mode version - forces good quality for emulator testing
    private fun initializeCameraManager() {
        cameraManager = CameraManager(requireContext())

        cameraManager.onImageQualityUpdate = { qualityResult ->
            // Dev mode: force good result for emulator testing
            val devModeResult = ImageQualityResult(
                brightness = 120f,
                isBrightnessOk = true,
                sharpness = 600f,
                isSharpnessOk = true,
                resolution = 1920 * 1080,
                isResolutionOk = true
            )

            // TODO: Switch to qualityResult on real device
            updateQualityStatus(devModeResult)
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
                // OpenCV failed but we can continue with fallback values
                Log.w(TAG, "Analysis module not fully ready, using fallbacks")
            }
        }
    }

    private fun initializeListeners() {
        captureButton.setOnClickListener {
            if (isImageQualityOk) {
                onCaptureClicked()
            } else {
                ToastHelper.showShort(requireContext(), "Please wait until image quality is OK")
            }
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

    // Updates UI based on live quality checks
    private fun updateQualityStatus(qualityResult: ImageQualityResult) {
        isImageQualityOk = qualityResult.isValid

        qualityStatusText.text = qualityResult.getValidationMessage()

        val backgroundColor = if (qualityResult.isValid) {
            ContextCompat.getColor(requireContext(), android.R.color.holo_green_light)
        } else {
            ContextCompat.getColor(requireContext(), android.R.color.holo_orange_light)
        }
        qualityStatusText.setBackgroundColor(backgroundColor)

        captureButton.isEnabled = qualityResult.isValid
        captureButton.alpha = if (qualityResult.isValid) 1f else 0.5f
    }

    private fun onCaptureClicked() {
        showLoading(true)

        val outputDir = requireContext().cacheDir

        cameraManager.captureImage(
            outputDirectory = outputDir,
            onImageCaptured = { imageFile ->
                validateAndProcessImage(imageFile)
            },
            onError = { errorMessage ->
                showLoading(false)
                ToastHelper.showShort(requireContext(), errorMessage)
            }
        )
    }

    // Real version - use on physical device
//    private fun validateAndProcessImage(imageFile: File) {
//        val validationResult = ImageValidator.validateCapturedImage(imageFile)
//
//        when (validationResult) {
//            is ValidationResult.Valid -> {
//                // Run portion estimation on the captured image
//                analyzePortionAndContinue(imageFile)
//            }
//            is ValidationResult.Invalid -> {
//                showLoading(false)
//                ToastHelper.showLong(
//                    requireContext(),
//                    "Image quality issues:\n${validationResult.getFormattedMessage()}"
//                )
//            }
//            is ValidationResult.Error -> {
//                showLoading(false)
//                ToastHelper.showShort(requireContext(), validationResult.message)
//            }
//        }
//    }

    // Dev mode version - skips validation for emulator
    private fun validateAndProcessImage(imageFile: File) {
        val devMode = true

        if (devMode) {
            // Skip validation, go straight to analysis
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


    // Replace the analyzePortionAndContinue function:
    private fun analyzePortionAndContinue(imageFile: File) {
        val bitmap = android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath)

        if (bitmap == null) {
            Log.e(TAG, "Failed to decode image file")
            showLoading(false)
            ToastHelper.showShort(requireContext(), "Failed to process image")
            return
        }

        // Get user email
        val email = UserProfileManager.getUserEmail(requireContext()) ?: "test@example.com"

        // Local portion analysis (optional - for extra data)
        val portionResult = portionEstimator?.estimatePortion(bitmap)
        val estimatedWeight = (portionResult as? PortionResult.Success)?.estimatedWeightGrams
        val confidence = (portionResult as? PortionResult.Success)?.confidence

        // Send to server
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                scanRepository.scanImage(bitmap, email, estimatedWeight, confidence)
            }

            result.onSuccess { mealDto ->
                Log.d(TAG, "Server returned meal: ${mealDto.mealId}")

                // Convert server response to local Meal object
                val meal = convertMealDtoToMeal(mealDto, portionResult)
                MealSessionManager.setCurrentMeal(meal)
                showLoading(false)
                navigateToSummary()

            }.onFailure { error ->
                Log.e(TAG, "Server scan failed: ${error.message}")
                // Fallback to local mock data
                val meal = createFallbackMeal(portionResult)
                MealSessionManager.setCurrentMeal(meal)
                showLoading(false)
                navigateToSummary()
            }
        }
    }

    // Helper to convert server response to local Meal
    private fun convertMealDtoToMeal(dto: MealDto, portionResult: PortionResult?): Meal {
        val totalCarbs = dto.totalCarbs ?: dto.foodItems?.sumOf {
            (it.carbsGrams ?: 0f).toDouble()
        }?.toFloat() ?: 0f

        return Meal(
            title = dto.foodItems?.firstOrNull()?.name ?: "Detected meal",
            carbs = totalCarbs,
            portionWeightGrams = dto.estimatedWeight ?: (portionResult as? PortionResult.Success)?.estimatedWeightGrams,
            portionVolumeCm3 = dto.plateVolumeCm3 ?: (portionResult as? PortionResult.Success)?.volumeCm3,
            plateDiameterCm = dto.plateDiameterCm ?: (portionResult as? PortionResult.Success)?.plateDiameterCm,
            plateDepthCm = dto.plateDepthCm ?: (portionResult as? PortionResult.Success)?.depthCm,
            analysisConfidence = dto.analysisConfidence ?: (portionResult as? PortionResult.Success)?.confidence,
            referenceObjectDetected = dto.referenceDetected ?: (portionResult as? PortionResult.Success)?.referenceObjectDetected,
            foodItems = dto.foodItems?.map { item ->
                FoodItem(
                    name = item.name,
                    nameHebrew = item.nameHebrew,
                    carbsGrams = item.carbsGrams,
                    weightGrams = item.estimatedWeightGrams,
                    confidence = item.confidence
                )
            }
        )
    }

    // Fallback when server is unavailable
    private fun createFallbackMeal(portionResult: PortionResult?): Meal {
        return when (portionResult) {
            is PortionResult.Success -> {
                Meal(
                    title = "Detected meal",
                    carbs = portionResult.estimatedWeightGrams * 0.2f, // rough estimate
                    portionWeightGrams = portionResult.estimatedWeightGrams,
                    portionVolumeCm3 = portionResult.volumeCm3,
                    plateDiameterCm = portionResult.plateDiameterCm,
                    plateDepthCm = portionResult.depthCm,
                    analysisConfidence = portionResult.confidence,
                    referenceObjectDetected = portionResult.referenceObjectDetected
                )
            }
            else -> Meal(title = "Unknown meal", carbs = 30f)
        }
    }

    private fun createMealFromAnalysis(result: PortionResult.Success): Meal {
        val estimatedCarbs = estimateCarbsFromPortion(result.estimatedWeightGrams)

        return Meal(
            title = "Detected meal",
            carbs = estimatedCarbs,
            portionWeightGrams = result.estimatedWeightGrams,
            portionVolumeCm3 = result.volumeCm3,
            plateDiameterCm = result.plateDiameterCm,
            plateDepthCm = result.depthCm,
            analysisConfidence = result.confidence,
            referenceObjectDetected = result.referenceObjectDetected
        )
    }

    private fun estimateCarbsFromPortion(weightGrams: Float): Float {
        val avgCarbDensity = 0.2f
        return weightGrams * avgCarbDensity
    }

    private fun createMockMealFromImage(): Meal {
        return Meal(
            title = "Chicken and rice",
            carbs = 48f
        )
    }

    private fun navigateToSummary() {
        findNavController().navigate(R.id.summaryFragment)
    }

    private fun showLoading(show: Boolean) {
        loadingOverlay.visibility = if (show) View.VISIBLE else View.GONE
        captureButton.isEnabled = !show
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraManager.shutdown()
        portionEstimator?.release()
        portionEstimator = null
    }
}