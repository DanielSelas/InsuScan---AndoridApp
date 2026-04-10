package com.example.insuscan.registration

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.insuscan.R
import com.example.insuscan.network.repository.UserRepositoryImpl
import com.example.insuscan.profile.UserProfileManager
import com.example.insuscan.registration.helper.RegistrationStep3Helper
import com.example.insuscan.utils.ToastHelper
import kotlinx.coroutines.launch

class RegistrationStep3Fragment : Fragment(R.layout.fragment_registration_step3) {

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

    private lateinit var helper: RegistrationStep3Helper

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        helper = RegistrationStep3Helper(requireContext(), UserRepositoryImpl())
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
        btnAddCustomPlan.setOnClickListener { addCustomPlanRow() }
        finishButton.setOnClickListener { saveAndSync() }
    }

    private fun addCustomPlanRow() {
        val planView = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_custom_plan_registration, layoutDynamicPlansContainer, false)

        planView.findViewById<ImageView>(R.id.btn_remove_custom_plan).setOnClickListener {
            layoutDynamicPlansContainer.removeView(planView)
        }

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

        etSicknessIcr.hint = icrVal; etSicknessIsf.hint = isfVal; etSicknessTarget.hint = targetVal
        etStressIcr.hint = icrVal;   etStressIsf.hint = isfVal;   etStressTarget.hint = targetVal
        etTrainingIcr.hint = icrVal; etTrainingIsf.hint = isfVal; etTrainingTarget.hint = targetVal
    }

    private fun saveAndSync() {
        val ctx = requireContext()
        val pm = UserProfileManager

        helper.collectAndSavePlans(
            etSicknessIcr, etSicknessIsf, etSicknessTarget,
            etStressIcr, etStressIsf, etStressTarget,
            etTrainingIcr, etTrainingIsf, etTrainingTarget,
            layoutDynamicPlansContainer
        )

        val email = pm.getUserEmail(ctx)
        if (email == null) {
            pm.setRegistrationComplete(ctx, true)
            navigateToSplash()
            return
        }

        loadingOverlay.visibility = View.VISIBLE
        finishButton.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val success = helper.syncWithServer(email, pm.getDoseRounding(ctx))
                if (success) ToastHelper.showShort(ctx, "Registration complete!")
            } catch (e: Exception) {
                android.util.Log.e("Registration", "Sync failed", e)
                ToastHelper.showShort(ctx, "Saved locally. Server sync failed.")
            } finally {
                loadingOverlay.visibility = View.GONE
                finishButton.isEnabled = true
                pm.setRegistrationComplete(ctx, true)
                navigateToSplash()
            }
        }
    }

    private fun navigateToSplash() {
        findNavController().navigate(R.id.action_registrationStep3_to_splashAnimation)
    }
}
