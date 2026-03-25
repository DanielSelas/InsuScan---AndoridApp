package com.example.insuscan.profile

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.example.insuscan.R
import com.example.insuscan.auth.AuthManager
import com.example.insuscan.profile.helpers.ProfileDataHelper
import com.example.insuscan.profile.helpers.ProfileImageHandler
import com.example.insuscan.profile.helpers.ProfileRepository
import com.example.insuscan.profile.helpers.ProfileUiManager
import com.example.insuscan.utils.ToastHelper
import com.example.insuscan.utils.TopBarHelper
import kotlinx.coroutines.launch
import java.util.Calendar

class ProfileFragment : Fragment(R.layout.fragment_profile) {
    private lateinit var ui: ProfileUiManager
    private val imageHandler = ProfileImageHandler(this)
    private lateinit var dataHelper: ProfileDataHelper
    private val repository = ProfileRepository()
    private val ctx get() = requireContext()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        ui = ProfileUiManager(view)
        ui.setupSpinners(ctx)
        
        imageHandler.bind(ui) { syncPhotoToServer() }
        dataHelper = ProfileDataHelper(ctx, ui)

        setupTopBar(view)
        setupListeners()

        dataHelper.loadProfile(imageHandler)
        dataHelper.loadGoogleProfile(imageHandler)
        fetchServerProfile()
    }

    private fun setupTopBar(view: View) {
        TopBarHelper.setupTopBar(
            rootView = view,
            title = "Profile Settings",
            onBack = { findNavController().navigate(R.id.homeFragment) }
        )
    }

    private fun setupListeners() {
        ui.saveButton.setOnClickListener { saveProfile() }
        ui.logoutButton.setOnClickListener { logout() }

        ui.genderSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                ui.pregnancyLayout.visibility = if (ui.genderOptions[position] == "Female") View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
        ui.pregnantSwitch.setOnCheckedChangeListener { _, isChecked ->
            ui.dueDateLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
        }
        ui.dueDateTextView.setOnClickListener { showDatePicker() }

        ui.profilePhoto.setOnClickListener { imageHandler.showPhotoOptionsDialog() }
        ui.editPhotoButton.setOnClickListener { imageHandler.showPhotoOptionsDialog() }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            ctx,
            { _, year, month, day ->
                ui.dueDateTextView.text = "%02d/%02d/%d".format(day, month + 1, year)
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun saveProfile() {
        val userDto = dataHelper.validateAndSaveLocal() ?: return
        ToastHelper.showShort(ctx, "Saving profile...")
        
        viewLifecycleOwner.lifecycleScope.launch {
            ui.loadingOverlay.visibility = View.VISIBLE
            ui.saveButton.isEnabled = false
            
            val result = repository.executeServerSync(ctx, userDto)
            
            ui.loadingOverlay.visibility = View.GONE
            ui.saveButton.isEnabled = true
            
            if (result.isSuccess) {
                ToastHelper.showShort(ctx, "Profile saved and synced!")
                findNavController().popBackStack()
            } else {
                ToastHelper.showShort(ctx, "Saved locally. Sync failed: ${result.exceptionOrNull()?.message}")
                findNavController().popBackStack()
            }
        }
    }

    private fun fetchServerProfile() {
        viewLifecycleOwner.lifecycleScope.launch {
            repository.fetchServerProfile(ctx).onSuccess {
                dataHelper.loadProfile(imageHandler)
            }
        }
    }

    private fun syncPhotoToServer() {
        viewLifecycleOwner.lifecycleScope.launch {
            dataHelper.validateAndSaveLocal()?.let { userDto ->
                repository.executeServerSync(ctx, userDto)
            }
        }
    }

    private fun logout() {
        UserProfileManager.clearAllData(ctx)
        AuthManager.signOut()
        findNavController().navigate(R.id.loginFragment)
    }
}