package com.example.insuscan.network.dto

import com.google.gson.annotations.SerializedName

// Matches server's UserBoundary
data class UserDto(
    val userId: UserIdDto?,
    val username: String?,
    val role: String?,
    val avatar: String?,
    val insulinCarbRatio: Float?,
    val correctionFactor: Float?,
    val targetGlucose: Int?,
    val syringeType: String?,
    val customSyringeLength: Float?,
    val createdTimestamp: String?,
    val updatedTimestamp: String?
)

data class UserIdDto(
    val systemId: String,
    val email: String
)

// For creating new user
data class NewUserDto(
    val email: String,
    val username: String,
    val role: String = "PATIENT",
    val insulinCarbRatio: Float? = null,
    val correctionFactor: Float? = null,
    val targetGlucose: Int? = null,
    val syringeType: String? = null
)
