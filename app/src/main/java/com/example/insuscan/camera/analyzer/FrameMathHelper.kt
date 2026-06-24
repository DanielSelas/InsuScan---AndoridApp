package com.example.insuscan.camera.analyzer

import android.graphics.Bitmap

object FrameMathHelper {

    fun calculateBrightness(data: ByteArray): Float {
        var sum = 0L
        for (byte in data) {
            sum += (byte.toInt() and 0xFF)
        }
        return sum.toFloat() / data.size
    }

    fun estimateSharpness(data: ByteArray, width: Int): Float {
        if (data.size < width * 2) return 0f

        var variance = 0.0
        val mean = data.map { it.toInt() and 0xFF }.average()

        for (byte in data) {
            val value = byte.toInt() and 0xFF
            variance += (value - mean) * (value - mean)
        }

        return (variance / data.size / (mean + 1)).toFloat() * 100f
    }

    fun toLuminanceBytes(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val out = ByteArray(width * height)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            out[i] = ((0.299 * r + 0.587 * g + 0.114 * b).toInt()).toByte()
        }
        return out
    }
}