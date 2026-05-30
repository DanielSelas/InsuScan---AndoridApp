package com.example.insuscan.scan

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.insuscan.R
import com.example.insuscan.meal.Meal
import com.example.insuscan.meal.MealSessionManager
import com.example.insuscan.utils.TopBarHelper

class ScanFragment : Fragment(R.layout.fragment_scan), ScanResultCallback {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        TopBarHelper.setupTopBar(
            rootView = view,
            title = "Scan your meal",
            onBack = { findNavController().navigate(R.id.homeFragment) }
        )
        if (savedInstanceState == null) {
            if (MealSessionManager.currentMeal != null) {
                showResumeOrNewScanDialog()
            } else {
                loadCameraFragment()
            }
        }
    }

    private fun showResumeOrNewScanDialog() {
        android.app.AlertDialog.Builder(requireContext())
            .setTitle("Resume scan?")
            .setMessage("You have an unsaved scan from earlier.")
            .setCancelable(false)
            .setPositiveButton("View Summary") { _, _ ->
                findNavController().navigate(R.id.summaryFragment)
            }
            .setNegativeButton("New Scan") { _, _ ->
                MealSessionManager.clearSession()
                loadCameraFragment()
            }
            .show()
    }

    private fun loadCameraFragment() {
        childFragmentManager.beginTransaction()
            .replace(R.id.camera_scan_container, CameraScanFragment())
            .commit()
    }

    override fun onScanSuccess(meal: Meal) {
        val glucoseInput = childFragmentManager.findFragmentById(R.id.camera_scan_container)
            ?.view?.findViewById<android.widget.EditText>(R.id.et_glucose_level)
        val glucoseValue = glucoseInput?.text?.toString()?.toIntOrNull()
//        val glucoseUnits = com.example.insuscan.profile.UserProfileManager.getGlucoseUnits(requireContext())

        val mealWithGlucose = meal.copy(
            glucoseLevel = glucoseValue,
//            glucoseUnits = glucoseUnits
        )
        MealSessionManager.setCurrentMeal(mealWithGlucose)
        findNavController().navigate(R.id.summaryFragment)
    }
}