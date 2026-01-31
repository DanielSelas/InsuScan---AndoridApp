package com.example.insuscan.profile


import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.insuscan.R
import com.example.insuscan.auth.AuthManager
import com.example.insuscan.utils.ToastHelper
import com.example.insuscan.utils.TopBarHelper
import androidx.lifecycle.lifecycleScope
import com.example.insuscan.network.repository.UserRepository
import com.bumptech.glide.Glide
import com.example.insuscan.network.dto.UserDto
import com.example.insuscan.network.repository.UserRepositoryImpl
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.Calendar
import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class ProfileFragment : Fragment(R.layout.fragment_profile) {

    // Views - Profile Header
    private lateinit var profilePhoto: ImageView
    private lateinit var nameEditText: EditText
    private lateinit var emailTextView: TextView

    // Views - Personal Info
    private lateinit var ageEditText: EditText
    private lateinit var genderSpinner: Spinner
    private lateinit var pregnancyLayout: LinearLayout
    private lateinit var pregnantSwitch: SwitchCompat
    private lateinit var dueDateLayout: LinearLayout
    private lateinit var dueDateTextView: TextView

    // Views - Medical Info
    private lateinit var diabetesTypeSpinner: Spinner
    private lateinit var insulinTypeSpinner: Spinner

    // Views - Insulin Parameters
    private lateinit var icrEditText: EditText
    private lateinit var isfEditText: EditText
    private lateinit var targetGlucoseEditText: EditText
    private lateinit var activeInsulinTimeText: TextView
    private lateinit var isfSubtitle: TextView
    private lateinit var targetSubtitle: TextView

    // Views - Syringe Settings
    private lateinit var syringeSizeSpinner: Spinner
    private lateinit var doseRoundingSpinner: Spinner

    // Views - Adjustment Factors
    private lateinit var sickAdjustmentEditText: EditText
    private lateinit var stressAdjustmentEditText: EditText
    private lateinit var lightExerciseEditText: EditText
    private lateinit var intenseExerciseEditText: EditText

    // Views - Preferences
    private lateinit var glucoseUnitsSpinner: Spinner

    // Views - Buttons
    private lateinit var saveButton: Button
    private lateinit var logoutButton: Button
    private lateinit var loadingOverlay: FrameLayout

    private val ctx get() = requireContext()
    private val userRepository = UserRepositoryImpl()

    // Photo picker
    private lateinit var editPhotoButton: ImageView
    private var photoUri: Uri? = null

    // Gallery picker launcher
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { uploadAndSavePhoto(it) }
    }

    // Camera launcher
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            photoUri?.let { uploadAndSavePhoto(it) }
        }
    }

    // Camera permission launcher
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) openCamera() else ToastHelper.showShort(ctx, "Camera permission denied")
    }

    // Spinner data
    private val genderOptions = arrayOf("Select", "Male", "Female", "Other", "Prefer not to say")
    private val diabetesOptions = arrayOf("Select", "Type 1", "Type 2", "Gestational", "Other")
    private val insulinOptions = arrayOf("Select", "Rapid-acting", "Short-acting", "Other")
    private val syringeOptions = arrayOf("0.3ml (30u)", "0.5ml (50u)", "1ml (100u)")
    private val roundingOptions = arrayOf("0.5 units", "1 unit")
    private val glucoseUnitOptions = arrayOf("mg/dL", "mmol/L")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        findViews(view)
        setupTopBar(view)
        setupSpinners()
        setupListeners()

        loadProfile()
        loadGoogleProfile()
        fetchServerProfile()
    }

    private fun findViews(view: View) {
        // Profile Header
        profilePhoto = view.findViewById(R.id.iv_profile_photo)
        nameEditText = view.findViewById(R.id.et_user_name)
        emailTextView = view.findViewById(R.id.tv_user_email)

        // Personal Info
        ageEditText = view.findViewById(R.id.et_user_age)
        genderSpinner = view.findViewById(R.id.spinner_gender)
        pregnancyLayout = view.findViewById(R.id.layout_pregnancy)
        pregnantSwitch = view.findViewById(R.id.switch_pregnant)
        dueDateLayout = view.findViewById(R.id.layout_due_date)
        dueDateTextView = view.findViewById(R.id.tv_due_date)

        // Medical Info
        diabetesTypeSpinner = view.findViewById(R.id.spinner_diabetes_type)
        insulinTypeSpinner = view.findViewById(R.id.spinner_insulin_type)

        // Insulin Parameters
        icrEditText = view.findViewById(R.id.et_insulin_carb_ratio)
        isfEditText = view.findViewById(R.id.et_correction_factor)
        targetGlucoseEditText = view.findViewById(R.id.et_target_glucose)
        activeInsulinTimeText = view.findViewById(R.id.tv_active_insulin_time)
        isfSubtitle = view.findViewById(R.id.tv_isf_subtitle)
        targetSubtitle = view.findViewById(R.id.tv_target_subtitle)

        // Syringe Settings
        syringeSizeSpinner = view.findViewById(R.id.spinner_syringe_size)
        doseRoundingSpinner = view.findViewById(R.id.spinner_dose_rounding)

        // Adjustment Factors
        sickAdjustmentEditText = view.findViewById(R.id.et_sick_adjustment)
        stressAdjustmentEditText = view.findViewById(R.id.et_stress_adjustment)
        lightExerciseEditText = view.findViewById(R.id.et_light_exercise_adjustment)
        intenseExerciseEditText = view.findViewById(R.id.et_intense_exercise_adjustment)

        // Preferences
        glucoseUnitsSpinner = view.findViewById(R.id.spinner_glucose_units)

        // Buttons
        saveButton = view.findViewById(R.id.btn_save_profile)
        logoutButton = view.findViewById(R.id.btn_logout)
        loadingOverlay = view.findViewById(R.id.loading_overlay)
        editPhotoButton = view.findViewById(R.id.iv_edit_photo)

    }

    private fun setupTopBar(view: View) {
        TopBarHelper.setupTopBar(
            rootView = view,
            title = "Profile Settings",
            onBack = { findNavController().navigate(R.id.homeFragment) }
        )
    }

    private fun setupSpinners() {
        // Gender
        genderSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, genderOptions)

        // Diabetes Type
        diabetesTypeSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, diabetesOptions)

        // Insulin Type
        insulinTypeSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, insulinOptions)

        // Syringe Size
        syringeSizeSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, syringeOptions)

        // Dose Rounding
        doseRoundingSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, roundingOptions)

        // Glucose Units
        glucoseUnitsSpinner.adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, glucoseUnitOptions)
    }

    private fun setupListeners() {
        // Save button
        saveButton.setOnClickListener { saveProfile() }

        // Logout button
        logoutButton.setOnClickListener { logout() }

        // Gender change - show/hide pregnancy
        genderSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val isFemale = genderOptions[position] == "Female"
                pregnancyLayout.visibility = if (isFemale) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Pregnant switch - show/hide due date
        pregnantSwitch.setOnCheckedChangeListener { _, isChecked ->
            dueDateLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        // Due date picker
        dueDateTextView.setOnClickListener { showDatePicker() }

        // Insulin type change - update DIA
        insulinTypeSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val dia = when (insulinOptions[position]) {
                    "Rapid-acting" -> "4h"
                    "Short-acting" -> "5h"
                    else -> "4h"
                }
                activeInsulinTimeText.text = dia
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Glucose units change - update subtitles
        glucoseUnitsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val unit = glucoseUnitOptions[position]
                isfSubtitle.text = "$unit drop per 1 unit"
                targetSubtitle.text = unit
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        // Profile photo click - show options dialog
        profilePhoto.setOnClickListener { showPhotoOptionsDialog() }
        editPhotoButton.setOnClickListener { showPhotoOptionsDialog() }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            ctx,
            { _, year, month, day ->
                val date = "%02d/%02d/%d".format(day, month + 1, year)
                dueDateTextView.text = date
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun loadGoogleProfile() {
        val user = FirebaseAuth.getInstance().currentUser ?: return

        // Email
        emailTextView.text = user.email ?: "No email"

        // Name - only if not already saved locally
        if (UserProfileManager.getUserName(ctx).isNullOrBlank()) {
            user.displayName?.let { nameEditText.setText(it) }
        }

        // Photo - use saved URL first, fallback to Google
        val savedUrl = UserProfileManager.getProfilePhotoUrl(ctx)
        if (!savedUrl.isNullOrBlank()) {
            loadProfilePhoto(savedUrl)
        } else {
            user.photoUrl?.let { uri ->
                Glide.with(this)
                    .load(uri)
                    .circleCrop()
                    .placeholder(R.drawable.ic_person)
                    .into(profilePhoto)
                profilePhoto.setPadding(0, 0, 0, 0)
            }
        }
    }

    private fun loadProfile() {
        val pm = UserProfileManager

        // Personal Info
        nameEditText.setText(pm.getUserName(ctx) ?: "")
        pm.getUserAge(ctx)?.let { ageEditText.setText(it.toString()) }
        pm.getUserGender(ctx)?.let { setSpinnerSelection(genderSpinner, genderOptions, it) }
        pregnantSwitch.isChecked = pm.getIsPregnant(ctx)
        pm.getDueDate(ctx)?.let { dueDateTextView.text = it }

        // Medical Info
        pm.getDiabetesType(ctx)?.let { setSpinnerSelection(diabetesTypeSpinner, diabetesOptions, it) }
        pm.getInsulinType(ctx)?.let { setSpinnerSelection(insulinTypeSpinner, insulinOptions, it) }

        // Insulin Parameters - Handle nulls safely (No defaults)
        val rawRatio = pm.getInsulinCarbRatioRaw(ctx)
        if (rawRatio != null) {
            val ratioParts = rawRatio.split(":")
            if (ratioParts.size == 2) {
                icrEditText.setText(ratioParts[1].trim())
            }
        } else {
            icrEditText.text.clear()
        }

        val isf = pm.getCorrectionFactor(ctx)
        if (isf != null) {
            isfEditText.setText(isf.toInt().toString())
        } else {
            isfEditText.text.clear()
        }

        val target = pm.getTargetGlucose(ctx)
        if (target != null) {
            targetGlucoseEditText.setText(target.toString())
        } else {
            targetGlucoseEditText.text.clear()
        }

        // Active Insulin Time (DIA)
        val diaValue = pm.getActiveInsulinTime(ctx)
        activeInsulinTimeText.text = "${diaValue.toInt()}h"

        // Syringe Settings
        setSpinnerByValue(syringeSizeSpinner, syringeOptions, pm.getSyringeSize(ctx))
        val rounding = if (pm.getDoseRounding(ctx) == 0.5f) "0.5 units" else "1 unit"
        setSpinnerByValue(doseRoundingSpinner, roundingOptions, rounding)

        // Adjustment Factors
        sickAdjustmentEditText.setText(pm.getSickDayAdjustment(ctx).toString())
        stressAdjustmentEditText.setText(pm.getStressAdjustment(ctx).toString())
        lightExerciseEditText.setText(pm.getLightExerciseAdjustment(ctx).toString())
        intenseExerciseEditText.setText(pm.getIntenseExerciseAdjustment(ctx).toString())

        // Preferences
        setSpinnerByValue(glucoseUnitsSpinner, glucoseUnitOptions, pm.getGlucoseUnits(ctx))

        // Load profile photo
        val savedPhotoUrl = pm.getProfilePhotoUrl(ctx)
        android.util.Log.e("ProfileFragment", "Loading Profile. Saved Photo URL: $savedPhotoUrl")
        loadProfilePhoto(savedPhotoUrl)
    }

    private fun setSpinnerSelection(spinner: Spinner, options: Array<String>, value: String) {
        val index = options.indexOf(value)
        if (index >= 0) spinner.setSelection(index)
    }

    private fun setSpinnerByValue(spinner: Spinner, options: Array<String>, value: String) {
        val index = options.indexOfFirst { it.contains(value, ignoreCase = true) || value.contains(it.substringBefore(" "), ignoreCase = true) }
        if (index >= 0) spinner.setSelection(index)
    }

    private fun saveProfile() {
        val pm = UserProfileManager

        // Validate required fields
        val icrValue = icrEditText.text.toString().trim()
        val isf = isfEditText.text.toString().trim()
        val target = targetGlucoseEditText.text.toString().trim()

        // Validation: Block 0 values
        var hasError = false

        // Check ICR (cannot be 0)
        val icrNum = icrValue.toFloatOrNull()
        if (icrNum != null && icrNum == 0f) {
            icrEditText.error = "Value cannot be 0"
            hasError = true
        }

        // Check ISF (cannot be 0)
        val isfNum = isf.toFloatOrNull()
        if (isfNum != null && isfNum == 0f) {
            isfEditText.error = "Value cannot be 0"
            hasError = true
        }

        // Check Target Glucose (cannot be 0)
        val targetNum = target.toIntOrNull()
        if (targetNum != null && targetNum == 0) {
            targetGlucoseEditText.error = "Value cannot be 0"
            hasError = true
        }

        if (hasError) {
            ToastHelper.showShort(ctx, "Please fix invalid values")
            return
        }
        
        // Format to 1:X
        val fullIcrString = "1:$icrValue"

        // Save Personal Info
        val name = nameEditText.text.toString().trim()
        if (name.isNotBlank()) pm.saveUserName(ctx, name)

        ageEditText.text.toString().toIntOrNull()?.let { pm.saveUserAge(ctx, it) }

        val gender = genderOptions[genderSpinner.selectedItemPosition]
        if (gender != "Select") pm.saveUserGender(ctx, gender)

        pm.saveIsPregnant(ctx, pregnantSwitch.isChecked)
        if (pregnantSwitch.isChecked) {
            val dueDate = dueDateTextView.text.toString()
            if (dueDate != "Select date") pm.saveDueDate(ctx, dueDate)
        }

        // Save Medical Info
        val diabetesType = diabetesOptions[diabetesTypeSpinner.selectedItemPosition]
        if (diabetesType != "Select") pm.saveDiabetesType(ctx, diabetesType)

        val insulinType = insulinOptions[insulinTypeSpinner.selectedItemPosition]
        if (insulinType != "Select") pm.saveInsulinType(ctx, insulinType)

        // Save Insulin Parameters
        pm.saveInsulinCarbRatio(ctx, fullIcrString)
        isf.toFloatOrNull()?.let { pm.saveCorrectionFactor(ctx, it) }
        target.toIntOrNull()?.let { pm.saveTargetGlucose(ctx, it) }

        // Save Syringe Settings
        val syringeSize = syringeOptions[syringeSizeSpinner.selectedItemPosition]
            .substringBefore(" ").trim()
        pm.saveSyringeSize(ctx, syringeSize)

        val doseRounding = if (doseRoundingSpinner.selectedItemPosition == 0) 0.5f else 1f
        pm.saveDoseRounding(ctx, doseRounding)

        // Save Adjustment Factors
        sickAdjustmentEditText.text.toString().toIntOrNull()?.let { pm.saveSickDayAdjustment(ctx, it) }
        stressAdjustmentEditText.text.toString().toIntOrNull()?.let { pm.saveStressAdjustment(ctx, it) }
        lightExerciseEditText.text.toString().toIntOrNull()?.let { pm.saveLightExerciseAdjustment(ctx, it) }
        intenseExerciseEditText.text.toString().toIntOrNull()?.let { pm.saveIntenseExerciseAdjustment(ctx, it) }

        // Save Preferences
        pm.saveGlucoseUnits(ctx, glucoseUnitOptions[glucoseUnitsSpinner.selectedItemPosition])

        // Save email
        FirebaseAuth.getInstance().currentUser?.email?.let { pm.saveUserEmail(ctx, it) }

        ToastHelper.showShort(ctx, "Saving profile...")

        // Launch coroutine to sync to server while blocking UI
        viewLifecycleOwner.lifecycleScope.launch {
            loadingOverlay.visibility = View.VISIBLE
            saveButton.isEnabled = false
            
            val result = executeServerSync()
            
            loadingOverlay.visibility = View.GONE
            saveButton.isEnabled = true

            if (result.isSuccess) {
                ToastHelper.showShort(ctx, "Profile saved and synced!")
                findNavController().popBackStack()
            } else {
                ToastHelper.showShort(ctx, "Saved locally. Sync failed: ${result.exceptionOrNull()?.message}")
                // Still pop backstack as local save was successful
                findNavController().popBackStack()
            }
        }
    }

    private fun fetchServerProfile() {
        val email = UserProfileManager.getUserEmail(ctx) ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            userRepository.getUser(email).onSuccess { userDto ->
                UserProfileManager.syncFromServer(ctx, userDto)

                loadProfile()

                android.util.Log.d("ProfileFragment", "Profile synced from server")
            }.onFailure { e ->
                android.util.Log.e("ProfileFragment", "Failed to fetch profile: ${e.message}")
            }
        }
    }

    private fun syncToServer() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = executeServerSync()
             if (result.isSuccess) {
                 android.util.Log.d("ProfileFragment", "Background sync success!")
             } else {
                 android.util.Log.e("ProfileFragment", "Background sync failed")
             }
        }
    }

    private suspend fun executeServerSync(): Result<UserDto> {
        val email = UserProfileManager.getUserEmail(ctx) 
            ?: return Result.failure(Exception("No email found"))

        return try {
            // 1. Determine dose rounding
            val doseRoundingValue = if (doseRoundingSpinner.selectedItemPosition == 0) "0.5" else "1"

            // 2. Determine active insulin time (integer)
            val diaValue = activeInsulinTimeText.text.toString().replace("h", "").trim().toIntOrNull()

            // 3. FIX: Map Syringe UI selection to Server Enum Name
            val selectedSyringeIndex = syringeSizeSpinner.selectedItemPosition
            val syringeEnumValue = when (selectedSyringeIndex) {
                0 -> "SYRINGE_30_UNIT"  // 0.3ml
                1 -> "SYRINGE_50_UNIT"  // 0.5ml
                else -> "SYRINGE_100_UNIT" // 1ml
            }

            // Build UserDto with ALL fields
            val userDto = UserDto(
                userId = null,
                username = nameEditText.text.toString().trim().ifEmpty { null },
                role = null,
                avatar = UserProfileManager.getProfilePhotoUrl(ctx),

                // Medical
                insulinCarbRatio = icrEditText.text.toString().trim().let {
                    if (it.isEmpty()) null else "1:$it"
                },
                correctionFactor = isfEditText.text.toString().toFloatOrNull(),
                targetGlucose = targetGlucoseEditText.text.toString().toIntOrNull(),

                // Syringe - SEND ENUM NAME, NOT UI STRING
                syringeType = syringeEnumValue,
                customSyringeLength = null,

                // Personal
                age = ageEditText.text.toString().toIntOrNull(),
                gender = genderOptions.getOrNull(genderSpinner.selectedItemPosition).takeIf { it != "Select" },
                pregnant = pregnantSwitch.isChecked,
                dueDate = dueDateTextView.text.toString().takeIf { it != "Select date" },

                // Medical Extended
                diabetesType = diabetesOptions.getOrNull(diabetesTypeSpinner.selectedItemPosition).takeIf { it != "Select" },
                insulinType = insulinOptions.getOrNull(insulinTypeSpinner.selectedItemPosition).takeIf { it != "Select" },
                activeInsulinTime = diaValue,

                // Dose Settings
                doseRounding = doseRoundingValue,

                // Adjustments
                sickDayAdjustment = sickAdjustmentEditText.text.toString().toIntOrNull(),
                stressAdjustment = stressAdjustmentEditText.text.toString().toIntOrNull(),
                lightExerciseAdjustment = lightExerciseEditText.text.toString().toIntOrNull(),
                intenseExerciseAdjustment = intenseExerciseEditText.text.toString().toIntOrNull(),

                // Preferences
                glucoseUnits = glucoseUnitOptions.getOrNull(glucoseUnitsSpinner.selectedItemPosition),

                createdTimestamp = null,
                updatedTimestamp = null
            )

            val result = userRepository.updateUser(email, userDto)
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun showPhotoOptionsDialog() {
        val options = arrayOf("Take Photo", "Choose from Gallery", "Remove Photo")
        AlertDialog.Builder(ctx)
            .setTitle("Profile Photo")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> checkCameraPermissionAndOpen()
                    1 -> pickImageLauncher.launch("image/*")
                    2 -> removeProfilePhoto()
                }
            }
            .show()
    }

    private fun checkCameraPermissionAndOpen() {
        when {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED -> openCamera()
            else -> cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun openCamera() {
        val photoFile = createImageFile()
        photoUri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.provider", photoFile)
        takePictureLauncher.launch(photoUri)
    }

    private fun createImageFile(): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val storageDir = ctx.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("PROFILE_${timestamp}_", ".jpg", storageDir)
    }

    private fun uploadAndSavePhoto(uri: Uri) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val storageRef = FirebaseStorage.getInstance().reference
            .child("profile_photos/$userId.jpg")

        ToastHelper.showShort(ctx, "Uploading photo...")

        storageRef.putFile(uri)
            .addOnSuccessListener {
                storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                    val url = downloadUrl.toString()

                    // 1. Save locally and update UI immediately
                    UserProfileManager.saveProfilePhotoUrl(ctx, url)
                    loadProfilePhoto(url)

                    // 2. Sync with server immediately to persist the new URL remotely
                    syncToServer()

                    ToastHelper.showShort(ctx, "Photo updated and saved!")
                }
            }
            .addOnFailureListener { e ->
                Log.e("ProfileFragment", "Upload failed: ${e.message}")
                ToastHelper.showShort(ctx, "Upload failed")
            }
    }

    private fun loadProfilePhoto(url: String?) {
        if (url.isNullOrBlank()) {
            profilePhoto.setImageResource(R.drawable.ic_person)
            profilePhoto.setPadding(32, 32, 32, 32)
            return
        }

        Glide.with(this)
            .load(url)
            .circleCrop()
            .placeholder(R.drawable.ic_person)
            .into(profilePhoto)
        profilePhoto.setPadding(0, 0, 0, 0)
    }

    private fun removeProfilePhoto() {
        UserProfileManager.saveProfilePhotoUrl(ctx, "")
        profilePhoto.setImageResource(R.drawable.ic_person)
        profilePhoto.setPadding(32, 32, 32, 32)
        ToastHelper.showShort(ctx, "Photo removed")
    }
    private fun logout() {
        UserProfileManager.clearAllData(ctx)
        AuthManager.signOut()
        findNavController().navigate(R.id.loginFragment)
    }
}