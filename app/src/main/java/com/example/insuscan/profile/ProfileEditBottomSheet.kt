package com.example.insuscan.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.example.insuscan.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Bottom sheet for editing a single profile field, with an optional unit suffix.
 * Reports the trimmed value via onSave.
 */
class ProfileEditBottomSheet(
    private val label: String,
    private val currentValue: String,
    private val inputType: Int = android.text.InputType.TYPE_CLASS_TEXT,
    private val suffix: String = "",
    private val onSave: (String) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.bottom_sheet_profile_edit, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<TextView>(R.id.tv_edit_label).text = label
        val input = view.findViewById<EditText>(R.id.et_edit_value)
        input.setText(currentValue)
        input.inputType = inputType
        if (suffix.isNotEmpty()) {
            view.findViewById<TextView>(R.id.tv_edit_suffix).apply {
                text = suffix
                visibility = View.VISIBLE
            }
        }
        input.requestFocus()
        input.selectAll()
        view.findViewById<Button>(R.id.btn_edit_save).setOnClickListener {
            onSave(input.text.toString().trim())
            dismiss()
        }
        view.findViewById<Button>(R.id.btn_edit_cancel).setOnClickListener { dismiss() }
    }
}