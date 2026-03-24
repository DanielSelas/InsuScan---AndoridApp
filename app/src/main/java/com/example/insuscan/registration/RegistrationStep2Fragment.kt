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

    private lateinit var insulinTypeSpinner: Spinner
    private lateinit var icrEditText: EditText
    private lateinit var isfEditText: EditText
    private lateinit var targetGlucoseEditText: EditText
    private lateinit var glucoseUnitsSpinner: Spinner
    private lateinit var isfSubtitle: TextView
    private lateinit var targetSubtitle: TextView
    private lateinit var nextButton: Button

    private val insulinOptions = arrayOf("Select", "Rapid-acting", "Short-acting", "Other")
    private val glucoseUnitOptions = arrayOf("mg/dL", "mmol/L")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        findViews(view)
        setupSpinners()
        setupListeners()
    }

    private fun findViews(view: View) {
        insulinTypeSpinner = view.findViewById(R.id.spinner_reg_insulin_type)
        icrEditText = view.findViewById(R.id.et_reg_icr)
        isfEditText = view.findViewById(R.id.et_reg_isf)
        targetGlucoseEditText = view.findViewById(R.id.et_reg_target_glucose)
        glucoseUnitsSpinner = view.findViewById(R.id.spinner_reg_glucose_units)
        isfSubtitle = view.findViewById(R.id.tv_reg_isf_subtitle)
        targetSubtitle = view.findViewById(R.id.tv_reg_target_subtitle)
        nextButton = view.findViewById(R.id.btn_next_step)
    }

    private fun setupSpinners() {
        insulinTypeSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, insulinOptions)
        glucoseUnitsSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, glucoseUnitOptions)
    }

    private fun setupListeners() {
        glucoseUnitsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val unit = glucoseUnitOptions[position]
                isfSubtitle.text = "$unit drop per 1 unit"
                targetSubtitle.text = unit
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        nextButton.setOnClickListener {
            if (validateInputsAndSave()) {
                findNavController().navigate(R.id.action_registrationStep2_to_registrationStep3)
            }
        }
    }

    private fun validateInputsAndSave(): Boolean {
        val insulinType = insulinOptions[insulinTypeSpinner.selectedItemPosition]
        val icrStr = icrEditText.text.toString().trim()
        val isfStr = isfEditText.text.toString().trim()
        val targetStr = targetGlucoseEditText.text.toString().trim()

        if (insulinType == "Select") {
            ToastHelper.showShort(requireContext(), "Please select an Insulin Type")
            return false
        }

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

        // Save valid data
        val pm = UserProfileManager
        val ctx = requireContext()

        pm.saveInsulinType(ctx, insulinType)
        pm.saveInsulinCarbRatio(ctx, "1:$icrStr")
        pm.saveCorrectionFactor(ctx, isfNum)
        pm.saveTargetGlucose(ctx, targetNum)
        pm.saveGlucoseUnits(ctx, glucoseUnitOptions[glucoseUnitsSpinner.selectedItemPosition])

        return true
    }
}
