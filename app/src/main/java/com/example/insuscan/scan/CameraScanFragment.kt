package com.example.insuscan.scan

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.insuscan.R
import com.example.insuscan.scan.coach.CameraCoachEvaluator
import com.example.insuscan.scan.helper.ScanFlowController
import com.example.insuscan.scan.helper.ScanHardwareController
import com.example.insuscan.scan.ui.ScanDialogHelper
import com.example.insuscan.scan.ui.ScanUiStateManager
import com.example.insuscan.utils.ToastHelper

class CameraScanFragment : Fragment(R.layout.fragment_camera_scan), ScanUiStateManager.Listener, ScanDialogHelper.Listener {

    private val callback: ScanResultCallback?
        get() = (parentFragment as? ScanResultCallback) ?: (activity as? ScanResultCallback)

    private lateinit var uiState: ScanUiStateManager
    private lateinit var dialogHelper: ScanDialogHelper
    private lateinit var hardwareController: ScanHardwareController
    private lateinit var flowController: ScanFlowController
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
            flowController.processGalleryImage(uri)
        } else {
            hardwareController.startCameraIfInitialized(viewLifecycleOwner, 
                onReady = {
                    uiState.captureButton.isEnabled = false
                    uiState.captureButton.alpha = 0.5f
                },
                onError = {
                    ToastHelper.showShort(requireContext(), it)
                    uiState.captureButton.isEnabled = false
                }
            )
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        uiState = ScanUiStateManager(view, this)
        dialogHelper = ScanDialogHelper(requireContext(), this)
        
        hardwareController = ScanHardwareController(
            fragment = this,
            uiState = uiState,
            onQualityUpdate = { flowController.updateQualityUI(it) },
            onOrientationChanged = { isLevel, isSideAngle -> handleOrientationChange(isLevel, isSideAngle) },
            onRefTypeChanged = { flowController.selectedReferenceType = it }
        )

        flowController = ScanFlowController(
            fragment = this,
            hardware = hardwareController,
            uiState = uiState,
            dialogHelper = dialogHelper,
            coachEvaluator = coachEvaluator,
            callback = callback
        )

        val openGalleryDirectly = arguments?.getBoolean("open_gallery_directly") == true
        flowController.isCaptureOnlyMode = arguments?.getBoolean("capture_only_mode", false) ?: false

        if (openGalleryDirectly) {
            arguments?.putBoolean("open_gallery_directly", false)
            hardwareController.initializeAll()
            initializeListeners()
            hardwareController.resetForGallery()
            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        } else {
            hardwareController.initializeAll()
            initializeListeners()
            checkCameraPermission()
        }
    }

    override fun onResume() {
        super.onResume()
        hardwareController.onResume()
    }

    override fun onPause() {
        super.onPause()
        hardwareController.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        hardwareController.onDestroy()
    }

    private fun handleOrientationChange(isLevel: Boolean, isSideAngle: Boolean) {
        val levelChanged = flowController.isDeviceLevel != isLevel
        val sideChanged = flowController.isDeviceSideAngle != isSideAngle
        flowController.isDeviceLevel = isLevel
        flowController.isDeviceSideAngle = isSideAngle
        
        if (levelChanged || (flowController.isSidePhotoMode && sideChanged)) {
            activity?.runOnUiThread {
                val quality = hardwareController.cameraManager.lastQualityResult
                if (quality != null) {
                    flowController.updateQualityUI(quality)
                } else if (flowController.isSidePhotoMode) {
                    uiState.applyCoachState(coachEvaluator.evaluateSidePhoto(flowController.isDeviceSideAngle))
                }
            }
        }
    }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> startCamera()
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                ToastHelper.showLong(requireContext(), "Camera permission is required for scanning")
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
            else -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        hardwareController.startCamera(
            lifecycleOwner = viewLifecycleOwner,
            onReady = {
                uiState.captureButton.isEnabled = false
                uiState.captureButton.alpha = 0.5f
            },
            onError = { errorMessage ->
                ToastHelper.showShort(requireContext(), errorMessage)
                uiState.captureButton.isEnabled = false
            }
        )
    }

    private fun initializeListeners() {
        flowController.isSidePhotoMode = false
        uiState.captureButton.setOnClickListener {
            if (flowController.isShowingCapturedImage) {
                if (flowController.isSidePhotoMode) dialogHelper.showRetakeOptionsDialog() else flowController.switchToCameraMode()
                return@setOnClickListener
            }

            val quality = hardwareController.cameraManager.lastQualityResult
            if (quality == null || !quality.isPlateFound) {
                ToastHelper.showShort(requireContext(), "Center the plate in the frame \uD83C\uDF7D️")
                return@setOnClickListener
            }
            val state = coachEvaluator.evaluate(quality, flowController.isDeviceLevel, flowController.selectedReferenceType)
            if (!state.canCapture) {
                ToastHelper.showShort(requireContext(), state.message)
                return@setOnClickListener
            }

            flowController.onCaptureClicked()
        }

        uiState.galleryButton.setOnClickListener {
            hardwareController.resetForGallery()
            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    // --- UI/Dialog Listeners ---
    override fun onSidePhotoReadyClicked() = uiState.hideSidePhotoCard { flowController.captureSidePhoto { initializeListeners() } }
    override fun onSidePhotoSkipClicked() = uiState.hideSidePhotoCard {
        uiState.showSkipAccuracyBanner()
        hardwareController.pipelineManager.skipSidePhoto()
        flowController.proceedWithPortionAnalysis()
    }

    override fun onArIndicatorClicked() {}
    override fun onTryAgainClicked() = flowController.switchToCameraMode()
    override fun onCancelClicked() { callback?.onScanCancelled() }
    override fun onTakeSidePhotoClicked() = flowController.captureSidePhoto { initializeListeners() }
    override fun onRetakeSidePhotoOnlyClicked() = flowController.captureSidePhoto { initializeListeners() }
    override fun onRetakeBothPhotosClicked() = flowController.switchToCameraMode()

    override fun onSkipSidePhotoClicked() {
        uiState.showSkipAccuracyBanner()
        hardwareController.pipelineManager.skipSidePhoto()
        flowController.proceedWithPortionAnalysis()
    }
}