package com.example.insuscan.profile

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.insuscan.R

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get references to UI fields
        val insulinCarbEditText = view.findViewById<EditText>(R.id.et_insulin_carb_ratio)
        val correctionFactorEditText = view.findViewById<EditText>(R.id.et_correction_factor)
        val targetGlucoseEditText = view.findViewById<EditText>(R.id.et_target_glucose)
        val nameEditText = view.findViewById<EditText>(R.id.et_user_name)
        val ageEditText = view.findViewById<EditText>(R.id.et_user_age)
        val saveButton = view.findViewById<Button>(R.id.btn_save_profile)

        // Load existing profile data (fallbacks used only if nothing is saved yet)
        val storedRatio = UserProfileManager.getInsulinCarbRatioRaw(requireContext()) ?: "1:10"
        val storedCorrection =
            UserProfileManager.getCorrectionFactor(requireContext())?.toString() ?: "50"
        val storedTarget =
            UserProfileManager.getTargetGlucose(requireContext())?.toString() ?: "100"
        val storedName = UserProfileManager.getUserName(requireContext()) ?: "Daniel"

        // TODO: Add persistent age support when we decide we really need it
        val storedAge = "30"

        // Apply loaded values to UI
        insulinCarbEditText.setText(storedRatio)
        correctionFactorEditText.setText(storedCorrection)
        targetGlucoseEditText.setText(storedTarget)
        nameEditText.setText(storedName)
        ageEditText.setText(storedAge)

        saveButton.setOnClickListener {
            val ratio = insulinCarbEditText.text.toString().trim()
            val correction = correctionFactorEditText.text.toString().trim()
            val target = targetGlucoseEditText.text.toString().trim()
            val name = nameEditText.text.toString().trim()
            val age = ageEditText.text.toString().trim() // not used yet, kept for future

            // Basic validation for the medical fields
            if (ratio.isEmpty() || correction.isEmpty() || target.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "Please fill all medical parameters",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            // Validate ratio format like "1:10"
            if (!ratio.contains(":")) {
                Toast.makeText(
                    requireContext(),
                    "Insulin to carb ratio should be like 1:10",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            // Save ratio as raw string, for example "1:15"
            UserProfileManager.saveInsulinCarbRatio(requireContext(), ratio)

            // Save correction factor if valid
            val correctionValue = correction.toFloatOrNull()
            if (correctionValue != null && correctionValue > 0f) {
                UserProfileManager.saveCorrectionFactor(requireContext(), correctionValue)
            }

            // Save target glucose if valid
            val targetValue = target.toIntOrNull()
            if (targetValue != null && targetValue > 0) {
                UserProfileManager.saveTargetGlucose(requireContext(), targetValue)
            }

            // Save user name if not blank
            if (name.isNotBlank()) {
                UserProfileManager.saveUserName(requireContext(), name)
            }

            // TODO: When age is persisted, validate and save it here as well

            Toast.makeText(requireContext(), "Profile saved", Toast.LENGTH_SHORT).show()
        }
    }
}