package com.example.insuscan.scan

import com.example.insuscan.meal.Meal

data class CapturedScanData(
    val imagePath: String,
    val referenceType: String?,
    val wasRefFoundInPreview: Boolean
)

interface ScanResultCallback {
    fun onScanSuccess(meal: Meal)
    fun onScanCancelled() {}
    fun onImageCapturedForBackground(data: CapturedScanData) {}
}