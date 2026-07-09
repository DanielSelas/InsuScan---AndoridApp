package com.example.insuscan.profile.helpers

import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.insuscan.R

class ProfileUiManager(val view: View) {

    val profilePhoto: ImageView = view.findViewById(R.id.iv_profile_photo)
    val editPhotoButton: ImageView = view.findViewById(R.id.iv_edit_photo)
    val nameDisplay: TextView = view.findViewById(R.id.tv_user_name_display)
    val emailTextView: TextView = view.findViewById(R.id.tv_user_email)
    val logoutButton: View = view.findViewById(R.id.btn_logout)
    val plansContainer: LinearLayout = view.findViewById(R.id.plans_container)
    val rowNameEmail: View = view.findViewById(R.id.row_name_email)
    val addPlanButton: TextView = view.findViewById(R.id.btn_add_plan)


    val rowAge: View = view.findViewById(R.id.row_age)
    val rowGender: View = view.findViewById(R.id.row_gender)
    val rowIcr: View = view.findViewById(R.id.row_icr)
    val rowIsf: View = view.findViewById(R.id.row_isf)
    val rowTargetGlucose: View = view.findViewById(R.id.row_target_glucose)
    val rowDoseRounding: View = view.findViewById(R.id.row_dose_rounding)

    fun setRowLabel(row: View, label: String) {
        row.findViewById<TextView>(R.id.tv_row_label).text = label
    }

    fun setRowValue(row: View, value: String) {
        row.findViewById<TextView>(R.id.tv_row_value).text = value
    }

    fun initRowLabels() {
        setRowLabel(rowAge, "Age")
        setRowLabel(rowGender, "Gender")
        setRowLabel(rowIcr, "ICR (Insulin-to-carb ratio)")
        setRowLabel(rowIsf, "ISF (Sensitivity factor)")
        setRowLabel(rowTargetGlucose, "Target glucose")
        setRowLabel(rowDoseRounding, "Dose rounding")
    }
}