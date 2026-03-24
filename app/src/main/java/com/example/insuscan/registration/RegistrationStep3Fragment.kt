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
import com.example.insuscan.network.repository.UserRepositoryImpl
import com.example.insuscan.profile.UserProfileManager
import com.example.insuscan.utils.ToastHelper
import kotlinx.coroutines.launch

class RegistrationStep3Fragment : Fragment(R.layout.fragment_registration_step3) {

    private lateinit var referenceTypeSpinner: Spinner
    private lateinit var customLengthLayout: LinearLayout
    private lateinit var customLengthEditText: EditText
    private lateinit var doseRoundingSpinner: Spinner
    private lateinit var sickAdjustmentEditText: EditText
    private lateinit var stressAdjustmentEditText: EditText
    private lateinit var exerciseAdjustmentEditText: EditText
    private lateinit var finishButton: Button
    private lateinit var loadingOverlay: FrameLayout

    private val referenceOptions = arrayOf("Insulin Pen (Standard)", "Fork / Knife")
    private val roundingOptions = arrayOf("0.5 units", "1 unit")

    private val userRepository = UserRepositoryImpl()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        findViews(view)
        setupSpinners()
        setupListeners()
    }

    private fun findViews(view: View) {
        referenceTypeSpinner = view.findViewById(R.id.spinner_reg_reference_type)
        customLengthLayout = view.findViewById(R.id.layout_reg_custom_length)
        customLengthEditText = view.findViewById(R.id.et_reg_custom_length)
        doseRoundingSpinner = view.findViewById(R.id.spinner_reg_dose_rounding)
        sickAdjustmentEditText = view.findViewById(R.id.et_reg_sick_adj)
        stressAdjustmentEditText = view.findViewById(R.id.et_reg_stress_adj)
        exerciseAdjustmentEditText = view.findViewById(R.id.et_reg_exercise_adj)
        finishButton = view.findViewById(R.id.btn_finish_registration)
        loadingOverlay = view.findViewById(R.id.loading_overlay)
    }

    private fun setupSpinners() {
        referenceTypeSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, referenceOptions)
        doseRoundingSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, roundingOptions)
        doseRoundingSpinner.setSelection(0)
    }

    private fun setupListeners() {
        referenceTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val isOther = referenceOptions[position].contains("Other") || referenceOptions[position].contains("Fork")
                customLengthLayout.visibility = if (isOther) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        finishButton.setOnClickListener {
            saveDataAndSync()
        }
    }

    private fun saveDataAndSync() {
        val selectedRef = referenceOptions[referenceTypeSpinner.selectedItemPosition]
        val doseRounding = if (doseRoundingSpinner.selectedItemPosition == 0) 0.5f else 1f

        saveStepData(selectedRef, doseRounding)
        syncWithServer(selectedRef, doseRounding)
    }

    private fun saveStepData(selectedRef: String, doseRounding: Float) {
        val pm = UserProfileManager
        val ctx = requireContext()

        if (selectedRef.contains("Insulin Pen")) {
            pm.saveSyringeSize(ctx, "INSULIN_PEN")
        } else {
            pm.saveSyringeSize(ctx, "CUSTOM_OBJECT")
            val len = customLengthEditText.text.toString().toFloatOrNull() ?: 15.0f
            pm.saveCustomSyringeLength(ctx, len)
        }

        pm.saveDoseRounding(ctx, doseRounding)

        sickAdjustmentEditText.text.toString().toIntOrNull()?.let { pm.saveSickDayAdjustment(ctx, it) }
        stressAdjustmentEditText.text.toString().toIntOrNull()?.let { pm.saveStressAdjustment(ctx, it) }
        exerciseAdjustmentEditText.text.toString().toIntOrNull()?.let {
            pm.saveLightExerciseAdjustment(ctx, it)
            pm.saveIntenseExerciseAdjustment(ctx, it * 2)
        }
    }

    private fun buildUserDto(selectedRef: String, doseRounding: Float): UserDto {
        val pm = UserProfileManager
        val ctx = requireContext()

        val syringeEnumValue = if (selectedRef.contains("Insulin Pen")) "INSULIN_PEN" else "CUSTOM_OBJECT"
        val customLen = if (syringeEnumValue == "CUSTOM_OBJECT") pm.getCustomSyringeLength(ctx) else null

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
            syringeType = syringeEnumValue,
            customSyringeLength = customLen,
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
            createdTimestamp = null,
            updatedTimestamp = null
        )
    }

    private fun syncWithServer(selectedRef: String, doseRounding: Float) {
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

            val userDto = buildUserDto(selectedRef, doseRounding)

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
