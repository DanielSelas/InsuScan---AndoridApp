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
            // just move to the summary atm, in the future from here will send photo to the server and will wait for an answer

            // TEMP MOCK: simulate a detected meal instead of real server response
            val mockMeal = Meal(
                title = "Chicken and rice",
                carbs = 48f
            )

            // Save the detected meal as the current active meal
            MealSessionManager.setCurrentMeal(mockMeal)

            // Move to summary
            findNavController().navigate(R.id.summaryFragment)
        }
    }
}