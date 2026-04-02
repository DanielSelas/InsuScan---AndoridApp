package com.example.insuscan.registration

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.insuscan.R
import com.example.insuscan.profile.UserProfileManager
import com.example.insuscan.utils.ToastHelper

class RegistrationStep2Fragment : Fragment(R.layout.fragment_registration_step2) {

    private lateinit var icrEditText: EditText
    private lateinit var isfEditText: EditText
    private lateinit var targetGlucoseEditText: EditText
    private lateinit var isfSubtitle: TextView
    private lateinit var targetSubtitle: TextView
    private lateinit var nextButton: Button


    private lateinit var doseRoundingSpinner: Spinner
    private val roundingOptions = arrayOf("0.5 units", "1 unit")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        findViews(view)
        setupSpinners()
        setupListeners()
    }

    private fun findViews(view: View) {
        icrEditText = view.findViewById(R.id.et_reg_icr)
        isfEditText = view.findViewById(R.id.et_reg_isf)
        targetGlucoseEditText = view.findViewById(R.id.et_reg_target_glucose)
        isfSubtitle = view.findViewById(R.id.tv_reg_isf_subtitle)
        targetSubtitle = view.findViewById(R.id.tv_reg_target_subtitle)
        doseRoundingSpinner = view.findViewById(R.id.spinner_reg_dose_rounding)
        nextButton = view.findViewById(R.id.btn_next_step)
    }

    private fun setupSpinners() {
        doseRoundingSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, roundingOptions)
        doseRoundingSpinner.setSelection(0)
    }

    private fun setupListeners() {

        nextButton.setOnClickListener {
            if (validateInputsAndSave()) {
                findNavController().navigate(R.id.action_registrationStep2_to_registrationStep3)
            }
        }
    }

    private fun validateInputsAndSave(): Boolean {
        val icrStr = icrEditText.text.toString().trim()
        val isfStr = isfEditText.text.toString().trim()
        val targetStr = targetGlucoseEditText.text.toString().trim()

        val icrNum = icrStr.toFloatOrNull()
        if (icrNum == null || icrNum <= 0f) {
            icrEditText.error = "Invalid ICR"
            return false
        }

        val isfNum = isfStr.toFloatOrNull()
        if (isfNum == null || isfNum <= 0f) {
            isfEditText.error = "Invalid ISF"
            return false
        }

        val targetNum = targetStr.toIntOrNull()
        if (targetNum == null || targetNum <= 0) {
            targetGlucoseEditText.error = "Invalid Target Glucose"
            return false
        }

        val pm = UserProfileManager
        val ctx = requireContext()

        pm.saveInsulinCarbRatio(ctx, "1:$icrStr")
        pm.saveCorrectionFactor(ctx, isfNum)
        pm.saveTargetGlucose(ctx, targetNum)

        val doseRounding = if (doseRoundingSpinner.selectedItemPosition == 0) 0.5f else 1f
        pm.saveDoseRounding(ctx, doseRounding)

        return true
    }
}
