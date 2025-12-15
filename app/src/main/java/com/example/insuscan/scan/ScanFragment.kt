package com.example.insuscan.scan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
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
import com.example.insuscan.camera.CameraManager
import com.example.insuscan.camera.ImageQualityResult
import com.example.insuscan.camera.ImageValidator
import com.example.insuscan.camera.ValidationResult
import com.example.insuscan.meal.Meal
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.utils.ToastHelper
import java.io.File

/**
 * ScanFragment - food scan screen.
 *
 * Uses CameraX to show preview, runs lightweight validation in real time,
 * and captures an image to continue to Summary.
 */
class ScanFragment : Fragment(R.layout.fragment_scan) {

    // Views
    private lateinit var cameraPreview: PreviewView
    private lateinit var captureButton: Button
    private lateinit var qualityStatusText: TextView
    private lateinit var loadingOverlay: FrameLayout

    // Camera
    private lateinit var cameraManager: CameraManager
    private var isImageQualityOk = false

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

        findViews(view)
        initializeCameraManager()
        initializeListeners()
        checkCameraPermission()
    }

    private fun findViews(view: View) {
        cameraPreview = view.findViewById(R.id.camera_preview)
        captureButton = view.findViewById(R.id.btn_capture)
        qualityStatusText = view.findViewById(R.id.tv_quality_status)
        loadingOverlay = view.findViewById(R.id.loading_overlay)
    }

    // This is the "real" version (kept commented on purpose for now).
    // We use this when running on a real device and trusting analysis results.
//    private fun initializeCameraManager() {
//        cameraManager = CameraManager(requireContext())
//
//        // Listen for live quality updates coming from ImageAnalysis
//        cameraManager.onImageQualityUpdate = { qualityResult ->
//            updateQualityStatus(qualityResult)
//        }
//    }

    private fun initializeCameraManager() {
        cameraManager = CameraManager(requireContext())

        // Listen for live quality updates coming from ImageAnalysis
        cameraManager.onImageQualityUpdate = { qualityResult ->
            // Dev mode: force a "good" result so we can test UX on emulator.
            val devModeResult = ImageQualityResult(
                brightness = 120f,
                isBrightnessOk = true,
                sharpness = 600f,
                isSharpnessOk = true,
                resolution = 1920 * 1080,
                isResolutionOk = true
            )

            // TODO: Switch back to qualityResult when testing on a real device.
            updateQualityStatus(devModeResult)
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

    // Updates the UI based on live quality checks.
    private fun updateQualityStatus(qualityResult: ImageQualityResult) {
        isImageQualityOk = qualityResult.isValid

        qualityStatusText.text = qualityResult.getValidationMessage()

        // Background color indicates status
        val backgroundColor = if (qualityResult.isValid) {
            ContextCompat.getColor(requireContext(), android.R.color.holo_green_light)
        } else {
            ContextCompat.getColor(requireContext(), android.R.color.holo_orange_light)
        }
        qualityStatusText.setBackgroundColor(backgroundColor)

        // Capture is enabled only when quality is valid
        captureButton.isEnabled = qualityResult.isValid
        captureButton.alpha = if (qualityResult.isValid) 1f else 0.5f
    }

    private fun onCaptureClicked() {
        showLoading(true)

        // We store the captured image in cache for now.
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

    /**
     * Validates the captured image and continues the flow.
     * For now, once valid, we create a mock meal and go to Summary.
     */

//    private fun validateAndProcessImage(imageFile: File) {
//        val validationResult = ImageValidator.validateCapturedImage(imageFile)
//
//        when (validationResult) {
//            is ValidationResult.Valid -> {
//                // Image is valid - create a meal and continue to Summary
//                // TODO: Later we'll send the image to the backend for real recognition
//                val mockMeal = createMockMealFromImage()
//                MealSessionManager.setCurrentMeal(mockMeal)
//
//                showLoading(false)
//                navigateToSummary()
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

    private fun validateAndProcessImage(imageFile: File) {
        // Dev mode: skip file validation to avoid emulator false negatives.
        // TODO: Turn this off on real devices.
        val devMode = true

        if (devMode) {
            val mockMeal = createMockMealFromImage()
            MealSessionManager.setCurrentMeal(mockMeal)
            showLoading(false)
            navigateToSummary()
            return
        }

        val validationResult = ImageValidator.validateCapturedImage(imageFile)

        when (validationResult) {
            is ValidationResult.Valid -> {
                val mockMeal = createMockMealFromImage()
                MealSessionManager.setCurrentMeal(mockMeal)
                showLoading(false)
                navigateToSummary()
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

    // Temporary stub until backend recognition is wired.
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
    }
}