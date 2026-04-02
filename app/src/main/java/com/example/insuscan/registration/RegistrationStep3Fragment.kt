package com.example.insuscan.registration

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.insuscan.R
import com.example.insuscan.network.dto.UserDto
import com.example.insuscan.network.dto.InsulinPlanDto
import com.example.insuscan.network.repository.UserRepositoryImpl
import com.example.insuscan.profile.UserProfileManager
import com.example.insuscan.utils.ToastHelper
import kotlinx.coroutines.launch
import android.view.LayoutInflater
import android.widget.ImageView

class RegistrationStep3Fragment : Fragment(R.layout.fragment_registration_step3) {

    // Plans fields
    private lateinit var etSicknessIcr: EditText
    private lateinit var etSicknessIsf: EditText
    private lateinit var etSicknessTarget: EditText

    private lateinit var etStressIcr: EditText
    private lateinit var etStressIsf: EditText
    private lateinit var etStressTarget: EditText

    private lateinit var etTrainingIcr: EditText
    private lateinit var etTrainingIsf: EditText
    private lateinit var etTrainingTarget: EditText

    private lateinit var layoutDynamicPlansContainer: LinearLayout
    private lateinit var btnAddCustomPlan: Button
    
    private lateinit var finishButton: Button
    private lateinit var loadingOverlay: FrameLayout

