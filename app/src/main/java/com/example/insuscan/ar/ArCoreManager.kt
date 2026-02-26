package com.example.insuscan.ar

import android.content.Context
import android.graphics.Rect
import android.util.Log
import com.example.insuscan.analysis.ContainerType
import com.google.ar.core.*
import com.google.ar.core.exceptions.UnavailableException

/**
 * Centralized ARCore session manager.
 * Provides real-world depth and scale measurements using ARCore's Depth API
 * and camera intrinsics for 2D→3D projection.
 *
 * Usage:
 *  1. Call [initialize] once during fragment setup.
 *  2. Call [updateFrame] on every camera frame (from CameraManager analyzer) to keep the session alive.
 *  3. Call [measurePlate] at capture time to get real depth + plate diameter.
 *  4. Call [release] when done.
 */
class ArCoreManager(private val context: Context) {

    companion object {
        private const val TAG = "ArCoreManager"

        // Minimum number of frames with valid depth before we consider AR "ready"
        private const val MIN_READY_FRAMES = 5
    }

    private var session: Session? = null
    private var isAvailable = false
    private var isDepthSupported = false
    private var latestFrame: Frame? = null
    private var validDepthFrameCount = 0
    private var hasPlanes = false

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

    // ────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ────────────────────────────────────────────────────────────────────

    /**
     * Initialize ARCore session with depth enabled.
     * Returns true if ARCore + depth are available.
     * @param activity The activity context needed for installation checks and session setup.
     */
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
            val isDepthSupported = session?.isDepthModeSupported(Config.DepthMode.AUTOMATIC) == true
            Log.i(TAG, "ARCore Session created. Depth support: $isDepthSupported")
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

