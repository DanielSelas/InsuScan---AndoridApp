package com.example.insuscan.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * ImageValidator - validates captured images before sending them to processing.
 *
 * Checks:
 * - Minimum resolution
 * - Brightness (not too dark / not too bright)
 * - Sharpness (not blurry)
 * - Reference object hint (insulin pen/syringe presence)
 */
object ImageValidator {

    // Minimum image requirements
    private const val MIN_WIDTH = 1920
    private const val MIN_HEIGHT = 1080
    private const val MIN_BRIGHTNESS = 40
    private const val MAX_BRIGHTNESS = 220
    private const val MIN_SHARPNESS_SCORE = 100.0

    /**
     * Runs full validation for a captured image file.
     */
    fun validateCapturedImage(imageFile: File): ValidationResult {
        if (!imageFile.exists()) {
            return ValidationResult.Error("File not found")
        }

        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            ?: return ValidationResult.Error("The image cannot be read.")

        return validateBitmap(bitmap)
    }

    /**
     * Runs validation on a Bitmap instance.
     */
    fun validateBitmap(bitmap: Bitmap): ValidationResult {
        val issues = mutableListOf<String>()

        // Resolution check
        if (bitmap.width < MIN_WIDTH || bitmap.height < MIN_HEIGHT) {
            issues.add(
                "Resolution is too low (${bitmap.width}x${bitmap.height}). " +
                        "Minimum required is ${MIN_WIDTH}x${MIN_HEIGHT}."
            )
        }

        // Brightness check
        val brightness = calculateAverageBrightness(bitmap)
        when {
            brightness < MIN_BRIGHTNESS ->
                issues.add("The picture is too dark. Please take it in a brighter place.")
            brightness > MAX_BRIGHTNESS ->
                issues.add("The image is too bright. Please reduce the lighting.")
        }

        // Sharpness check
        val sharpness = calculateSharpnessScore(bitmap)
        if (sharpness < MIN_SHARPNESS_SCORE) {
            issues.add("The picture is blurry. Please keep the phone steady and try again.")
        }

        return if (issues.isEmpty()) {
            ValidationResult.Valid(
                brightness = brightness,
                sharpness = sharpness,
                resolution = "${bitmap.width}x${bitmap.height}"
            )
        } else {
            ValidationResult.Invalid(issues)
        }
    }

    /**
     * Computes average brightness using luminance:
     * 0.299R + 0.587G + 0.114B
     */
    private fun calculateAverageBrightness(bitmap: Bitmap): Int {
        // Sample pixels (not all pixels) to keep it fast
        val sampleSize = 50
        val stepX = bitmap.width / sampleSize
        val stepY = bitmap.height / sampleSize

        var totalBrightness = 0L
        var count = 0

        for (x in 0 until bitmap.width step stepX.coerceAtLeast(1)) {
            for (y in 0 until bitmap.height step stepY.coerceAtLeast(1)) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                val brightness = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                totalBrightness += brightness
                count++
            }
        }

        return if (count > 0) (totalBrightness / count).toInt() else 0
    }

    /**
     * Computes a sharpness score using Laplacian variance (approx).
     * Higher value = sharper image.
     */
    private fun calculateSharpnessScore(bitmap: Bitmap): Double {
        // Convert to grayscale (sampled)
        val sampleSize = 100
        val stepX = (bitmap.width / sampleSize).coerceAtLeast(1)
        val stepY = (bitmap.height / sampleSize).coerceAtLeast(1)

        val grayValues = mutableListOf<Int>()

        for (x in 1 until bitmap.width - 1 step stepX) {
            for (y in 1 until bitmap.height - 1 step stepY) {
                val pixel = bitmap.getPixel(x, y)
                val gray = ((pixel shr 16 and 0xFF) * 0.299 +
                        (pixel shr 8 and 0xFF) * 0.587 +
                        (pixel and 0xFF) * 0.114).toInt()
                grayValues.add(gray)
            }
        }

        if (grayValues.isEmpty()) return 0.0

        // Variance
        val mean = grayValues.average()
        val variance = grayValues.map { (it - mean) * (it - mean) }.average()

        return sqrt(variance)
    }

    /**
     * Checks if there is a hint of a reference object in the image.
     * This is only a lightweight heuristic - full detection should happen server-side.
     */
    fun hasReferenceObjectHint(bitmap: Bitmap): Boolean {
        // Basic heuristic: look for a reasonable amount of very bright pixels
        // (often white/light-gray elongated areas typical for an insulin pen/syringe)

        var whitePixelCount = 0
        val sampleSize = 30
        val stepX = (bitmap.width / sampleSize).coerceAtLeast(1)
        val stepY = (bitmap.height / sampleSize).coerceAtLeast(1)
        var totalSamples = 0

        for (x in 0 until bitmap.width step stepX) {
            for (y in 0 until bitmap.height step stepY) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF

                // Bright pixel check
                if (r > 200 && g > 200 && b > 200) {
                    whitePixelCount++
                }
                totalSamples++
            }
        }

        // If there is a reasonable ratio of bright pixels, a reference object might be present
        val whiteRatio = whitePixelCount.toFloat() / totalSamples
        return whiteRatio in 0.02f..0.15f // between 2% and 15% of the sampled pixels
    }
}

/**
 * Validation result wrapper.
 */
sealed class ValidationResult {
    data class Valid(
        val brightness: Int,
        val sharpness: Double,
        val resolution: String
    ) : ValidationResult()

    data class Invalid(
        val issues: List<String>
    ) : ValidationResult() {
        fun getFormattedMessage(): String = issues.joinToString("\n• ", prefix = "• ")
    }

    data class Error(
        val message: String
    ) : ValidationResult()
}