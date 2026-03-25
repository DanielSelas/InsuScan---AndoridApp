package com.example.insuscan.profile.helpers

import android.content.Context
import android.view.View
import android.widget.*
import androidx.appcompat.widget.SwitchCompat
import com.example.insuscan.R

class ProfileUiManager(val view: View) {
    val profilePhoto: ImageView = view.findViewById(R.id.iv_profile_photo)
    val nameEditText: EditText = view.findViewById(R.id.et_user_name)
    val emailTextView: TextView = view.findViewById(R.id.tv_user_email)
    val ageEditText: EditText = view.findViewById(R.id.et_user_age)
    val genderSpinner: Spinner = view.findViewById(R.id.spinner_gender)
    val pregnancyLayout: LinearLayout = view.findViewById(R.id.layout_pregnancy)
    val pregnantSwitch: SwitchCompat = view.findViewById(R.id.switch_pregnant)
    val dueDateLayout: LinearLayout = view.findViewById(R.id.layout_due_date)
    val dueDateTextView: TextView = view.findViewById(R.id.tv_due_date)
    val icrEditText: EditText = view.findViewById(R.id.et_insulin_carb_ratio)
    val isfEditText: EditText = view.findViewById(R.id.et_correction_factor)
    val targetGlucoseEditText: EditText = view.findViewById(R.id.et_target_glucose)
    val isfSubtitle: TextView = view.findViewById(R.id.tv_isf_subtitle)
    val targetSubtitle: TextView = view.findViewById(R.id.tv_target_subtitle)
    val doseRoundingSpinner: Spinner = view.findViewById(R.id.spinner_dose_rounding)
    val sickAdjustmentEditText: EditText = view.findViewById(R.id.et_sick_adjustment)
    val stressAdjustmentEditText: EditText = view.findViewById(R.id.et_stress_adjustment)
    val lightExerciseEditText: EditText = view.findViewById(R.id.et_light_exercise_adjustment)
    val intenseExerciseEditText: EditText = view.findViewById(R.id.et_intense_exercise_adjustment)
    val saveButton: Button = view.findViewById(R.id.btn_save_profile)
    val logoutButton: Button = view.findViewById(R.id.btn_logout)
    val loadingOverlay: FrameLayout = view.findViewById(R.id.loading_overlay)
    val editPhotoButton: ImageView = view.findViewById(R.id.iv_edit_photo)

    val genderOptions = arrayOf("Select", "Male", "Female", "Other", "Prefer not to say")
    val roundingOptions = arrayOf("0.5 units", "1 unit")

    fun setupSpinners(context: Context) {
        genderSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, genderOptions)
        doseRoundingSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, roundingOptions)
    }

    fun setSpinnerSelection(spinner: Spinner, options: Array<String>, value: String) {
        val index = options.indexOf(value)
        if (index >= 0) spinner.setSelection(index)
    }

    fun setSpinnerByValue(spinner: Spinner, options: Array<String>, value: String) {
        val index = options.indexOfFirst {
            it.contains(value, ignoreCase = true) || value.contains(it.substringBefore(" "), ignoreCase = true)
        }
        if (index >= 0) spinner.setSelection(index)
    }
}