    /**
     * Call on every live preview frame to keep ARCore session alive and accumulate depth data.
     * This is cheap — ARCore processes asynchronously.
     */
    fun updateFrame() {
        val s = session ?: return
        try {
            // Log state occasionally
            if (Math.random() < 0.01) {
                Log.v(TAG, "Attempting frame update")
            }
            latestFrame = s.update()
            val frame = latestFrame ?: return

            // 1. Accumulate depth frames if supported
            if (isDepthSupported) {
                try {
                    val depthImage = frame.acquireDepthImage16Bits()
                    if (depthImage != null) {
                        val isValid = depthImage.width > 0 && depthImage.height > 0
                        depthImage.close()
                        if (isValid) {
                            validDepthFrameCount++
                            if (validDepthFrameCount == 1 || validDepthFrameCount == 5 || validDepthFrameCount % 50 == 0) {
                                Log.d(TAG, "Depth frame accumulated. Count: $validDepthFrameCount")
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Depth not yet available in this frame
                }
            }

            // 2. Always track planes as fallback
            val planes = s.getAllTrackables(Plane::class.java)
            hasPlanes = planes.any { it.trackingState == TrackingState.TRACKING && it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }

        } catch (e: Exception) {
            // Session may not be resumed — this is OK during startup
            if (Math.random() < 0.05) {
                Log.e(TAG, "Error updating AR frame", e)
            }
        }
    }

    /**
     * Resume the ARCore session. Call from onResume().
     */
    fun resume() {
        try {
            session?.resume()
            Log.i(TAG, "ARCore Session resumed")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume ARCore session", e)
        }
    }

    /**
     * Pause the ARCore session. Call from onPause().
     */
    fun pause() {
        try {
            session?.pause()
            Log.d(TAG, "ARCore session paused")
        } catch (e: Exception) {
            Log.w(TAG, "ARCore pause failed: ${e.message}")
        }
    }

    /**
     * Release all ARCore resources. Call from onDestroy().
     */
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

        // 1. Try Depth API first (Most accurate)
        if (isDepthSupported) {
            val measurement = measureUsingDepth(frame, plateBoundsPixels, imageWidth, imageHeight)
            if (measurement != null) return measurement
        }

        // 2. Fallback: Plane Hit Test (For A32 and devices without Depth API)
        return measureUsingPlanes(frame, plateBoundsPixels, imageWidth, imageHeight)
    }

    /** Accurate measurement using Depth API point cloud. */
    private fun measureUsingDepth(
        frame: Frame,
        plateBoundsPixels: Rect,
        imageWidth: Int,
        imageHeight: Int
    ): ArMeasurement? {
        return try {
            val depthImage = try {
                frame.acquireDepthImage16Bits()
            } catch (e: Exception) {
                return null
            }

            val depthWidth = depthImage.width
            val depthHeight = depthImage.height

            if (depthWidth == 0 || depthHeight == 0) {
                depthImage.close()
                return null
            }

            // Scale plate bounds
            val scaleX = depthWidth.toFloat() / imageWidth.toFloat()
            val scaleY = depthHeight.toFloat() / imageHeight.toFloat()

            val cx = ((plateBoundsPixels.left + plateBoundsPixels.right) / 2 * scaleX).toInt()
                .coerceIn(0, depthWidth - 1)
            val cy = ((plateBoundsPixels.top + plateBoundsPixels.bottom) / 2 * scaleY).toInt()
                .coerceIn(0, depthHeight - 1)

            val rimPoints = buildRimSamplePoints(plateBoundsPixels, scaleX, scaleY, depthWidth, depthHeight)

            val plane = depthImage.planes[0]
            val buffer = plane.buffer
            val rowStride = plane.rowStride

            fun sampleDepthMm(x: Int, y: Int): Int {
                val offset = y * rowStride + x * 2
                return if (offset + 1 < buffer.capacity()) {
                    (buffer.get(offset).toInt() and 0xFF) or
                            ((buffer.get(offset + 1).toInt() and 0xFF) shl 8)
                } else 0
            }

            val centerDepthMm = sampleDepthMm(cx, cy)
            val rimDepthsMm = rimPoints.map { sampleDepthMm(it.first, it.second) }.filter { it > 0 }
            depthImage.close()

            if (centerDepthMm <= 0 || rimDepthsMm.isEmpty()) return null

            val avgRimDepthMm = rimDepthsMm.average()
            val depthCm = ((avgRimDepthMm - centerDepthMm) / 10.0).toFloat().coerceIn(0.3f, 20.0f)

            val plateDiameterCm = projectPlateDiameter(frame, plateBoundsPixels, avgRimDepthMm.toFloat(), imageWidth, imageHeight)
            val surfaceDistanceCm = (avgRimDepthMm / 10.0).toFloat()

            val containerType = when {
                depthCm < 1.5f -> ContainerType.FLAT_PLATE
                depthCm < 5.0f -> ContainerType.REGULAR_BOWL
                else -> ContainerType.DEEP_BOWL
            }

            ArMeasurement(
                depthCm = depthCm,
                plateDiameterCm = plateDiameterCm,
                surfaceDistanceCm = surfaceDistanceCm,
                containerType = containerType,
                confidence = (rimDepthsMm.size.toFloat() / rimPoints.size).coerceIn(0.5f, 1.0f)
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Fallback measurement using Plane Hit Test (for SM-A325F etc). */
    private fun measureUsingPlanes(
        frame: Frame,
        plateBoundsPixels: Rect,
        imageWidth: Int,
        imageHeight: Int
    ): ArMeasurement? {
        try {
            // Hit test at the center of the plate
            val cxPixel = (plateBoundsPixels.centerX()).toFloat()
            val cyPixel = (plateBoundsPixels.centerY()).toFloat()

            // Map pixel coordinates to ARCore screen coordinates (0.0 to 1.0)
            // Note: ARCore hit test takes screen pixels relative to the GL surface. 
            // In our case, we'll use the center for robustness.
            val hits = frame.hitTest(cxPixel, cyPixel)
            
            // Find the first trackable horizontal plane
            val hitOnPlane = hits.firstOrNull { hit ->
                val trackable = hit.trackable
                trackable is Plane && trackable.isPoseInPolygon(hit.hitPose) && 
                        trackable.type == Plane.Type.HORIZONTAL_UPWARD_FACING
            } ?: return null

            // Distance from camera to plane
            val distanceM = hitOnPlane.distance
            val distanceMm = distanceM * 1000f

            // Project plate size at this distance
            val plateDiameterCm = projectPlateDiameter(frame, plateBoundsPixels, distanceMm, imageWidth, imageHeight)
            
            Log.i(TAG, "Plane Fallback measurement: diameter=${plateDiameterCm}cm, distance=${distanceM}m")

            return ArMeasurement(
                depthCm = 0.5f, // Cannot measure depth without Depth API - assuming flat
                plateDiameterCm = plateDiameterCm,
                surfaceDistanceCm = distanceM * 100f,
                containerType = ContainerType.FLAT_PLATE, // Default to plate
                confidence = 0.6f // Moderate confidence for planes
            )
        } catch (e: Exception) {
            Log.e(TAG, "Plane hit test failed: ${e.message}")
            return null
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // Projection: pixel width → real-world cm
    // ────────────────────────────────────────────────────────────────────

    /**
     * Projects the plate's pixel width to real-world centimeters.
     *
     * Formula: real_width = (pixel_width × depth_meters) / focal_length_px
     *
     * Camera intrinsics give us the focal length in pixels, and depth tells us
     * how far the plate is. Together, they let us compute exact physical size.
     */
    private fun projectPlateDiameter(
        frame: Frame,
        plateBounds: Rect,
        depthAtRimMm: Float,
        imageWidth: Int,
        imageHeight: Int
    ): Float {
        return try {
            val camera = frame.camera
            val intrinsics = camera.imageIntrinsics

            // intrinsics.focalLength returns [fx, fy] in pixels
            val fx = intrinsics.focalLength[0]
            val fy = intrinsics.focalLength[1]

            // intrinsics image dimensions may differ from our image — scale focal length
            val intrinsicsDims = intrinsics.imageDimensions
            val fxScaled = fx * (imageWidth.toFloat() / intrinsicsDims[0].toFloat())
            val fyScaled = fy * (imageHeight.toFloat() / intrinsicsDims[1].toFloat())

            val depthMeters = depthAtRimMm / 1000.0f

            // Project plate width: real_width = (pixel_width * depth) / fx
            val plateWidthPixels = plateBounds.width().toFloat()
            val plateHeightPixels = plateBounds.height().toFloat()

            val realWidthM = (plateWidthPixels * depthMeters) / fxScaled
            val realHeightM = (plateHeightPixels * depthMeters) / fyScaled

            // Use the larger dimension as plate diameter (handles angled views)
            val diameterM = maxOf(realWidthM, realHeightM)
            val diameterCm = diameterM * 100.0f

            Log.d(TAG, "Projection: plate=${plateWidthPixels.toInt()}x${plateHeightPixels.toInt()}px, " +
                    "depth=${depthMeters}m, fx=$fxScaled, fy=$fyScaled → diameter=${diameterCm}cm")

            diameterCm.coerceIn(5.0f, 60.0f) // Sanity: 5cm to 60cm
        } catch (e: Exception) {
            Log.w(TAG, "Projection failed: ${e.message}, falling back to depth-only estimate")
            // Fallback: rough estimate based on depth and typical field of view (~60°)
            val depthM = depthAtRimMm / 1000.0f
            val plateWidthFraction = plateBounds.width().toFloat() / imageWidth.toFloat()
            val fovRadians = Math.toRadians(60.0).toFloat()
            val totalRealWidth = 2.0f * depthM * kotlin.math.tan(fovRadians / 2.0f)
            val diameterCm = totalRealWidth * plateWidthFraction * 100.0f
            diameterCm.coerceIn(5.0f, 60.0f)
        }
    }

    // ────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────

    /**
     * Build 8 sample points around the plate rim for more stable depth sampling.
     */
    private fun buildRimSamplePoints(
        bounds: Rect,
        scaleX: Float,
        scaleY: Float,
        maxW: Int,
        maxH: Int
    ): List<Pair<Int, Int>> {
        val cx = ((bounds.left + bounds.right) / 2.0f * scaleX).toInt()
        val cy = ((bounds.top + bounds.bottom) / 2.0f * scaleY).toInt()
        val rx = ((bounds.right - bounds.left) / 2.0f * scaleX).toInt()
        val ry = ((bounds.bottom - bounds.top) / 2.0f * scaleY).toInt()

        // 8 points at 0°, 45°, 90°, ..., 315° around the ellipse
        return (0 until 8).map { i ->
            val angle = i * Math.PI / 4.0
            val px = (cx + rx * kotlin.math.cos(angle)).toInt().coerceIn(0, maxW - 1)
            val py = (cy + ry * kotlin.math.sin(angle)).toInt().coerceIn(0, maxH - 1)
            Pair(px, py)
        }
    }
}

/**
 * Result of ARCore-based measurement at capture time.
 */
data class ArMeasurement(
    val depthCm: Float,          // Real depth (bowl height or food pile height)
    val plateDiameterCm: Float,  // Real plate diameter from 2D→3D projection
    val surfaceDistanceCm: Float,// Distance from camera to table surface
    val containerType: ContainerType, // Classified from real depth
    val confidence: Float        // 0.0 to 1.0 based on depth sample quality
)
