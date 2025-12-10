package com.example.insuscan.manualentry

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.insuscan.R

class ManualEntryFragment : Fragment(R.layout.fragment_manual_entry) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val foodNameEditText = view.findViewById<EditText>(R.id.et_food_name)
        val carbsEditText = view.findViewById<EditText>(R.id.et_carbs)
        val saveButton = view.findViewById<Button>(R.id.btn_save_manual)

        saveButton.setOnClickListener {
            val name = foodNameEditText.text.toString().trim()
            val carbs = carbsEditText.text.toString().trim()


            // add validation here
            if (name.isEmpty() || carbs.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill food name and carbs", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // message for getting the values
            Toast.makeText(
                requireContext(),
                "Saved (mock): $name - $carbs g carbs",
                Toast.LENGTH_SHORT
            ).show()

            findNavController().popBackStack()
        }
    }
}