package com.example.insuscan.profile

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.insuscan.R
import com.example.insuscan.utils.ToastHelper
import com.example.insuscan.utils.TopBarHelper
import androidx.lifecycle.lifecycleScope
import com.example.insuscan.network.repository.UserRepository
import kotlinx.coroutines.launch
import com.example.insuscan.auth.AuthManager

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private lateinit var insulinCarbEditText: EditText
    private lateinit var correctionFactorEditText: EditText
    private lateinit var targetGlucoseEditText: EditText
    private lateinit var nameEditText: EditText
    private lateinit var ageEditText: EditText
    private lateinit var saveButton: Button

    private lateinit var logoutButton: Button

    private lateinit var storedRatio: String
    private lateinit var storedCorrection: String
    private lateinit var storedTarget: String
    private lateinit var storedName: String
    private lateinit var storedAge: String

    private val ctx get() = requireContext()

    private val userRepository = UserRepository()


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        findViews(view)

        TopBarHelper.setupTopBar(
            rootView = view,
            title = "Profile settings",
            onBack = {
                findNavController().navigate(R.id.homeFragment)
            }
        )

        setUpHardCodedProfile()
        initializeListeners()
    }

    private fun findViews(view: View) {
        insulinCarbEditText = view.findViewById(R.id.et_insulin_carb_ratio)
        correctionFactorEditText = view.findViewById(R.id.et_correction_factor)
        targetGlucoseEditText = view.findViewById(R.id.et_target_glucose)
        nameEditText = view.findViewById(R.id.et_user_name)
        ageEditText = view.findViewById(R.id.et_user_age)
        saveButton = view.findViewById(R.id.btn_save_profile)
        logoutButton = view.findViewById(R.id.btn_logout)

    }

    private fun setUpHardCodedProfile() {
        val ctx = requireContext()

        storedRatio = UserProfileManager.getInsulinCarbRatioRaw(ctx) ?: "1:10"
        storedCorrection = UserProfileManager.getCorrectionFactor(ctx)?.toString() ?: "50"
        storedTarget = UserProfileManager.getTargetGlucose(ctx)?.toString() ?: "100"
        storedName = UserProfileManager.getUserName(ctx) ?: "Daniel"

        // TODO: Add persistent age support when we decide we really need it
        storedAge = "30"

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
        saveButton.setOnClickListener { onSaveClicked() }
        logoutButton.setOnClickListener { onLogoutClicked() }

    }

    private fun onSaveClicked() {
        val ctx = requireContext()

        val ratio = insulinCarbEditText.text.toString().trim()
        val correction = correctionFactorEditText.text.toString().trim()
        val target = targetGlucoseEditText.text.toString().trim()
        val name = nameEditText.text.toString().trim()
        val age = ageEditText.text.toString().trim() // not used yet, kept for future

        if (ratio.isEmpty() || correction.isEmpty() || target.isEmpty()) {
            ToastHelper.showShort(ctx, "Please fill all medical parameters")
            return
        }

        if (!ratio.contains(":")) {
            ToastHelper.showShort(ctx, "Insulin to carb ratio should be like 1:10")
            return
        }

        UserProfileManager.saveInsulinCarbRatio(ctx, ratio)

        val correctionValue = correction.toFloatOrNull()
        if (correctionValue != null && correctionValue > 0f) {
            UserProfileManager.saveCorrectionFactor(ctx, correctionValue)
        }

        val targetValue = target.toIntOrNull()
        if (targetValue != null && targetValue > 0) {
            UserProfileManager.saveTargetGlucose(ctx, targetValue)
        }

        if (name.isNotBlank()) {
            UserProfileManager.saveUserName(ctx, name)
        }

        // TODO: When age is persisted, validate and save it here as well
        ToastHelper.showShort(ctx, "Profile saved")
    }

    private fun saveProfileToServer() {
        val email = UserProfileManager.getUserEmail(ctx) ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            // First try to get existing user
            val getResult = userRepository.getUser(email)

            getResult.onSuccess { existingUser ->
                // Update existing user
                val updatedUser = existingUser.copy(
                    // update fields as needed
                )
                userRepository.updateUser(email, updatedUser)
            }.onFailure {
                // User doesn't exist, create new one
                val name = nameEditText.text.toString()
                userRepository.register(email, name)
            }
        }
    }
    private fun onLogoutClicked() {
        // Clear local session (server-side auth is email-based)
        UserProfileManager.clearUserEmail(ctx)
        AuthManager.signOut()
        findNavController().navigate(R.id.loginFragment)
    }
}