package com.example.insuscan.history

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.insuscan.R
import com.example.insuscan.meal.MealSessionManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryFragment : Fragment(R.layout.fragment_history) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val lastTitle = view.findViewById<TextView>(R.id.tv_last_meal_title)
        val lastDetails = view.findViewById<TextView>(R.id.tv_last_meal_details)
        val lastTime = view.findViewById<TextView>(R.id.tv_last_meal_time)
        val previousHeader = view.findViewById<TextView>(R.id.tv_previous_meals_header)
        val recyclerView = view.findViewById<RecyclerView>(R.id.rv_meal_history)

        val scanNextButton = view.findViewById<Button>(R.id.btn_scan_next_meal)

        val allMeals = MealSessionManager.getHistory()
        val dateFormat = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())

        if (allMeals.isEmpty()) {
            lastTitle.text = "No meals yet"
            lastDetails.text = "Your meals and insulin doses will appear here."
            lastTime.text = ""

            previousHeader.text = "Previous meals"

            recyclerView.layoutManager =
                LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
            recyclerView.adapter = MealHistoryAdapter(emptyList())

            val snapHelper = PagerSnapHelper()
            snapHelper.attachToRecyclerView(recyclerView)
            // TODO: Persist empty/non-empty state using local storage (Room/SharedPreferences) if history should survive app restarts
            return
        }

        val lastMeal = allMeals.first()

        val lastCarbsText = "Carbs: ${lastMeal.carbs.toInt()} g"
        val lastInsulinText = if (lastMeal.insulinDose != null) {
            "Insulin: ${String.format("%.1f", lastMeal.insulinDose)} units"
        } else {
            "Insulin: -"
        }

        lastTitle.text = lastMeal.title
        lastDetails.text = "$lastCarbsText   |   $lastInsulinText"
        lastTime.text = "Time: ${dateFormat.format(Date(lastMeal.timestamp))}"

        val previousMeals = if (allMeals.size > 1) {
            allMeals.drop(1)
        } else {
            emptyList()
        }

        val itemsForAdapter = previousMeals.map { meal ->
            val carbsText = "Carbs: ${meal.carbs.toInt()} g"
            val insulinText = if (meal.insulinDose != null) {
                "Insulin: ${String.format("%.1f", meal.insulinDose)} units"
            } else {
                "Insulin: -"
            }
            val timeText = dateFormat.format(Date(meal.timestamp))

            MealHistoryItem(
                title = meal.title,
                carbsText = carbsText,
                insulinText = insulinText,
                timeText = timeText
            )
        }

        recyclerView.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)

        recyclerView.adapter = MealHistoryAdapter(itemsForAdapter)

        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(recyclerView)
        // TODO: Consider grouping meals by day or adding filters (e.g. by time of day or carbs range)

        scanNextButton.setOnClickListener {
            val bottomNav = requireActivity().findViewById<BottomNavigationView>(R.id.bottom_nav)
            bottomNav.selectedItemId = R.id.scanFragment
        }
    }
}