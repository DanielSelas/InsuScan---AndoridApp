package com.example.insuscan.scan

/**
 * Describes which measurement inputs are available for the current scan session.
 *
 * The mode is resolved once at capture time from the ARCore state and the
 * presence of a reference object. It determines whether a side photo is required.
 */
enum class ScanMode {
    /** ARCore with real depth — highest accuracy, no side photo needed. */
    MODE_1_FULL_AR,
    /** ARCore available but no real depth — reference object used for scale. */
    MODE_2_AR_WITH_REF,
    /** ARCore available, no reference object — basic AR-only estimation. */
    MODE_3_AR_NO_REF,
    /** No ARCore, reference object present — reference-only scale estimation. */
    MODE_4_NO_AR_WITH_REF,
    /** No ARCore, no reference object — lowest accuracy, estimation only. */
    MODE_5_NO_AR_NO_REF;

    /** `true` when the mode cannot fully estimate depth from the top photo alone. */
    val requiresSidePhoto: Boolean
        get() = this != MODE_1_FULL_AR

    companion object {
        /**
         * Selects the appropriate [ScanMode] from the current hardware state.
         *
         * @param arReady       Whether ARCore has an active tracking session.
         * @param hasRealDepth  Whether the ARCore session provides real depth data.
         * @param hasRefObject  Whether the user has selected a reference object.
         */
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