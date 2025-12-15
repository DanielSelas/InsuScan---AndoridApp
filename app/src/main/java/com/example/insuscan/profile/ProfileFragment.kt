package com.example.insuscan.profile

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.insuscan.R
import com.example.insuscan.utils.ToastHelper

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private lateinit var insulinCarbEditText: EditText
    private lateinit var correctionFactorEditText: EditText
    private lateinit var targetGlucoseEditText: EditText
    private lateinit var nameEditText: EditText
    private lateinit var ageEditText: EditText
    private lateinit var saveButton: Button

    private lateinit var storedRatio: String
    private lateinit var storedCorrection: String
    private lateinit var storedTarget: String
    private lateinit var storedName: String
    private lateinit var storedAge: String


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        findViews(view)

        setUpHardCodedProfile()

        initializeListeners()
    }

    private fun findViews(view: View) {
        // Get references to UI fields
        insulinCarbEditText = view.findViewById<EditText>(R.id.et_insulin_carb_ratio)
        correctionFactorEditText = view.findViewById<EditText>(R.id.et_correction_factor)
        targetGlucoseEditText = view.findViewById<EditText>(R.id.et_target_glucose)
        nameEditText = view.findViewById<EditText>(R.id.et_user_name)
        ageEditText = view.findViewById<EditText>(R.id.et_user_age)
        saveButton = view.findViewById<Button>(R.id.btn_save_profile)
    }

    private fun setUpHardCodedProfile() {
        val ctx = requireContext()

        // Load existing profile data (fallbacks used only if nothing is saved yet)
        storedRatio = UserProfileManager.getInsulinCarbRatioRaw(ctx) ?: "1:10"
        storedCorrection = UserProfileManager.getCorrectionFactor(ctx)?.toString() ?: "50"
        storedTarget = UserProfileManager.getTargetGlucose(ctx)?.toString() ?: "100"
        storedName = UserProfileManager.getUserName(ctx) ?: "Daniel"

        // TODO: Add persistent age support when we decide we really need it
        storedAge = "30"

        // Apply to UI
        setFields()
    }

    private fun setFields() {
        insulinCarbEditText.setText(storedRatio)
        correctionFactorEditText.setText(storedCorrection)
        targetGlucoseEditText.setText(storedTarget)
        nameEditText.setText(storedName)
        ageEditText.setText(storedAge)
    }

    private fun initializeListeners() {
        saveButton.setOnClickListener {
            onSaveClicked()
        }
    }

    private fun onSaveClicked() {
        val ctx = requireContext()

        val ratio = insulinCarbEditText.text.toString().trim()
        val correction = correctionFactorEditText.text.toString().trim()
        val target = targetGlucoseEditText.text.toString().trim()
        val name = nameEditText.text.toString().trim()
        val age = ageEditText.text.toString().trim() // not used yet, kept for future

        // Basic validation for the medical fields
        if (ratio.isEmpty() || correction.isEmpty() || target.isEmpty()) {
            ToastHelper.showShort(ctx,"Please fill all medical parameters")
            return
        }

        // Validate ratio format like "1:10"
        if (!ratio.contains(":")) {
            ToastHelper.showShort(ctx,"Insulin to carb ratio should be like 1:10")
        return
        }

        // Save ratio as raw string, for example "1:15"
        UserProfileManager.saveInsulinCarbRatio(ctx, ratio)

        // Save correction factor if valid
        val correctionValue = correction.toFloatOrNull()
        if (correctionValue != null && correctionValue > 0f) {
            UserProfileManager.saveCorrectionFactor(ctx, correctionValue)
        }

        // Save target glucose if valid
        val targetValue = target.toIntOrNull()
        if (targetValue != null && targetValue > 0) {
            UserProfileManager.saveTargetGlucose(ctx, targetValue)
        }

        // Save user name if not blank
        if (name.isNotBlank()) {
            UserProfileManager.saveUserName(ctx, name)
        }

        // TODO: When age is persisted, validate and save it here as well
        ToastHelper.showShort(ctx,"Profile saved")
    }
}

