package com.example.insuscan.analysis.model

import com.example.insuscan.ar.model.ArMeasurement

/**
 * Source of scale measurement for portion estimation.
 */
internal sealed class ScaleSource {
    data class ReferenceObject(val pixelToCmRatio: Float) : ScaleSource()
    data class ArProjection(val arMeasurement: ArMeasurement) : ScaleSource()
    data object None : ScaleSource()
}
