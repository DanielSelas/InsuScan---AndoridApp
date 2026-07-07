package com.example.insuscan.summary.helpers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanValidatorTest {

    @Test
    fun emptyActivePlanIsRejected() {
        val message = PlanValidator.validate(
            planActive = true,
            planIcr = null, planIsf = null, planTarget = null,
            effectiveIcr = 10f, effectiveIsf = 50f, effectiveTarget = 100
        )
        assertEquals("The selected plan is missing values (ICR, ISF or target glucose). Complete it in your profile.", message)
    }

    @Test
    fun zeroIsfIsRejected() {
        val message = PlanValidator.validate(
            planActive = false,
            planIcr = null, planIsf = null, planTarget = null,
            effectiveIcr = 10f, effectiveIsf = 0f, effectiveTarget = 100
        )
        assertEquals("ISF must be greater than zero. Update it in your profile.", message)
    }

    @Test
    fun missingIcrIsRejected() {
        val message = PlanValidator.validate(
            planActive = false,
            planIcr = null, planIsf = null, planTarget = null,
            effectiveIcr = null, effectiveIsf = 50f, effectiveTarget = 100
        )
        assertEquals("ICR must be a positive value. Update it in your profile.", message)
    }

    @Test
    fun completeValuesPass() {
        val message = PlanValidator.validate(
            planActive = true,
            planIcr = 10f, planIsf = 50f, planTarget = 100,
            effectiveIcr = 10f, effectiveIsf = 50f, effectiveTarget = 100
        )
        assertNull(message)
    }
}