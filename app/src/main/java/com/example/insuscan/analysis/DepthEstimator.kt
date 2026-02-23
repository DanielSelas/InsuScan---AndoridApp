package com.example.insuscan.analysis

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.UnavailableException

/**
 * Estimates depth of food plate using ARCore
 * Falls back to default values when ARCore is unavailable
 */
class DepthEstimator(private val context: Context) {

    companion object {
        private const val TAG = "DepthEstimator"

        // Default depth values (in cm)
        const val DEFAULT_PLATE_DEPTH = 2.5f      // flat plate
        const val DEFAULT_BOWL_DEPTH = 6.0f       // regular bowl
        const val DEFAULT_DEEP_BOWL_DEPTH = 10.0f // deep bowl
    }

    private var arSession: Session? = null
    private var isArCoreAvailable = false


    // Check if ARCore is supported and available on this device
    fun checkArCoreAvailability(): ArCoreStatus {
        return try {
            val availability = ArCoreApk.getInstance().checkAvailability(context)

            when {
                availability.isSupported -> {
                    isArCoreAvailable = true
                    Log.d(TAG, "ARCore is supported on this device")
                    ArCoreStatus.SUPPORTED
                }
                availability.isTransient -> {
                    // Still checking, try again later
                    Log.d(TAG, "ARCore availability check in progress")
                    ArCoreStatus.CHECKING
                }
                else -> {
                    Log.d(TAG, "ARCore is not supported on this device")
                    ArCoreStatus.NOT_SUPPORTED
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking ARCore availability", e)
            ArCoreStatus.NOT_SUPPORTED
        }
    }


    // Initialize ARCore session for depth estimation
    fun initializeSession(): Boolean {
        if (!isArCoreAvailable) {
            Log.w(TAG, "ARCore not available, using fallback")
            return false
        }

        return try {
            // Request ARCore installation if needed
            val installStatus = ArCoreApk.getInstance().requestInstall(null, true)
            if (installStatus == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                Log.d(TAG, "ARCore installation requested")
                return false
            }

            // Create session with depth enabled
            arSession = Session(context).apply {
                val config = Config(this)
                if (isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    config.depthMode = Config.DepthMode.AUTOMATIC
                    Log.d(TAG, "Depth mode enabled")
                } else {
                    Log.w(TAG, "Depth mode not supported on this device")
                }
                configure(config)
            }
            true
        } catch (e: UnavailableException) {
            Log.e(TAG, "ARCore unavailable", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create AR session", e)
            false
        }
    }


    // Estimate depth from image
    // Tries ARCore depth sampling first, falls back to container-type heuristics
    fun estimateDepth(bitmap: Bitmap, containerType: ContainerType, plateBounds: android.graphics.Rect? = null): DepthResult {
        // Try to get real depth from ARCore session
        if (isArCoreAvailable && arSession != null && plateBounds != null) {
            val arDepth = estimateDepthFromArSession(plateBounds, bitmap.width, bitmap.height)
            if (arDepth != null) {
                return arDepth
            }
        }

        // Fallback: return estimated values based on container type
        val estimatedDepth = when (containerType) {
            ContainerType.FLAT_PLATE -> DEFAULT_PLATE_DEPTH
            ContainerType.REGULAR_BOWL -> DEFAULT_BOWL_DEPTH
            ContainerType.DEEP_BOWL -> DEFAULT_DEEP_BOWL_DEPTH
            ContainerType.UNKNOWN -> DEFAULT_PLATE_DEPTH
        }

        Log.d(TAG, "Estimated depth for $containerType: $estimatedDepth cm (fallback)")

        return DepthResult(
            depthCm = estimatedDepth,
            confidence = if (isArCoreAvailable) 0.4f else 0.3f,
            isFromArCore = false,
            containerType = containerType
        )
    }

    /**
     * Samples ARCore depth image at plate center and 4 rim points.
     * depth = average(rim depths) - center depth → gives food pile height.
     */
    private fun estimateDepthFromArSession(
        plateBounds: android.graphics.Rect,
        imageWidth: Int,
        imageHeight: Int
    ): DepthResult? {
        return try {
            val session = arSession ?: return null
            val frame = session.update()

            val depthImage = try {
                frame.acquireDepthImage16Bits()
            } catch (e: Exception) {
                Log.w(TAG, "Depth image not available: ${e.message}")
                return null
            }

            val depthWidth = depthImage.width
            val depthHeight = depthImage.height

            // Scale plate bounds from image coords to depth image coords
            val scaleX = depthWidth.toFloat() / imageWidth.toFloat()
            val scaleY = depthHeight.toFloat() / imageHeight.toFloat()

            val cx = ((plateBounds.left + plateBounds.right) / 2 * scaleX).toInt().coerceIn(0, depthWidth - 1)
            val cy = ((plateBounds.top + plateBounds.bottom) / 2 * scaleY).toInt().coerceIn(0, depthHeight - 1)

            // 4 rim points (top, bottom, left, right of plate bbox)
            val rimPoints = listOf(
                Pair(cx, (plateBounds.top * scaleY).toInt().coerceIn(0, depthHeight - 1)),  // top
                Pair(cx, (plateBounds.bottom * scaleY).toInt().coerceIn(0, depthHeight - 1)), // bottom
                Pair((plateBounds.left * scaleX).toInt().coerceIn(0, depthWidth - 1), cy),   // left
                Pair((plateBounds.right * scaleX).toInt().coerceIn(0, depthWidth - 1), cy)   // right
            )

            // Sample depth at center
            val plane = depthImage.planes[0]
            val buffer = plane.buffer
            val rowStride = plane.rowStride

            fun sampleDepthMm(x: Int, y: Int): Int {
                val offset = y * rowStride + x * 2 // 16-bit depth
                return if (offset + 1 < buffer.capacity()) {
                    (buffer.get(offset).toInt() and 0xFF) or ((buffer.get(offset + 1).toInt() and 0xFF) shl 8)
                } else 0
            }

            val centerDepthMm = sampleDepthMm(cx, cy)
            val rimDepthsMm = rimPoints.map { sampleDepthMm(it.first, it.second) }.filter { it > 0 }

            depthImage.close()

            if (centerDepthMm <= 0 || rimDepthsMm.isEmpty()) {
                Log.w(TAG, "Invalid depth samples (center=$centerDepthMm, rims=${rimDepthsMm.size})")
                return null
            }

            val avgRimDepthMm = rimDepthsMm.average()
            // Rim is farther from camera than center of food pile
            // So rim depth > center depth → positive height
            val foodHeightCm = ((avgRimDepthMm - centerDepthMm) / 10.0f).toFloat()
                .coerceIn(0.5f, 15.0f) // Sanity bounds

            // Determine container type from depth difference
            val arContainerType = when {
                foodHeightCm < 1.5f -> ContainerType.FLAT_PLATE
                foodHeightCm < 4.0f -> ContainerType.REGULAR_BOWL
                else -> ContainerType.DEEP_BOWL
            }

            Log.d(TAG, "ARCore depth: center=${centerDepthMm}mm, avgRim=${avgRimDepthMm}mm, height=${foodHeightCm}cm → $arContainerType")

            DepthResult(
                depthCm = foodHeightCm,
                confidence = 0.85f,
                isFromArCore = true,
                containerType = arContainerType
            )
        } catch (e: Exception) {
            Log.w(TAG, "ARCore depth sampling failed: ${e.message}")
            null
        }
    }


    // Detect container type from image (plate vs bowl)
    // Uses simple heuristics - can be improved with ML later

    fun detectContainerType(plateResult: PlateDetectionResult): ContainerType {
        // Use plate bounding box aspect ratio (not full image / photo orientation)
        val bounds = plateResult.bounds ?: return ContainerType.UNKNOWN

        val plateAspectRatio = bounds.width().toFloat() / bounds.height().toFloat()

        // Plate bbox aspect ratio indicates shape as seen from above:
        // ~1.0 = circular plate (seen straight-on)
        // > 1.3 = likely a flat plate seen from angle (wider than tall)
        // < 0.8 = significantly taller than wide → deep bowl seen from side
        return when {
            plateAspectRatio > 1.3f -> ContainerType.FLAT_PLATE
            plateAspectRatio < 0.8f -> ContainerType.DEEP_BOWL
            else -> ContainerType.REGULAR_BOWL
        }
    }


    // Release ARCore resources

    // --- AR Surface Scanning Logic ---

    private var isScanningSurface = false
    private var surfaceScanCallback: ((Boolean) -> Unit)? = null

    fun startSurfaceScan(callback: (Boolean) -> Unit) {
        // Relaxed check for simulation/testing
        if (!isArCoreAvailable || arSession == null) {
            Log.w(TAG, "ARCore not available, proceeding with SIMULATION")
            // callback(false) // Don't fail immediately, let simulation run
        }

        isScanningSurface = true
        surfaceScanCallback = callback
        
        // In a real app, we would attach a frame listener here
        // For now, we'll simulate a successful plane detection after 2 seconds
        // to demonstrate the UI flow without physical AR device
        
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (isScanningSurface) {
                Log.d(TAG, "Simulated AR Plane Detected!")
                isScanningSurface = false
                surfaceScanCallback?.invoke(true)
            }
        }, 2000)
    }

    fun stopSurfaceScan() {
        isScanningSurface = false
        surfaceScanCallback = null
    }

    fun release() {
        stopSurfaceScan()
        arSession?.close()
        arSession = null
        Log.d(TAG, "ARCore session released")
    }
}

// ARCore availability status
enum class ArCoreStatus {
    SUPPORTED,
    NOT_SUPPORTED,
    CHECKING
}

// Type of food container
enum class ContainerType {
    FLAT_PLATE,
    REGULAR_BOWL,
    DEEP_BOWL,
    UNKNOWN
}

// Result of depth estimation
data class DepthResult(
    val depthCm: Float,           // depth in centimeters
    val confidence: Float,         // 0.0 to 1.0
    val isFromArCore: Boolean,     // true if from actual ARCore measurement
    val containerType: ContainerType
)