package com.example.insuscan.ar

import android.content.Context
import android.graphics.Rect
import android.util.Log
import com.example.insuscan.ar.measurement.ArDepthMeasurer
import com.example.insuscan.ar.model.ArMeasurement
import com.google.ar.core.*
import com.google.ar.core.exceptions.UnavailableException

import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Centralized ARCore session manager.
 * Handles the AR lifecycle, GL Surface rendering loop, and Session status.
 * Delegates mathematical measurements to [ArDepthMeasurer].
 */
class ArCoreManager(private val context: Context) : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "ArCoreManager"
        private const val MIN_READY_FRAMES = 5
    }

    private var session: Session? = null
    private var isAvailable = false
    private var isDepthSupported = false
    private var validDepthFrameCount = 0
    private var hasPlanes = false
    @Volatile private var latestFrame: Frame? = null
    @Volatile private var isPaused = true

    private val depthMeasurer = ArDepthMeasurer()

    /** True when ARCore has accumulated enough depth data OR detected planes to provide measurements. */
    val isReady: Boolean
        get() = isAvailable && (isDepthReady || isPlaneReady)

    private val isDepthReady: Boolean
        get() = isDepthSupported && validDepthFrameCount >= MIN_READY_FRAMES

    private val isPlaneReady: Boolean
        get() = hasPlanes && !isDepthSupported

    /** True when ARCore is supported on this device. */
    val isSupported: Boolean
        get() = isAvailable

    val hasRealDepth: Boolean
        get() = isDepthSupported && isDepthReady

    // ────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ────────────────────────────────────────────────────────────────────

    fun initialize(activity: android.app.Activity): Boolean {
        return try {
            val availability = ArCoreApk.getInstance().checkAvailability(activity)
            if (!availability.isSupported) {
                Log.w(TAG, "ARCore not supported on this device")
                isAvailable = false
                return false
            }
            isAvailable = true

            // Check for installation/update
            val installStatus = ArCoreApk.getInstance().requestInstall(activity, true)
            if (installStatus == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                Log.d(TAG, "ARCore installation requested")
                return false
            }

            session = Session(activity).apply {
                val config = Config(this)
                isDepthSupported = isDepthModeSupported(Config.DepthMode.AUTOMATIC)
                if (isDepthSupported) {
                    config.depthMode = Config.DepthMode.AUTOMATIC
                    Log.d(TAG, "ARCore depth mode AUTOMATIC enabled")
                } else {
                    Log.w(TAG, "Depth mode not supported — measurements will be limited")
                }
                config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                configure(config)
            }

            Log.d(TAG, "ARCore session initialized (depth=$isDepthSupported)")
            true
        } catch (e: UnavailableException) {
            Log.e(TAG, "ARCore unavailable: ${e.message}")
            isAvailable = false
            false
        } catch (e: Exception) {
            Log.e(TAG, "ARCore session initialization failed: ${e.message}", e)
            isAvailable = false
            false
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // GLSurfaceView.Renderer implementation
    // ────────────────────────────────────────────────────────────────────

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Prepare ARCore session texture
        val textureId = IntArray(1)
        android.opengl.GLES20.glGenTextures(1, textureId, 0)
        session?.setCameraTextureName(textureId[0])
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        session?.setDisplayGeometry(android.view.Surface.ROTATION_0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        if (isPaused) return
        val s = session ?: return
        try {
            // This is the critical update loop running on the GL thread
            val frame = s.update()
            latestFrame = frame

            if (isDepthSupported) {
                try {
                    val depthImage = frame.acquireDepthImage16Bits()
                    if (depthImage != null) {
                        val isValid = depthImage.width > 0 && depthImage.height > 0
                        depthImage.close()
                        if (isValid) {
                            validDepthFrameCount++
                        }
                    }
                } catch (e: Exception) {
                    // Depth not yet available in this frame
                }
            }

            val planes = s.getAllTrackables(Plane::class.java)
            hasPlanes = planes.any { it.trackingState == TrackingState.TRACKING && it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }

        } catch (e: Exception) {
            // Ignore missing context during pause
        }
    }

    fun resume() {
        try {
            session?.resume()
            isPaused = false
            Log.i(TAG, "ARCore session resumed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume session", e)
        }
    }

    fun pause() {
        try {
            isPaused = true
            session?.pause()
            Log.d(TAG, "ARCore session paused")
        } catch (e: Exception) {
            Log.w(TAG, "ARCore session pause failed: ${e.message}")
        }
    }

    fun release() {
        session?.close()
        session = null
        latestFrame = null
        validDepthFrameCount = 0
        Log.d(TAG, "ARCore session released")
    }

    // ────────────────────────────────────────────────────────────────────
    // Measurement
    // ────────────────────────────────────────────────────────────────────

    fun measurePlate(
        plateBoundsPixels: Rect,
        imageWidth: Int,
        imageHeight: Int
    ): ArMeasurement? {
        val frame = latestFrame ?: run {
            Log.w(TAG, "No AR frame available")
            return null
        }

        return depthMeasurer.measurePlate(
            frame = frame,
            isDepthSupported = isDepthSupported,
            plateBoundsPixels = plateBoundsPixels,
            imageWidth = imageWidth,
            imageHeight = imageHeight
        )
    }
}
