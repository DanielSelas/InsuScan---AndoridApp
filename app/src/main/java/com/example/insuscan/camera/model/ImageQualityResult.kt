package com.example.insuscan.camera.model

import com.example.insuscan.camera.quality.ImageQualityReport

data class ImageQualityResult(
    val report: ImageQualityReport,
    val debugInfo: String = ""
) {
    val isPlateFound: Boolean get() = report.isPlateFound

    val isReferenceObjectFound: Boolean
        get() = report.referenceObject != null && report.referenceObject.level != QualityLevel.FAILED

    val overall: QualityLevel get() = report.overall

    val canProceed: Boolean get() = report.canProceed

    val message: String get() = report.message
}