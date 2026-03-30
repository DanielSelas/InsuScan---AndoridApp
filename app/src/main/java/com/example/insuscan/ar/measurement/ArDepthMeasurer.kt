package com.example.insuscan.ar.measurement

import android.graphics.Rect
import android.util.Log
import com.example.insuscan.analysis.model.ContainerType
import com.example.insuscan.ar.model.ArMeasurement
import com.google.ar.core.Frame
import com.google.ar.core.Plane

/**
 * Extracts real-world depth and plate dimensions from an ARCore Frame.
 * Handles both Depth API point clouds and fallback Plane intersections.
 */
class ArDepthMeasurer {

    companion object {
        private const val TAG = "ArDepthMeasurer"

        // Magic numbers for ContainerType classification
        private const val FLAT_PLATE_MAX_DEPTH_CM = 2.0f
        private const val REGULAR_BOWL_MAX_DEPTH_CM = 4.5f

        // Magic numbers for depth estimation & bounds
        private const val MIN_DEPTH_CM = 0.3f
        private const val MAX_DEPTH_CM = 20.0f
        
        // FOV fallback constants
        private const val TYPICAL_FOV_DEGREES = 60.0
        private const val MIN_PLATE_DIAMETER_CM = 5.0f
        private const val MAX_PLATE_DIAMETER_CM = 60.0f
    }

    fun measurePlate(
        frame: Frame,
        isDepthSupported: Boolean,
        plateBoundsPixels: Rect,
        imageWidth: Int,
        imageHeight: Int
    ): ArMeasurement? {
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
            val depthCm = ((avgRimDepthMm - centerDepthMm) / 10.0).toFloat().coerceIn(MIN_DEPTH_CM, MAX_DEPTH_CM)

            val plateDiameterCm = projectPlateDiameter(frame, plateBoundsPixels, avgRimDepthMm.toFloat(), imageWidth, imageHeight)
            val surfaceDistanceCm = (avgRimDepthMm / 10.0).toFloat()

            val containerType = when {
                depthCm < FLAT_PLATE_MAX_DEPTH_CM -> ContainerType.FLAT_PLATE
                depthCm < REGULAR_BOWL_MAX_DEPTH_CM -> ContainerType.REGULAR_BOWL
                else -> ContainerType.DEEP_BOWL
            }

            ArMeasurement(
                depthCm = depthCm,
                plateDiameterCm = plateDiameterCm,
                surfaceDistanceCm = surfaceDistanceCm,
                containerType = containerType,
                confidence = (rimDepthsMm.size.toFloat() / rimPoints.size).coerceIn(0.5f, 1.0f),
                isRealDepth = true
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

            val hits = frame.hitTest(cxPixel, cyPixel)
            
            // Find the first trackable horizontal plane
            val hitOnPlane = hits.firstOrNull { hit ->
                val trackable = hit.trackable
                trackable is Plane && trackable.isPoseInPolygon(hit.hitPose) && 
                        trackable.type == Plane.Type.HORIZONTAL_UPWARD_FACING
            } ?: return null

            val distanceM = hitOnPlane.distance
            val distanceMm = distanceM * 1000f

            val plateDiameterCm = projectPlateDiameter(frame, plateBoundsPixels, distanceMm, imageWidth, imageHeight)
            
            Log.i(TAG, "Plane Fallback measurement: diameter=${plateDiameterCm}cm, distance=${distanceM}m")

            return ArMeasurement(
                depthCm = 0.5f, // Cannot measure depth without Depth API - assuming flat
                plateDiameterCm = plateDiameterCm,
                surfaceDistanceCm = distanceM * 100f,
                containerType = ContainerType.FLAT_PLATE, 
                confidence = 0.6f, 
                isRealDepth = false 
            )
        } catch (e: Exception) {
            Log.e(TAG, "Plane hit test failed: ${e.message}")
            return null
        }
    }

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

            val fx = intrinsics.focalLength[0]
            val fy = intrinsics.focalLength[1]

            val intrinsicsDims = intrinsics.imageDimensions
            val fxScaled = fx * (imageWidth.toFloat() / intrinsicsDims[0].toFloat())
            val fyScaled = fy * (imageHeight.toFloat() / intrinsicsDims[1].toFloat())

            val depthMeters = depthAtRimMm / 1000.0f

            val plateWidthPixels = plateBounds.width().toFloat()
            val plateHeightPixels = plateBounds.height().toFloat()

            val realWidthM = (plateWidthPixels * depthMeters) / fxScaled
            val realHeightM = (plateHeightPixels * depthMeters) / fyScaled

            val diameterM = maxOf(realWidthM, realHeightM)
            val diameterCm = diameterM * 100.0f

            diameterCm.coerceIn(MIN_PLATE_DIAMETER_CM, MAX_PLATE_DIAMETER_CM) 
        } catch (e: Exception) {
            Log.w(TAG, "Projection failed: ${e.message}, falling back to depth-only estimate")
            val depthM = depthAtRimMm / 1000.0f
            val plateWidthFraction = plateBounds.width().toFloat() / imageWidth.toFloat()
            val fovRadians = Math.toRadians(TYPICAL_FOV_DEGREES).toFloat()
            val totalRealWidth = 2.0f * depthM * kotlin.math.tan(fovRadians / 2.0f)
            val diameterCm = totalRealWidth * plateWidthFraction * 100.0f
            diameterCm.coerceIn(MIN_PLATE_DIAMETER_CM, MAX_PLATE_DIAMETER_CM)
        }
    }

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

        return (0 until 8).map { i ->
            val angle = i * Math.PI / 4.0
            val px = (cx + rx * kotlin.math.cos(angle)).toInt().coerceIn(0, maxW - 1)
            val py = (cy + ry * kotlin.math.sin(angle)).toInt().coerceIn(0, maxH - 1)
            Pair(px, py)
        }
    }
}
