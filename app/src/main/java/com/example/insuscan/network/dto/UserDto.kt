package com.example.insuscan.network.dto

import com.google.gson.annotations.SerializedName

// Matches server's UserBoundary
data class UserDto(
    val userId: UserIdDto?,
    @SerializedName("userName")
    val username: String?,
    val role: String?,
    val avatar: String?,

    // Medical Profile
    val insulinCarbRatio: String?,
    val correctionFactor: Float?,
    val targetGlucose: Int?,

    // Syringe Settings
    val syringeType: String?,
    val customSyringeLength: Float?,

    // Personal Info
    val age: Int?,
    val gender: String?,
    val pregnant: Boolean?,
    val dueDate: String?,

    // Medical Info Extended
    val diabetesType: String?,
    val insulinType: String?,
    val activeInsulinTime: Int?,

    // Dose Settings
    val doseRounding: String?, // Server expects String "0.5" or "1"

    // Adjustment Factors
    val sickDayAdjustment: Int?,
    val stressAdjustment: Int?,
    val lightExerciseAdjustment: Int?,
    val intenseExerciseAdjustment: Int?,

    // Preferences
    val glucoseUnits: String?,

    // Timestamps
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
    @SerializedName("userName")
    val username: String,
    val role: String = "PATIENT",
    val insulinCarbRatio: String? = null,
    val correctionFactor: Float? = null,
    val targetGlucose: Int? = null,
    val syringeType: String? = null
)