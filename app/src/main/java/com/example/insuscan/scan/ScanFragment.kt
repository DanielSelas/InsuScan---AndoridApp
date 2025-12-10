package com.example.insuscan.scan

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.example.insuscan.R
import com.example.insuscan.meal.Meal
import com.example.insuscan.meal.MealSessionManager

class ScanFragment : Fragment(R.layout.fragment_scan) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val captureButton = view.findViewById<Button>(R.id.btn_capture)

        captureButton.setOnClickListener {

            val mockMeal = Meal(
                title = "Chicken and rice",
                carbs = 48f
            )

            MealSessionManager.setCurrentMeal(mockMeal)

            // TODO: Replace mock meal with real backend response based on captured image
            findNavController().navigate(R.id.summaryFragment)
        }
    }
}