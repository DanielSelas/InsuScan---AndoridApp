package com.example.insuscan.summary.helpers

object PlanValidator {

    fun validate(
        planActive: Boolean,
        planIcr: Float?,
        planIsf: Float?,
        planTarget: Int?,
        effectiveIcr: Float?,
        effectiveIsf: Float?,
        effectiveTarget: Int?
    ): String? {
        if (planActive && (planIcr == null || planIsf == null || planTarget == null)) {
            return "The selected plan is missing values (ICR, ISF or target glucose). Complete it in your profile."
        }
        if (effectiveIcr == null || effectiveIcr <= 0f) {
            return "ICR must be a positive value. Update it in your profile."
        }
        if (effectiveIsf == null || effectiveIsf <= 0f) {
            return "ISF must be greater than zero. Update it in your profile."
        }
        if (effectiveTarget == null || effectiveTarget <= 0) {
            return "Target glucose must be set. Update it in your profile."
        }
        return null
    }
}