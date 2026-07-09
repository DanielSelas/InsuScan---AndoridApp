package com.example.insuscan.summary.helpers

import com.example.insuscan.R

/**
 * Validates the active insulin plan and effective ICR/ISF/target values before saving.
 */
object PlanValidator {

    /**
     * Returns an error message resource ID if the plan or effective values are invalid, or null if valid.
     */
    fun validate(
        planActive: Boolean,
        planIcr: Float?,
        planIsf: Float?,
        planTarget: Int?,
        effectiveIcr: Float?,
        effectiveIsf: Float?,
        effectiveTarget: Int?
    ): Int? {
        if (planActive && (planIcr == null || planIsf == null || planTarget == null)) {
            return R.string.error_plan_missing_values
        }
        if (effectiveIcr == null || effectiveIcr <= 0f) {
            return R.string.error_icr_positive
        }
        if (effectiveIsf == null || effectiveIsf <= 0f) {
            return R.string.error_isf_positive
        }
        if (effectiveTarget == null || effectiveTarget <= 0) {
            return R.string.error_target_positive
        }
        return null
    }
}