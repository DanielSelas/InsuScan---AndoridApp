package com.example.insuscan.camera.quality

object QualityThresholds {

    const val LIGHTING_FAILED_LUX = 10f
    const val LIGHTING_OK_MIN_LUX = 40f
    const val LIGHTING_OK_MAX_LUX = 100f

    const val RESOLUTION_OK_PIXELS = 1280L * 960L
    const val RESOLUTION_MIN_PIXELS = 1280L * 720L

    const val SHARPNESS_OK = 1000f
    const val SHARPNESS_MIN = 600f

    const val REFERENCE_EDGE_MARGIN_RATIO = 0.05f
}