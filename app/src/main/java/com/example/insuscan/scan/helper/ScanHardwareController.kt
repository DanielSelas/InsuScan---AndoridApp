package com.example.insuscan.scan.helper

import android.util.Log
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.example.insuscan.ar.ArCoreManager
import com.example.insuscan.camera.CameraManager
import com.example.insuscan.camera.model.ImageQualityResult
import com.example.insuscan.camera.sensor.LightSensorHelper
import com.example.insuscan.camera.sensor.OrientationHelper
import com.example.insuscan.scan.ReferenceChipsController
import com.example.insuscan.scan.ScanPipelineManager
import com.example.insuscan.scan.ui.ScanUiStateManager
import com.example.insuscan.utils.ReferenceObjectHelper

/**
 * Owns and wires up all scan hardware components: camera, ARCore, orientation sensor,
 * light sensor, pipeline manager, and reference-object chips.
 *
 * Delegates event notifications (quality updates, orientation changes, reference type changes)
 * to the provided callbacks so the fragment stays free of hardware concerns.
 */
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
    lateinit var lightSensorHelper: LightSensorHelper

    val currentLux: Float
        get() = if (::lightSensorHelper.isInitialized) lightSensorHelper.currentLux else -1f

    lateinit var pipelineManager: ScanPipelineManager
    lateinit var refChipsController: ReferenceChipsController

    /** Initialises all hardware components in dependency order. */
    fun initializeAll() {
        val context = fragment.requireContext()

        arCoreManager = ArCoreManager(context)
        val arReady = arCoreManager?.initialize(fragment.requireActivity()) == true
        Log.d(TAG, "ARCore initialised — ready: $arReady, supported: ${arCoreManager?.isSupported}")

        uiState.hiddenArSurfaceView.preserveEGLContextOnPause = true
        uiState.hiddenArSurfaceView.setEGLContextClientVersion(2)
        uiState.hiddenArSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        uiState.hiddenArSurfaceView.setRenderer(arCoreManager)
        uiState.hiddenArSurfaceView.renderMode = android.opengl.GLSurfaceView.RENDERMODE_CONTINUOUSLY

        cameraManager = CameraManager(context)
        cameraManager.arCoreManager = arCoreManager
        cameraManager.onImageQualityUpdate = { quality -> onQualityUpdate(quality) }

        pipelineManager = ScanPipelineManager(context)
        pipelineManager.arCoreManager = arCoreManager

        refChipsController = ReferenceChipsController(
            context = context,
            chipGroup = uiState.chipGroupRefObject,
            targetZone = uiState.viewTargetZone
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

        orientationHelper = OrientationHelper(context)
        orientationHelper.onOrientationChanged = { _, _, isLevel, isSideAngle ->
            onOrientationChanged(isLevel, isSideAngle)
        }

        lightSensorHelper = LightSensorHelper(context)
        Log.d(TAG, "Light sensor available: ${lightSensorHelper.isAvailable}")
        cameraManager.luxProvider = { lightSensorHelper.currentLux }
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

    /**
     * Initialises hardware if not already done, then starts the camera preview.
     * Safe to call on each fragment resume.
     */
    fun startCameraIfInitialized(lifecycleOwner: LifecycleOwner, onReady: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (!::cameraManager.isInitialized) {
            initializeAll()
            if (fragment.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                if (::orientationHelper.isInitialized) orientationHelper.start()
                if (::lightSensorHelper.isInitialized) lightSensorHelper.start()
            }
        }
        startCamera(lifecycleOwner, onReady, onError)
    }

    fun onResume() {
        if (::orientationHelper.isInitialized) orientationHelper.start()
        if (::lightSensorHelper.isInitialized) lightSensorHelper.start()
        uiState.hiddenArSurfaceView.onResume()
        arCoreManager?.resume()
    }

    fun onPause() {
        if (::orientationHelper.isInitialized) orientationHelper.stop()
        if (::lightSensorHelper.isInitialized) lightSensorHelper.stop()
        arCoreManager?.pause()
        uiState.hiddenArSurfaceView.onPause()
    }

    fun onDestroy() {
        if (::cameraManager.isInitialized) cameraManager.shutdown()
        arCoreManager?.release()
        arCoreManager = null
    }

    fun setTorchEnabled(enabled: Boolean) {
        if (::cameraManager.isInitialized) cameraManager.setTorchEnabled(enabled)
    }

    /** Resets reference chips to NONE and stops the camera preview (used when loading a gallery image). */
    fun resetForGallery() {
        onRefTypeChanged("NONE")
        refChipsController.setType(ReferenceObjectHelper.ReferenceObjectType.NONE)
        if (::cameraManager.isInitialized) {
            cameraManager.selectedReferenceType = "NONE"
            cameraManager.stopPreview()
        }
    }
}
