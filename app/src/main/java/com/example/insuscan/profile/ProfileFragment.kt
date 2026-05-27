package com.example.insuscan.profile

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.example.insuscan.R
import com.example.insuscan.appdata.AppDataStore
import com.example.insuscan.appdata.DataState
import com.example.insuscan.auth.AuthManager
import com.example.insuscan.profile.helpers.ProfileDataHelper
import com.example.insuscan.profile.helpers.ProfileImageHandler
import com.example.insuscan.profile.helpers.ProfileUiManager
import com.example.insuscan.utils.TopBarHelper
import kotlinx.coroutines.launch


class ProfileFragment : Fragment(R.layout.fragment_profile) {

    private lateinit var ui: ProfileUiManager
    private val imageHandler = ProfileImageHandler(this)
    private lateinit var dataHelper: ProfileDataHelper
    private val ctx get() = requireContext()

    private val fm get() = parentFragmentManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ui = ProfileUiManager(view)
        ui.initRowLabels()
        dataHelper = ProfileDataHelper(ctx, ui)
        imageHandler.bind(ui, updateCallback = { saveToServer() })

        TopBarHelper.setupTopBar(
            rootView = view,
            title = "Profile",
            onBack = { findNavController().navigate(R.id.homeFragment) }
        )

        dataHelper.loadProfile(imageHandler)
        dataHelper.loadGoogleProfile(imageHandler)
        loadPlansFromLocal()
        observeDataStore()
        bindRowListeners()
        bindLogout()
    }

    private fun bindRowListeners() {
        ui.editPhotoButton.setOnClickListener { imageHandler.showPhotoOptionsDialog() }
        ui.profilePhoto.setOnClickListener { imageHandler.showPhotoOptionsDialog() }

        ui.rowNameEmail.setOnClickListener {
            val current = UserProfileManager.getUserName(ctx) ?: ""
            showEditSheet("Name", current, InputType.TYPE_CLASS_TEXT, "") { value ->
                UserProfileManager.saveUserName(ctx, value)
                ui.nameDisplay.text = value
                saveToServer()
            }
        }

        ui.rowAge.setOnClickListener {
            val current = UserProfileManager.getUserAge(ctx)?.toString() ?: ""
            showEditSheet("Age", current, InputType.TYPE_CLASS_NUMBER, "") { value ->
                value.toIntOrNull()?.let {
                    UserProfileManager.saveUserAge(ctx, it)
                    ui.setRowValue(ui.rowAge, value)
                    saveToServer()
                }
            }
        }

        ui.rowGender.setOnClickListener {
            val current = UserProfileManager.getUserGender(ctx) ?: ""
            showEditSheet("Gender", current, InputType.TYPE_CLASS_TEXT, "") { value ->
                UserProfileManager.saveUserGender(ctx, value)
                ui.setRowValue(ui.rowGender, value)
                saveToServer()
            }
        }

        ui.rowIcr.setOnClickListener {
            val current = UserProfileManager.getInsulinCarbRatioRaw(ctx)?.split(":")?.lastOrNull()?.trim() ?: ""
            showEditSheet("ICR — grams per unit", current, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL, "g/u") { value ->
                UserProfileManager.saveInsulinCarbRatio(ctx, "1:$value")
                ui.setRowValue(ui.rowIcr, "1u : ${value}g")
                saveToServer()
            }
        }

        ui.rowIsf.setOnClickListener {
            val current = UserProfileManager.getCorrectionFactor(ctx)?.toInt()?.toString() ?: ""
            showEditSheet("ISF — mg/dL per unit", current, InputType.TYPE_CLASS_NUMBER, "mg/dL") { value ->
                value.toFloatOrNull()?.let {
                    UserProfileManager.saveCorrectionFactor(ctx, it)
                    ui.setRowValue(ui.rowIsf, "$value mg/dL")
                    saveToServer()
                }
            }
        }

        ui.rowTargetGlucose.setOnClickListener {
            val current = UserProfileManager.getTargetGlucose(ctx)?.toString() ?: ""
            showEditSheet("Target glucose", current, InputType.TYPE_CLASS_NUMBER, "mg/dL") { value ->
                value.toIntOrNull()?.let {
                    UserProfileManager.saveTargetGlucose(ctx, it)
                    ui.setRowValue(ui.rowTargetGlucose, "$value mg/dL")
                    saveToServer()
                }
            }
        }

        ui.rowDoseRounding.setOnClickListener {
            val current = if (UserProfileManager.getDoseRounding(ctx) == 0.5f) "0.5" else "1"
            showEditSheet("Dose rounding", current, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL, "u") { value ->
                value.toFloatOrNull()?.let {
                    UserProfileManager.saveDoseRounding(ctx, it)
                    ui.setRowValue(ui.rowDoseRounding, "$value u")
                    saveToServer()
                }
            }
        }

        ui.addPlanButton.setOnClickListener { showAddPlanDialog() }

    }

    private fun showEditSheet(label: String, current: String, inputType: Int, suffix: String, onSave: (String) -> Unit) {
        ProfileEditBottomSheet(label, current, inputType, suffix, onSave).show(fm, "ProfileEditBottomSheet")
    }

    private fun showAddPlanDialog() {
        val layout = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }
        val nameInput = android.widget.EditText(ctx).apply { hint = "Plan name" }
        val icrInput = android.widget.EditText(ctx).apply {
            hint = "ICR (grams per unit)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val isfInput = android.widget.EditText(ctx).apply {
            hint = "ISF (mg/dL per unit)"
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val targetInput = android.widget.EditText(ctx).apply {
            hint = "Target glucose"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        layout.addView(nameInput)
        layout.addView(icrInput)
        layout.addView(isfInput)
        layout.addView(targetInput)

        android.app.AlertDialog.Builder(ctx)
            .setTitle("New insulin plan")
            .setView(layout)
            .setPositiveButton("Add") { _, _ ->
                val name = nameInput.text.toString().trim()
                if (name.isNotBlank()) {
                    dataHelper.planViewManager.addNewPlan(
                        name,
                        icrInput.text.toString().toFloatOrNull(),
                        isfInput.text.toString().toFloatOrNull(),
                        targetInput.text.toString().toIntOrNull()
                    )

                    UserProfileManager.saveInsulinPlans(
                        ctx,
                        dataHelper.planViewManager.getPlans().map { plan ->
                            com.example.insuscan.network.dto.InsulinPlanDto(
                                id = plan.id,
                                name = plan.name,
                                isDefault = plan.isDefault,
                                icr = plan.icr,
                                isf = plan.isf,
                                targetGlucose = plan.targetGlucose
                            )
                        }
                    )

                    saveToServer()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun bindLogout() {
        ui.logoutButton.setOnClickListener {
            UserProfileManager.clearAllData(ctx)
            AuthManager.signOut()
            findNavController().navigate(R.id.loginFragment)
        }
    }

    private fun saveToServer() {
        AppDataStore.saveProfile(dataHelper.buildUserDto())
    }
    private fun loadPlansFromLocal() {
        val plans = UserProfileManager.getInsulinPlans(ctx)?.map { dto ->
            InsulinPlan(
                id = dto.id ?: "",
                name = dto.name ?: "",
                isDefault = dto.isDefault,
                icr = dto.icr,
                isf = dto.isf,
                targetGlucose = dto.targetGlucose
            )
        }
        dataHelper.planViewManager.loadPlans(plans)
    }

    private fun observeDataStore() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    AppDataStore.profileState.collect { state ->
                        if (state is DataState.Ready) dataHelper.loadProfile(imageHandler)
                    }
                }
                launch {
                    AppDataStore.saveErrors.collect { message ->
                        Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}