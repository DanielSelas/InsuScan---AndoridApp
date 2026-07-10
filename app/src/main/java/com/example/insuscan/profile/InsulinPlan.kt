package com.example.insuscan.profile

/**
 * An insulin dosing plan with its personalised ICR, ISF, and target glucose values.
 *
 * @property id            Unique plan identifier (UUID).
 * @property name          User-visible plan name (e.g. "Sick Day", "Workout").
 * @property isDefault     Whether this is the user's primary plan.
 * @property icr           Insulin-to-carb ratio (grams of carbs per 1 unit).
 * @property isf           Insulin sensitivity factor (mg/dL drop per 1 unit).
 * @property targetGlucose Target blood glucose level in mg/dL.
 */
data class InsulinPlan(
    val id: String = "",
    val name: String = "",
    val isDefault: Boolean = false,
    val icr: Float? = null,
    val isf: Float? = null,
    val targetGlucose: Int? = null
)