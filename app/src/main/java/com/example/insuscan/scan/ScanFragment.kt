package com.example.insuscan.scan

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.insuscan.MainActivity
import com.example.insuscan.R
import com.example.insuscan.meal.Meal
import com.example.insuscan.meal.MealSessionManager

class ScanFragment : Fragment(R.layout.fragment_scan) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val captureButton = view.findViewById<Button>(R.id.btn_capture)

        captureButton.setOnClickListener {

            // For now use a mock meal until backend is ready
            val mockMeal = Meal(
                title = "Chicken and rice",
                carbs = 48f
            )

            // Store the current scanned meal in session
            MealSessionManager.setCurrentMeal(mockMeal)

            // Once we have a meal, Summary tab should be available in bottom navigation
            (requireActivity() as? MainActivity)?.enableSummaryTab()

            // TODO: Replace mock meal + direct navigation with real flow after image processing backend
            findNavController().navigate(R.id.summaryFragment)
        }
    }
}