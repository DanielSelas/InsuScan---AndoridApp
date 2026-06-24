package com.example.insuscan.camera.model

enum class QualityLevel {
    OK,
    BORDERLINE,
    FAILED;

    companion object {
        fun worst(levels: List<QualityLevel>): QualityLevel =
            levels.maxByOrNull { it.ordinal } ?: OK
    }
}