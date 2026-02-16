package com.example.insuscan.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.example.insuscan.R
import com.example.insuscan.profile.UserProfileManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

// bottom sheet for editing ICR, ISF, Target Glucose inline
class EditMedicalBottomSheet(
    private val onSave: (icr: Double?, isf: Double?, target: Int?) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_edit_medical, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()
        val pm = UserProfileManager

        val etIcr = view.findViewById<EditText>(R.id.et_icr)
        val etIsf = view.findViewById<EditText>(R.id.et_isf)
        val etTarget = view.findViewById<EditText>(R.id.et_target)
        val tvUnits = view.findViewById<TextView>(R.id.tv_glucose_units)
        val btnSave = view.findViewById<Button>(R.id.btn_save_medical)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel_medical)

        // pre-fill with current values
        pm.getGramsPerUnit(ctx)?.let { etIcr.setText(String.format("%.1f", it)) }
        pm.getCorrectionFactor(ctx)?.let { etIsf.setText(String.format("%.0f", it)) }
        pm.getTargetGlucose(ctx)?.let { etTarget.setText(it.toString()) }
        tvUnits.text = pm.getGlucoseUnits(ctx) ?: "mg/dL"

        btnSave.setOnClickListener {
            val icr = etIcr.text.toString().toDoubleOrNull()
            val isf = etIsf.text.toString().toDoubleOrNull()
            val target = etTarget.text.toString().toIntOrNull()
            onSave(icr, isf, target)
            dismiss()
        }

        btnCancel.setOnClickListener { dismiss() }
    }
}