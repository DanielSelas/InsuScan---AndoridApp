package com.example.insuscan.network.dto

import com.google.gson.annotations.SerializedName

data class UserDto(
    val userId: UserIdDto?,
    @SerializedName("userName")
    val username: String?,
    val role: String?,
    val avatar: String?,

    val insulinCarbRatio: String?,
    val correctionFactor: Float?,
    val targetGlucose: Int?,

    val age: Int?,
    val gender: String?,

    val doseRounding: String?,

    val insulinPlans: List<InsulinPlanDto>? = null,

    val createdTimestamp: String?,
    val updatedTimestamp: String?
)

data class UserIdDto(
    val systemId: String,
    val email: String
)

data class NewUserDto(
    val email: String,
    @SerializedName("userName")
    val username: String,
    val role: String = "PATIENT",
    val insulinCarbRatio: String? = null,
    val correctionFactor: Float? = null,
    val targetGlucose: Int? = null
)

data class InsulinPlanDto(
    val id: String? = null,
    val name: String? = null,
    val isDefault: Boolean = false,
    val icr: Float? = null,
    val isf: Float? = null,
    val targetGlucose: Int? = null
)