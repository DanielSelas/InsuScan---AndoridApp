package com.example.insuscan.registration.helper

import android.content.Context
import android.widget.EditText
import android.widget.LinearLayout
import com.example.insuscan.R
import com.example.insuscan.network.dto.InsulinPlanDto
import com.example.insuscan.network.dto.UserDto
import com.example.insuscan.network.repository.UserRepository
import com.example.insuscan.profile.UserProfileManager
import java.util.UUID

/**
 * Handles data collection, DTO building, and server sync for RegistrationStep3.
 * Extracted from RegistrationStep3Fragment to keep the Fragment focused on UI only.
 */
class RegistrationStep3Helper(
    private val context: Context,
    private val userRepository: UserRepository
) {

    /**
     * Reads all plan fields from the UI and saves them to SharedPreferences.
     */
    fun collectAndSavePlans(
        etSicknessIcr: EditText, etSicknessIsf: EditText, etSicknessTarget: EditText,
        etStressIcr: EditText, etStressIsf: EditText, etStressTarget: EditText,
        etTrainingIcr: EditText, etTrainingIsf: EditText, etTrainingTarget: EditText,
        dynamicPlansContainer: LinearLayout
    ) {
        val plansList = mutableListOf<InsulinPlanDto>()

        plansList.add(buildPlanDto("Sickness", etSicknessIcr, etSicknessIsf, etSicknessTarget))
        plansList.add(buildPlanDto("Stress", etStressIcr, etStressIsf, etStressTarget))
        plansList.add(buildPlanDto("Training", etTrainingIcr, etTrainingIsf, etTrainingTarget))

        for (i in 0 until dynamicPlansContainer.childCount) {
            val view = dynamicPlansContainer.getChildAt(i)
            val name = view.findViewById<EditText>(R.id.et_custom_plan_name)
                .text.toString().takeIf { it.isNotBlank() } ?: "Custom Plan ${i + 1}"
            plansList.add(InsulinPlanDto(
                id = UUID.randomUUID().toString(),
                name = name,
                isDefault = false,
                icr = view.findViewById<EditText>(R.id.et_custom_plan_icr).text.toString().toFloatOrNull(),
                isf = view.findViewById<EditText>(R.id.et_custom_plan_isf).text.toString().toFloatOrNull(),
                targetGlucose = view.findViewById<EditText>(R.id.et_custom_plan_target).text.toString().toIntOrNull()
            ))
        }

        UserProfileManager.saveInsulinPlans(context, plansList)
    }

    /**
     * Builds a UserDto from all locally stored profile data.
     */
    fun buildUserDto(doseRounding: Float): UserDto {
        val pm = UserProfileManager
        var rawRatio = pm.getInsulinCarbRatioRaw(context)
        if (rawRatio != null && !rawRatio.contains(":")) rawRatio = "1:$rawRatio"

        return UserDto(
            userId = null,
            username = pm.getUserName(context),
            role = null,
            avatar = pm.getProfilePhotoUrl(context),
            insulinCarbRatio = rawRatio,
            correctionFactor = pm.getCorrectionFactor(context),
            targetGlucose = pm.getTargetGlucose(context),
            syringeType = null,
            customSyringeLength = null,
            age = pm.getUserAge(context),
            gender = pm.getUserGender(context),
            pregnant = pm.getIsPregnant(context),
            dueDate = pm.getDueDate(context),
            diabetesType = pm.getDiabetesType(context),
            insulinType = pm.getInsulinType(context),
            activeInsulinTime = pm.getActiveInsulinTime(context).toInt(),
            doseRounding = doseRounding.toString(),
            sickDayAdjustment = pm.getSickDayAdjustment(context),
            stressAdjustment = pm.getStressAdjustment(context),
            lightExerciseAdjustment = pm.getLightExerciseAdjustment(context),
            intenseExerciseAdjustment = pm.getIntenseExerciseAdjustment(context),
            glucoseUnits = pm.getGlucoseUnits(context),
            insulinPlans = pm.getInsulinPlans(context),
            createdTimestamp = null,
            updatedTimestamp = null
        )
    }

    /**
     * Syncs the user profile to the server. Falls back to register+update if user not found.
     * Returns true if the sync succeeded.
     */
    suspend fun syncWithServer(email: String, doseRounding: Float): Boolean {
        val userDto = buildUserDto(doseRounding)
        val result = userRepository.updateUser(email, userDto)
        if (!result.isSuccess) {
            userRepository.register(email, UserProfileManager.getUserName(context) ?: "User")
            userRepository.updateUser(email, userDto)
        }
        return result.isSuccess
    }

    private fun buildPlanDto(name: String, icrEt: EditText, isfEt: EditText, targetEt: EditText) =
        InsulinPlanDto(
            id = UUID.randomUUID().toString(),
            name = name,
            isDefault = false,
            icr = icrEt.text.toString().toFloatOrNull(),
            isf = isfEt.text.toString().toFloatOrNull(),
            targetGlucose = targetEt.text.toString().toIntOrNull()
        )
}
