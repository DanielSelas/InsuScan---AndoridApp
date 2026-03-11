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
            childFragmentManager.beginTransaction()
                .replace(R.id.camera_scan_container, CameraScanFragment())
                .commit()
        }
    }

    override fun onScanSuccess(meal: Meal) {
        MealSessionManager.setCurrentMeal(meal)
        findNavController().navigate(R.id.summaryFragment)
    }
}