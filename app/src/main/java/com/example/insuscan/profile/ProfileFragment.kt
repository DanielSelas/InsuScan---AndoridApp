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
import com.example.insuscan.mapping.InsulinPlanMapper
import com.example.insuscan.profile.helpers.ProfileDataHelper
import com.example.insuscan.profile.helpers.ProfileImageHandler
import com.example.insuscan.profile.helpers.ProfileUiManager
import com.example.insuscan.utils.TopBarHelper
import kotlinx.coroutines.launch
import com.example.insuscan.utils.ToastHelper

/**
 * Profile screen: shows and edits the user's personal and insulin settings,
 * manages insulin plans, and syncs changes to the server.
 */
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
        dataHelper.planViewManager.onPlansEdited = {
            UserProfileManager.saveInsulinPlans(
                ctx,
                InsulinPlanMapper.toDtoList(dataHelper.planViewManager.getPlans())
            )
            saveToServer()
        }
        imageHandler.bind(ui, updateCallback = { saveToServer() })

        TopBarHelper.setupTopBar(
            rootView = view,
            title = getString(R.string.profile_title),
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

        bindEditableRow(
            row = ui.rowNameEmail,
            label = getString(R.string.profile_edit_name),
            inputType = InputType.TYPE_CLASS_TEXT,
            suffix = "",
            currentProvider = { UserProfileManager.getUserName(ctx) ?: "" }
        ) { value ->
            UserProfileManager.saveUserName(ctx, value)
            ui.nameDisplay.text = value
            saveToServer()
        }

        bindEditableRow(
            row = ui.rowAge,
            label = getString(R.string.profile_edit_age),
            inputType = InputType.TYPE_CLASS_NUMBER,
            suffix = "",
            currentProvider = { UserProfileManager.getUserAge(ctx)?.toString() ?: "" }
        ) { value ->
            value.toIntOrNull()?.let {
                UserProfileManager.saveUserAge(ctx, it)
                ui.setRowValue(ui.rowAge, value)
                saveToServer()
            }
        }

        bindEditableRow(
            row = ui.rowGender,
            label = getString(R.string.profile_edit_gender),
            inputType = InputType.TYPE_CLASS_TEXT,
            suffix = "",
            currentProvider = { UserProfileManager.getUserGender(ctx) ?: "" }
        ) { value ->
            UserProfileManager.saveUserGender(ctx, value)
            ui.setRowValue(ui.rowGender, value)
            saveToServer()
        }

        bindEditableRow(
            row = ui.rowIcr,
            label = getString(R.string.profile_edit_icr),
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            suffix = getString(R.string.unit_grams_per_unit),
            currentProvider = { UserProfileManager.getInsulinCarbRatioRaw(ctx)?.split(":")?.lastOrNull()?.trim() ?: "" }
        ) { value ->
            val trimmed = value.trim()
            val grams = trimmed.toFloatOrNull()
            if (grams == null || grams <= 0f) {
                ToastHelper.showShort(ctx, getString(R.string.profile_icr_positive_error))
                return@bindEditableRow
            }
            UserProfileManager.saveInsulinCarbRatio(ctx, "1:$trimmed")
            ui.setRowValue(ui.rowIcr, getString(R.string.profile_icr_value_format, trimmed))
            saveToServer()
        }

        bindEditableRow(
            row = ui.rowIsf,
            label = getString(R.string.profile_edit_isf),
            inputType = InputType.TYPE_CLASS_NUMBER,
            suffix = getString(R.string.unit_mg_dl),
            currentProvider = { UserProfileManager.getCorrectionFactor(ctx)?.toInt()?.toString() ?: "" }
        ) { value ->
            value.toFloatOrNull()?.let {
                UserProfileManager.saveCorrectionFactor(ctx, it)
                ui.setRowValue(ui.rowIsf, getString(R.string.value_mg_dl_format, value))
                saveToServer()
            }
        }

        bindEditableRow(
            row = ui.rowTargetGlucose,
            label = getString(R.string.profile_edit_target_glucose),
            inputType = InputType.TYPE_CLASS_NUMBER,
            suffix = getString(R.string.unit_mg_dl),
            currentProvider = { UserProfileManager.getTargetGlucose(ctx)?.toString() ?: "" }
        ) { value ->
            value.toIntOrNull()?.let {
                UserProfileManager.saveTargetGlucose(ctx, it)
                ui.setRowValue(ui.rowTargetGlucose, getString(R.string.value_mg_dl_format, value))
                saveToServer()
            }
        }

        bindEditableRow(
            row = ui.rowDoseRounding,
            label = getString(R.string.profile_edit_dose_rounding),
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL,
            suffix = getString(R.string.unit_units),
            currentProvider = { if (UserProfileManager.getDoseRounding(ctx) == UserProfileManager.DEFAULT_DOSE_ROUNDING) "0.5" else "1" }
        ) { value ->
            value.toFloatOrNull()?.let {
                UserProfileManager.saveDoseRounding(ctx, it)
                ui.setRowValue(ui.rowDoseRounding, getString(R.string.value_units_format, value))
                saveToServer()
            }
        }

        ui.addPlanButton.setOnClickListener { showAddPlanDialog() }
    }
    private fun showEditSheet(label: String, current: String, inputType: Int, suffix: String, onSave: (String) -> Unit) {
        ProfileEditBottomSheet(label, current, inputType, suffix, onSave).show(fm, "ProfileEditBottomSheet")
    }

    private fun bindEditableRow(
        row: View,
        label: String,
        inputType: Int,
        suffix: String,
        currentProvider: () -> String,
        onSave: (String) -> Unit
    ) {
        row.setOnClickListener {
            showEditSheet(label, currentProvider(), inputType, suffix, onSave)
        }
    }

    private fun showAddPlanDialog() {
        val layout = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 32)
        }
        val nameInput = android.widget.EditText(ctx).apply { hint = getString(R.string.plan_hint_name) }
        val icrInput = android.widget.EditText(ctx).apply {
            hint = getString(R.string.plan_hint_icr)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val isfInput = android.widget.EditText(ctx).apply {
            hint = getString(R.string.plan_hint_isf)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }
        val targetInput = android.widget.EditText(ctx).apply {
            hint = getString(R.string.profile_edit_target_glucose)
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        layout.addView(nameInput)
        layout.addView(icrInput)
        layout.addView(isfInput)
        layout.addView(targetInput)

        android.app.AlertDialog.Builder(ctx)
            .setTitle(getString(R.string.plan_dialog_title))
            .setView(layout)
            .setPositiveButton(getString(R.string.dialog_add)) { _, _ ->
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
                        InsulinPlanMapper.toDtoList(dataHelper.planViewManager.getPlans())
                    )

                    saveToServer()
                }
            }
            .setNegativeButton(getString(R.string.dialog_cancel), null)
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
        val plans = InsulinPlanMapper.toModelList(UserProfileManager.getInsulinPlans(ctx))
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