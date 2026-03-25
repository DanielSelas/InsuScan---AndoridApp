package com.example.insuscan.profile

data class InsulinPlan(
    val id: String = "",
    val name: String = "",
    val isDefault: Boolean = false,
    val icr: Float? = null,
    val isf: Float? = null,
    val targetGlucose: Int? = null

)