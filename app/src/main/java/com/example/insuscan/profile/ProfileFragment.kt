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

        val insulinCarbEditText = view.findViewById<EditText>(R.id.et_insulin_carb_ratio)
        val correctionFactorEditText = view.findViewById<EditText>(R.id.et_correction_factor)
        val targetGlucoseEditText = view.findViewById<EditText>(R.id.et_target_glucose)
        val nameEditText = view.findViewById<EditText>(R.id.et_user_name)
        val ageEditText = view.findViewById<EditText>(R.id.et_user_age)
        val saveButton = view.findViewById<Button>(R.id.btn_save_profile)

        // ערכי דמו התחלתיים - אפשר לשנות אחר כך
        insulinCarbEditText.setText("1:10")
        correctionFactorEditText.setText("50")
        targetGlucoseEditText.setText("100")
        nameEditText.setText("Sarah")
        ageEditText.setText("32")

        saveButton.setOnClickListener {
            val ratio = insulinCarbEditText.text.toString().trim()
            val correction = correctionFactorEditText.text.toString().trim()
            val target = targetGlucoseEditText.text.toString().trim()
            val name = nameEditText.text.toString().trim()
            val age = ageEditText.text.toString().trim()

            if (ratio.isEmpty() || correction.isEmpty() || target.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "Please fill all medical parameters",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (!ratio.contains(":")) {
                Toast.makeText(
                    requireContext(),
                    "Insulin to carb ratio should be like 1:10",
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            UserProfileManager.saveInsulinCarbRatio(requireContext(), ratio)

            Toast.makeText(
                requireContext(),
                "Profile saved",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}