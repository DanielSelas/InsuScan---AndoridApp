package com.example.insuscan.camera.validator

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.example.insuscan.camera.analyzer.FrameMathHelper
import com.example.insuscan.camera.quality.ImageQualityEvaluator
import com.example.insuscan.camera.quality.ImageQualityReport
import java.io.File

object ImageValidator {

    private const val TAG = "ImageValidator"
    private const val MEASUREMENT_MAX_DIMENSION = 1280

    private val evaluator = ImageQualityEvaluator()

    fun validateCapturedImage(imageFile: File, lux: Float): ValidationResult {
        if (!imageFile.exists()) {
            return ValidationResult.Error("File not found")
        }

        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            ?: return ValidationResult.Error("The image cannot be read.")

        Log.d(TAG, "Validating image: ${imageFile.name} (${imageFile.length()} bytes)")
        return validateBitmap(bitmap, lux)
    }

    fun validateBitmap(bitmap: Bitmap, lux: Float): ValidationResult {
        val width = bitmap.width
        val height = bitmap.height

        val measurementBitmap = scaleForMeasurement(bitmap)
        val luminance = FrameMathHelper.toLuminanceBytes(measurementBitmap)
        val sharpness = FrameMathHelper.estimateSharpness(luminance, measurementBitmap.width)

        val report = evaluator.evaluateCapturedImage(
            lux = lux,
            sharpness = sharpness,
            width = width,
            height = height,
            plateFound = true,
            referenceExpected = false,
            referenceDetection = null
        )

        Log.d(TAG, "Captured validation | resolution=${width}x${height} | sharpness=$sharpness | lux=$lux | overall=${report.overall} | message=${report.message}")
        return ValidationResult.Evaluated(report)
    }

    private fun scaleForMeasurement(bitmap: Bitmap): Bitmap {
        val maxDim = maxOf(bitmap.width, bitmap.height)
        if (maxDim <= MEASUREMENT_MAX_DIMENSION) return bitmap
        val ratio = MEASUREMENT_MAX_DIMENSION.toFloat() / maxDim
        val newWidth = (bitmap.width * ratio).toInt()
        val newHeight = (bitmap.height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}

sealed class ValidationResult {
    data class Evaluated(val report: ImageQualityReport) : ValidationResult()

    data class Error(val message: String) : ValidationResult()
}