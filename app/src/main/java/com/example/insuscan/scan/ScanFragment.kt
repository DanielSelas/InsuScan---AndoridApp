package com.example.insuscan.scan

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.insuscan.R
import com.example.insuscan.meal.Meal
import com.example.insuscan.meal.MealSessionManager

class ScanFragment : Fragment(R.layout.fragment_scan), ScanResultCallback {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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
            .setTitle(R.string.dialog_resume_scan_title)
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

        val mealWithGlucose = meal.copy(
            glucoseLevel = glucoseValue,
        )
        MealSessionManager.setCurrentMeal(mealWithGlucose)

        val confidence = meal.analysisConfidence
        if (confidence != null && confidence < LOW_CONFIDENCE_THRESHOLD) {
            val args = Bundle().apply { putBoolean(ARG_LOW_CONFIDENCE_SCAN, true) }
            findNavController().navigate(R.id.manualEntryFragment, args)
        } else {
            findNavController().navigate(R.id.summaryFragment)
        }
    }

    companion object {
        private const val LOW_CONFIDENCE_THRESHOLD = 0.7f
        const val ARG_LOW_CONFIDENCE_SCAN = "isLowConfidenceScan"
    }


}