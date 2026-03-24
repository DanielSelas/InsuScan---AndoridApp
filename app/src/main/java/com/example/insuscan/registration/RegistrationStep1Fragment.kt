package com.example.insuscan.registration

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.insuscan.R
import com.example.insuscan.profile.UserProfileManager
import java.util.Calendar

class RegistrationStep1Fragment : Fragment(R.layout.fragment_registration_step1) {

    private lateinit var nameEditText: EditText
    private lateinit var ageEditText: EditText
    private lateinit var genderSpinner: Spinner
    private lateinit var pregnancyLayout: LinearLayout
    private lateinit var pregnantSwitch: SwitchCompat
    private lateinit var dueDateLayout: LinearLayout
    private lateinit var dueDateTextView: TextView
    private lateinit var diabetesTypeSpinner: Spinner
    private lateinit var nextButton: Button

    private val genderOptions = arrayOf("Select", "Male", "Female", "Other", "Prefer not to say")
    private val diabetesOptions = arrayOf("Select", "Type 1", "Type 2", "Gestational", "Other")

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        findViews(view)
        setupSpinners()
        setupListeners()
        prefillData()
    }

    private fun findViews(view: View) {
        nameEditText = view.findViewById(R.id.et_reg_name)
        ageEditText = view.findViewById(R.id.et_reg_age)
        genderSpinner = view.findViewById(R.id.spinner_reg_gender)
        pregnancyLayout = view.findViewById(R.id.layout_reg_pregnancy)
        pregnantSwitch = view.findViewById(R.id.switch_reg_pregnant)
        dueDateLayout = view.findViewById(R.id.layout_reg_due_date)
        dueDateTextView = view.findViewById(R.id.tv_reg_due_date)
        diabetesTypeSpinner = view.findViewById(R.id.spinner_reg_diabetes_type)
        nextButton = view.findViewById(R.id.btn_next_step)
    }

    private fun setupSpinners() {
        genderSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, genderOptions)
        diabetesTypeSpinner.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, diabetesOptions)
    }

    private fun setupListeners() {
        genderSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val isFemale = genderOptions[position] == "Female"
                pregnancyLayout.visibility = if (isFemale) View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        pregnantSwitch.setOnCheckedChangeListener { _, isChecked ->
            dueDateLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        dueDateTextView.setOnClickListener { showDatePicker() }

        nextButton.setOnClickListener {
            saveStepData()
            findNavController().navigate(R.id.action_registrationStep1_to_registrationStep2)
        }
    }

    private fun prefillData() {
        val userName = UserProfileManager.getUserName(requireContext())
        if (!userName.isNullOrBlank()) {
            nameEditText.setText(userName)
        }
    }

    private fun showDatePicker() {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val date = "%02d/%02d/%d".format(day, month + 1, year)
                dueDateTextView.text = date
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun saveStepData() {
        val pm = UserProfileManager
        val ctx = requireContext()

        val name = nameEditText.text.toString().trim()
        if (name.isNotBlank()) pm.saveUserName(ctx, name)

        ageEditText.text.toString().toIntOrNull()?.let { pm.saveUserAge(ctx, it) }

        val gender = genderOptions[genderSpinner.selectedItemPosition]
        if (gender != "Select") pm.saveUserGender(ctx, gender)

        pm.saveIsPregnant(ctx, pregnantSwitch.isChecked)
        if (pregnantSwitch.isChecked) {
            val dueDate = dueDateTextView.text.toString()
            if (dueDate != "Select Date") pm.saveDueDate(ctx, dueDate)
        }

        val diabetesType = diabetesOptions[diabetesTypeSpinner.selectedItemPosition]
        if (diabetesType != "Select") pm.saveDiabetesType(ctx, diabetesType)
    }
}
