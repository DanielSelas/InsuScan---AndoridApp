package com.example.insuscan.scan

enum class ScanMode {
    MODE_1_FULL_AR,
    MODE_2_AR_WITH_REF,
    MODE_3_AR_NO_REF,
    MODE_4_NO_AR_WITH_REF,
    MODE_5_NO_AR_NO_REF;

    val requiresSidePhoto: Boolean
        get() = this != MODE_1_FULL_AR

    companion object {
        fun detect(
            arReady: Boolean,
            hasRealDepth: Boolean,
            hasRefObject: Boolean
        ): ScanMode = when {
            arReady && hasRealDepth -> MODE_1_FULL_AR
            arReady && hasRefObject -> MODE_2_AR_WITH_REF
            arReady && !hasRefObject -> MODE_3_AR_NO_REF
            !arReady && hasRefObject -> MODE_4_NO_AR_WITH_REF
            else -> MODE_5_NO_AR_NO_REF
        }
    }
}