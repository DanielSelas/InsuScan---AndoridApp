package com.example.insuscan.camera.analyzer

/**
 * Utility object for performing raw mathematical calculations on image byte arrays.
 */
object FrameMathHelper {

    /** Computes average brightness from the Y plane. */
    fun calculateBrightness(data: ByteArray): Float {
        var sum = 0L
        for (byte in data) {
            sum += (byte.toInt() and 0xFF)
        }
        return sum.toFloat() / data.size
    }

    /** Estimates sharpness using pixel variance. */
    fun estimateSharpness(data: ByteArray, width: Int): Float {
        if (data.size < width * 2) return 0f

        var variance = 0.0
        val mean = data.map { it.toInt() and 0xFF }.average()

        for (byte in data) {
            val value = byte.toInt() and 0xFF
            variance += (value - mean) * (value - mean)
        }

        // Return variance divided by mean to normalize across lighting conditions
        return (variance / data.size / (mean + 1)).toFloat() * 100f
    }
}
