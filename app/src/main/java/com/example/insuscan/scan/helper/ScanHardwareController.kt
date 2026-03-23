package com.example.insuscan.scan.helper

import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.example.insuscan.analysis.estimation.PortionEstimator
import com.example.insuscan.ar.ArCoreManager
import com.example.insuscan.camera.CameraManager
import com.example.insuscan.camera.model.ImageQualityResult
import com.example.insuscan.camera.sensor.OrientationHelper
import com.example.insuscan.scan.ReferenceChipsController
import com.example.insuscan.scan.ScanPipelineManager
import com.example.insuscan.scan.ui.ScanUiStateManager
import com.example.insuscan.utils.ReferenceObjectHelper

class ScanHardwareController(
    private val fragment: Fragment,
    private val uiState: ScanUiStateManager,
    private val onQualityUpdate: (ImageQualityResult) -> Unit,
    private val onOrientationChanged: (isLevel: Boolean, isSideAngle: Boolean) -> Unit,
    private val onRefTypeChanged: (String?) -> Unit
) {
    companion object {
        private const val TAG = "ScanHardwareController"
    }

    lateinit var cameraManager: CameraManager
    var arCoreManager: ArCoreManager? = null
    lateinit var orientationHelper: OrientationHelper
    var portionEstimator: PortionEstimator? = null
    lateinit var pipelineManager: ScanPipelineManager
    lateinit var refChipsController: ReferenceChipsController

    fun initializeAll() {
        val context = fragment.requireContext()
        
        // 1. ARCore
        arCoreManager = ArCoreManager(context)
        val arReady = arCoreManager?.initialize(fragment.requireActivity()) == true
        Log.d(TAG, "ArCoreManager initialized: $arReady (supported=${arCoreManager?.isSupported})")

        uiState.hiddenArSurfaceView.preserveEGLContextOnPause = true
        uiState.hiddenArSurfaceView.setEGLContextClientVersion(2)
        uiState.hiddenArSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        uiState.hiddenArSurfaceView.setRenderer(arCoreManager)
        uiState.hiddenArSurfaceView.renderMode = android.opengl.GLSurfaceView.RENDERMODE_CONTINUOUSLY

        // 2. Camera Manager
        cameraManager = CameraManager(context)
        cameraManager.arCoreManager = arCoreManager
        cameraManager.onImageQualityUpdate = { quality -> onQualityUpdate(quality) }

        // 3. Portion Estimator & Pipeline
        portionEstimator = PortionEstimator(context)
        portionEstimator?.initialize()
        
        pipelineManager = ScanPipelineManager(context)
        pipelineManager.portionEstimator = portionEstimator
        pipelineManager.arCoreManager = arCoreManager

        // 4. Reference Chips
        refChipsController = ReferenceChipsController(
            context = context,
            chipGroup = uiState.chipGroupRefObject,
            toggleButton = uiState.btnRefToggle,
            targetZone = uiState.viewTargetZone,
            dismissAnchor = uiState.cameraPreview
        )
        refChipsController.onSelectionChanged = { type ->
            val serverValue = type.serverValue
            cameraManager.selectedReferenceType = serverValue
            onRefTypeChanged(serverValue)
        }
        refChipsController.setup()
        val initialRefType = refChipsController.selectedServerValue
        cameraManager.selectedReferenceType = initialRefType
        onRefTypeChanged(initialRefType)

        // 5. Orientation Helper
        orientationHelper = OrientationHelper(context)
        orientationHelper.onOrientationChanged = { _, _, isLevel, isSideAngle ->
            onOrientationChanged(isLevel, isSideAngle)
        }
    }

    fun startCamera(lifecycleOwner: LifecycleOwner, onReady: () -> Unit = {}, onError: (String) -> Unit = {}) {
        uiState.cameraPreview.post { 
            cameraManager.startCamera(
                lifecycleOwner = lifecycleOwner,
                previewView = uiState.cameraPreview,
                onCameraReady = onReady,
                onError = onError
            )
        }
    }

    fun startCameraIfInitialized(lifecycleOwner: LifecycleOwner, onReady: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (!::cameraManager.isInitialized) {
            initializeAll()
            if (fragment.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                if (::orientationHelper.isInitialized) orientationHelper.start()
            }
        }
        startCamera(lifecycleOwner, onReady, onError)
    }

    fun onResume() {
        if (::orientationHelper.isInitialized) orientationHelper.start()
        arCoreManager?.resume()
        uiState.hiddenArSurfaceView.onResume()
    }

    fun onPause() {
        if (::orientationHelper.isInitialized) orientationHelper.stop()
        arCoreManager?.pause()
        uiState.hiddenArSurfaceView.onPause()
    }

    fun onDestroy() {
        if (::cameraManager.isInitialized) cameraManager.shutdown()
        portionEstimator?.release()
        portionEstimator = null
        arCoreManager?.release()
        arCoreManager = null
    }

    fun resetForGallery() {
        onRefTypeChanged("NONE")
        refChipsController.setType(ReferenceObjectHelper.ReferenceObjectType.NONE)
        if (::cameraManager.isInitialized) {
            cameraManager.selectedReferenceType = "NONE"
            cameraManager.stopPreview()
        }
    }
}
