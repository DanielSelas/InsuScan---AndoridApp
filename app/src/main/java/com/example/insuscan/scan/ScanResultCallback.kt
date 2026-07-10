package com.example.insuscan.scan

import com.example.insuscan.meal.Meal

/**
 * Holds the data captured during a background-mode scan (before server analysis).
 *
 * @property imagePath              Absolute path to the captured top image.
 * @property referenceType          Server value of the selected reference object, or null.
 * @property wasRefFoundInPreview   Whether the reference was detected during the live preview.
 */
data class CapturedScanData(
    val imagePath: String,
    val referenceType: String?,
    val wasRefFoundInPreview: Boolean
)

/**
 * Callback interface for the scan flow result.
 */
interface ScanResultCallback {
    /** Called when the scan completes successfully with a populated [meal]. */
    fun onScanSuccess(meal: Meal)

    /** Called when the user cancels the scan. */
    fun onScanCancelled() {}

    /**
     * Called in capture-only mode once the image is captured but before server analysis.
     * The host can use [data] to start a background analysis job.
     */
    fun onImageCapturedForBackground(data: CapturedScanData) {}
}