    private val userRepository = UserRepositoryImpl()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        findViews(view)
        setupListeners()
        prefillPlansData()
    }

    private fun findViews(view: View) {
        etSicknessIcr = view.findViewById(R.id.et_plan_sickness_icr)
        etSicknessIsf = view.findViewById(R.id.et_plan_sickness_isf)
        etSicknessTarget = view.findViewById(R.id.et_plan_sickness_target)

        etStressIcr = view.findViewById(R.id.et_plan_stress_icr)
        etStressIsf = view.findViewById(R.id.et_plan_stress_isf)
        etStressTarget = view.findViewById(R.id.et_plan_stress_target)

        etTrainingIcr = view.findViewById(R.id.et_plan_training_icr)
        etTrainingIsf = view.findViewById(R.id.et_plan_training_isf)
        etTrainingTarget = view.findViewById(R.id.et_plan_training_target)

        layoutDynamicPlansContainer = view.findViewById(R.id.layout_dynamic_plans_container)
        btnAddCustomPlan = view.findViewById(R.id.btn_add_custom_plan)

        finishButton = view.findViewById(R.id.btn_finish_registration)
        loadingOverlay = view.findViewById(R.id.loading_overlay)
    }

    private fun setupListeners() {

        btnAddCustomPlan.setOnClickListener {
            addCustomPlanRow()
        }

        finishButton.setOnClickListener {
            saveDataAndSync()
        }
    }

    private fun addCustomPlanRow() {
        val planView = LayoutInflater.from(requireContext()).inflate(R.layout.item_custom_plan_registration, layoutDynamicPlansContainer, false)
        
        val removeButton = planView.findViewById<ImageView>(R.id.btn_remove_custom_plan)
        removeButton.setOnClickListener {
            layoutDynamicPlansContainer.removeView(planView)
        }

        // Prefill new custom plan with base values
        val rawRatio = UserProfileManager.getInsulinCarbRatioRaw(requireContext())
        val defaultIcr = if (rawRatio != null && rawRatio.contains(":")) rawRatio.split(":")[1] else rawRatio ?: "10"
        val defaultIsf = UserProfileManager.getCorrectionFactor(requireContext())?.toString() ?: "40"
        val defaultTarget = UserProfileManager.getTargetGlucose(requireContext())?.toString() ?: "100"

        planView.findViewById<EditText>(R.id.et_custom_plan_icr).hint = defaultIcr
        planView.findViewById<EditText>(R.id.et_custom_plan_isf).hint = defaultIsf
        planView.findViewById<EditText>(R.id.et_custom_plan_target).hint = defaultTarget

        layoutDynamicPlansContainer.addView(planView)
    }

    private fun prefillPlansData() {
        val pm = UserProfileManager
        val ctx = requireContext()
        val rawRatio = pm.getInsulinCarbRatioRaw(ctx)
        val icrVal = if (rawRatio != null && rawRatio.contains(":")) rawRatio.split(":")[1] else rawRatio ?: "10"
        val isfVal = pm.getCorrectionFactor(ctx)?.toString() ?: "40"
        val targetVal = pm.getTargetGlucose(ctx)?.toString() ?: "100"

        // Sickness
        etSicknessIcr.hint = icrVal
        etSicknessIsf.hint = isfVal
        etSicknessTarget.hint = targetVal

        // Stress
        etStressIcr.hint = icrVal
        etStressIsf.hint = isfVal
        etStressTarget.hint = targetVal

        // Training
        etTrainingIcr.hint = icrVal
        etTrainingIsf.hint = isfVal
        etTrainingTarget.hint = targetVal
    }

    private fun saveDataAndSync() {
        saveStepData()
        val doseRounding = UserProfileManager.getDoseRounding(requireContext())
        syncWithServer(doseRounding)
    }

    private fun saveStepData() {
        val pm = UserProfileManager
        val ctx = requireContext()

        val plansList = mutableListOf<InsulinPlanDto>()
        
        plansList.add(InsulinPlanDto(
            id = java.util.UUID.randomUUID().toString(),
            name = "Sickness",
            isDefault = false,
            icr = etSicknessIcr.text.toString().toFloatOrNull(),
            isf = etSicknessIsf.text.toString().toFloatOrNull(),
            targetGlucose = etSicknessTarget.text.toString().toIntOrNull()
        ))
        plansList.add(InsulinPlanDto(
            id = java.util.UUID.randomUUID().toString(),
            name = "Stress",
            isDefault = false,
            icr = etStressIcr.text.toString().toFloatOrNull(),
            isf = etStressIsf.text.toString().toFloatOrNull(),
            targetGlucose = etStressTarget.text.toString().toIntOrNull()
        ))
        plansList.add(InsulinPlanDto(
            id = java.util.UUID.randomUUID().toString(),
            name = "Training",
            isDefault = false,
            icr = etTrainingIcr.text.toString().toFloatOrNull(),
            isf = etTrainingIsf.text.toString().toFloatOrNull(),
            targetGlucose = etTrainingTarget.text.toString().toIntOrNull()
        ))

        // Collect dynamic plans
        for (i in 0 until layoutDynamicPlansContainer.childCount) {
            val view = layoutDynamicPlansContainer.getChildAt(i)
            val nameStr = view.findViewById<EditText>(R.id.et_custom_plan_name).text.toString().takeIf { it.isNotBlank() } ?: "Custom Plan ${i+1}"
            val icrVal = view.findViewById<EditText>(R.id.et_custom_plan_icr).text.toString().toFloatOrNull()
            val isfVal = view.findViewById<EditText>(R.id.et_custom_plan_isf).text.toString().toFloatOrNull()
            val targetVal = view.findViewById<EditText>(R.id.et_custom_plan_target).text.toString().toIntOrNull()
            
            plansList.add(InsulinPlanDto(
                id = java.util.UUID.randomUUID().toString(),
                name = nameStr,
                isDefault = false,
                icr = icrVal,
                isf = isfVal,
                targetGlucose = targetVal
            ))
        }

        pm.saveInsulinPlans(ctx, plansList)
    }

    private fun buildUserDto(doseRounding: Float): UserDto {
        val pm = UserProfileManager
        val ctx = requireContext()

        var rawRatio = pm.getInsulinCarbRatioRaw(ctx)
        if (rawRatio != null && !rawRatio.contains(":")) {
            rawRatio = "1:$rawRatio"
        }

        return UserDto(
            userId = null,
            username = pm.getUserName(ctx),
            role = null,
            avatar = pm.getProfilePhotoUrl(ctx),
            insulinCarbRatio = rawRatio,
            correctionFactor = pm.getCorrectionFactor(ctx),
            targetGlucose = pm.getTargetGlucose(ctx),
            syringeType = null,
            customSyringeLength = null,
            age = pm.getUserAge(ctx),
            gender = pm.getUserGender(ctx),
            pregnant = pm.getIsPregnant(ctx),
            dueDate = pm.getDueDate(ctx),
            diabetesType = pm.getDiabetesType(ctx),
            insulinType = pm.getInsulinType(ctx),
            activeInsulinTime = pm.getActiveInsulinTime(ctx).toInt(),
            doseRounding = doseRounding.toString(),
            sickDayAdjustment = pm.getSickDayAdjustment(ctx),
            stressAdjustment = pm.getStressAdjustment(ctx),
            lightExerciseAdjustment = pm.getLightExerciseAdjustment(ctx),
            intenseExerciseAdjustment = pm.getIntenseExerciseAdjustment(ctx),
            glucoseUnits = pm.getGlucoseUnits(ctx),
            insulinPlans = pm.getInsulinPlans(ctx),
            createdTimestamp = null,
            updatedTimestamp = null
        )
    }

    private fun syncWithServer(doseRounding: Float) {
        loadingOverlay.visibility = View.VISIBLE
        finishButton.isEnabled = false

        val pm = UserProfileManager
        val ctx = requireContext()

        viewLifecycleOwner.lifecycleScope.launch {
            val email = pm.getUserEmail(ctx)
            if (email == null) {
                pm.setRegistrationComplete(ctx, true)
                navigateToSplash()
                return@launch
            }

            val userDto = buildUserDto(doseRounding)

            try {
                val result = userRepository.updateUser(email, userDto)

                loadingOverlay.visibility = View.GONE
                finishButton.isEnabled = true

                if (result.isSuccess) {
                    ToastHelper.showShort(ctx, "Registration complete!")
                } else {
                    userRepository.register(email, pm.getUserName(ctx) ?: "Daniel")
                    userRepository.updateUser(email, userDto)
                }
            } catch (e: Exception) {
                loadingOverlay.visibility = View.GONE
                finishButton.isEnabled = true
                android.util.Log.e("Registration", "Sync failed", e)
                ToastHelper.showShort(ctx, "Saved locally. Server sync failed.")
            }

            pm.setRegistrationComplete(ctx, true)
            navigateToSplash()
        }
    }

    private fun navigateToSplash() {
        findNavController().navigate(R.id.action_registrationStep3_to_splashAnimation)
    }
}
